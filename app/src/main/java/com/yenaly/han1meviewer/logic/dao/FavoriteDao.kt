package com.yenaly.han1meviewer.logic.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yenaly.han1meviewer.logic.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 本地收藏（离线收藏）DAO
 *
 * @author Yenaly Liew
 */
@Dao
abstract class FavoriteDao {

    @Query("SELECT * FROM FavoriteEntity ORDER BY savedAt DESC")
    abstract fun loadAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM FavoriteEntity ORDER BY savedAt DESC")
    abstract suspend fun getAll(): List<FavoriteEntity>

    @Query("SELECT * FROM FavoriteEntity WHERE (`videoCode` = :videoCode) LIMIT 1")
    abstract suspend fun findBy(videoCode: String): FavoriteEntity?

    /** 批量移动收藏到指定分组，groupId 为 null 表示移入未分组 */
    @Query("UPDATE FavoriteEntity SET groupId = :groupId WHERE id IN (:ids)")
    abstract suspend fun updateGroupIds(ids: List<Int>, groupId: Int?)

    @Delete
    abstract suspend fun delete(favorite: FavoriteEntity)

    @Query("DELETE FROM FavoriteEntity")
    abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(favorite: FavoriteEntity)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun update(favorite: FavoriteEntity)

    @Transaction
    open suspend fun insertOrUpdate(favorite: FavoriteEntity) {
        val dbEntity = findBy(favorite.videoCode)
        if (dbEntity != null) {
            update(favorite.copy(id = dbEntity.id))
        } else {
            insert(favorite)
        }
    }
}
