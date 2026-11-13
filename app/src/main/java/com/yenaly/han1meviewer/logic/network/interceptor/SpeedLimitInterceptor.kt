package com.yenaly.han1meviewer.logic.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class SpeedLimitInterceptor(maxSpeed: Long) : Interceptor {

    private val rateLimiter = DownloadRateLimiter(maxSpeed)

    var maxSpeed: Long
        get() = rateLimiter.bytesPerSecond
        set(value) {
            rateLimiter.bytesPerSecond = value
        }

    companion object {
        const val NO_LIMIT = 0L

        const val NO_LIMIT_INDEX = 0

        @JvmField
        val SPEED_BYTES = longArrayOf(
            /* 不限速 */ 0L,
            128 * 1024L, 256 * 1024L, 512 * 1024L,
            1024 * 1024L, 2048 * 1024L, 4096 * 1024L,
            8192 * 1024L, 10240 * 1024L
        )

        fun speedBytesAt(index: Int): Long = SPEED_BYTES.getOrElse(index) { NO_LIMIT }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        return response.newBuilder()
            .body(SpeedLimitResponseBody(body, rateLimiter))
            .build()
    }
}
