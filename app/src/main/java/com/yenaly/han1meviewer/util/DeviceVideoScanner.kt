package com.yenaly.han1meviewer.util

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.yenaly.han1meviewer.HFileManager.HANIME_DOWNLOAD_FOLDER
import com.yenaly.han1meviewer.logic.model.DeviceVideoItem

object DeviceVideoScanner {

    fun scan(context: Context): List<DeviceVideoItem> {
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Video.Media.DATA)
            }
        }.toTypedArray()

        val result = mutableListOf<DeviceVideoItem>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Video.Media.DATE_ADDED + " DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val bucketColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME
                )
                val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                }

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathColumn).orEmpty()
                    if (path.isHanimeDownloadPath()) continue
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn).orEmpty()
                    result += DeviceVideoItem(
                        id = id,
                        contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id,
                        ).toString(),
                        title = displayName.substringBeforeLast('.').ifBlank { displayName },
                        durationMs = cursor.getLong(durationColumn).coerceAtLeast(0L),
                        sizeBytes = cursor.getLong(sizeColumn).coerceAtLeast(0L),
                        folderName = cursor.getString(bucketColumn),
                        dateAddedSeconds = cursor.getLong(dateColumn),
                    )
                }
            }
        }
        return result
    }
}

internal fun String.isHanimeDownloadPath(): Boolean {
    val normalized = replace('\\', '/')
    return normalized.contains(HANIME_DOWNLOAD_FOLDER, ignoreCase = true) ||
        normalized.contains("HanimeDownload", ignoreCase = true)
}
