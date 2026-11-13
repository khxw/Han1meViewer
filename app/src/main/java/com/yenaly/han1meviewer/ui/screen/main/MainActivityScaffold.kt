package com.yenaly.han1meviewer.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.navigation.main.MainDrawerDestination
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import kotlinx.coroutines.launch

/**
 * 底部导航栏目的地（开启底部导航栏模式后显示）。
 */
enum class MainBottomDestination(
    @param:androidx.annotation.DrawableRes val iconRes: Int,
    @param:androidx.annotation.StringRes val titleRes: Int,
) {
    Home(R.drawable.ic_baseline_home_24, R.string.home_page),
    Download(R.drawable.ic_baseline_download_24, R.string.download),
    Settings(R.drawable.ic_baseline_settings_24, R.string.settings),
}

@Composable
fun MainActivityScaffold(
    drawerState: DrawerState,
    drawerEnabled: Boolean,
    selectedDestination: MainDrawerDestination?,
    avatarUrl: String?,
    username: String?,
    isLoggedIn: Boolean,
    isLoading: Boolean,
    currentSite: String,
    useBottomNavigation: Boolean,
    selectedBottomItem: MainBottomDestination?,
    onBottomNavigationSelected: (MainBottomDestination) -> Unit,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit,
    onSwitchSiteClick: () -> Unit,
    onDrawerItemSelected: (MainDrawerDestination) -> Boolean,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drawerFraction by animateFloatAsState(
        targetValue = if (drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open) 1f else 0f,
        label = "drawer_fraction",
    )

    ModalNavigationDrawer(
        gesturesEnabled = drawerEnabled,
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = DrawerDefaults.shape,
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                MainDrawerHeader(
                    avatarUrl = avatarUrl,
                    username = username,
                    isLoggedIn = isLoggedIn,
                    isLoading = isLoading,
                    currentSite = currentSite,
                    onAvatarClick = onAvatarClick,
                    onAvatarLongClick = onAvatarLongClick,
                    onSwitchSiteClick = onSwitchSiteClick,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    MainDrawerPrimaryItems(selectedDestination, onDrawerItemSelected)
                    MainDrawerSection(
                        titleRes = R.string.my_list,
                        items = listOf(
                            MainDrawerDestination.WatchLater,
                            MainDrawerDestination.FavVideo,
                            MainDrawerDestination.Playlist,
                            MainDrawerDestination.Subscription,
                            MainDrawerDestination.CreatorCenter,
                        ),
                        selectedDestination = selectedDestination,
                        onItemClick = { destination ->
                            if (onDrawerItemSelected(destination)) {
                                scope.launch { drawerState.close() }
                            }
                        },
                    )
                    MainDrawerSection(
                        titleRes = R.string.video,
                        items = listOf(
                            MainDrawerDestination.WatchHistory,
                            MainDrawerDestination.LocalMedia,
                            MainDrawerDestination.LocalFavorite,
                            MainDrawerDestination.Series,
                            MainDrawerDestination.Download,
                        ),
                        selectedDestination = selectedDestination,
                        onItemClick = { destination ->
                            if (onDrawerItemSelected(destination)) {
                                scope.launch { drawerState.close() }
                            }
                        },
                    )
                    MainDrawerSection(
                        titleRes = R.string.debug,
                        items = listOf(
                            MainDrawerDestination.NetworkDiagnostics,
                        ),
                        selectedDestination = selectedDestination,
                        onItemClick = { destination ->
                            if (onDrawerItemSelected(destination)) {
                                scope.launch { drawerState.close() }
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

            }
        },
    ) {
        // 内容层与悬浮导航栏重叠布局：内容填满全屏，滚动时从悬浮导航栏后方穿过
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f - (0.03f * drawerFraction)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (0.08f * drawerFraction)
                    },
            ) {
                // 内容层填满全屏（含悬浮导航栏后方区域）：页面内容从导航栏缝隙/后方穿过；
                // 各 Tab 页面列表已通过底部 contentPadding 保证末尾内容可滚至导航栏上方。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ),
                        ),
                ) {
                    content()
                }
                if (drawerFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.14f * drawerFraction)),
                    )
                }
            }
            // 圆角悬浮导航栏：覆盖在内容之上，居中悬浮，缝隙处透出页面内容；
            // 显隐带平滑滑入/滑出（进详情页/播放时隐藏、返回时重现）
            AnimatedVisibility(
                visible = useBottomNavigation,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                enter = slideInVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { it } + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { it } + fadeOut(animationSpec = tween(300)),
            ) {
                Surface(
                    modifier = Modifier
                        .width(240.dp)
                        .height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 1.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    ),
                ) {
                    NavigationBar(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        tonalElevation = 0.dp,
                    ) {
                        MainBottomDestination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = selectedBottomItem == item,
                                onClick = { onBottomNavigationSelected(item) },
                                icon = {
                                    Icon(
                                        painter = painterResource(item.iconRes),
                                        contentDescription = stringResource(item.titleRes),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        BackHandler(
            enabled = drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open,
        ) {
            scope.launch { drawerState.close() }
        }
    }
}

@Composable
private fun MainDrawerPrimaryItems(
    selectedDestination: MainDrawerDestination?,
    onDrawerItemSelected: (MainDrawerDestination) -> Boolean,
) {
    val primaryItems = listOf(
        MainDrawerDestination.Home,
        MainDrawerDestination.Announcement,
        MainDrawerDestination.Settings,
        MainDrawerDestination.DailyCheckIn,
    )
    Column {
        primaryItems.forEach { item ->
            DrawerMenuItem(
                item = item,
                selected = selectedDestination == item,
                onClick = { onDrawerItemSelected(item) },
            )
        }
    }
}

@Composable
private fun MainDrawerSection(
    titleRes: Int,
    items: List<MainDrawerDestination>,
    selectedDestination: MainDrawerDestination?,
    onItemClick: (MainDrawerDestination) -> Unit,
) {
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 12.dp),
    )
    Column {
        items.forEach { item ->
            DrawerMenuItem(
                item = item,
                selected = selectedDestination == item,
                onClick = { onItemClick(item) },
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    item: MainDrawerDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(stringResource(item.titleRes)) },
        icon = {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = stringResource(item.titleRes),
            )
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
private fun MainActivityScaffoldPreview() {
    ComponentPreview {
        MainActivityScaffold(
            drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
            drawerEnabled = true,
            selectedDestination = MainDrawerDestination.Home,
            avatarUrl = null,
            username = "Han1meViewer",
            isLoggedIn = true,
            isLoading = false,
            currentSite = "https://hanime1.me/",
            onAvatarClick = {},
            onAvatarLongClick = {},
            onSwitchSiteClick = {},
            useBottomNavigation = true,
            selectedBottomItem = MainBottomDestination.Home,
            onBottomNavigationSelected = {},
            onDrawerItemSelected = { true },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }
    }
}
