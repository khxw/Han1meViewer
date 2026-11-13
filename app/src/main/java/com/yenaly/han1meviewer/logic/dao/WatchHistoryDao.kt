package com.yenaly.han1meviewer.logic.dao

import androidx.room.*
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/07/02 002 16:47
 */
@Dao
abstract class WatchHistoryDao {

    @Query("SELECT * FROM WatchHistoryEntity ORDER BY watchDate DESC")
    abstract fun loadAll(): Flow<MutableList<WatchHistoryEntity>>

    @Query("SELECT * FROM WatchHistoryEntity ORDER BY watchDate DESC")
    abstract suspend fun getAll(): List<WatchHistoryEntity>

    @Delete
    abstract suspend fun delete(history: WatchHistoryEntity)

    @Query("DELETE FROM WatchHistoryEntity")
    abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(history: WatchHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(histories: List<WatchHistoryEntity>)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun update(history: WatchHistoryEntity)

    @Query("SELECT * FROM WatchHistoryEntity WHERE (`videoCode` = :videoCode) LIMIT 1")
    abstract suspend fun findBy(videoCode: String): WatchHistoryEntity?

    @Query("SELECT * FROM WatchHistoryEntity WHERE videoCode IN (:videoCodes)")
    abstract suspend fun findByVideoCodes(videoCodes: List<String>): List<WatchHistoryEntity>

    @Query("SELECT * FROM WatchHistoryEntity ORDER BY watchDate DESC LIMIT :limit")
    abstract suspend fun getRecentWatches(limit: Int): List<WatchHistoryEntity>

    @Query(
        """
        SELECT * FROM WatchHistoryEntity
        WHERE progress > 0 AND isCompleted = 0
        ORDER BY watchDate DESC
        LIMIT :limit
        """
    )
    abstract fun loadContinueWatching(limit: Int): Flow<List<WatchHistoryEntity>>

    @Query("UPDATE WatchHistoryEntity SET progress = :progress WHERE videoCode = :videoCode")
    abstract suspend fun updateProgress(videoCode: String, progress: Long)

    @Query(
        """
        UPDATE WatchHistoryEntity
        SET progress = :progress,
            duration = CASE WHEN :duration > 0 THEN :duration ELSE duration END
        WHERE videoCode = :videoCode
        """
    )
    abstract suspend fun updateProgress(videoCode: String, progress: Long, duration: Long)

    @Query(
        """
        UPDATE WatchHistoryEntity
        SET progress = CASE WHEN :duration > 0 THEN :duration ELSE progress END,
            duration = CASE WHEN :duration > 0 THEN :duration ELSE duration END,
            isCompleted = 1
        WHERE videoCode = :videoCode
        """
    )
    abstract suspend fun markCompleted(videoCode: String, duration: Long)

    @Query("UPDATE WatchHistoryEntity SET isCompleted = 0 WHERE videoCode = :videoCode")
    abstract suspend fun resetCompleted(videoCode: String)

    @Transaction
    open suspend fun insertOrUpdate(history: WatchHistoryEntity) {
        val dbEntity = findBy(history.videoCode)
        if (dbEntity != null) {
            val merged = history.copy(
                id = dbEntity.id,
                progress = if (history.progress == 0L) dbEntity.progress else history.progress,
                duration = if (history.duration == 0L) dbEntity.duration else history.duration,
                isCompleted = when {
                    history.isCompleted -> true
                    history.progress > 0L -> false
                    else -> dbEntity.isCompleted
                },
            )
            update(merged)
        } else {
            insert(history)
        }
    }
}
