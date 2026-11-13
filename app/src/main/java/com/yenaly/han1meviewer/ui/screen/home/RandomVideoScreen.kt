package com.yenaly.han1meviewer.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.InteractionPreferences
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.RandomVideoOptions
import com.yenaly.han1meviewer.logic.model.RandomVideoSource
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.component.appbar.HanimeScaffold
import com.yenaly.han1meviewer.ui.component.content.EmptyContent
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.component.lazy.LazyVerticalGrid
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import com.yenaly.han1meviewer.ui.preview.fakeHomePageVideos
import com.yenaly.han1meviewer.ui.screen.videoCompactMinWidth
import com.yenaly.han1meviewer.ui.screen.videoGridSpacing
import com.yenaly.han1meviewer.ui.viewmodel.RandomVideoViewModel

/**
 * 随机视频页
 *
 * 支持切换首页、本地收藏、观看历史和已下载来源，并过滤本地已有内容。
 *
 * @author Yenaly Liew
 */
@Composable
fun RandomVideoScreen(
    viewModel: RandomVideoViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val state by viewModel.randomFlow.collectAsStateWithLifecycle()
    val options by viewModel.optionsFlow.collectAsStateWithLifecycle()
    var showOptionsDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadRandom()
    }

    if (showOptionsDialog) {
        RandomVideoOptionsDialog(
            options = options,
            onDismiss = { showOptionsDialog = false },
            onConfirm = {
                showOptionsDialog = false
                viewModel.updateOptions(it)
            },
        )
    }

    HanimeScaffold(
        title = stringResource(R.string.random_video),
        subtitle = {
            Text(
                text = randomVideoSummary(options),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onBack = onBack,
        actions = {
            FilledIconButton(onClick = { showOptionsDialog = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_settings_24),
                    contentDescription = stringResource(R.string.random_video_options),
                )
            }
            FilledIconButton(onClick = { viewModel.loadRandom(force = true) }) {
                Icon(
                    painter = painterResource(R.drawable.baseline_shuffle_24),
                    contentDescription = stringResource(R.string.random_video_refresh),
                )
            }
        },
    ) { paddingValues ->
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                if (InteractionPreferences.animationsEnabled) {
                    fadeIn(tween(240)) togetherWith fadeOut(tween(160))
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            contentKey = { it::class },
            label = "random-video-state",
        ) { current ->
            when (current) {
                is PageLoadingState.Loading -> LoadingContent(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )

                is PageLoadingState.Error -> ErrorContent(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    onRetry = { viewModel.loadRandom(force = true) },
                )

                is PageLoadingState.Success -> {
                    val videos = current.info
                    if (videos.isEmpty()) {
                        EmptyContent(
                            hint = stringResource(R.string.random_video_empty_title),
                            subHint = stringResource(R.string.random_video_empty_description),
                        )
                    } else {
                        RandomVideoGrid(
                            videos = videos,
                            onOpenVideo = { video -> onOpenVideo(video.videoCode) },
                            modifier = Modifier.fillMaxSize().padding(paddingValues),
                        )
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun randomVideoSummary(options: RandomVideoOptions): String {
    val source = options.sources
        .ifEmpty { setOf(RandomVideoSource.LATEST_HANIME) }
        .sortedBy { it.ordinal }
        // map 是 inline，能在 @Composable 上下文里调用 stringResource；
        // 不能直接写进 joinToString 的 transform（非 inline）。
        .map { randomVideoSourceLabel(it) }
        .joinToString(" / ")
    val filters = buildList {
        if (options.excludeWatched) add(stringResource(R.string.random_video_exclude_watched_short))
        if (options.excludeDownloaded) add(stringResource(R.string.random_video_exclude_downloaded_short))
        if (options.excludeFavorited) add(stringResource(R.string.random_video_exclude_favorited_short))
    }
    return if (filters.isEmpty()) {
        stringResource(R.string.random_video_summary_with_source, source)
    } else {
        stringResource(
            R.string.random_video_summary_with_source_filters,
            source,
            filters.joinToString(" · "),
        )
    }
}

@Composable
private fun RandomVideoOptionsDialog(
    options: RandomVideoOptions,
    onDismiss: () -> Unit,
    onConfirm: (RandomVideoOptions) -> Unit,
) {
    var draft by remember(options) { mutableStateOf(options) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.random_video_options)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OptionSectionTitle(stringResource(R.string.random_video_source))
                RandomVideoSource.entries.forEach { source ->
                    val checked = source in draft.sources
                    OptionCheckRow(
                        title = randomVideoSourceLabel(source),
                        checked = checked,
                        // 至少保留一个来源
                        enabled = !checked || draft.sources.size > 1,
                        onCheckedChange = { isChecked ->
                            draft = draft.copy(
                                sources = if (isChecked) {
                                    draft.sources + source
                                } else {
                                    draft.sources - source
                                },
                            )
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OptionSectionTitle(stringResource(R.string.random_video_filters))
                OptionSwitchRow(
                    title = stringResource(R.string.random_video_exclude_watched),
                    checked = draft.excludeWatched,
                    onCheckedChange = { draft = draft.copy(excludeWatched = it) },
                )
                OptionSwitchRow(
                    title = stringResource(R.string.random_video_exclude_downloaded),
                    checked = draft.excludeDownloaded,
                    onCheckedChange = { draft = draft.copy(excludeDownloaded = it) },
                )
                OptionSwitchRow(
                    title = stringResource(R.string.random_video_exclude_favorited),
                    checked = draft.excludeFavorited,
                    onCheckedChange = { draft = draft.copy(excludeFavorited = it) },
                )
                OptionSwitchRow(
                    title = stringResource(R.string.random_video_avoid_recent),
                    checked = draft.avoidRecent,
                    onCheckedChange = { draft = draft.copy(avoidRecent = it) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun randomVideoSourceLabel(source: RandomVideoSource): String = when (source) {
    RandomVideoSource.LATEST_HANIME -> stringResource(R.string.random_video_source_latest_hanime)
    RandomVideoSource.SHORT_EPISODE -> stringResource(R.string.random_video_source_short_episode)
    RandomVideoSource.THREE_DCG -> stringResource(R.string.random_video_source_three_dcg)
}

@Composable
private fun OptionSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun OptionCheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
        )
    }
}

@Composable
private fun OptionSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun RandomVideoGrid(
    videos: List<HanimeInfo>,
    onOpenVideo: (HanimeInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = videoCompactMinWidth),
        modifier = modifier,
        contentPadding = PaddingValues(videoGridSpacing),
        horizontalArrangement = Arrangement.spacedBy(videoGridSpacing),
        verticalArrangement = Arrangement.spacedBy(videoGridSpacing),
    ) {
        items(videos, key = { it.videoCode }) { video ->
            VideoCardItem(
                videoItem = video,
                // 随机池里 NORMAL / SIMPLIFIED 混在一起，若按 itemType 决定布局，
                // 3:4 与 16:9 会混排导致「有些卡片变大」；统一用横卡保证网格整齐。
                isHorizontalCard = true,
                isWatched = video.watched == true,
                onClickVideosItem = { onOpenVideo(video) },
                onLongClickVideosItem = { _, _ -> },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 800)
@Composable
private fun RandomVideoGridPreview() {
    ComponentPreview {
        RandomVideoGrid(
            videos = fakeHomePageVideos,
            onOpenVideo = {},
        )
    }
}
