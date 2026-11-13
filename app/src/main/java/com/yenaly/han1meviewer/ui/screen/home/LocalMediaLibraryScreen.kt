package com.yenaly.han1meviewer.ui.screen.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.model.DeviceVideoItem
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.LocalMediaDiscoveryResult
import com.yenaly.han1meviewer.ui.component.LocalMediaDiscoveryDialog
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.component.appbar.HanimeScaffold
import com.yenaly.han1meviewer.ui.component.lazy.LazyVerticalGrid
import com.yenaly.han1meviewer.ui.screen.videoCompactMinWidth
import com.yenaly.han1meviewer.ui.screen.videoGridSpacing
import com.yenaly.han1meviewer.ui.viewmodel.LocalMediaLibraryViewModel
import com.yenaly.han1meviewer.util.SafFileManager
import com.yenaly.han1meviewer.util.SafFileManager.checkSafPermissions
import com.yenaly.yenaly_libs.utils.showLongToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val MEDIA_LIBRARY_INITIAL_DELAY_MS = 500L

@Composable
fun LocalMediaLibraryScreen(
    viewModel: LocalMediaLibraryViewModel,
    onBack: () -> Unit,
    onOpenLocalVideo: (String, String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { DownloadDatabase.instance.hanimeDownloadDao }
    val appDownloads by viewModel.appDownloads.collectAsStateWithLifecycle()
    val deviceVideos by viewModel.deviceVideos.collectAsStateWithLifecycle()
    val mediaPermissions = remember { requiredMediaPermissions() }
    var hasMediaAccess by remember { mutableStateOf(hasMediaAccess(context)) }
    var isScanning by remember { mutableStateOf(false) }
    var pendingDiscovery by remember { mutableStateOf<LocalMediaDiscoveryResult?>(null) }
    val scanUnavailableMessage = stringResource(R.string.local_media_library_scan_unavailable)
    val noChangesMessage = stringResource(R.string.local_media_library_no_changes)
    val importCompleteMessage = stringResource(R.string.local_media_library_import_complete)

    val requestMediaPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasMediaAccess = grants.values.any { it } || hasMediaAccess(context)
        if (hasMediaAccess) viewModel.refreshDeviceVideos()
    }

    fun scanLocalMedia(showResult: Boolean) {
        if (isScanning) return
        scope.launch {
            isScanning = true
            if (hasMediaAccess) viewModel.refreshDeviceVideos()
            val canScan = Preferences.downloadAutoDiscoveryEnabled &&
                !Preferences.isUsePrivateStorage &&
                !Preferences.safDownloadPath.isNullOrBlank() &&
                checkSafPermissions(context)
            if (!canScan) {
                isScanning = false
                if (showResult) showLongToast(scanUnavailableMessage)
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SafFileManager.discoverHanimeDownloads(
                        context = context,
                        dao = dao,
                        fetchMissingMetadata = false,
                    )
                }.getOrNull()
            }
            isScanning = false
            if (result?.hasPendingChanges == true) {
                pendingDiscovery = result
            } else if (showResult) {
                showLongToast(noChangesMessage)
            }
        }
    }

    LaunchedEffect(Unit) {
        // 等页面进入动画和首帧渲染完成后再启动媒体扫描，避免同时争抢主线程。
        delay(MEDIA_LIBRARY_INITIAL_DELAY_MS)
        if (hasMediaAccess) {
            viewModel.refreshDeviceVideos()
        } else {
            requestMediaPermission.launch(mediaPermissions)
        }
        scanLocalMedia(showResult = false)
    }

    pendingDiscovery?.let { discovery ->
        LocalMediaDiscoveryDialog(
            result = discovery,
            onConfirm = {
                pendingDiscovery = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        SafFileManager.importDiscoveredHanimeDownloads(dao, discovery)
                    }
                    showLongToast(importCompleteMessage)
                }
            },
            onDismiss = { pendingDiscovery = null },
        )
    }

    HanimeScaffold(
        title = stringResource(R.string.local_media_library),
        subtitle = {
            Text(
                text = stringResource(
                    R.string.local_media_library_sources_count,
                    appDownloads.size,
                    deviceVideos.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onBack = onBack,
        actions = {
            FilledIconButton(
                enabled = !isScanning,
                onClick = {
                    if (!hasMediaAccess) {
                        requestMediaPermission.launch(mediaPermissions)
                    } else {
                        scanLocalMedia(showResult = true)
                    }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_screen_rotation_24),
                    contentDescription = stringResource(R.string.local_media_library_scan),
                )
            }
        },
    ) { paddingValues ->
        LocalMediaGrid(
            appDownloads = appDownloads.distinctBy { it.videoCode },
            deviceVideos = deviceVideos,
            hasMediaAccess = hasMediaAccess,
            onOpenLocalVideo = onOpenLocalVideo,
            onRequestMediaPermission = { requestMediaPermission.launch(mediaPermissions) },
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        )
    }
}

@Composable
private fun LocalMediaGrid(
    appDownloads: List<HanimeDownloadEntity>,
    deviceVideos: List<DeviceVideoItem>,
    hasMediaAccess: Boolean,
    onOpenLocalVideo: (String, String?) -> Unit,
    onRequestMediaPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = videoCompactMinWidth),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = videoGridSpacing,
            top = 4.dp,
            end = videoGridSpacing,
            bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(videoGridSpacing),
        verticalArrangement = Arrangement.spacedBy(videoGridSpacing),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            MediaSectionHeader(
                title = stringResource(R.string.local_media_source_app_downloads),
                count = appDownloads.size,
            )
        }
        if (appDownloads.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MediaSectionHint(stringResource(R.string.local_media_source_app_downloads_empty))
            }
        } else {
            items(appDownloads, key = { "app-" + it.id }) { item ->
                val video = remember(item) {
                    HanimeInfo(
                        title = item.title,
                        coverUrl = item.coverUri ?: item.coverUrl,
                        videoCode = item.videoCode,
                        itemType = HanimeInfo.NORMAL,
                    )
                }
                VideoCardItem(
                    videoItem = video,
                    isHorizontalCard = true,
                    onClickVideosItem = { onOpenLocalVideo(item.videoCode, item.videoUri) },
                    onLongClickVideosItem = { _, _ -> },
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            MediaSectionHeader(
                title = stringResource(R.string.local_media_source_device_videos),
                count = deviceVideos.size,
            )
        }
        if (!hasMediaAccess) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MediaSectionHint(
                    text = stringResource(R.string.local_media_permission_hint),
                    onClick = onRequestMediaPermission,
                )
            }
        } else if (deviceVideos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MediaSectionHint(stringResource(R.string.local_media_source_device_videos_empty))
            }
        } else {
            items(deviceVideos, key = { "device-" + it.id }) { item ->
                DeviceVideoCard(
                    item = item,
                    onClick = { onOpenLocalVideo("-1", item.contentUri) },
                )
            }
        }
    }
}

@Composable
private fun MediaSectionHeader(title: String, count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun MediaSectionHint(text: String, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun DeviceVideoCard(
    item: DeviceVideoItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val thumbnailRequest = remember(item, context) {
        ImageRequest.Builder(context)
            .data(item.contentUri)
            .size(480, 270)
            .build()
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = thumbnailRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.38f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_play_circle_outline_24),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(32.dp),
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.68f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_smartphone_24),
                        contentDescription = stringResource(R.string.local_media_device_badge),
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp).size(16.dp),
                    )
                }
                Text(
                    text = formatDuration(item.durationMs),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(5.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                text = item.title,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.folderName.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatFileSize(item.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun requiredMediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaAccess(context: android.content.Context): Boolean {
    fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            granted(Manifest.permission.READ_MEDIA_VIDEO) ||
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            granted(Manifest.permission.READ_MEDIA_VIDEO)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.0f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
