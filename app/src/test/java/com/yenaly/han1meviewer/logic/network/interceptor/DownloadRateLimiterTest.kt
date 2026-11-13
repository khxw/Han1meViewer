package com.yenaly.han1meviewer.logic.network.interceptor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRateLimiterTest {

    @Test
    fun unlimitedDownloadsDoNotWait() {
        val waits = mutableListOf<Long>()
        val limiter = DownloadRateLimiter(
            initialBytesPerSecond = 0L,
            nanoTime = { 0L },
            sleeper = { waits += it },
        )

        limiter.acquire(1_000_000L)

        assertTrue(waits.isEmpty())
    }

    @Test
    fun aggregateLimitIsSharedAndUpdatesImmediately() {
        var now = 0L
        val waits = mutableListOf<Long>()
        val limiter = DownloadRateLimiter(
            initialBytesPerSecond = 1_000L,
            nanoTime = { now },
            sleeper = { waits += it },
        )

        limiter.acquire(500L)
        assertTrue(waits.isEmpty())

        // The second response shares the first response's bandwidth budget.
        limiter.acquire(500L)
        assertEquals(500_000_000L, waits.single())

        // Changing the limit resets the shared schedule for the next read.
        now = 1_000_000_000L
        limiter.bytesPerSecond = 2_000L
        limiter.acquire(1_000L)
        assertEquals(1, waits.size)
    }
}
