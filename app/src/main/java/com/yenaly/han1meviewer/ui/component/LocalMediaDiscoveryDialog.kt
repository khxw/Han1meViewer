package com.yenaly.han1meviewer.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.LocalMediaDiscoveryResult

@Composable
fun LocalMediaDiscoveryDialog(
    result: LocalMediaDiscoveryResult,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val header = stringResource(
        R.string.local_media_found_summary,
        result.newItems.size,
        result.movedItems.size,
        result.missingVideoCodes.size,
    )
    val previewItems = (result.newItems + result.movedItems)
        .take(8)
        .joinToString("\n") { "- " + it.title + " (" + it.videoCode + ")" }
    val preview = if (previewItems.isBlank()) {
        ""
    } else {
        "\n\n" + stringResource(R.string.local_media_preview_list, previewItems)
    }
    val skipped = if (result.skippedCount > 0) {
        "\n\n" + pluralStringResource(R.plurals.local_media_skipped, result.skippedCount, result.skippedCount)
    } else {
        ""
    }
    ConfirmDialog(
        visible = true,
        title = stringResource(R.string.local_media_found_title),
        message = header + preview + skipped,
        confirmText = stringResource(R.string.ok),
        dismissText = stringResource(R.string.cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
