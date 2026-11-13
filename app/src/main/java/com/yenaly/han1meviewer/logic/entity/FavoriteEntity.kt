package com.yenaly.han1meviewer.logic.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 本地收藏（离线收藏）实体。
 *
 * 不依赖 Hanime 账号，数据仅保存在本机数据库，离线也可查看收藏列表。
 *
 * @author Yenaly Liew
 */
@Serializable
@Entity
data class FavoriteEntity(
    val videoCode: String,
    val title: String,
    val coverUrl: String,
    val releaseDate: Long,
    val savedAt: Long,
    /** 所属分组 id，null 表示未分组 */
    val groupId: Int? = null,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
)
