package com.yenaly.han1meviewer.logic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yenaly.han1meviewer.HFileManager
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.dao.download.HanimeDownloadDao
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.state.DownloadState
import com.yenaly.han1meviewer.util.DeviceVideoScanner
import com.yenaly.han1meviewer.util.LocalVideoAvailability
import com.yenaly.han1meviewer.util.SafFileManager
import com.yenaly.yenaly_libs.utils.folderSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

object StorageManager {

    private const val RECENT_ORPHAN_WINDOW_MS = 60 * 60 * 1000L

    data class FolderEntry(
        val name: String,
        val sizeBytes: Long,
        val lastModified: Long,
    )

    data class Snapshot(
        val cacheBytes: Long,
        val appDataBytes: Long,
        val downloadBytes: Long,
        val downloadCount: Int,
        val deviceVideoBytes: Long,
        val deviceVideoCount: Int,
        val invalidRecordCount: Int,
        val invalidRecordNames: List<String>,
        val duplicateRecordCount: Int,
        val duplicateRecordNames: List<String>,
        val orphanedFolderCount: Int,
        val orphanedFolderBytes: Long,
        val orphanedFolderNames: List<String>,
        val mediaPermissionGranted: Boolean,
        val totalBytes: Long,
        val freeBytes: Long,
    ) {
        val accountedBytes: Long
            get() = cacheBytes + appDataBytes + downloadBytes + deviceVideoBytes
    }

    private data class DownloadScanResult(
        val bytes: Long,
        val orphanedFolders: List<FolderEntry>,
    )

    suspend fun scan(
        context: Context,
        dao: HanimeDownloadDao,
    ): Snapshot = withContext(Dispatchers.IO) {
        val entities = dao.getAll()
        val videoCodes = entities.mapTo(mutableSetOf()) { it.videoCode }
        val internalCacheBytes = context.cacheDir?.folderSize ?: 0L
        val externalCacheBytes = context.externalCacheDir?.folderSize ?: 0L
        val cacheBytes = internalCacheBytes + externalCacheBytes
        val appDataBytes = ((context.dataDir?.folderSize ?: 0L) - internalCacheBytes)
            .coerceAtLeast(0L)
        val downloadScan = scanDownloads(context, videoCodes)
        val deviceVideos = if (hasMediaAccess(context)) {
            DeviceVideoScanner.scan(context)
        } else {
            emptyList()
        }
        val invalidRecords = entities.filter { isDefinitelyMissing(context, it) }
        val duplicateGroups = duplicateGroups(entities)
        val statFs = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()

        Snapshot(
            cacheBytes = cacheBytes,
            appDataBytes = appDataBytes,
            downloadBytes = downloadScan.bytes,
            downloadCount = entities.size,
            deviceVideoBytes = deviceVideos.sumOf { it.sizeBytes },
            deviceVideoCount = deviceVideos.size,
            invalidRecordCount = invalidRecords.size,
            invalidRecordNames = invalidRecords.take(3).map { it.title },
            duplicateRecordCount = duplicateGroups.sumOf { it.size - 1 },
            duplicateRecordNames = duplicateGroups.flatten().take(3).map { it.title },
            orphanedFolderCount = downloadScan.orphanedFolders.size,
            orphanedFolderBytes = downloadScan.orphanedFolders.sumOf { it.sizeBytes },
            orphanedFolderNames = downloadScan.orphanedFolders.take(3).map { it.name },
            mediaPermissionGranted = hasMediaAccess(context),
            totalBytes = statFs?.totalBytes ?: 0L,
            freeBytes = statFs?.availableBytes ?: 0L,
        )
    }

    suspend fun clearAppCache(context: Context): Long = withContext(Dispatchers.IO) {
        val before = cacheSize(context)
        clearDirectory(context.cacheDir)
        clearDirectory(context.externalCacheDir)
        (before - cacheSize(context)).coerceAtLeast(0L)
    }

    suspend fun clearInvalidDownloadRecords(
        context: Context,
        dao: HanimeDownloadDao,
    ): Int = withContext(Dispatchers.IO) {
        var removed = 0
        dao.getAll().forEach { entity ->
            if (isDefinitelyMissing(context, entity)) {
                dao.delete(entity)
                removed++
            }
        }
        removed
    }

    suspend fun mergeDuplicateDownloadRecords(
        context: Context,
        dao: HanimeDownloadDao,
    ): Int = withContext(Dispatchers.IO) {
        var removed = 0
        duplicateGroups(dao.getAll()).forEach { group ->
            val hasActiveTask = group.any {
                it.state == DownloadState.Unknown ||
                    it.state == DownloadState.Queued ||
                    it.state == DownloadState.Downloading
            }
            if (hasActiveTask) return@forEach
            val best = group.maxWithOrNull(
                compareBy<HanimeDownloadEntity>(
                    { LocalVideoAvailability.isAvailable(context, it.videoUri) },
                    { it.state == DownloadState.Finished },
                    { if (it.length > 0L) it.downloadedLength.toDouble() / it.length else 0.0 },
                    { it.id },
                )
            ) ?: return@forEach
            group.filter { it.id != best.id }.forEach { entity ->
                dao.delete(entity)
                removed++
            }
        }
        removed
    }

    suspend fun deleteOrphanedDownloadFolders(
        context: Context,
        dao: HanimeDownloadDao,
    ): Int = withContext(Dispatchers.IO) {
        val videoCodes = dao.getAll().mapTo(mutableSetOf()) { it.videoCode }
        if (Preferences.isUsePrivateStorage) {
            val root = privateDownloadRoot(context)
            root.listFiles()
                .orEmpty()
                .count { folder ->
                    folder.isDirectory &&
                        folder.name !in videoCodes &&
                        !isRecent(folder.lastModified()) &&
                        folder.deleteRecursively()
                }
        } else {
            val root = SafFileManager.getAppDownloadFolderSaf(context)
                ?.findFile(HFileManager.HANIME_DOWNLOAD_FOLDER)
                ?: return@withContext 0
            root.listFiles()
                .orEmpty()
                .count { folder ->
                    val name = folder.name
                    folder.isDirectory &&
                        name != null &&
                        name !in videoCodes &&
                        !isRecent(folder.lastModified()) &&
                        deleteDocumentRecursively(folder)
                }
        }
    }

    private fun scanDownloads(
        context: Context,
        videoCodes: Set<String>,
    ): DownloadScanResult {
        return if (Preferences.isUsePrivateStorage) {
            scanPrivateDownloads(context, videoCodes)
        } else {
            scanSafDownloads(context, videoCodes)
        }
    }

    private fun scanPrivateDownloads(
        context: Context,
        videoCodes: Set<String>,
    ): DownloadScanResult {
        val root = privateDownloadRoot(context)
        if (!root.exists() || !root.isDirectory) return DownloadScanResult(0L, emptyList())

        val folders = root.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .map { folder ->
                FolderEntry(
                    name = folder.name,
                    sizeBytes = folder.folderSize,
                    lastModified = folder.lastModified(),
                )
            }
        return DownloadScanResult(
            bytes = root.folderSize,
            orphanedFolders = folders.filter {
                it.name !in videoCodes && !isRecent(it.lastModified)
            },
        )
    }

    private fun scanSafDownloads(
        context: Context,
        videoCodes: Set<String>,
    ): DownloadScanResult {
        val root = SafFileManager.getAppDownloadFolderSaf(context)
            ?.findFile(HFileManager.HANIME_DOWNLOAD_FOLDER)
            ?: return DownloadScanResult(0L, emptyList())
        if (!root.isDirectory) return DownloadScanResult(0L, emptyList())

        val children = root.listFiles().orEmpty()
        val folders = children
            .filter { it.isDirectory && !it.name.isNullOrBlank() }
            .map { folder ->
                FolderEntry(
                    name = folder.name.orEmpty(),
                    sizeBytes = documentSize(folder),
                    lastModified = folder.lastModified(),
                )
            }
        return DownloadScanResult(
            bytes = children.sumOf(::documentSize),
            orphanedFolders = folders.filter {
                it.name !in videoCodes && !isRecent(it.lastModified)
            },
        )
    }

    private fun privateDownloadRoot(context: Context): File =
        File(HFileManager.getAppDownloadFolder(context), HFileManager.HANIME_DOWNLOAD_FOLDER)

    private fun duplicateGroups(
        entities: List<HanimeDownloadEntity>,
    ): List<List<HanimeDownloadEntity>> =
        entities.groupBy { it.videoCode to it.quality }
            .values
            .filter { it.size > 1 }

    private fun isDefinitelyMissing(
        context: Context,
        entity: HanimeDownloadEntity,
    ): Boolean {
        if (entity.state != DownloadState.Finished) return false
        val uri = runCatching { entity.videoUri.toUri() }.getOrNull() ?: return true
        return when (uri.scheme?.lowercase()) {
            "file" -> {
                val file = uri.path?.let(::File) ?: return true
                !file.exists() || !file.isFile || file.length() <= 0L
            }

            "content" -> isContentMissing(context, uri)
            else -> false
        }
    }

    private fun isContentMissing(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length == 0L
            } ?: true
        } catch (_: FileNotFoundException) {
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun cacheSize(context: Context): Long =
        (context.cacheDir?.folderSize ?: 0L) + (context.externalCacheDir?.folderSize ?: 0L)

    private fun clearDirectory(directory: File?) {
        if (directory == null || !directory.exists()) return
        directory.listFiles()?.forEach { child -> child.deleteRecursively() }
    }

    private fun documentSize(document: DocumentFile): Long {
        if (document.isDirectory) {
            return document.listFiles().orEmpty().sumOf(::documentSize)
        }
        return document.length().coerceAtLeast(0L)
    }

    private fun deleteDocumentRecursively(document: DocumentFile): Boolean {
        if (document.isDirectory) {
            document.listFiles().orEmpty().forEach(::deleteDocumentRecursively)
        }
        return document.delete()
    }

    private fun isRecent(lastModified: Long): Boolean =
        lastModified > 0L && System.currentTimeMillis() - lastModified < RECENT_ORPHAN_WINDOW_MS

    private fun hasMediaAccess(context: Context): Boolean {
        fun granted(permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                granted(Manifest.permission.READ_MEDIA_VIDEO) ||
                    granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                granted(Manifest.permission.READ_MEDIA_VIDEO)

            else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}