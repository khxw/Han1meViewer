package com.yenaly.han1meviewer.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class DownloadErrorPolicyTest {

    @Test
    fun storageFailuresAreNotRetriedAsNetworkErrors() {
        assertFalse(IOException("No space left on device").isRetryableDownloadNetworkError())
        assertFalse(IOException("Permission denied").isRetryableDownloadNetworkError())
        assertFalse(IOException("Read-only file system").isRetryableDownloadNetworkError())
    }

    @Test
    fun transientNetworkFailuresRemainRetryable() {
        assertTrue(SocketTimeoutException().isRetryableDownloadNetworkError())
        assertTrue(IOException("unexpected end of stream").isRetryableDownloadNetworkError())
        assertFalse(IOException("Canceled").isRetryableDownloadNetworkError())
    }
}
