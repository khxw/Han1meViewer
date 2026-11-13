package com.yenaly.han1meviewer.ui.navigation.main

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.InteractionPreferences
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.popBackStackSafely
import com.yenaly.han1meviewer.ui.screen.account.AvatarCropScreen
import com.yenaly.han1meviewer.ui.screen.home.CreatorCenterScreen
import com.yenaly.han1meviewer.ui.screen.home.FavoritePosterRouteScreen
import com.yenaly.han1meviewer.ui.navigation.settings.DownloadSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.DownloadSettingsRouteScreen
import com.yenaly.han1meviewer.ui.navigation.settings.HKeyframeSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.HKeyframeSettingsRouteScreen
import com.yenaly.han1meviewer.ui.navigation.settings.HKeyframesRoute
import com.yenaly.han1meviewer.ui.navigation.settings.HKeyframesRouteScreen
import com.yenaly.han1meviewer.ui.navigation.settings.HKeyframesTopBarActions
import com.yenaly.han1meviewer.ui.navigation.settings.HomeSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.HomeSettingsRouteScreen
import com.yenaly.han1meviewer.ui.navigation.settings.InteractionSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.InteractionSettingsRouteScreen
import com.yenaly.han1meviewer.ui.navigation.settings.LogRoute
import com.yenaly.han1meviewer.ui.navigation.settings.LogRouteScreen
import com.yenaly.han1meviewer.ui.navigation.settings.MpvPlayerSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.MpvPlayerSettingsRouteScreen
import com.yenaly.han1meviewer.ui.navigation.settings.NetworkDiagnosticsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.NetworkDiagnosticsRouteScreen
import com.yenaly.han1meviewer.ui.navigation.settings.NetworkSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.NetworkSettingsRouteScreen
import com.yenaly.han1meviewer.ui.navigation.settings.PlayerSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.PlayerSettingsRouteScreen
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsScaffold
import com.yenaly.han1meviewer.ui.navigation.settings.SharedHKeyframesRoute
import com.yenaly.han1meviewer.ui.navigation.settings.SharedHKeyframesRouteScreen
import com.yenaly.han1meviewer.ui.navigation.settings.StorageManagerRoute
import com.yenaly.han1meviewer.ui.navigation.settings.StorageManagerRouteScreen
import com.yenaly.han1meviewer.ui.screen.account.AccountScreen
import com.yenaly.han1meviewer.ui.viewmodel.CreatorCenterViewModel
import com.yenaly.han1meviewer.ui.viewmodel.UserAccountViewModel
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.serialization.json.Json

@Composable
fun MainNavHost(
    activity: MainActivity,
    navController: NavHostController,
    isDrawerOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onDestinationChanged: (MainDestinationSpec) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destinationSpec = MainDestinationSpec.fromDestination(backStackEntry?.destination)
    var pendingAvatarCropResult by remember { mutableStateOf<String?>(null) }

    val onBack: () -> Unit = { navController.popBackStackSafely() }
    val onNavigateToVideo: (String) -> Unit = { code -> navController.navigateSafely(VideoRoute(code)) }
    val onNavigateToLocalVideo: (String, String?) -> Unit =
        { code, uri -> navController.navigateSafely(VideoRoute(code, uri)) }

    LaunchedEffect(destinationSpec) {
        destinationSpec?.let(onDestinationChanged)
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        // 抽屉主入口使用小位移与同步淡化，避免整页缩放带来的突兀和额外绘制开销；
        // 详情页、搜索页和设置子页保留方向滑动，并使用更舒缓的加减速节奏。
        enterTransition = {
            if (!InteractionPreferences.animationsEnabled) {
                return@NavHost EnterTransition.None
            }
            if (targetState.isDrawerDestination()) {
                fadeIn(
                    animationSpec = tween(480, easing = LinearOutSlowInEasing)
                ) + slideInHorizontally(
                    animationSpec = tween(520, easing = LinearOutSlowInEasing),
                    initialOffsetX = { -it / 24 },
                )
            } else {
                slideInHorizontally(
                    animationSpec = tween(440, easing = LinearOutSlowInEasing),
                    initialOffsetX = { it / 10 },
                ) + fadeIn(
                    animationSpec = tween(340, easing = LinearOutSlowInEasing)
                )
            }
        },
        exitTransition = {
            if (targetState.isDrawerDestination()) {
                fadeOut(
                    animationSpec = tween(320, easing = FastOutLinearInEasing)
                ) + slideOutHorizontally(
                    animationSpec = tween(420, easing = FastOutLinearInEasing),
                    targetOffsetX = { it / 24 },
                )
            } else {
                slideOutHorizontally(
                    animationSpec = tween(420, easing = FastOutLinearInEasing),
                    targetOffsetX = { -it / 24 },
                ) + fadeOut(
                    animationSpec = tween(260, easing = FastOutLinearInEasing)
                )
            }
        },
        popEnterTransition = {
            if (!InteractionPreferences.animationsEnabled) {
                return@NavHost EnterTransition.None
            }
            if (targetState.isDrawerDestination()) {
                fadeIn(
                    animationSpec = tween(420, easing = LinearOutSlowInEasing)
                ) + slideInHorizontally(
                    animationSpec = tween(460, easing = LinearOutSlowInEasing),
                    initialOffsetX = { -it / 24 },
                )
            } else {
                slideInHorizontally(
                    animationSpec = tween(400, easing = LinearOutSlowInEasing),
                    initialOffsetX = { -it / 24 },
                ) + fadeIn(
                    animationSpec = tween(300, easing = LinearOutSlowInEasing)
                )
            }
        },
        popExitTransition = {
            if (!InteractionPreferences.animationsEnabled) {
                return@NavHost ExitTransition.None
            }
            if (initialState.isDrawerDestination() && targetState.isDrawerDestination()) {
                fadeOut(
                    animationSpec = tween(280, easing = FastOutLinearInEasing)
                ) + slideOutHorizontally(
                    animationSpec = tween(360, easing = FastOutLinearInEasing),
                    targetOffsetX = { it / 30 },
                )
            } else {
                slideOutHorizontally(
                    animationSpec = tween(440, easing = FastOutLinearInEasing),
                    targetOffsetX = { it / 10 },
                ) + fadeOut(
                    animationSpec = tween(260, easing = FastOutLinearInEasing)
                )
            }
        }
    ) {
        composable<HomeRoute> {
            HomeRouteScreen(
                activity = activity,
                isDrawerOpen = isDrawerOpen,
                onOpenDrawer = onOpenDrawer,
                onNavigateToPreview = { navController.navigateSafely(PreviewRoute) },
                onNavigateToRandomVideo = { navController.navigateSafely(RandomVideoRoute) },
                onNavigateToSearch = { query -> navController.navigateSafely(SearchRoute(query = query)) },
                onNavigateToSearchAdvanced = { params ->
                    navController.navigateSafely(
                        SearchRoute(advancedSearchJson = Json.encodeToString(params))
                    )
                },
                onNavigateToVideo = { code, autoPlay ->
                    navController.navigateSafely(
                        VideoRoute(code, autoPlay = autoPlay)
                    )
                },
            )
        }
        composable<WatchHistoryRoute> {
            WatchHistoryRouteScreen(
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        composable<LocalFavoriteRoute> {
            LocalFavoriteRouteScreen(
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
                onOpenPosterWall = {
                    navController.navigateSafely(FavoritePosterRoute)
                },
            )
        }
        composable<FavoritePosterRoute> {
            FavoritePosterRouteScreen(onBack = onBack)
        }
        composable<RandomVideoRoute> {
                RandomVideoRouteScreen(
                    onBack = onBack,
                    onNavigateToVideo = onNavigateToVideo,
                )
        }
        composable<SeriesRoute> {
            SeriesRouteScreen(
                onBack = onBack,
                onOpenEpisode = { code, localUri ->
                    navController.navigateSafely(
                        VideoRoute(
                            videoCode = code,
                            localUri = localUri,
                            autoPlay = true,
                            autoPlayNextEpisode = true,
                        ),
                    )
                },
                onSearchMissingEpisode = { title, episode ->
                    navController.navigateSafely(
                        SearchRoute(query = "$title 第${episode}集")
                    )
                },
            )
        }
        composable<MyFavVideoRoute> {
            FavVideoRouteScreen(
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        composable<MyWatchLaterRoute> {
            WatchLaterRouteScreen(
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        composable<MyPlaylistRoute> {
            MyPlaylistRouteScreen(
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        composable<SubscriptionRoute> {
            SubscriptionRouteScreen(
                onBack = onBack,
                onNavigateToSearch = { query -> navController.navigateSafely(SearchRoute(query = query)) },
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        composable<DailyCheckInRoute> {
            DailyCheckInRouteScreen(
                activity = activity,
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        composable<DownloadRoute> {
            DownloadRouteScreen(
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
                onNavigateToLocalVideo = onNavigateToLocalVideo,
            )
        }
        composable<LocalMediaLibraryRoute> {
            LocalMediaLibraryRouteScreen(
                onBack = onBack,
                onNavigateToLocalVideo = onNavigateToLocalVideo,
            )
        }
        composable<CreatorCenterRoute> {
            val creatorViewModel: CreatorCenterViewModel = viewModel()
            CreatorCenterScreen(
                viewModel = creatorViewModel,
                onBack = onBack,
                onOpenUploadedVideo = { item -> onNavigateToVideo(item.videoCode) },
                onOpenUploadingVideo = { item -> onNavigateToLocalVideo("-1", item.remoteVideoUrl) },
            )
        }
        composable<AccountRoute> {
            val accountViewModel: UserAccountViewModel = viewModel()
            AccountScreen(
                viewModel = accountViewModel,
                onBack = onBack,
                onOpenAvatarCrop = { sourceUri ->
                    navController.navigateSafely(AvatarCropRoute(sourceUri))
                },
                pendingAvatarCropResult = pendingAvatarCropResult,
                onAvatarCropResultConsumed = { pendingAvatarCropResult = null },
                onRefreshHome = { activity.viewModel.getHomePage() },
                onLogout = { activity.showLogoutConfirmDialog(closeCurrentPageOnConfirm = true) },
            )
        }
        composable<AvatarCropRoute> {
            val route = it.toRoute<AvatarCropRoute>()
            AvatarCropScreen(
                sourceUri = route.sourceUri,
                onBack = onBack,
                onConfirm = { file ->
                    pendingAvatarCropResult = file.absolutePath
                    onBack()
                },
            )
        }
        composable<HomeSettingsRoute> {
            SettingsScaffold(
                navController = navController,
                fallbackDestination = HomeRoute,
            ) {
                HomeSettingsRouteScreen(
                    activity = activity,
                    onNavigateToPlayerSettings = { navController.navigateSafely(PlayerSettingsRoute) },
                    onNavigateToInteractionSettings = {
                        navController.navigateSafely(InteractionSettingsRoute)
                    },
                    onNavigateToHKeyframeSettings = { navController.navigateSafely(HKeyframeSettingsRoute) },
                    onNavigateToDownloadSettings = { navController.navigateSafely(DownloadSettingsRoute) },
                    onNavigateToStorageManager = { navController.navigateSafely(StorageManagerRoute) },
                    onNavigateToNetworkSettings = { navController.navigateSafely(NetworkSettingsRoute) },
                )
            }
        }
        composable<PlayerSettingsRoute> {
            SettingsScaffold(
                navController = navController,
                fallbackDestination = HomeSettingsRoute,
            ) {
                PlayerSettingsRouteScreen(
                    onNavigateToMpvSettings = { navController.navigateSafely(MpvPlayerSettingsRoute) },
                )
            }
        }
        composable<InteractionSettingsRoute> {
            SettingsScaffold(
                navController = navController,
                fallbackDestination = HomeSettingsRoute,
            ) {
                InteractionSettingsRouteScreen()
            }
        }
        composable<NetworkSettingsRoute> {
            SettingsScaffold(
                navController = navController,
                fallbackDestination = HomeSettingsRoute,
            ) {
                NetworkSettingsRouteScreen(
                    onOpenLogs = { navController.navigateSafely(LogRoute) },
                    onOpenNetworkDiagnostics = {
                        navController.navigateSafely(NetworkDiagnosticsRoute)
                    },
                )
            }
        }
        composable<NetworkDiagnosticsRoute> {
            SettingsScaffold(
                navController = navController,
                fallbackDestination = NetworkSettingsRoute,
            ) {
                NetworkDiagnosticsRouteScreen()
            }
        }
        composable<LogRoute> {
            SettingsScaffold(
                navController = navController,
                fallbackDestination = NetworkSettingsRoute,
            ) {
                LogRouteScreen()
            }
        }
        composable<DownloadSettingsRoute> {
            SettingsScaffold(
                navController = navController,
                fallbackDestination = HomeSettingsRoute,
            ) {
                DownloadSettingsRouteScreen(activity = activity)
            }
        }
        composable<StorageManagerRoute> {
            SettingsScaffold(
                navController = navController,
                fallbackDestination = HomeSettingsRoute,
            ) {
                StorageManagerRouteScreen()
            }
        }
        composable<MpvPlayerSettingsRoute> {
            SettingsScaffold(
                navController = navController,
                fallbackDestination = PlayerSettingsRoute,
            ) {
                MpvPlayerSettingsRouteScreen()
            }
        }
        composable<HKeyframesRoute> {
            var showImportDialog by remember { mutableStateOf(false) }
            SettingsScaffold(
                navController = navController,
                fallbackDestination = HKeyframeSettingsRoute,
                actions = {
                    HKeyframesTopBarActions(onImportClick = { showImportDialog = true })
                },
            ) {
                HKeyframesRouteScreen(
                    onOpenVideo = onNavigateToVideo,
                    showImportDialog = showImportDialog,
                    onImportDialogDismiss = { showImportDialog = false },
                )
            }
        }
        composable<SharedHKeyframesRoute> {
            SettingsScaffold(
                navController = navController,
                fallbackDestination = HKeyframeSettingsRoute,
            ) {
                SharedHKeyframesRouteScreen(
                    onOpenVideo = onNavigateToVideo,
                )
            }
        }
        composable<HKeyframeSettingsRoute> {
            SettingsScaffold(
                navController = navController,
                fallbackDestination = HomeSettingsRoute,
            ) {
                HKeyframeSettingsRouteScreen(
                    onNavigateToHKeyframes = { navController.navigateSafely(HKeyframesRoute) },
                    onNavigateToSharedHKeyframes = { navController.navigateSafely(SharedHKeyframesRoute) },
                )
            }
        }
        composable<SearchRoute> {
            SearchRouteScreen(
                route = it.toRoute(),
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        composable<PreviewRoute> {
            PreviewRouteScreen(
                activity = activity,
                onBack = onBack,
                onNavigateToGetchuPreview = {
                    navController.navigateSafely(GetchuPreviewRoute)
                },
                onNavigateToPreviewComment = { date, dateCode ->
                    navController.navigateSafely(PreviewCommentRoute(date, dateCode))
                },
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        composable<GetchuPreviewRoute> {
            GetchuPreviewRouteScreen(
                onBack = onBack,
                onNavigateToDetail = { id -> navController.navigateSafely(GetchuPreviewDetailRoute(id)) },
            )
        }
        composable<GetchuPreviewDetailRoute> {
            GetchuPreviewDetailRouteScreen(
                route = it.toRoute(),
                onBack = onBack,
                onNavigateToDetail = { id -> navController.navigateSafely(GetchuPreviewDetailRoute(id)) },
                onNavigateToVideoUrl = { url -> navController.navigateSafely(VideoRoute("-1", url)) },
            )
        }
        composable<PreviewCommentRoute> {
            PreviewCommentRouteScreen(
                activity = activity,
                route = it.toRoute(),
                onBack = onBack,
            )
        }
        composable<VideoRoute> {
            VideoRouteScreen(
                activity = activity,
                route = it.toRoute(),
            )
        }
    }
}


private fun NavBackStackEntry.isDrawerDestination(): Boolean =
    MainDestinationSpec.fromDestination(destination)?.drawerDestination != null
