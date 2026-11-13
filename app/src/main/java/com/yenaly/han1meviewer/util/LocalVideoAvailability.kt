package com.yenaly.han1meviewer.util

import android.content.Context
import androidx.core.net.toUri
import java.io.File

object LocalVideoAvailability {

    fun isAvailable(context: Context, uriString: String): Boolean {
        if (uriString.isBlank()) return false
        val uri = runCatching { uriString.toUri() }.getOrNull() ?: return false
        return when (uri.scheme?.lowercase()) {
            "file" -> uri.path
                ?.let(::File)
                ?.let { it.exists() && it.isFile && it.length() > 0L }
                ?: false

            "content" -> openContentUri(context, uri)
            else -> false
        }
    }

    private fun openContentUri(context: Context, uri: android.net.Uri): Boolean {
        val resolver = context.contentResolver
        val descriptorOpened = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
        }.getOrDefault(false)
        if (descriptorOpened) return true

        return runCatching {
            resolver.openInputStream(uri)?.use { true } == true
        }.getOrDefault(false)
    }
}
