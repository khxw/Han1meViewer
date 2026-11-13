package com.yenaly.han1meviewer.logic.network

import com.yenaly.han1meviewer.logic.log.HLumberjack
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 当前线路限流冷却保护。
 *
 * 收到 403/429 后暂停访问同一域名，不自动切换域名。
 */
object NetworkRateLimitManager {
    private const val DEFAULT_COOLDOWN_MILLIS = 5 * 60 * 1000L
    private const val MIN_COOLDOWN_MILLIS = 30 * 1000L
    private const val MAX_COOLDOWN_MILLIS = 30 * 60 * 1000L

    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    fun isCoolingDown(url: HttpUrl): Boolean =
        (cooldownUntil[url.host] ?: 0L) > System.currentTimeMillis()

    fun markCoolingDown(url: HttpUrl, retryAfterMillis: Long? = null) {
        val now = System.currentTimeMillis()
        val cooldown = (retryAfterMillis ?: DEFAULT_COOLDOWN_MILLIS)
            .coerceIn(MIN_COOLDOWN_MILLIS, MAX_COOLDOWN_MILLIS)
        val until = now + cooldown
        cooldownUntil.compute(url.host) { _, existingUntil ->
            maxOf(existingUntil ?: 0L, until)
        }
        HLumberjack.w(
            "RATE_LIMIT",
            "${url.host} cooling down for ${cooldown / 1000}s",
        )
    }

    fun markHealthy(url: HttpUrl) {
        val now = System.currentTimeMillis()
        cooldownUntil.computeIfPresent(url.host) { _, until ->
            until.takeIf { it > now }
        }
    }

    fun reset() {
        cooldownUntil.clear()
    }

    fun retryAfterMillis(value: String?): Long? {
        val seconds = value?.trim()?.toLongOrNull() ?: return null
        return seconds.coerceAtLeast(1L) * 1000L
    }
}
