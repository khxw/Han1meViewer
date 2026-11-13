package com.yenaly.han1meviewer.ui.screen.main

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys
import com.yenaly.han1meviewer.ui.component.ConfirmDialog
import com.yenaly.yenaly_libs.ActivityManager
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.exception.CloudFlareBlockedException
import com.yenaly.han1meviewer.logic.model.Announcement
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.logic.state.PageState
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.component.UpdateDialog
import com.yenaly.han1meviewer.ui.component.UsageNoticeDialog
import com.yenaly.han1meviewer.ui.navigation.main.DownloadRoute
import com.yenaly.han1meviewer.ui.navigation.main.HomeRoute
import com.yenaly.han1meviewer.ui.navigation.main.MainDestinationSpec
import com.yenaly.han1meviewer.ui.navigation.main.MainDrawerDestination
import com.yenaly.han1meviewer.ui.navigation.main.MainNavHost
import com.yenaly.han1meviewer.ui.navigation.main.handleMainIntent
import com.yenaly.han1meviewer.ui.navigation.main.navigateDrawerDestination
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.settings.HomeSettingsRoute
import com.yenaly.han1meviewer.ui.screen.home.homepage.component.AnnouncementListDialog
import com.yenaly.han1meviewer.ui.theme.HanimeTheme
import com.yenaly.han1meviewer.ui.viewmodel.AppViewModel
import com.yenaly.han1meviewer.ui.screen.home.homepage.HomePageViewModel
import com.yenaly.han1meviewer.util.getUpdateIfExists
import com.yenaly.han1meviewer.util.installApkPackage
import com.yenaly.han1meviewer.util.requestPostNotificationPermission
import com.yenaly.han1meviewer.worker.HUpdateWorker
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun MainActivityContent(
    activity: MainActivity,
    viewModel: HomePageViewModel,
    pendingNavigationRequests: Flow<Intent>,
    showAuthGuard: Boolean,
    onOpenAccount: () -> Unit,
    onLogoutClick: () -> Unit,
    onRequireLogin: () -> Unit,
    onSwitchSiteClick: () -> Unit,
    onNavigateControllerReady: (NavHostController) -> Unit,
) {
    HanimeTheme {
        val composeNavController = rememberNavController()
        var currentMainDestination by remember { mutableStateOf(MainDestinationSpec.Home) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var pendingUpdate by remember { mutableStateOf<Latest?>(null) }
        // 进入界面前先修正历史遗留的不一致状态：
        // 旧版本曾出现"须知未同意但内置 Hosts 已开启"（apply 落盘被重启截断所致），
        // 这里同步复位，确保用户在同意须知之前绝不会看到 Hosts 处于开启状态。
        remember { Preferences.repairInconsistentHostsState() }
        var showUsageNotice by remember { mutableStateOf(!Preferences.usageNoticeAccepted) }
        // 同意使用须知后，若还没问过、且内置 Hosts 未开启，则提示"是否自动开启并重启"
        var showHostsAutoPrompt by remember { mutableStateOf(false) }
        var announcementDialogList by remember { mutableStateOf<List<Announcement>?>(null) }
        val isDrawerOpen =
            drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open

        val homeState by viewModel.homePageFlow.collectAsStateWithLifecycle()
        val isLoggedIn by Preferences.loginStateFlow.collectAsStateWithLifecycle()
        // 详情页小屏播放中：隐藏悬浮底部导航栏，避免遮挡播放画面
        val videoPlaying by activity.videoPlayingState.collectAsStateWithLifecycle()
        val headerAvatarUrl = if (isLoggedIn) {
            (homeState as? PageState.Success)?.info?.page?.avatarUrl
        } else {
            null
        }
        val headerUsername = if (isLoggedIn) {
            (homeState as? PageState.Success)?.info?.page?.username
        } else {
            null
        }
        val headerIsLoading = isLoggedIn && homeState is PageState.Loading
        val selectedDrawerDestination = currentMainDestination.drawerDestination

        // 底部导航栏模式：设置变更后在下一次导航时刷新
        var useBottomNavigation by remember { mutableStateOf(Preferences.useBottomNavigation) }
        LaunchedEffect(currentMainDestination) {
            useBottomNavigation = Preferences.useBottomNavigation
        }
        val selectedBottomItem = when (currentMainDestination) {
            MainDestinationSpec.Home -> MainBottomDestination.Home
            MainDestinationSpec.Download -> MainBottomDestination.Download
            MainDestinationSpec.SettingsHome,
            MainDestinationSpec.SettingsPlayer,
            MainDestinationSpec.SettingsNetwork,
            MainDestinationSpec.SettingsDownload,
            MainDestinationSpec.SettingsMpv,
            MainDestinationSpec.SettingsHKeyframes,
            MainDestinationSpec.SettingsSharedHKeyframes,
            MainDestinationSpec.SettingsHKeyframeSettings,
            -> MainBottomDestination.Settings
            else -> null
        }
        fun navigateToBottomItem(item: MainBottomDestination) {
            if (item == selectedBottomItem) return
            when (item) {
                MainBottomDestination.Home -> composeNavController.navigateSafely(HomeRoute)
                MainBottomDestination.Download -> composeNavController.navigateSafely(DownloadRoute)
                MainBottomDestination.Settings -> composeNavController.navigateSafely(HomeSettingsRoute)
            }
        }

        LaunchedEffect(composeNavController) {
            onNavigateControllerReady(composeNavController)
        }
        LaunchedEffect(Unit) {
            pendingNavigationRequests.collect { intent ->
                composeNavController.handleMainIntent(intent)
            }
        }
        LaunchedEffect(Unit) {
            AppViewModel.pendingUpdateDialog.collect { latest ->
                Preferences.lastUpdatePopupTime = kotlin.time.Clock.System.now().epochSeconds
                pendingUpdate = latest
            }
        }
        LaunchedEffect(Unit) {
            AppViewModel.checkForUpdateOnAppStart()
        }
        LaunchedEffect(viewModel) {
            viewModel.sessionExpiredMessage.collect { event ->
                event.message?.let(::showShortToast) ?: showShortToast(event.fallbackResId)
            }
        }
        LaunchedEffect(homeState) {
            if (homeState is PageState.Error) {
                val throwable = (homeState as PageState.Error).throwable
                if (throwable is CloudFlareBlockedException) {
                    Log.e("error", "被屏蔽时的处理")
                }
            }
        }
        // 悬浮底部导航栏仅在主 tab 顶层页面（首页/下载/设置）显示；
        // 其余子页面（详情/评论/观看历史/本地收藏等）一律隐藏，避免遮挡页面内容
        val bottomNavVisible = useBottomNavigation && !videoPlaying &&
            currentMainDestination.showBottomNavigation
        LaunchedEffect(bottomNavVisible) {
            activity.setBottomNavVisible(bottomNavVisible)
        }
        MainActivityScaffold(
            drawerState = drawerState,
            drawerEnabled = currentMainDestination.drawerEnabled,
            selectedDestination = selectedDrawerDestination,
            avatarUrl = headerAvatarUrl,
            username = headerUsername,
            isLoggedIn = isLoggedIn,
            isLoading = headerIsLoading,
            currentSite = Preferences.baseUrl,
            onAvatarClick = {
                if (isLoggedIn) {
                    scope.launch { drawerState.close() }
                    onOpenAccount()
                } else {
                    onRequireLogin()
                }
            },
            onAvatarLongClick = {
                onLogoutClick()
            },
            onSwitchSiteClick = onSwitchSiteClick,
            // 仅顶层页面（首页/下载/设置）显示悬浮底部导航栏，子页面一律隐藏
            useBottomNavigation = bottomNavVisible,
            selectedBottomItem = selectedBottomItem,
            onBottomNavigationSelected = { item -> navigateToBottomItem(item) },
            onDrawerItemSelected = { destination ->
                if (destination == MainDrawerDestination.Announcement) {
                    scope.launch { drawerState.close() }
                    scope.launch {
                        val list = viewModel.fetchAnnouncementsForViewing()
                        if (list.isEmpty()) {
                            showShortToast(R.string.no_announcement_available)
                        } else {
                            announcementDialogList = list
                        }
                    }
                    true
                } else {
                    val handled = composeNavController.navigateDrawerDestination(
                        destination = destination,
                        isLoggedIn = isLoggedIn,
                        onRequireLogin = { showShortToast(R.string.login_first) },
                    )
                    if (handled) {
                        scope.launch { drawerState.close() }
                    }
                    handled
                }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                MainNavHost(
                    activity = activity,
                    navController = composeNavController,
                    isDrawerOpen = isDrawerOpen,
                    onOpenDrawer = {
                        if (currentMainDestination.drawerEnabled) {
                            scope.launch { drawerState.open() }
                        }
                    },
                    onDestinationChanged = { destination ->
                        currentMainDestination = destination
                    },
                )

                if (showAuthGuard) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                    )
                }

                pendingUpdate?.let { latest ->
                    UpdateDialog(
                        latest = latest,
                        onDismiss = { pendingUpdate = null },
                        onConfirm = {
                            pendingUpdate = null
                            scope.launch {
                                val file = activity.getUpdateIfExists(latest)
                                if (file != null) {
                                    activity.installApkPackage(file)
                                } else {
                                    if (activity.requestPostNotificationPermission()) {
                                        HUpdateWorker.enqueue(activity.applicationContext, latest)
                                        showShortToast(R.string.update_download_background)
                                    }
                                }
                            }
                        },
                    )
                }
                UsageNoticeDialog(
                    visible = showUsageNotice,
                    onAccepted = {
                        // 同步落盘：随后可能一路走到重启，apply 的异步写入会被切断
                        Preferences.markUsageNoticeAcceptedSync()
                        showUsageNotice = false
                        if (!Preferences.useBuiltInHosts && !Preferences.hostsAutoPromptShown) {
                            showHostsAutoPrompt = true
                        }
                    },
                    onDeclined = { activity.finish() },
                )
                ConfirmDialog(
                    visible = showHostsAutoPrompt,
                    title = stringResource(R.string.hosts_auto_enable_title),
                    message = stringResource(R.string.hosts_auto_enable_message),
                    confirmText = stringResource(R.string.hosts_auto_enable_confirm),
                    dismissText = stringResource(R.string.hosts_auto_enable_decline),
                    cancelable = false,
                    onConfirm = {
                        // 唯一写入点：只有点了"开启并重启"才会打开内置 Hosts。
                        // 两项都同步落盘，并回读校验，确认真的写入成功才重启，
                        // 避免出现"Hosts 开了、须知又弹出来"这类状态错位。
                        Preferences.markHostsAutoPromptShownSync()
                        Preferences.setUseBuiltInHostsSync(true)
                        if (Preferences.useBuiltInHosts) {
                            ActivityManager.restart(killProcess = true)
                        } else {
                            // 极少数写入失败的情况：不重启，保持界面可用
                            showHostsAutoPrompt = false
                        }
                    },
                    onDismiss = {
                        Preferences.markHostsAutoPromptShownSync()
                        showHostsAutoPrompt = false
                    },
                )
                announcementDialogList?.let { list ->
                    AnnouncementListDialog(
                        announcements = list,
                        onDismiss = { announcementDialogList = null },
                    )
                }
            }
        }
    }
}
