package com.yenaly.han1meviewer.logic.network.interceptor

import com.yenaly.han1meviewer.logic.log.HLumberjack
import com.yenaly.han1meviewer.logic.network.NetworkRateLimitManager
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * GET 请求重试与限流冷却。
 *
 * 检测到 403/429 后只冷却当前线路，不自动切换域名。
 */
class RetryOnErrorInterceptor(
    private val maxRetries: Int = 1,
    private val retryDelayMillis: Long = 1_000L,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (NetworkRateLimitManager.isCoolingDown(request.url)) {
            return rateLimitedResponse(request)
        }

        var attempt = 0
        while (true) {
            try {
                val response = chain.proceed(request)
                if (isRateLimited(response)) {
                    NetworkRateLimitManager.markCoolingDown(
                        response.request.url,
                        NetworkRateLimitManager.retryAfterMillis(response.header("Retry-After")),
                    )
                    return response
                }

                NetworkRateLimitManager.markHealthy(response.request.url)
                return response
            } catch (e: IOException) {
                if (request.method != "GET" || attempt >= maxRetries) throw e
                attempt++
                HLumberjack.w(
                    "HTTP",
                    "GET ${request.url} failed: ${e.message}, retry ($attempt/$maxRetries)"
                )
                try {
                    Thread.sleep(retryDelayMillis)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
            }
        }
    }

    private fun isRateLimited(response: Response): Boolean {
        if (response.code == 429) return true
        if (response.code != 403) return false
        // Cloudflare challenge 由下游拦截器处理，不在这里当成普通限流。
        return response.header("cf-mitigated") != "challenge"
    }

    private fun rateLimitedResponse(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Rate limited; current host is cooling down")
            .body("Rate limited by cooldown".toResponseBody("text/plain".toMediaType()))
            .build()
    }
}
