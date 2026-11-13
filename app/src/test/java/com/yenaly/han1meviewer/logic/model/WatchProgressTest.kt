package com.yenaly.han1meviewer.logic.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「已看」判定必须是「看完 或 进度 ≥ 90%」：
 * 仅仅点开视频（progress = 0）不能被当成已看。
 */
class WatchProgressTest {

    private val duration = 100_000L

    @Test
    fun completedIsAlwaysWatched() {
        assertTrue(WatchProgress.isWatched(progress = 0L, duration = duration, isCompleted = true))
        assertTrue(WatchProgress.isWatched(progress = 0L, duration = 0L, isCompleted = true))
    }

    @Test
    fun ninetyPercentOrMoreIsWatched() {
        assertTrue(WatchProgress.isWatched(progress = 90_000L, duration = duration, isCompleted = false))
        assertTrue(WatchProgress.isWatched(progress = 100_000L, duration = duration, isCompleted = false))
    }

    @Test
    fun justOpenedOrPartiallyWatchedIsNotWatched() {
        assertFalse(WatchProgress.isWatched(progress = 0L, duration = duration, isCompleted = false))
        assertFalse(WatchProgress.isWatched(progress = 89_999L, duration = duration, isCompleted = false))
    }

    @Test
    fun unknownDurationIsNotWatched() {
        // 播放器还没上报时长时，不能仅凭 progress 判为已看
        assertFalse(WatchProgress.isWatched(progress = 50_000L, duration = 0L, isCompleted = false))
    }
}
