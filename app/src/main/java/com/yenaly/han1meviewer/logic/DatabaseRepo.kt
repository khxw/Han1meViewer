package com.yenaly.han1meviewer.logic

import android.util.Log
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.dao.HistoryDatabase
import com.yenaly.han1meviewer.logic.dao.MiscellanyDatabase
import com.yenaly.han1meviewer.logic.entity.ComicProgressEntity
import com.yenaly.han1meviewer.logic.entity.FavoriteEntity
import com.yenaly.han1meviewer.logic.entity.FavoriteGroupEntity
import com.yenaly.han1meviewer.logic.entity.HKeyframeEntity
import com.yenaly.han1meviewer.logic.entity.HKeyframeHeader
import com.yenaly.han1meviewer.logic.entity.HKeyframeType
import com.yenaly.han1meviewer.logic.entity.HanimeAdvancedSearchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.SearchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.download.DownloadGroupEntity
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.model.SearchOption
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.FileNotFoundException

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/22 022 23:00
 */
object DatabaseRepo {

    object HKeyframe {
        private val hKeyframeDao = MiscellanyDatabase.instance.hKeyframeDao

        fun loadAll(keyword: String? = null) =
            if (keyword != null) hKeyframeDao.loadAll(keyword)
            else hKeyframeDao.loadAll()

        // #issue-106: 剧集分类
        @OptIn(ExperimentalSerializationApi::class)
        fun loadAllShared(): Flow<List<HKeyframeType>> = flow {
            val res = applicationContext.assets.let { assets ->
                assets.list("h_keyframes")?.asSequence()
                    ?.filter { it.endsWith(".json") }
                    ?.mapNotNull { fileName ->
                        try {
                            assets.open("h_keyframes/$fileName").use { inputStream ->
                                Json.decodeFromStream<HKeyframeEntity>(inputStream)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                    ?.sortedWith(
                        compareBy<HKeyframeEntity> { it.group }.thenBy { it.episode }
                    )
                    ?.groupBy { it.group ?: "???" }
                    ?.flatMap { (group, entities) ->
                        listOf(HKeyframeHeader(title = group, attached = entities)) + entities
                    }
                    .orEmpty()
            }
            emit(res)
        }

        suspend fun findBy(videoCode: String) =
            hKeyframeDao.findBy(videoCode)

        @OptIn(ExperimentalSerializationApi::class)
        fun observe(videoCode: String): Flow<HKeyframeEntity?> {
            if (Preferences.sharedHKeyframesEnable) {
                return flow t@{
                    val find = hKeyframeDao.findBy(videoCode)
                    if (find == null || Preferences.sharedHKeyframesUseFirst) {
                        runCatching {
                            applicationContext.assets
                                .open("h_keyframes/$videoCode.json")
                                .use { inputStream ->
                                    val entity = Json.decodeFromStream<HKeyframeEntity>(inputStream)
                                    this@t.emit(entity)
                                }
                        }.onFailure { e ->
                            // 文件不存在或解析错误
                            if (e is FileNotFoundException) {
                                Log.w("HKeyframe", "未找到关键帧文件: $videoCode.json")
                            } else {
                                Log.e("HKeyframe", "读取关键帧失败: ${e.message}", e)
                            }
                        }
                    } else {
                        hKeyframeDao.observe(videoCode).collect {
                            this@t.emit(it)
                        }
                    }
                }.catch t@{ e ->
                    e.printStackTrace()
                    hKeyframeDao.observe(videoCode).collect {
                        this@t.emit(it)
                    }
                }
            }
            return hKeyframeDao.observe(videoCode)
        }

        suspend fun insert(entity: HKeyframeEntity) = hKeyframeDao.insert(entity)

        suspend fun update(entity: HKeyframeEntity) = hKeyframeDao.update(entity)

        suspend fun delete(entity: HKeyframeEntity) =
            hKeyframeDao.delete(entity)

        suspend fun modifyKeyframe(
            videoCode: String,
            oldKeyframe: HKeyframeEntity.Keyframe, keyframe: HKeyframeEntity.Keyframe,
        ) = hKeyframeDao.modifyKeyframe(videoCode, oldKeyframe, keyframe)

        suspend fun appendKeyframe(
            videoCode: String, title: String,
            keyframe: HKeyframeEntity.Keyframe,
        ) = hKeyframeDao.appendKeyframe(videoCode, title, keyframe)

        suspend fun removeKeyframe(
            videoCode: String,
            keyframe: HKeyframeEntity.Keyframe,
        ) = hKeyframeDao.removeKeyframe(videoCode, keyframe)
    }

    object SearchHistory {
        private val searchHistoryDao = HistoryDatabase.instance.searchHistory

        @JvmOverloads
        fun loadAll(keyword: String? = null) =
            if (keyword.isNullOrBlank()) searchHistoryDao.loadAll()
            else searchHistoryDao.loadAll(keyword)

        suspend fun delete(history: SearchHistoryEntity) =
            searchHistoryDao.delete(history)

        suspend fun insert(history: SearchHistoryEntity) =
            searchHistoryDao.insertOrUpdate(history)

        suspend fun deleteByKeyword(query: String) =
            searchHistoryDao.deleteByKeyword(query)
    }

    object HanimeAdvancedSearchRepo {
        private val dao = HistoryDatabase.instance.hanimeAdvancedSearchHistory

        suspend fun saveSearch(
            query: String?,
            genre: String?,
            sort: String?,
            broad: Boolean?,
            date: String?,
            duration: String?,
            tags: Set<SearchOption>?,
            brands: Set<SearchOption>?
        ) {
            val entity = HanimeAdvancedSearchHistoryEntity(
                query = query,
                genre = genre,
                sort = sort,
                broad = broad,
                date = date,
                duration = duration,
                tags = tags?.toDbString(),
                brands = brands?.toDbString()
            )
            dao.insertHistory(entity)
        }

        fun getSearchHistories(limit: Int = 20) = dao.loadHistories(limit)
        suspend fun deleteHistory(id: Long) = dao.deleteHistory(id)
        fun Set<SearchOption>.toDbString(): String =
            mapNotNull { it.searchKey }.joinToString(",")
        fun String.toSearchOptionSet(): Set<SearchOption> =
            if (isBlank()) emptySet()
            else split(",").map { SearchOption(searchKey = it) }.toSet()
    }

    object WatchHistory {
        private val watchHistoryDao = HistoryDatabase.instance.watchHistory

        fun loadAll() =
            watchHistoryDao.loadAll()

        suspend fun getAll() =
            watchHistoryDao.getAll()

        fun loadContinueWatching(limit: Int = 10) =
            watchHistoryDao.loadContinueWatching(limit)

        suspend fun delete(history: WatchHistoryEntity) =
            watchHistoryDao.delete(history)

        suspend fun deleteAll() =
            watchHistoryDao.deleteAll()

        suspend fun update(history: WatchHistoryEntity) =
            watchHistoryDao.update(history)

        suspend fun updateProgress(videoCode: String,progress: Long) =
            watchHistoryDao.updateProgress(videoCode, progress)

        suspend fun updateProgress(videoCode: String, progress: Long, duration: Long) =
            watchHistoryDao.updateProgress(videoCode, progress, duration)

        suspend fun markCompleted(videoCode: String, duration: Long) =
            watchHistoryDao.markCompleted(videoCode, duration)

        suspend fun resetCompleted(videoCode: String) =
            watchHistoryDao.resetCompleted(videoCode)

        suspend fun insert(history: WatchHistoryEntity) =
            watchHistoryDao.insertOrUpdate(history)

        suspend fun findBy(videoCode: String) =
            watchHistoryDao.findBy(videoCode)

        /**
         * 返回真正「已看」的视频编号。
         *
         * 以前直接查「历史表里有没有记录」，点开就算已看；现在按
         * [com.yenaly.han1meviewer.logic.model.WatchProgress] 的统一规则过滤：
         * 看完 或 进度 ≥ 90%。
         */
        suspend fun getWatched(resultList: List<String>): List<String> =
            watchHistoryDao.findByVideoCodes(resultList)
                .filter { it.isWatched }
                .map { it.videoCode }
    }

    object Favorite {
        private val favoriteDao = HistoryDatabase.instance.favorite

        fun loadAll() =
            favoriteDao.loadAll()

        suspend fun getAll() =
            favoriteDao.getAll()

        suspend fun findBy(videoCode: String) =
            favoriteDao.findBy(videoCode)

        suspend fun delete(favorite: FavoriteEntity) =
            favoriteDao.delete(favorite)

        suspend fun delete(videoCode: String) =
            favoriteDao.findBy(videoCode)?.let { favoriteDao.delete(it) }

        suspend fun deleteAll() =
            favoriteDao.deleteAll()

        suspend fun insert(favorite: FavoriteEntity) =
            favoriteDao.insertOrUpdate(favorite)

        suspend fun update(favorite: FavoriteEntity) =
            favoriteDao.update(favorite)

        /** 批量移动收藏到指定分组，groupId 为 null 表示移入未分组 */
        suspend fun moveToGroup(ids: List<Int>, groupId: Int?) =
            favoriteDao.updateGroupIds(ids, groupId)
    }

    /** 漫画阅读进度。 */
    object ComicProgress {
        private val dao = HistoryDatabase.instance.comicProgress

        suspend fun getPage(comicId: String): Int =
            dao.findBy(comicId)?.page ?: 0

        suspend fun save(comicId: String, page: Int) =
            dao.upsert(
                ComicProgressEntity(
                    comicId = comicId,
                    page = page,
                    updatedAt = System.currentTimeMillis(),
                )
            )
    }

    object FavoriteGroup {
        private val favoriteGroupDao = HistoryDatabase.instance.favoriteGroup

        fun loadAll() =
            favoriteGroupDao.loadAll()

        suspend fun getAll() =
            favoriteGroupDao.getAll()

        /** 创建分组并返回新分组 id */
        suspend fun insert(name: String): Int {
            val id = favoriteGroupDao.insert(
                FavoriteGroupEntity(name = name, createdAt = System.currentTimeMillis())
            )
            return id.toInt()
        }

        suspend fun rename(group: FavoriteGroupEntity, name: String) =
            favoriteGroupDao.update(group.copy(name = name))

        /** 删除分组，组内收藏自动移入未分组 */
        suspend fun deleteWithFavorites(group: FavoriteGroupEntity) =
            favoriteGroupDao.deleteWithFavorites(group)
    }

    object HanimeDownload {
        private val hanimeDownloadDao = DownloadDatabase.instance.hanimeDownloadDao
        private val downloadGroupDao = DownloadDatabase.instance.downloadGroupDao
        fun loadAllDownloadingHanime() =
            hanimeDownloadDao.loadAllDownloadingHanime()

        suspend fun getAll() =
            hanimeDownloadDao.getAll()

        /**
         * 查询所有视频，并且每个视频要有当前他在的分类
         */
        fun loadAllDownloadedHanime(
            sortedBy: HanimeDownloadEntity.SortedBy,
            ascending: Boolean,
        ) = when (sortedBy) {
            HanimeDownloadEntity.SortedBy.TITLE ->
                hanimeDownloadDao.loadAllDownloadedHanimeByTitle(ascending)

            HanimeDownloadEntity.SortedBy.ID ->
                hanimeDownloadDao.loadAllDownloadedHanimeById(ascending)
        }
        suspend fun delete(videoCode: String, quality: String) =
            hanimeDownloadDao.delete(videoCode, quality)

        suspend fun delete(videoCode: String) =
            hanimeDownloadDao.delete(videoCode)

        suspend fun pauseAll() =
            hanimeDownloadDao.pauseAll()

        suspend fun delete(entity: HanimeDownloadEntity) =
            hanimeDownloadDao.delete(entity)

        suspend fun insert(entity: HanimeDownloadEntity) =
            hanimeDownloadDao.insert(entity)

        suspend fun update(entity: HanimeDownloadEntity) =
            hanimeDownloadDao.update(entity)

        suspend fun find(videoCode: String, quality: String) =
            hanimeDownloadDao.find(videoCode, quality)

        suspend fun find(videoCode: String) =
            hanimeDownloadDao.find(videoCode)

        suspend fun insertDefaultGroup() =
            downloadGroupDao.insertDefaultGroup()

        fun getAllGroups()=
            downloadGroupDao.getAllGroups()

        suspend fun getGroupById(id: Int)=
            downloadGroupDao.getGroupById(id)

        suspend fun updateVideoGroup(videoCode: String, newGroupId: Int)=
            hanimeDownloadDao.updateVideoGroup(videoCode, newGroupId)

        suspend fun createNewGroup(name: String): Long{
            val maxIndex = downloadGroupDao.getMaxOrderIndex() ?: 0
            val newIndex = maxIndex + 1
            val newGroup = DownloadGroupEntity(
                name = name,
                orderIndex = newIndex
            )
            return downloadGroupDao.insert(newGroup)
        }
        suspend fun deleteGroup(group: DownloadGroupEntity) {
            downloadGroupDao.deleteGroup(group)
        }

        suspend fun updateGroup(group: DownloadGroupEntity)=
            downloadGroupDao.update(group)
    }
}
