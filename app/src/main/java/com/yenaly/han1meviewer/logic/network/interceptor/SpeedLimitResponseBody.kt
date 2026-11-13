package com.yenaly.han1meviewer.logic.network.interceptor

import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

internal class SpeedLimitResponseBody(
    private val responseBody: ResponseBody,
    private val rateLimiter: DownloadRateLimiter,
) : ResponseBody() {

    override fun contentLength(): Long = responseBody.contentLength()

    override fun contentType() = responseBody.contentType()

    override fun source(): BufferedSource =
        SpeedLimitedSource(responseBody.source(), rateLimiter).buffer()
}

private class SpeedLimitedSource(
    source: Source,
    private val rateLimiter: DownloadRateLimiter,
) : ForwardingSource(source) {
    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = super.read(sink, byteCount)
        if (read > 0L) rateLimiter.acquire(read)
        return read
    }
}
