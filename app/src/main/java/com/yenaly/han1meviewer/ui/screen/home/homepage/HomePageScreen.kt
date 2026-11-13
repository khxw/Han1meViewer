package com.yenaly.han1meviewer.ui.screen.home.homepage

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.PageState
import com.yenaly.han1meviewer.logic.state.dataOrNull
import com.yenaly.han1meviewer.ui.component.PageContent
import com.yenaly.han1meviewer.ui.component.PullRefreshOverlay
import com.yenaly.han1meviewer.ui.component.isFirstPageEmpty
import com.yenaly.han1meviewer.ui.component.isFirstPageError
import com.yenaly.han1meviewer.ui.component.isFirstPageLoading
import com.yenaly.han1meviewer.ui.screen.home.homepage.component.HomePageTopBar
import com.yenaly.han1meviewer.ui.screen.rememberRandomLoadingHint
import com.yenaly.han1meviewer.util.toNetworkErrorMessageRes

/**
 * 首页容器屏幕，负责连接 ViewModel 状态与导航回调。
 *
 * @param viewModel 提供首页数据与公告数据的 ViewModel。
 * @param isDrawerOpen 侧边抽屉是否已打开。
 * @param modifier 作用于屏幕根布局的修饰符。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomePageScreen(
    viewModel: HomePageViewModel,
    isDrawerOpen: Boolean,
    onEvent: (HomeUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pageState by viewModel.homePageFlow.collectAsStateWithLifecycle()
    val continueWatching by remember(viewModel) {
        viewModel.loadContinueWatching()
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val refreshState = rememberPullToRefreshState()
    var wasRefreshing by remember { mutableStateOf(false) }
    val loadingHint = rememberRandomLoadingHint()
    LaunchedEffect(Unit) {
        if (pageState !is PageState.Success) {
            viewModel.getHomePage(isRefresh = false)
        }
    }

    BackHandler(enabled = !isDrawerOpen) {
        onEvent(HomeUiEvent.ShowExitDialog)
    }

    val isCurrentlyRefreshing = (pageState as? PageState.Success)?.isRefreshing == true

    LaunchedEffect(pageState) {
        val errorState = pageState as? PageState.Error
        if (wasRefreshing && errorState?.cachedInfo != null) {
            Toast.makeText(
                context,
                errorState.throwable.toNetworkErrorMessageRes(),
                Toast.LENGTH_SHORT
            ).show()
        }
        wasRefreshing = isCurrentlyRefreshing
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomePageTopBar(
                onOpenDrawer = { onEvent(HomeUiEvent.OpenDrawer) },
                onSearchClick = { onEvent(HomeUiEvent.OpenSearchPage()) },
                onNavigateToPreview = { onEvent(HomeUiEvent.NavigateToPreview) }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullToRefresh(
                        state = refreshState,
                        isRefreshing = isCurrentlyRefreshing,
                        onRefresh = {
                            viewModel.getHomePage(isRefresh = true)
                        }
                    )
            ) {
                PageContent(
                    isLoading = pageState.isFirstPageLoading,
                    isError = pageState.isFirstPageError,
                    isEmpty = pageState.isFirstPageError || pageState.isFirstPageEmpty,
                    errorMessage = (pageState as? PageState.Error)?.throwable?.toNetworkErrorMessageRes()?.let {
                        stringResource(it)
                    } ?: "",
                    onRetry = { viewModel.getHomePage(isRefresh = false) },
                    loadingMessage = loadingHint,
                ) {
                    val homeData = pageState.dataOrNull

                    if (homeData != null) {
                        // 直接渲染，不再套 AnimatedContent：HomePage 里的 timestamp 每次刷新都会变，
                        // 用它当过渡键会让每次下拉刷新都重建整个首页内容——
                        // 列表跳回顶部、轮播回到第 0 页、自动轮播计时也被重置。
                        // 加载/错误/内容之间的切换动画由外层 PageContent 负责，这里不需要再来一层。
                        HomePageContent(
                            data = homeData,
                            continueWatching = continueWatching,
                            onEvent = onEvent,
                            onCloseAnnouncement = viewModel::dismissAnnouncements,
                        )
                    }
                }

                PullRefreshOverlay(
                    state = refreshState,
                    isRefreshing = isCurrentlyRefreshing,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }
        }

        FloatingActionButton(
            onClick = { onEvent(HomeUiEvent.NavigateToRandomVideo) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .navigationBarsPadding()
                // 底部导航栏模式下抬升到悬浮导航栏上方
                .padding(bottom = if (Preferences.useBottomNavigation) 96.dp else 0.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_shuffle_24),
                contentDescription = stringResource(R.string.random_video),
            )
        }
    }
}
