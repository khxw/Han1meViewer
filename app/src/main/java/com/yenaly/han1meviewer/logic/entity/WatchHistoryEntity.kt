package com.yenaly.han1meviewer.logic.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yenaly.han1meviewer.logic.model.WatchProgress
import kotlinx.serialization.Serializable

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/07/02 002 13:13
 */
@Serializable
@Entity
data class WatchHistoryEntity(
    val coverUrl: String,
    val title: String,
    val releaseDate: Long,
    val watchDate: Long,
    val videoCode: String,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val isCompleted: Boolean = false,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
) {
    /** 看完，或进度达到 90% 才算已看；仅仅打开过视频不算。 */
    val isWatched: Boolean
        get() = WatchProgress.isWatched(progress, duration, isCompleted)

    val releaseDateDays: Int
        get() = (releaseDate / (24 * 60 * 60 * 1000)).toInt()
}
