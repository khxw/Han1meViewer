package com.yenaly.han1meviewer.logic.model

/** 用于自动识别系列和集数的本地候选视频。 */
data class SeriesCandidate(
    val videoCode: String,
    val title: String,
    val coverUrl: String,
    val timestamp: Long,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val isCompleted: Boolean = false,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val localUri: String? = null,
)

/** 自动归并后的单集。 */
data class LocalSeriesEpisode(
    val videoCode: String,
    val title: String,
    val coverUrl: String,
    val episode: Int,
    val progress: Long,
    val duration: Long,
    val isCompleted: Boolean,
    val isFavorite: Boolean,
    val isDownloaded: Boolean,
    val localUri: String?,
) {
    /** 与视频列表角标共用同一套判定（看完或进度 ≥ 90%）。 */
    val isWatched: Boolean
        get() = WatchProgress.isWatched(progress, duration, isCompleted)
}

/** 自动归并后的系列。 */
data class LocalSeries(
    val id: String,
    val title: String,
    val coverUrl: String,
    val episodes: List<LocalSeriesEpisode>,
    val missingEpisodes: List<Int>,
    val latestTimestamp: Long,
) {
    val episodeCount: Int
        get() = episodes.size

    val completedCount: Int
        get() = episodes.count { it.isWatched }

    val progressFraction: Float
        get() = if (episodeCount == 0) 0f else completedCount.toFloat() / episodeCount

    /** 优先继续最靠前的未看完内容，没有未完成集时返回 null。 */
    val nextEpisode: LocalSeriesEpisode?
        get() = episodes.firstOrNull { !it.isWatched }
}
