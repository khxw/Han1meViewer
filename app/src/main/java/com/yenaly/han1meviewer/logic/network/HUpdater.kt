package com.yenaly.han1meviewer.logic.network

import android.util.Log
import com.yenaly.han1meviewer.GITHUB_UPDATE_MIRROR
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.util.checkNeedUpdate
import com.yenaly.han1meviewer.util.copyTo
import com.yenaly.han1meviewer.util.await
import okhttp3.Request
import okhttp3.ResponseBody
import okio.use
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2024/03/21 021 08:28
 */
object HUpdater {

    const val TAG = "HUpdater"

    /**
     * Check for update
     *
     * @param forceCheck force check
     */
    suspend fun checkForUpdate(forceCheck: Boolean = false): Latest? {
        if (forceCheck || Preferences.isUpdateDialogVisible) {
            val ver = HanimeNetwork.githubService.getLatestVersion()
            val isNeeded = checkNeedUpdate(ver.tagName)
            if (isNeeded) {
                val asset = ver.pickBestApkAsset() ?: return null
                val downloadUrl = if (Preferences.useUpdateMirror) {
                    asset.browserDownloadURL.toGitHubUpdateMirrorUrl()
                } else {
                    asset.browserDownloadURL
                }
                return Latest(
                    ver.tagName, ver.body,
                    downloadUrl,
                    asset.nodeID
                )
            }
        }
        return null
    }

    /**
     * 从 Release 资源里挑选最合适的 APK：
     * 优先匹配当前设备 ABI 的分架构包（体积更小、正确），
     * 找不到则用 universal 通用包，再不行退回第一个 .apk / 第一个资源。
     */
    private fun com.yenaly.han1meviewer.logic.model.github.Release.pickBestApkAsset():
            com.yenaly.han1meviewer.logic.model.github.Release.Asset? {
        if (assets.isEmpty()) return null
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return assets.first()
        // 1) 按设备支持的 ABI 顺序匹配分架构包
        for (abi in android.os.Build.SUPPORTED_ABIS) {
            apkAssets.firstOrNull { it.name.contains("-$abi.apk", ignoreCase = true) }
                ?.let { return it }
        }
        // 2) universal 通用包
        apkAssets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?.let { return it }
        // 3) 兜底：第一个 apk
        return apkAssets.first()
    }

    /**
     * Inject update to file
     *
     * @param url update url
     */
    suspend fun File.injectUpdate(url: String, progress: (suspend (Int, Long, Long) -> Unit)? = null) {
        val candidates = buildUpdateDownloadCandidates(url, Preferences.useUpdateMirror)
        var lastError: Exception? = null
        for (candidate in candidates) {
            try {
                delete()
                downloadUpdateOnce(candidate, progress)
                if (length() > 0L) return
                throw IOException("Downloaded update is empty")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                delete()
                Log.w(TAG, "Update download failed: $candidate - ${e.message}")
            }
        }
        throw lastError ?: IOException("No usable update download URL")
    }

    private suspend fun File.downloadUpdateOnce(
        url: String,
        progress: (suspend (Int, Long, Long) -> Unit)?,
    ) {
        if (url.startsWith(GITHUB_UPDATE_MIRROR)) {
            ServiceCreator.downloadClient.newCall(
                Request.Builder().url(url).get().build()
            ).await().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                writeUpdateBody(url, response.body, progress)
            }
        } else {
            val response = HanimeNetwork.githubService.request(url)
            if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
            val body = response.body() ?: throw IOException("Empty update response")
            writeUpdateBody(url, body, progress)
        }
    }

    private suspend fun File.writeUpdateBody(
        url: String,
        body: ResponseBody,
        progress: (suspend (Int, Long, Long) -> Unit)?,
    ) {
        if (url.endsWith("zip")) {
            Log.d(TAG, "Injecting update from zip ($url)")
            body.use {
                body.byteStream().use { stream ->
                    ZipInputStream(stream).use { zip ->
                        zip.nextEntry
                        this.outputStream().use {
                            Log.i(TAG, "content length: ${body.contentLength()}")
                            // 估摸着压缩率为0.56左右，稍微估算解压后大小，防止进度卡在100%时间过长
                            zip.copyTo(it, (body.contentLength() * 1.79).toLong(), progress = progress)
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "Injecting update from release ($url)")
            this.outputStream().use { output ->
                body.use { response ->
                    Log.i(TAG, "content length: ${response.contentLength()}")
                    response.byteStream().copyTo(
                        output,
                        response.contentLength(),
                        progress = progress,
                    )
                }
            }
        }
    }

}

internal fun buildUpdateDownloadCandidates(url: String, useMirror: Boolean): List<String> {
    val directUrl = if (url.startsWith(GITHUB_UPDATE_MIRROR)) {
        url.removePrefix(GITHUB_UPDATE_MIRROR)
    } else {
        url
    }
    return when {
        !useMirror -> listOf(directUrl)
        directUrl.startsWith("https://github.com/") ->
            listOf(directUrl.toGitHubUpdateMirrorUrl(), directUrl)
        else -> listOf(url)
    }.distinct()
}

internal fun String.toGitHubUpdateMirrorUrl(): String {
    return when {
        startsWith(GITHUB_UPDATE_MIRROR) -> this
        startsWith("https://github.com/") -> GITHUB_UPDATE_MIRROR + this
        else -> this
    }
}
