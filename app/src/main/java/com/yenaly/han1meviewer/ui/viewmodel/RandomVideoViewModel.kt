package com.yenaly.han1meviewer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.HomePageCache
import com.yenaly.han1meviewer.logic.NetworkRepo
import com.yenaly.han1meviewer.logic.RandomVideoPreferences
import com.yenaly.han1meviewer.logic.log.HLumberjack
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.HomePage
import com.yenaly.han1meviewer.logic.model.RandomVideoOptions
import com.yenaly.han1meviewer.logic.model.RandomVideoSource
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.util.TagLocalizer
import com.yenaly.han1meviewer.util.isTitleBlocked
import com.yenaly.han1meviewer.util.markWatchedList
import com.yenaly.yenaly_libs.base.YenalyViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 随机视频 ViewModel
 *
 * 支持首页、本地收藏、观看历史和已下载视频候选，并可排除已看、
 * 已下载和已收藏内容。所有筛选只作用于本地已加载数据。
 *
 * @author Yenaly Liew
 */
class RandomVideoViewModel(application: Application) : YenalyViewModel(application) {

    companion object {
        /** 一次随机展示的视频数量 */
        private const val MAX_VIDEOS = 24

        /** 详情标签校验的并行批量大小 */
        private const val VERIFY_BATCH_SIZE = 12

        /** 持久化保留的最近随机记录数量 */
        private const val MAX_RECENT_VIDEO_CODES = 240

        /**
         * 每个分类额外抓取的搜索页（第 2、3 页）。
         * 首页分类本身只有一页，对一次抽 24 条的随机来说太小。
         */
        private val EXTRA_SEARCH_PAGES = 2..3

        /** 每次刷新最多补抓多少个「尚未校验标签」的视频详情，避免一次刷爆站点。 */
        private const val MAX_TAG_FETCH_PER_REFRESH = 30

        /** 标签校验后保留的随机池上限（远大于一次展示的 24 条）。 */
        private const val MAX_VERIFIED_POOL = 120
    }

    private val _randomFlow =
        MutableStateFlow<PageLoadingState<List<HanimeInfo>>>(PageLoadingState.Loading)
    val randomFlow = _randomFlow.asStateFlow()

    private val _optionsFlow = MutableStateFlow(RandomVideoPreferences.loadOptions())
    val optionsFlow = _optionsFlow.asStateFlow()

    /** 本次会话已经展示过的视频，优先抽取未展示项。 */
    private val recentVideoCodes = ArrayDeque<String>().apply {
        addAll(RandomVideoPreferences.loadRecentVideoCodes())
    }

    /** 视频标签校验缓存：videoCode -> 是否命中屏蔽标签。 */
    private val tagVerificationCache = mutableMapOf<String, Boolean>()

    /** 缓存对应的屏蔽标签快照；屏蔽项变化时清空缓存。 */
    private var tagCacheSignature: Set<String> = emptySet()

    /** 下载来源的本地播放地址。 */
    /** 是否已执行过随机加载；返回页面时防止自动重新随机 */
    private var hasLoaded = false
    private var loadJob: Job? = null

    fun updateOptions(options: RandomVideoOptions) {
        val sanitized = sanitizeOptions(options)
        RandomVideoPreferences.saveOptions(sanitized)
        _optionsFlow.value = sanitized
        loadRandom(force = true)
    }

    fun loadRandom(force: Boolean = false) {
        if (hasLoaded && !force) return
        hasLoaded = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _randomFlow.value = PageLoadingState.Loading
            val options = _optionsFlow.value
            try {
                val pool = fetchCandidates(options)
                if (pool.isEmpty()) {
                    _randomFlow.value = PageLoadingState.Error(
                        Throwable("no available random video")
                    )
                    return@launch
                }

                val selected = selectRandom(pool, options.avoidRecent)
                if (selected.isEmpty()) {
                    _randomFlow.value = PageLoadingState.Error(
                        Throwable("no available random video")
                    )
                    return@launch
                }

                rememberSelected(selected)
                val markWatched = markWatchedList(selected)
                _randomFlow.value = PageLoadingState.Success(markWatched)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _randomFlow.value = PageLoadingState.Error(e)
            }
        }
    }

    private suspend fun fetchCandidates(options: RandomVideoOptions): List<HanimeInfo> {
        val page = fetchHomePage() ?: return emptyList()
        val sources = options.sources.ifEmpty { setOf(RandomVideoSource.LATEST_HANIME) }
        // 首页分类 + 额外的搜索页一起进池子，多个来源再合并。
        val expanded = coroutineScope {
            sources.map { source ->
                async {
                    val homeList = when (source) {
                        RandomVideoSource.LATEST_HANIME -> page.latestHanime
                        RandomVideoSource.SHORT_EPISODE -> page.shortEpisodeAnime
                        RandomVideoSource.THREE_DCG -> page.threeDCG
                    }
                    homeList + fetchCategoryPages(source)
                }
            }.awaitAll().flatten()
        }
        val candidates = expanded
            .distinctBy { it.videoCode }
            .filterNot { isTagBlocked(it.tags) }
            .filterNot { isTitleBlocked(it.title) }
        HLumberjack.i("RandomVideo", "随机池: ${candidates.size} 条, 来源=$sources")

        val filtered = applyExclusions(candidates, options)
        return verifyUnknownTags(filtered)
    }

    /** 按分类拉取第 2、3 页搜索结果，用于扩大随机池；失败时返回空列表。 */
    private suspend fun fetchCategoryPages(source: RandomVideoSource): List<HanimeInfo> =
        withContext(Dispatchers.IO) {
            val (genre, sort) = when (source) {
                RandomVideoSource.LATEST_HANIME -> "裏番" to "最新上傳"
                RandomVideoSource.SHORT_EPISODE -> "泡麵番" to "最新上傳"
                RandomVideoSource.THREE_DCG -> "3DCG" to "最新上傳"
            }
            EXTRA_SEARCH_PAGES.flatMap { pageIndex ->
                runCatching {
                    NetworkRepo.getHanimeSearchResult(
                        page = pageIndex,
                        query = null,
                        genre = genre,
                        sort = sort,
                        broad = false,
                        date = null,
                        duration = null,
                        tags = emptySet(),
                        brands = emptySet(),
                    ).first { it !is PageLoadingState.Loading }
                }.onFailure {
                    HLumberjack.w(
                        "RandomVideo",
                        "$source 第 $pageIndex 页拉取失败: ${it.message}",
                    )
                }.getOrNull().let { state ->
                    // PageLoadingState.NoMoreData 没有 info，直接当作「本页没数据」
                    val items = if (state is PageLoadingState.Success) state.info else emptyList()
                    HLumberjack.i("RandomVideo", "$source 第 $pageIndex 页: ${items.size} 条")
                    items
                }
            }
        }

    /** 首页数据：优先网络，失败时回退本地缓存。 */
    private suspend fun fetchHomePage(): HomePage? = withContext(Dispatchers.IO) {
        runCatching {
            NetworkRepo.getHomePage().first { it !is WebsiteState.Loading }
        }.getOrNull()?.let { (it as? WebsiteState.Success)?.info }
    } ?: withContext(Dispatchers.IO) {
        HomePageCache.load()
    }

    private suspend fun applyExclusions(
        candidates: List<HanimeInfo>,
        options: RandomVideoOptions,
    ): List<HanimeInfo> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyList()
        val codes = candidates.map { it.videoCode }
        var result = candidates

        if (options.excludeWatched) {
            val watchedCodes = DatabaseRepo.WatchHistory.getWatched(codes).toSet()
            result = result.filterNot { it.videoCode in watchedCodes }
        }
        if (options.excludeDownloaded) {
            val downloadedCodes = DatabaseRepo.HanimeDownload.getAll()
                .asSequence()
                .filter { it.isDownloaded && it.videoCode in codes }
                .map { it.videoCode }
                .toSet()
            result = result.filterNot { it.videoCode in downloadedCodes }
        }
        if (options.excludeFavorited) {
            val favoriteCodes = DatabaseRepo.Favorite.getAll()
                .asSequence()
                .filter { it.videoCode in codes }
                .map { it.videoCode }
                .toSet()
            result = result.filterNot { it.videoCode in favoriteCodes }
        }
        result
    }

    private suspend fun verifyUnknownTags(candidates: List<HanimeInfo>): List<HanimeInfo> {
        val blockedTags = Preferences.blockedTags
        val needsTagVerification =
            Preferences.blockTagsEnabled && blockedTags.isNotEmpty()
        if (!needsTagVerification) return candidates

        // 屏蔽标签被修改过：旧缓存不再可信，清空重来
        if (blockedTags != tagCacheSignature) {
            tagVerificationCache.clear()
            tagCacheSignature = blockedTags
        }

        return coroutineScope {
            val verified = mutableListOf<HanimeInfo>()
            var index = 0
            // 每次刷新只补抓有限个「未知」详情，已缓存的直接复用
            var fetchBudget = MAX_TAG_FETCH_PER_REFRESH
            while (verified.size < MAX_VERIFIED_POOL && index < candidates.size) {
                val end = minOf(index + VERIFY_BATCH_SIZE, candidates.size)
                val batch = candidates.subList(index, end)
                index = end

                val unknown = mutableListOf<HanimeInfo>()
                for (video in batch) {
                    when (tagVerificationCache[video.videoCode]) {
                        true -> Unit // 命中屏蔽标签，丢弃
                        false -> if (verified.size < MAX_VERIFIED_POOL) verified += video
                        null -> unknown += video
                    }
                }
                if (unknown.isEmpty() || fetchBudget <= 0) continue

                val toFetch = unknown.take(fetchBudget)
                fetchBudget -= toFetch.size
                val results = toFetch
                    .map { video -> async { video to fetchVideoTags(video.videoCode) } }
                    .awaitAll()
                for ((video, detailTags) in results) {
                    if (verified.size >= MAX_VERIFIED_POOL) break
                    // 拉取失败不写缓存，下次刷新再试；本次保守地不加入
                    if (detailTags.isNullOrEmpty()) continue
                    val blocked = isTagBlocked(detailTags)
                    tagVerificationCache[video.videoCode] = blocked
                    if (!blocked) verified += video
                }
            }
            HLumberjack.i(
                "RandomVideo",
                "标签校验: 候选 ${candidates.size} 条, 可用 ${verified.size} 条",
            )
            verified
        }
    }

    private fun selectRandom(
        candidates: List<HanimeInfo>,
        avoidRecent: Boolean,
    ): List<HanimeInfo> {
        if (!avoidRecent) return candidates.shuffled().take(MAX_VIDEOS)

        // deque 里 index 越小越新、越大越旧。
        val recentOrder = recentVideoCodes.withIndex().associate { (index, code) -> code to index }
        val unseen = candidates.filterNot { it.videoCode in recentOrder }.shuffled()
        // 池子不够时才回收旧内容，并且优先用最久没出现过的，避免刚刷新就重复。
        val recycled = candidates
            .filter { it.videoCode in recentOrder }
            .shuffled()
            .sortedByDescending { recentOrder[it.videoCode] ?: Int.MAX_VALUE }
        return (unseen + recycled).take(MAX_VIDEOS)
    }

    private fun rememberSelected(videos: List<HanimeInfo>) {
        videos.asReversed().forEach { video ->
            recentVideoCodes.remove(video.videoCode)
            recentVideoCodes.addFirst(video.videoCode)
        }
        while (recentVideoCodes.size > MAX_RECENT_VIDEO_CODES) {
            recentVideoCodes.removeLast()
        }
        RandomVideoPreferences.saveRecentVideoCodes(recentVideoCodes.toList())
    }

    private fun sanitizeOptions(options: RandomVideoOptions): RandomVideoOptions {
        // 至少保留一个来源，否则随机池为空
        return if (options.sources.isEmpty()) {
            options.copy(sources = setOf(RandomVideoSource.LATEST_HANIME))
        } else {
            options
        }
    }

    /** 请求视频详情页获取真实标签，失败返回 null。 */
    private suspend fun fetchVideoTags(videoCode: String): List<String>? {
        return withContext(Dispatchers.IO) {
            runCatching {
                NetworkRepo.getHanimeVideo(videoCode)
                    .first { it !is VideoLoadingState.Loading }
            }.getOrNull()?.let { (it as? VideoLoadingState.Success)?.info?.tags }
        }
    }

    /** 是否命中屏蔽标签（开启屏蔽且标签列表命中时返回 true） */
    private fun isTagBlocked(tags: List<String>): Boolean {
        if (!Preferences.blockTagsEnabled) return false
        val blockedTags = Preferences.blockedTags
        if (blockedTags.isEmpty()) return false
        if (tags.isEmpty()) return false

        val videoTagSet = buildSet {
            tags.forEach { raw ->
                TagLocalizer.tagVariants(raw).forEach { add(normalizeTag(it)) }
            }
        }
        val blockedTagSet = buildSet {
            blockedTags.forEach { raw ->
                TagLocalizer.tagVariants(raw).forEach { add(normalizeTag(it)) }
            }
        }
        return videoTagSet.any { it in blockedTagSet }
    }

    private fun normalizeTag(tag: String): String =
        tag.substringBefore(" (")
            .substringBefore("（")
            .removePrefix("#")
            .trim()
            .lowercase()
            .replace(" ", "")
            .replace("　", "")
}
