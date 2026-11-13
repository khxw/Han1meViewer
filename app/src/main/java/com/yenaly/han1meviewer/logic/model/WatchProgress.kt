package com.yenaly.han1meviewer.logic.model

/**
 * 统一的「已看」判定。
 *
 * 之前有两套规则：
 * - 视频列表角标（`markWatchedList`）用 SQL 查「历史表里有没有这条记录」，
 *   于是**只要点开过视频就立刻显示已看**，哪怕一秒都没看；
 * - 系列页（`LocalSeriesEpisode.isWatched`）用的是「已看完 或 进度 ≥ 90%」。
 *
 * 现在收敛到这里，两处都调用 [isWatched]。
 */
object WatchProgress {

    /** 进度达到该百分比即视为已看。 */
    const val WATCHED_PERCENT = 90

    /**
     * @param progress 已播放时长（毫秒）
     * @param duration 视频总时长（毫秒）；为 0 表示播放器还没上报过时长
     * @param isCompleted 是否已标记看完
     */
    fun isWatched(progress: Long, duration: Long, isCompleted: Boolean): Boolean =
        isCompleted || (duration > 0L && progress * 100 >= duration * WATCHED_PERCENT)
}
