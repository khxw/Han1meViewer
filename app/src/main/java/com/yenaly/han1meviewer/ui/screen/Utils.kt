package com.yenaly.han1meviewer.ui.screen

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import com.yenaly.han1meviewer.ui.theme.SpacingSmall
import com.yenaly.han1meviewer.ui.theme.VideoNormalCardMinWidth
import com.yenaly.han1meviewer.ui.theme.VideoSimplifiedCardMinWidth

/**
 * 视频网格/横滑行间距：紧凑模式下缩小为 4dp，否则 8dp
 */
val videoGridSpacing: Dp
    get() = if (Preferences.useCompactMode) SpacingSmall else SpacingNormal

/**
 * 自适应网格卡片最小宽度：紧凑模式下缩小（一屏更多列）
 */
val videoCompactMinWidth: Dp
    get() = if (Preferences.useCompactMode) 120.dp else VideoNormalCardMinWidth

/**
 * 简化卡片（竖版 3:4）自适应最小宽度：紧凑模式下缩小
 */
val videoSimplifiedCompactMinWidth: Dp
    get() = if (Preferences.useCompactMode) 80.dp else VideoSimplifiedCardMinWidth

/**
 * 图片加载组件：
 * - 加载中显示 [placeholder]，失败显示 [error]
 * - 失败后不自动重试：网络切换（如关闭 Wi-Fi）时图片加载失败直接显示错误占位图，
 *   避免反复重试造成"转圈重新加载"的观感；网络恢复后重新进入/滚动会自然重新加载
 */
@Composable
fun RetryableImage(
    model: Any,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    placeholder: Painter,
    error: Painter,
    contentScale: ContentScale? = ContentScale.Fit,
    crossfade: Boolean = true,
) {
    val context = LocalContext.current
    // 列表滚动时复用同一个请求；逐卡片淡入会持续触发重绘，故列表内关闭 crossfade。
    val imageRequest = remember(context, model, crossfade) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(crossfade)
            .listener(
                onError = { _, result ->
                    Log.e("CoilError", "Image load failed", result.throwable)
                }
            ).build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        placeholder = placeholder,
        error = error,
        modifier = modifier,
        contentScale = contentScale ?: ContentScale.Fit
    )
}

@Composable
fun getColumnCount(itemWidth: Int): Int {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width
    val screenWidthDp = with(density) { screenWidthPx.toDp() }
    return maxOf(2, (screenWidthDp / itemWidth.dp).toInt())
}

@Composable
fun rememberCardResponsiveWidth(
    horizontalPadding: Dp = SpacingLarge,
    itemSpacing: Dp = SpacingNormal
): Pair<Dp, Float> {
    val containerWidth = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    val currentWidthDp = with(density) { containerWidth.toDp() }

    val isPreview = LocalInspectionMode.current
    val itemsToShow = if (!isPreview) {
        Preferences.horizontalCardCountConfig.countForWidthDp(currentWidthDp.value.toInt())
    } else {
        val estimatedCardWidth = 160.dp
        maxOf(1f, ((currentWidthDp - (horizontalPadding * 2)) / (estimatedCardWidth + itemSpacing)))
    }

    val safeItemsToShow = maxOf(1f, itemsToShow)
    val cardWidth = (currentWidthDp - (horizontalPadding * 2) - (itemSpacing * (safeItemsToShow - 1))) / safeItemsToShow

    return Pair(cardWidth, safeItemsToShow)
}

@Composable
fun rememberVideoGridColumns(): Int {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width
    val screenWidthDp = with(density) { screenWidthPx.toDp() }

    val isPreview = LocalInspectionMode.current

    return if (!isPreview && Preferences.tabletMode) {
        val base = Preferences.searchGridColumnsConfig.columnsForWidthDp(screenWidthDp.value.toInt())
        if (Preferences.useCompactMode) base + 1 else base
    } else {
        val base = maxOf(2, ((screenWidthDp + SpacingNormal) / (VideoNormalCardMinWidth + SpacingNormal)).toInt())
        if (Preferences.useCompactMode) base + 1 else base
    }
}

@Composable
fun rememberRandomLoadingHint(): String {
    val placeholders = stringArrayResource(R.array.loading_hints)
    return remember(placeholders) { placeholders.random() }
}