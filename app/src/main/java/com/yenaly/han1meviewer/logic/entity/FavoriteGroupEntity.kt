package com.yenaly.han1meviewer.logic.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 本地收藏分组实体。
 *
 * 用于将本地收藏（离线收藏）按用户自定义分组整理。
 *
 * @author Yenaly Liew
 */
@Serializable
@Entity
data class FavoriteGroupEntity(
    val name: String,
    val createdAt: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
)
