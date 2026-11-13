package com.yenaly.han1meviewer.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.model.VideoItemType
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import com.yenaly.han1meviewer.ui.preview.fakeVideosItem
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import com.yenaly.han1meviewer.util.DisplayTextLocalizer


/**
 * 标准视频卡片项组件。
 *
 * 展示视频封面、标题等信息，支持水平和垂直两种布局，支持点击和长按交互。
 * 自适应宽度，由外部布局控制。
 *
 * @param videoItem 视频数据
 * @param isHorizontalCard 是否为水平卡片布局，默认为 true
 * @param enablePressScale 是否使用常驻缩放层，滚动密集列表可关闭。
 * @param onClickVideosItem 点击回调，参数为视频 ID
 * @param onLongClickVideosItem 长按回调，参数为视频 ID 和视频标题
 */
@Composable
fun VideoCardItem(
    modifier: Modifier = Modifier,
    videoItem: VideoItemType,
    isHorizontalCard: Boolean = true,
    isWatched: Boolean = false,
    isPlaying: Boolean = false,
    enablePressScale: Boolean = true,
    onClickVideosItem: (String) -> Unit,
    onLongClickVideosItem: (String, String) -> Unit,
) {
    val textFontSize = dimensionResource(id = R.dimen.video_view_and_time_and_duration).value.sp
    val iconSize = dimensionResource(id = R.dimen.view_view_and_time_icon_size)
    val imageAspectRatio = if (isHorizontalCard) 16f / 9f else 3f / 4f
    val interactionSource = remember { MutableInteractionSource() }
    val isCompact = Preferences.useCompactMode
    val cardShape = RoundedCornerShape(if (isCompact) 10.dp else 16.dp)
    val pressScaleModifier = if (enablePressScale) {
        Modifier.pressScale(interactionSource)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .combinedClickable(
                interactionSource = interactionSource,
                enabled = !isPlaying,
                onClick = { onClickVideosItem(videoItem.videoCode) },
                onLongClick = { onLongClickVideosItem(videoItem.videoCode, videoItem.title) },
            ),
        shape = cardShape,
        tonalElevation = if (isCompact) 0.dp else 1.dp,
        shadowElevation = if (isCompact) 1.dp else 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio),
            ) {
                RetryableImage(
                    model = videoItem.coverUrl,
                    contentDescription = videoItem.title,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.FillWidth,
                    crossfade = false,
                )

                if (isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 6.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.played),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    // 未观看时右上角显示时长角标
                    videoItem.duration?.takeIf { it.isNotBlank() }?.let { duration ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = duration,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // 底部半透明遮罩（播放量和时长）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surfaceVariant
                                ),
                            ),
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    videoItem.views?.let {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_play_circle_outline_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(iconSize),
                        )
                        Text(
                            modifier = Modifier.padding(horizontal = 2.dp),
                            text = DisplayTextLocalizer.localizeViews(it),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = textFontSize,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    videoItem.duration?.let {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_access_time_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(iconSize),
                        )
                        Text(
                            modifier = Modifier.padding(horizontal = 2.dp),
                            text = it,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = textFontSize,
                        )
                    }
                }
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_play_circle_outline_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.now_playing),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Text(
                text = videoItem.title,
                maxLines = 2,
                minLines = 2,
                style = MaterialTheme.typography.titleSmall,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
            )
            Text(
                text = videoItem.currentArtist ?: "作者",
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
                    .heightIn(min = iconSize),
            ) {
                videoItem.reviews?.takeIf { it.isNotEmpty() }?.let { reviewsText ->
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_thumb_up_alt_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = reviewsText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!videoItem.uploadTime.isNullOrEmpty()) {
                    Text(
                        text = DisplayTextLocalizer.localizeRelativeTime(videoItem.uploadTime!!),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoCardItemPreview() {
    ComponentPreview {
        VideoCardItem(
            videoItem = fakeVideosItem,
            onClickVideosItem = {},
            onLongClickVideosItem = { _, _ -> },
            isWatched = true,
            isPlaying = true
        )
    }
}
