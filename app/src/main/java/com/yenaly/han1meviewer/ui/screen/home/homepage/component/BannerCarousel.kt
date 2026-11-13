package com.yenaly.han1meviewer.ui.screen.home.homepage.component

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.HomePage
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import com.yenaly.han1meviewer.ui.preview.fakeBanner
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 显示首页 Banner 轮播图。
 *
 * @param banners Banner 数据，为空时不渲染内容。
 * @param onBannerClick 点击 Banner 时调用，参数为视频编号。
 * @param modifier 应用于轮播图根布局的修饰符。
 */
@Composable
fun BannerCarousel(
    banners: List<HomePage.Banner>,
    onBannerClick: (String?) -> Unit,
    modifier: Modifier = Modifier,
    autoScrollEnabled: Boolean = true,
) {
    if (banners.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { banners.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    // 刷新后页数可能变少（例如补充池不足 4 条）：把越界的页码收回来，
    // 否则 currentPage 会停在不存在的位置，指示点全部失焦。
    LaunchedEffect(banners.size) {
        if (pagerState.currentPage > banners.lastIndex) {
            pagerState.scrollToPage(banners.lastIndex)
        }
    }

    // 自动轮播：每 4 秒切换到下一页，用户手动拖动时跳过本轮
    LaunchedEffect(pagerState, banners.size, autoScrollEnabled) {
        if (banners.size <= 1 || !autoScrollEnabled) return@LaunchedEffect
        while (true) {
            delay(4000)
            if (!pagerState.isScrollInProgress && autoScrollEnabled) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % banners.size)
            }
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(0.dp),
                pageSpacing = 0.dp,
                beyondViewportPageCount = 1
            ) { page ->
                val item = banners[page.coerceIn(banners.indices)]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onBannerClick(item.videoCode) }
                ) {
                    RetryableImage(
                        model = item.picUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.h_chan_loading),
                        error = painterResource(R.drawable.h_chan_load_failed)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        item.description?.let { desc ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (banners.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(banners.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
                                )
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                        )
                        if (index < banners.lastIndex) Spacer(Modifier.width(6.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Banner 轮播")
@Composable
private fun BannerCarouselPreview() {
    ComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp)
        ) {
            BannerCarousel(
                banners = fakeBanner,
                onBannerClick = {}
            )
        }
    }
}
