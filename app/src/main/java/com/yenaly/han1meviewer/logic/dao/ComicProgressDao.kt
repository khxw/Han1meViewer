package com.yenaly.han1meviewer.logic.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yenaly.han1meviewer.logic.entity.ComicProgressEntity

/** 漫画阅读进度 DAO。 */
@Dao
abstract class ComicProgressDao {

    @Query("SELECT * FROM ComicProgressEntity WHERE comicId = :comicId LIMIT 1")
    abstract suspend fun findBy(comicId: String): ComicProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: ComicProgressEntity)

    @Query("DELETE FROM ComicProgressEntity WHERE comicId = :comicId")
    abstract suspend fun delete(comicId: String)
}
