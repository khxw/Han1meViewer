package com.yenaly.han1meviewer.logic.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 漫画阅读进度（本地）。page 为 1 起算的页码。 */
@Entity
data class ComicProgressEntity(
    @PrimaryKey
    val comicId: String,
    val page: Int,
    val updatedAt: Long,
)
