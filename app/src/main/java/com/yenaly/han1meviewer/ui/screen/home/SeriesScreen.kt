package com.yenaly.han1meviewer.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.SeriesCompletionStore
import com.yenaly.han1meviewer.logic.model.LocalSeries
import com.yenaly.han1meviewer.logic.model.LocalSeriesEpisode
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.appbar.HanimeScaffold
import com.yenaly.han1meviewer.ui.component.content.EmptyContent
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import com.yenaly.han1meviewer.ui.viewmodel.SeriesViewModel

/**
 * 自动系列归并页。
 *
 * 数据来源为本地收藏、观看历史和已下载视频，不主动请求网站。
 */
@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel,
    onBack: () -> Unit,
    onOpenEpisode: (String, String?) -> Unit,
    onSearchMissingEpisode: (String, Int) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedSeries by remember { mutableStateOf<LocalSeries?>(null) }
    var completionRevision by remember { mutableIntStateOf(0) }

    HanimeScaffold(
        title = stringResource(R.string.auto_series),
        subtitle = {
            Text(
                text = stringResource(R.string.auto_series_summary),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onBack = onBack,
    ) { paddingValues ->
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(150))
            },
            contentKey = { it::class },
            label = "auto-series-state",
        ) { current ->
            when (current) {
                is PageLoadingState.Loading -> LoadingContent(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )

                is PageLoadingState.Error -> ErrorContent(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    onRetry = {},
                )

                is PageLoadingState.Success -> {
                    if (current.info.isEmpty()) {
                        EmptyContent(
                            hint = stringResource(R.string.auto_series_empty_title),
                            subHint = stringResource(R.string.auto_series_empty_description),
                        )
                    } else {
                        SeriesList(
                            series = current.info,
                            onOpenEpisode = onOpenEpisode,
                            onOpenCompletion = { selectedSeries = it },
                            modifier = Modifier.fillMaxSize().padding(paddingValues),
                        )
                    }
                }

                else -> Unit
            }
        }
    }

    selectedSeries?.let { series ->
        val ignoredEpisodes = remember(series.id, completionRevision) {
            SeriesCompletionStore.ignoredEpisodes(series.id)
        }
        SeriesCompletionDialog(
            series = series,
            ignoredEpisodes = ignoredEpisodes,
            onSearch = { episode -> onSearchMissingEpisode(series.title, episode) },
            onMarkMissing = { episode ->
                SeriesCompletionStore.setIgnored(series.id, episode, true)
                completionRevision++
            },
            onRestoreMissing = { episode ->
                SeriesCompletionStore.setIgnored(series.id, episode, false)
                completionRevision++
            },
            onDismiss = { selectedSeries = null },
        )
    }
}

@Composable
private fun SeriesList(
    series: List<LocalSeries>,
    onOpenEpisode: (String, String?) -> Unit,
    onOpenCompletion: (LocalSeries) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(series, key = { it.id }) { item ->
            SeriesCard(
                series = item,
                onOpenEpisode = onOpenEpisode,
                onOpenCompletion = { onOpenCompletion(item) },
            )
        }
    }
}

@Composable
private fun SeriesCard(
    series: LocalSeries,
    onOpenEpisode: (String, String?) -> Unit,
    onOpenCompletion: () -> Unit,
) {
    var expanded by rememberSaveable(series.id) { mutableStateOf(false) }
    val nextEpisode = series.nextEpisode

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                SeriesCover(
                    coverUrl = series.coverUrl,
                    modifier = Modifier
                        .width(86.dp)
                        .height(116.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = series.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pluralStringResource(R.plurals.auto_series_episode_count, series.episodeCount, series.episodeCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(
                            R.string.auto_series_watched_count,
                            series.completedCount,
                            series.episodeCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { series.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (series.missingEpisodes.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.auto_series_missing_episodes,
                            series.missingEpisodes.joinToString(", "),
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (nextEpisode != null) {
                    TextButton(
                        onClick = {
                            onOpenEpisode(nextEpisode.videoCode, nextEpisode.localUri)
                        },
                    ) {
                        Text(
                            text = stringResource(
                                R.string.auto_series_continue_episode,
                                nextEpisode.episode,
                            ),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.auto_series_completed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onOpenCompletion) {
                        Text(stringResource(R.string.series_completion_wizard))
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    series.episodes.forEach { episode ->
                        EpisodeChip(
                            episode = episode,
                            onClick = {
                                onOpenEpisode(episode.videoCode, episode.localUri)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesCompletionDialog(
    series: LocalSeries,
    ignoredEpisodes: Set<Int>,
    onSearch: (Int) -> Unit,
    onMarkMissing: (Int) -> Unit,
    onRestoreMissing: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val missingEpisodes = series.missingEpisodes.filterNot { it in ignoredEpisodes }
    val downloadedCount = series.episodes.count { it.isDownloaded }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(series.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.series_completion_summary,
                        series.episodeCount,
                        series.completedCount,
                        downloadedCount,
                        missingEpisodes.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.series_completion_missing),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (missingEpisodes.isEmpty()) {
                    Text(
                        text = stringResource(R.string.series_completion_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    missingEpisodes.forEach { episode ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.auto_series_episode_format, episode),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onSearch(episode) }) {
                                Text(stringResource(R.string.search))
                            }
                            TextButton(onClick = { onMarkMissing(episode) }) {
                                Text(stringResource(R.string.series_completion_mark_nonexistent))
                            }
                        }
                    }
                }
                if (ignoredEpisodes.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.series_completion_ignored),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    ignoredEpisodes.sorted().forEach { episode ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.auto_series_episode_format, episode),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onRestoreMissing(episode) }) {
                                Text(stringResource(R.string.series_completion_restore))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun SeriesCover(
    coverUrl: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_video_quilty),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EpisodeChip(
    episode: LocalSeriesEpisode,
    onClick: () -> Unit,
) {
    val episodeText = stringResource(
        R.string.auto_series_episode_format,
        episode.episode,
    )
    val downloadedText = stringResource(R.string.auto_series_downloaded)
    val label = if (episode.isDownloaded) "$episodeText · $downloadedText" else episodeText

    FilterChip(
        selected = episode.isWatched,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Preview(showBackground = true, widthDp = 420, heightDp = 800)
@Composable
private fun SeriesCardPreview() {
    ComponentPreview {
        SeriesCard(
            series = LocalSeries(
                id = "preview",
                title = "小悪魔の補習計画",
                coverUrl = "",
                episodes = listOf(
                    LocalSeriesEpisode(
                        videoCode = "1",
                        title = "第1話",
                        coverUrl = "",
                        episode = 1,
                        progress = 0L,
                        duration = 0L,
                        isCompleted = true,
                        isFavorite = true,
                        isDownloaded = false,
                        localUri = null,
                    ),
                    LocalSeriesEpisode(
                        videoCode = "2",
                        title = "第2話",
                        coverUrl = "",
                        episode = 2,
                        progress = 0L,
                        duration = 0L,
                        isCompleted = false,
                        isFavorite = false,
                        isDownloaded = true,
                        localUri = null,
                    ),
                ),
                missingEpisodes = emptyList(),
                latestTimestamp = 0L,
            ),
            onOpenEpisode = { _, _ -> },
            onOpenCompletion = {},
        )
    }
}
