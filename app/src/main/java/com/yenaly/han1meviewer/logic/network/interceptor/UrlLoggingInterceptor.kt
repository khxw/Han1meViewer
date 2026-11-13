package com.yenaly.han1meviewer.logic.network.interceptor

import android.util.Log
import com.yenaly.han1meviewer.logic.log.HLumberjack
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class UrlLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.name())
        Log.i("NetworkRequest", decodedUrl)
        val start = System.currentTimeMillis()
        return try {
            val response = chain.proceed(request)
            HLumberjack.i(
                "HTTP",
                "$decodedUrl -> ${response.code} (${System.currentTimeMillis() - start}ms)",
            )
            response
        } catch (e: IOException) {
            HLumberjack.w(
                "HTTP",
                "$decodedUrl -> FAILED (${System.currentTimeMillis() - start}ms): ${e.message}",
            )
            throw e
        }
    }
}