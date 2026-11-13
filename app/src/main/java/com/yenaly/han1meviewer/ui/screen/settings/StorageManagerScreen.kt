package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.StorageManager
import com.yenaly.han1meviewer.ui.component.ConfirmDialog
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import com.yenaly.han1meviewer.ui.viewmodel.StorageManagerUiState
import com.yenaly.yenaly_libs.utils.formatFileSizeV2

/**
 * 存储空间管理器。
 *
 * 版面：整页只有一个正方形卡片，卡片内自下而上依次是
 * 占用总览、四类明细（2×2）、以及"清理 / 扫描"两个动作按钮。
 * 原本独立列出的问题检查项收进"清理"弹窗，按检查结果决定是否可点。
 */
@Composable
fun StorageManagerScreen(
    state: StorageManagerUiState,
    onRefresh: () -> Unit,
    onClearCache: () -> Unit,
    onClearInvalidRecords: () -> Unit,
    onMergeDuplicateRecords: () -> Unit,
    onDeleteOrphanedFolders: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val snapshot = state.snapshot
    val busy = state.isScanning || state.isWorking

    var showCleanupPicker by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showClearInvalidConfirm by remember { mutableStateOf(false) }
    var showMergeDuplicatesConfirm by remember { mutableStateOf(false) }
    var showDeleteOrphansConfirm by remember { mutableStateOf(false) }

    val cacheBytes = snapshot?.cacheBytes ?: 0L
    val invalidCount = snapshot?.invalidRecordCount ?: 0
    val duplicateCount = snapshot?.duplicateRecordCount ?: 0
    val orphanCount = snapshot?.orphanedFolderCount ?: 0
    val orphanBytes = snapshot?.orphanedFolderBytes ?: 0L
    val hasCleanableItem =
        cacheBytes > 0L || invalidCount > 0 || duplicateCount > 0 || orphanCount > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StorageOverviewSquareCard(
            modifier = Modifier.widthIn(max = 420.dp),
            usedBytes = snapshot?.accountedBytes,
            freeBytes = snapshot?.freeBytes ?: 0L,
            cacheBytes = cacheBytes,
            downloadBytes = snapshot?.downloadBytes ?: 0L,
            deviceVideoBytes = snapshot?.deviceVideoBytes ?: 0L,
            appDataBytes = snapshot?.appDataBytes ?: 0L,
            scanning = state.isScanning,
            cleanEnabled = !busy && hasCleanableItem,
            scanEnabled = !busy,
            onCleanClick = { showCleanupPicker = true },
            onScanClick = onRefresh,
        )
        state.message?.let { message ->
            StorageMessage(
                message = message,
                onDismiss = onDismissMessage,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
    }

    CleanupPickerDialog(
        visible = showCleanupPicker,
        cacheBytes = cacheBytes,
        invalidCount = invalidCount,
        duplicateCount = duplicateCount,
        orphanCount = orphanCount,
        onDismiss = { showCleanupPicker = false },
        onPickCache = {
            showCleanupPicker = false
            showClearCacheConfirm = true
        },
        onPickInvalid = {
            showCleanupPicker = false
            showClearInvalidConfirm = true
        },
        onPickDuplicates = {
            showCleanupPicker = false
            showMergeDuplicatesConfirm = true
        },
        onPickOrphans = {
            showCleanupPicker = false
            showDeleteOrphansConfirm = true
        },
    )

    ConfirmDialog(
        visible = showClearCacheConfirm,
        title = stringResource(R.string.sure_to_clear),
        message = stringResource(R.string.sure_to_clear_cache),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showClearCacheConfirm = false
            onClearCache()
        },
        onDismiss = { showClearCacheConfirm = false },
    )

    ConfirmDialog(
        visible = showClearInvalidConfirm,
        title = stringResource(R.string.storage_manager_confirm_clear_invalid_title),
        message = stringResource(
            R.string.storage_manager_confirm_clear_invalid_message,
            invalidCount,
        ),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showClearInvalidConfirm = false
            onClearInvalidRecords()
        },
        onDismiss = { showClearInvalidConfirm = false },
    )

    ConfirmDialog(
        visible = showMergeDuplicatesConfirm,
        title = stringResource(R.string.storage_manager_confirm_merge_duplicates_title),
        message = stringResource(
            R.string.storage_manager_confirm_merge_duplicates_message,
            duplicateCount,
        ),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showMergeDuplicatesConfirm = false
            onMergeDuplicateRecords()
        },
        onDismiss = { showMergeDuplicatesConfirm = false },
    )

    ConfirmDialog(
        visible = showDeleteOrphansConfirm,
        title = stringResource(R.string.storage_manager_confirm_delete_orphans_title),
        message = stringResource(
            R.string.storage_manager_confirm_delete_orphans_message,
            orphanCount,
            formatSize(orphanBytes),
        ),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showDeleteOrphansConfirm = false
            onDeleteOrphanedFolders()
        },
        onDismiss = { showDeleteOrphansConfirm = false },
    )
}

/**
 * 正方形主卡片：占用总览 + 四类明细网格 + 清理/扫描按钮。
 */
@Composable
private fun StorageOverviewSquareCard(
    usedBytes: Long?,
    freeBytes: Long,
    cacheBytes: Long,
    downloadBytes: Long,
    deviceVideoBytes: Long,
    appDataBytes: Long,
    scanning: Boolean,
    cleanEnabled: Boolean,
    scanEnabled: Boolean,
    onCleanClick: () -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_storage_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.storage_manager_total_used),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = usedBytes?.let { formatSize(it) } ?: "—",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (scanning) {
            // 扫描进行中：脉冲圆环 + 文案，替换掉静态的剩余空间说明
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.storage_manager_scanning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = stringResource(
                    R.string.storage_manager_free_space,
                    formatSize(freeBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StorageMetricCell(
                    label = stringResource(R.string.storage_manager_cache),
                    value = if (scanning) null else formatSize(cacheBytes),
                    modifier = Modifier.weight(1f),
                )
                StorageMetricCell(
                    label = stringResource(R.string.storage_manager_downloads),
                    value = if (scanning) null else formatSize(downloadBytes),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StorageMetricCell(
                    label = stringResource(R.string.storage_manager_device_videos),
                    value = if (scanning) null else formatSize(deviceVideoBytes),
                    modifier = Modifier.weight(1f),
                )
                StorageMetricCell(
                    label = stringResource(R.string.storage_manager_app_data),
                    value = if (scanning) null else formatSize(appDataBytes),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(
                onClick = onCleanClick,
                enabled = cleanEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.storage_manager_action_clean),
                    maxLines = 1,
                )
            }
            OutlinedButton(
                onClick = onScanClick,
                enabled = scanEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.storage_manager_action_scan),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StorageMetricCell(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (value == null) {
            StorageSkeletonBar(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(0.7f),
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 扫描中的骨架占位：明暗呼吸动画，表示数值正在读取。 */
@Composable
private fun StorageSkeletonBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "storage-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "storage-skeleton-alpha",
    )
    Box(
        modifier = modifier
            .height(13.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
    )
}

/**
 * 清理选择弹窗：列出四类可清理项，数量为 0 的项不可点击。
 */
@Composable
private fun CleanupPickerDialog(
    visible: Boolean,
    cacheBytes: Long,
    invalidCount: Int,
    duplicateCount: Int,
    orphanCount: Int,
    onDismiss: () -> Unit,
    onPickCache: () -> Unit,
    onPickInvalid: () -> Unit,
    onPickDuplicates: () -> Unit,
    onPickOrphans: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.storage_manager_cleanup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CleanupPickerRow(
                    title = stringResource(R.string.storage_manager_clear_cache),
                    valueText = formatSize(cacheBytes),
                    enabled = cacheBytes > 0L,
                    onClick = onPickCache,
                )
                CleanupPickerRow(
                    title = stringResource(R.string.storage_manager_clear_invalid),
                    valueText = invalidCount.toString(),
                    enabled = invalidCount > 0,
                    onClick = onPickInvalid,
                )
                CleanupPickerRow(
                    title = stringResource(R.string.storage_manager_merge_duplicates),
                    valueText = duplicateCount.toString(),
                    enabled = duplicateCount > 0,
                    onClick = onPickDuplicates,
                )
                CleanupPickerRow(
                    title = stringResource(R.string.storage_manager_delete_orphans),
                    valueText = orphanCount.toString(),
                    enabled = orphanCount > 0,
                    onClick = onPickOrphans,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun CleanupPickerRow(
    title: String,
    valueText: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            },
        )
    }
}

/** 操作结果提示：一行文字，点击即关闭。 */
@Composable
private fun StorageMessage(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_check_circle_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatSize(bytes: Long): String = bytes.formatFileSizeV2()

@Preview(showBackground = true)
@Composable
private fun StorageManagerScreenPreview() {
    ComponentPreview {
        StorageManagerScreen(
            state = StorageManagerUiState(
                snapshot = StorageManager.Snapshot(
                    cacheBytes = 128L * 1024 * 1024,
                    appDataBytes = 24L * 1024 * 1024,
                    downloadBytes = 3L * 1024 * 1024 * 1024,
                    downloadCount = 12,
                    deviceVideoBytes = 8L * 1024 * 1024 * 1024,
                    deviceVideoCount = 31,
                    invalidRecordCount = 2,
                    invalidRecordNames = listOf("示例视频 A", "示例视频 B"),
                    duplicateRecordCount = 1,
                    duplicateRecordNames = listOf("示例视频 C"),
                    orphanedFolderCount = 3,
                    orphanedFolderBytes = 512L * 1024 * 1024,
                    orphanedFolderNames = listOf("abc123", "def456", "ghi789"),
                    mediaPermissionGranted = true,
                    totalBytes = 128L * 1024 * 1024 * 1024,
                    freeBytes = 42L * 1024 * 1024 * 1024,
                ),
            ),
            onRefresh = {},
            onClearCache = {},
            onClearInvalidRecords = {},
            onMergeDuplicateRecords = {},
            onDeleteOrphanedFolders = {},
            onDismissMessage = {},
        )
    }
}