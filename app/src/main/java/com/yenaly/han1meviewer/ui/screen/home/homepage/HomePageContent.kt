package com.yenaly.han1meviewer.ui.screen.home.homepage

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.logic.model.HomePage
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import com.yenaly.han1meviewer.ui.preview.fakeAnnouncements
import com.yenaly.han1meviewer.ui.preview.fakeHomePage
import com.yenaly.han1meviewer.ui.screen.home.homepage.component.AnnouncementCard
import com.yenaly.han1meviewer.ui.screen.home.homepage.component.BannerCarousel
import com.yenaly.han1meviewer.ui.screen.home.homepage.component.CategoryRow
import com.yenaly.han1meviewer.ui.screen.home.homepage.component.ContinueWatchingRow

/**
 * 渲染首页可滚动内容区域。
 *
 * @param data 主页数据
 * @param onEvent 主页事件回调
 * @param onCloseAnnouncement 关闭公告时调用。
 * @param modifier 应用于列表根布局的修饰符。
 */
@Composable
fun HomePageContent(
    data: HomeData,
    continueWatching: List<WatchHistoryEntity> = emptyList(),
    onEvent: (HomeUiEvent) -> Unit,
    onCloseAnnouncement: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 轮播图多页化：官方 Banner 排首位，其余页从三个分类轮流取。
    //
    // 素材来源固定为「最新里番 / 泡面番 / 3D作品」三类，按刷新**整批轮换**：
    // - 每次下拉刷新（data.refreshEpoch +1）切换一个主分类，一次取满 4 条；
    // - 轮完三个分类算一圈，下一圈从各类第 5 条开始接着取；
    // - 主分类不足 4 条时，按轮换顺序从另外两类补齐，保证页数不塌。
    //
    // 之前是「三类轮流各取一条」：相邻两次刷新会有 3 条重合，看起来就是「有些内容没变」。
    //
    // 不用 data.page 当 key：HomePage.timestamp 每次网络刷新都会变，
    // 会导致重组、关闭公告、isWatched 变化等非刷新事件也重新洗牌。
    val bannerSignature = remember(
        data.page.banner,
        data.page.latestHanime,
        data.page.shortEpisodeAnime,
        data.page.threeDCG,
    ) {
        buildList {
            data.page.banner?.let { add(BannerSource(it.videoCode, it.title, it.picUrl)) }
            (data.page.latestHanime + data.page.shortEpisodeAnime + data.page.threeDCG).forEach {
                add(BannerSource(it.videoCode, it.title, it.coverUrl))
            }
        }
    }
    val banners = remember(bannerSignature, data.refreshEpoch) {
        val built = buildList {
            data.page.banner?.let { add(it) }
            val officialCode = data.page.banner?.videoCode

            val pools = listOf(
                data.page.latestHanime,      // 最新里番
                data.page.shortEpisodeAnime, // 泡面番
                data.page.threeDCG,          // 3D作品
            )
            // 每次刷新换一个主分类；转完一圈后各分类整体往后取下一批
            val categoryIndex = data.refreshEpoch % pools.size
            val round = data.refreshEpoch / pools.size
            val rotatedPools = pools.drop(categoryIndex) + pools.take(categoryIndex)

            val picked = mutableListOf<HomePage.Banner>()
            rotatedPools.forEachIndexed { order, pool ->
                if (picked.size >= SUPPLEMENT_BANNER_COUNT) return@forEachIndexed
                // 主分类按圈数向后推进；补齐用的分类从头取即可
                val skip = if (order == 0) round * SUPPLEMENT_BANNER_COUNT else 0
                pool.asSequence()
                    // 官方 Banner 已展示的不重复；空封面会显示失败图，直接跳过
                    .filter { it.videoCode != officialCode }
                    .filter { it.coverUrl.isNotBlank() }
                    .filter { item -> picked.none { it.videoCode == item.videoCode } }
                    .drop(skip)
                    .take(SUPPLEMENT_BANNER_COUNT - picked.size)
                    .mapTo(picked) {
                        HomePage.Banner(it.title, null, it.coverUrl, it.videoCode)
                    }
            }
            addAll(picked)
        }
        // 每次刷新把整个轮播整体左移一格：官方 Banner 仍留在轮播里，但不再永远占第 1 页，
        // 所以「刷新后第一个不变」的问题不会出现。
        val shift = if (built.size > 1) data.refreshEpoch % built.size else 0
        if (shift == 0) built else built.drop(shift) + built.take(shift)
    }
    val announcements = remember(data.announcements) {
        data.announcements.filter { it.isActive }
    }

    val categoryOrder = homeCategoryOrder
    val hiddenCategoryKeys = hiddenHomeCategoryKeys
    val categories = remember(data.page, categoryOrder, hiddenCategoryKeys) {
        buildCategoryList(data.page)
    }
    val categoryByKey = remember(categories) {
        categories.associateBy { it.key }
    }
    // 底部导航栏模式下为悬浮导航栏（80dp + 上下 8dp 空隙 + 系统导航条）预留空间，
    // 保证列表末尾可滚至导航栏上方；内容滚动时仍会从悬浮导航栏后方穿过
    val bottomNavReserved = if (Preferences.useBottomNavigation) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp
    } else 0.dp
    val listState = rememberLazyListState()
    // listState 的滚动状态属于 @FrequentlyChangingValue：直接在 composition 里读取
    // 会在滚动过程中触发不必要的重组。用 derivedStateOf 收敛成布尔值，
    // 只有"是否该自动轮播"真正翻转时才通知重组。
    val bannerAutoScrollEnabled by remember(listState) {
        derivedStateOf {
            !listState.isScrollInProgress && listState.firstVisibleItemIndex == 0
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = bottomNavReserved),
        // 首页分类较多，滚动时关闭逐项入场动画，避免与图片解码同时抢帧。
        enableItemAnimation = false,
    ) {
        item(key = "banner", contentType = "home_banner") {
            BannerCarousel(
                banners = banners,
                onBannerClick = { videoCode ->
                    videoCode?.let {
                        onEvent(HomeUiEvent.OpenVideo(it))
                    }
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                // 用户正在上下滚动或 Banner 已离屏时暂停自动轮播，减少无效绘制。
                autoScrollEnabled = bannerAutoScrollEnabled,
            )
        }
        if (announcements.isNotEmpty()) {
            item(key = "announcement", contentType = "home_announcement") {
                AnnouncementCard(
                    announcements = announcements,
                    onAnnouncementClick = { announcement ->
                        onEvent(HomeUiEvent.ShowAnnouncementDialog(announcement))
                    },
                    onClose = onCloseAnnouncement,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        categoryOrder.forEach { key ->
            when (key) {
                HOME_CATEGORY_CONTINUE_WATCHING -> {
                    if (continueWatching.isNotEmpty() && key !in hiddenCategoryKeys) {
                        item(
                            key = "home_category_$key",
                            contentType = "home_continue_watching",
                        ) {
                            ContinueWatchingRow(
                                histories = continueWatching,
                                onVideoClick = { videoCode ->
                                    onEvent(HomeUiEvent.OpenVideo(videoCode, autoPlay = true))
                                },
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }

                else -> {
                    val category = categoryByKey[key] ?: return@forEach
                    item(
                        key = "home_category_$key",
                        contentType = "home_category",
                    ) {
                        CategoryRow(
                            title = stringResource(category.titleRes),
                            videos = category.videos,
                            onMoreClick = {
                                val params = category.toAdvancedSearchParams()
                                if (params.isNotEmpty()) {
                                    onEvent(HomeUiEvent.NavigateToSearchAdvanced(params))
                                }
                            },
                            onVideoClick = { code ->
                                onEvent(HomeUiEvent.OpenVideo(code))
                            },
                            onVideoLongClick = { _, _ ->
                                // onEvent(HomeUiEvent.LongPressVideoCopy(code, title))
                            },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 轮播图内容签名：只保留影响轮播展示的字段，用来判断「内容是否真的变了」。 */
private data class BannerSource(
    val videoCode: String?,
    val title: String,
    val coverUrl: String,
)

/** 官方 Banner 之外最多补充几页，太少滑不起来，太多会稀释内容。 */
private const val SUPPLEMENT_BANNER_COUNT = 4

@Preview(showBackground = true, name = "首页主内容")
@Composable
private fun HomePageContentPreview() {
    ComponentPreview {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomePageContent(
                data = HomeData(
                    page = fakeHomePage,
                    announcements = fakeAnnouncements,
                ),
                onEvent = {},
                onCloseAnnouncement = {},
            )
        }
    }
}
