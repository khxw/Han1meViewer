package com.yenaly.han1meviewer.logic.network.interceptor

import com.yenaly.han1meviewer.Preferences
import okhttp3.Interceptor
import okhttp3.Response

object UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // 使用有效 UA：手动导入 Cloudflare Cookie 时会带上与之绑定的 UA 覆盖值，
        // 否则为默认 USER_AGENT。
        // 允许个别请求临时覆盖 UA（例如拉取订阅时需要 Clash 的 UA 才会返回 YAML 配置）
        val override = chain.request().header("X-UA-Override")
        val request = chain.request().newBuilder()
            .header("User-Agent", override ?: Preferences.effectiveUserAgent)
            .removeHeader("X-UA-Override")
            .build()
        return chain.proceed(request)
    }
}