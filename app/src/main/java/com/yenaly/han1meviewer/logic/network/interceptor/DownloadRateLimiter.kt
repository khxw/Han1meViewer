package com.yenaly.han1meviewer.logic.network.interceptor

import java.util.concurrent.locks.LockSupport

/**
 * Shared download limiter. A single instance is used by every download response,
 * so the configured limit applies to the aggregate bandwidth rather than each task.
 */
internal class DownloadRateLimiter(
    initialBytesPerSecond: Long = 0L,
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleeper: (Long) -> Unit = LockSupport::parkNanos,
) {
    private val lock = Any()
    private var nextAvailableNanos = 0L

    @Volatile
    var bytesPerSecond: Long = initialBytesPerSecond.coerceAtLeast(0L)
        set(value) {
            field = value.coerceAtLeast(0L)
            synchronized(lock) {
                // Apply a new limit to the next read instead of waiting for a reconnect.
                nextAvailableNanos = nanoTime()
            }
        }

    fun acquire(byteCount: Long) {
        if (byteCount <= 0L || bytesPerSecond <= 0L) return

        val waitNanos = synchronized(lock) {
            val limit = bytesPerSecond
            if (limit <= 0L) return

            val now = nanoTime()
            val startNanos = maxOf(nextAvailableNanos, now)
            val costNanos = ((byteCount * NANOS_PER_SECOND) + limit - 1L) / limit
            nextAvailableNanos = startNanos + costNanos
            startNanos - now
        }

        if (waitNanos > 0L) sleeper(waitNanos)
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
