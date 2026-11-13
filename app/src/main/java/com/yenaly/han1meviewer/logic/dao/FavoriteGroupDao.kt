package com.yenaly.han1meviewer.logic.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yenaly.han1meviewer.logic.entity.FavoriteGroupEntity
import kotlinx.coroutines.flow.Flow

/**
 * 本地收藏分组 DAO
 *
 * @author Yenaly Liew
 */
@Dao
abstract class FavoriteGroupDao {

    @Query("SELECT * FROM FavoriteGroupEntity ORDER BY createdAt ASC")
    abstract fun loadAll(): Flow<List<FavoriteGroupEntity>>

    @Query("SELECT * FROM FavoriteGroupEntity ORDER BY createdAt ASC")
    abstract suspend fun getAll(): List<FavoriteGroupEntity>

    @Query("SELECT * FROM FavoriteGroupEntity WHERE (`id` = :groupId) LIMIT 1")
    abstract suspend fun findBy(groupId: Int): FavoriteGroupEntity?

    @Insert
    abstract suspend fun insert(group: FavoriteGroupEntity): Long

    @Insert
    abstract suspend fun insertAll(groups: List<FavoriteGroupEntity>)

    @Update
    abstract suspend fun update(group: FavoriteGroupEntity)

    @Delete
    abstract suspend fun delete(group: FavoriteGroupEntity)

    @Query("DELETE FROM FavoriteGroupEntity")
    abstract suspend fun deleteAll()

    /** 将该分组下的收藏全部移入未分组（groupId 置空）。 */
    @Query("UPDATE FavoriteEntity SET groupId = NULL WHERE groupId = :groupId")
    abstract suspend fun clearGroup(groupId: Int)

    /**
     * 删除分组，组内收藏自动移入未分组，事务保证原子性。
     */
    @Transaction
    open suspend fun deleteWithFavorites(group: FavoriteGroupEntity) {
        clearGroup(group.id)
        delete(group)
    }
}
