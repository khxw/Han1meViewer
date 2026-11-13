package com.yenaly.han1meviewer.logic.network

import android.util.Log
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.GITHUB_UPDATE_MIRROR
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.model.github.CommitComparison
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.util.checkNeedUpdate
import com.yenaly.han1meviewer.util.copyTo
import com.yenaly.han1meviewer.util.runSuspendCatching
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

    const val DEFAULT_BRANCH = "main"

    /**
     * Regex to match multiple line feeds to a single line feed
     */
    private val linefeedRegex = Regex("\\n{2,}")

    /**
     * Check for update
     *
     * @param forceCheck force check
     */
    suspend fun checkForUpdate(forceCheck: Boolean = false): Latest? {
        if (forceCheck || Preferences.isUpdateDialogVisible) {
            if (Preferences.useCIUpdateChannel) {
                val curSha = BuildConfig.COMMIT_SHA
                // 特殊情况下才用注释部分，一般情况下 branch 都是固定的，要不然多一次
                // request 会对我的 API Token 造成负担。
                // val apiReq = request(HA1_GITHUB_API_URL)
                // val branch = apiReq.body?.string()?.let(::JSONObject)?.getString("default_branch")
                //     ?: return null
                val workflowRun = HanimeNetwork.githubService.getWorkflowRuns()
                    .workflowRuns.firstOrNull() ?: return null
                val shortSha = workflowRun.headSha.take(7)
                if (shortSha != curSha) {
                    val artifacts =
                        HanimeNetwork.githubService.getArtifacts(workflowRun.artifactsUrl)
                    val archiveUrl = artifacts.downloadLink
                    val nodeId = artifacts.nodeId
                    val changelog = runSuspendCatching {
                        HanimeNetwork.githubService.getCommitComparison(
                            curSha = curSha,
                            latestSha = shortSha
                        ).commits.toChangelogPrettyString()
                    }.getOrNull() ?: workflowRun.title
                    return Latest("$shortSha (CI)", changelog, archiveUrl, nodeId)
                }
            } else {
                val ver = HanimeNetwork.githubService.getLatestVersion()
                val isNeeded = checkNeedUpdate(ver.tagName)
                if (isNeeded) {
                    val asset = ver.pickBestApkAsset() ?: return null
                    return Latest(
                        ver.tagName, ver.body,
                        asset.browserDownloadURL.toGitHubUpdateMirrorUrl(),
                        asset.nodeID
                    )
                }
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
        val candidates = buildList {
            add(url)
            when {
                url.startsWith(GITHUB_UPDATE_MIRROR) -> {
                    add(url.removePrefix(GITHUB_UPDATE_MIRROR))
                }

                url.startsWith("https://github.com/") -> {
                    add(url.toGitHubUpdateMirrorUrl())
                }
            }
        }.distinct()
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

    /**
     * This function is used to filter out commits that are not authored by the user.
     */
    private val CommitComparison.Commit.CommitDetail.CommitAuthor.isAuthorShouldIgnore: Boolean
        get() = name.contains("dependabot")

    private fun List<CommitComparison.Commit>.toChangelogPrettyString(): String {
        return filterNot { commit ->
            commit.commit.author.isAuthorShouldIgnore
        }.distinct().reversed().joinToString("\n\n") { commit ->
            val message = commit.commit.message.replace(linefeedRegex, "\n")
            "↓ (@${commit.commit.author.name})\n$message"
        }
    }
}

internal fun String.toGitHubUpdateMirrorUrl(): String {
    return when {
        startsWith(GITHUB_UPDATE_MIRROR) -> this
        startsWith("https://github.com/") -> GITHUB_UPDATE_MIRROR + this
        else -> this
    }
}
