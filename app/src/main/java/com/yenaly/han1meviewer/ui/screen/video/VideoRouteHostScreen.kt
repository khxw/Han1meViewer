package com.yenaly.han1meviewer.ui.screen.video

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.jzvd.JZMediaInterface
import cn.jzvd.Jzvd
import coil.load
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.getHanimeVideoLink
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.dao.CheckInRecordDatabase
import com.yenaly.han1meviewer.logic.entity.HKeyframeEntity
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.logic.exception.ParseException
import com.yenaly.han1meviewer.logic.LocalSeriesRepository
import com.yenaly.han1meviewer.logic.log.HLumberjack
import com.yenaly.han1meviewer.logic.model.SearchOption
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.bridge.VideoPageHost
import com.yenaly.han1meviewer.ui.component.ConfirmDialog
import com.yenaly.han1meviewer.PermissionRequester
import com.yenaly.han1meviewer.ui.navigation.main.VideoRoute
import com.yenaly.han1meviewer.ui.view.video.ExoMediaKernel
import com.yenaly.han1meviewer.ui.view.video.HJzvdStd
import com.yenaly.han1meviewer.ui.view.video.HMediaKernel
import com.yenaly.han1meviewer.ui.view.video.HanimeDataSource
import com.yenaly.han1meviewer.ui.view.video.MpvMediaKernel
import com.yenaly.han1meviewer.ui.view.video.VideoPlayerAppBarBehavior
import com.yenaly.han1meviewer.ui.viewmodel.CommentViewModel
import com.yenaly.han1meviewer.ui.viewmodel.VideoViewModel
import com.yenaly.han1meviewer.util.checkBadGuy
import com.yenaly.han1meviewer.util.loadAssetAs
import com.yenaly.yenaly_libs.utils.OrientationManager
import com.yenaly.yenaly_libs.utils.browse
import com.yenaly.yenaly_libs.utils.copyToClipboard
import com.yenaly.yenaly_libs.utils.dp
import com.yenaly.yenaly_libs.utils.shareText
import com.yenaly.yenaly_libs.utils.showShortToast
import com.yenaly.yenaly_libs.utils.startActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun VideoRouteHostScreen(
    activity: MainActivity,
    route: VideoRoute,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val viewModel: VideoViewModel = viewModel()
    val commentViewModel: CommentViewModel = viewModel()
    val kernel = remember { HMediaKernel.Type.fromString(Preferences.switchPlayerKernel) }
    val genres = remember(Preferences.baseUrl) {
        loadAssetAs<List<SearchOption>>(
            if (Preferences.baseUrl == com.yenaly.han1meviewer.HanimeConstants.HANIME_URL[3]) {
                "search_options/genre_av.json"
            } else {
                "search_options/genre.json"
            }
        ).orEmpty()
    }
    val player = remember(route.videoCode, route.localUri) {
        createVideoPlayerView(activity)
    }
    val shell = remember(route.videoCode, route.localUri) {
        VideoRouteShell(activity, player)
    }
    val hostUiState by viewModel.videoHostUiStateFlow.collectAsStateWithLifecycle()
    val relatedItems =
        viewModel.hanimeVideoFlow.collectAsStateWithLifecycle().value?.relatedHanimes.orEmpty()
    val stringLongPressShare = remember(activity) {
        activity.getString(R.string.long_press_share_to_copy)
    }

    commentViewModel.code = route.videoCode
    player.videoCode = route.videoCode
    viewModel.fromDownload = route.videoCode == "-1" || route.localUri != null

    var checkedQuality by remember(
        route.videoCode,
        route.localUri
    ) { mutableStateOf<String?>(null) }
    var pendingDownloadPrompt by remember(route.videoCode, route.localUri) {
        mutableStateOf<DownloadPromptState?>(null)
    }
    var videoTitle by remember(route.videoCode, route.localUri) { mutableStateOf<String?>(null) }
    var isSideRelatedCollapsed by remember { mutableStateOf(false) }
    var showAddHKeyframeDialog by remember { mutableStateOf<Pair<Long, String>?>(null) }

    val actions = remember(activity, scope, viewModel, genres) {
        VideoRouteActions(
            context = activity,
            scope = scope,
            viewModel = viewModel,
            genres = genres,
            requestStoragePermission = { onGranted, onDenied, onPermanentlyDenied ->
                (activity as PermissionRequester).requestStoragePermission(
                    onGranted = onGranted,
                    onDenied = onDenied,
                    onPermanentlyDenied = onPermanentlyDenied,
                )
            },
            onPendingDownloadPromptChange = { pendingDownloadPrompt = it },
            getCheckedQuality = { checkedQuality },
            setCheckedQuality = { checkedQuality = it },
            onStoragePermissionDenied = { activity.navController.popBackStack() },
            onDownloadPermissionDialogCancelled = { activity.navController.popBackStack() },
        )
    }

    val jzBackCallback = remember(activity) {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (!Jzvd.backPress()) {
                    isEnabled = false
                    activity.onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    fun setPlayerHeight(height: Int) {
        shell.setPlayerHeight(height)
    }


    fun updatePipAction() {
        if (!activity.isInPictureInPictureMode) return
        val isPlaying = when (val mi = Jzvd.CURRENT_JZVD?.mediaInterface) {
            is ExoMediaKernel -> mi.isPlaying
            is MpvMediaKernel -> mi.isPlaying
            else -> false
        }
        val icon = if (isPlaying) {
            Icon.createWithResource(activity, R.drawable.ic_pip_pause_24)
        } else {
            Icon.createWithResource(activity, R.drawable.ic_pip_play_arrow_24)
        }
        val title = if (isPlaying) "Pause Video" else "Play Video"
        val intent = PendingIntent.getBroadcast(
            activity,
            0,
            Intent(MainActivity.ACTION_TOGGLE_PLAY).setPackage(activity.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val action = RemoteAction(icon, title, activity.getString(R.string.play_pause), intent)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(action))
            .build()
        activity.setPictureInPictureParams(params)
    }

    fun changeScreenNormal() {
        if (player.screen == Jzvd.SCREEN_FULLSCREEN) {
            player.gotoNormalScreen()
        }
    }

    fun changeScreenFullLandscape(orientation: OrientationManager.ScreenOrientation) {
        if (player.screen != Jzvd.SCREEN_FULLSCREEN &&
            System.currentTimeMillis() - Jzvd.lastAutoFullscreenTime > 2000
        ) {
            player.autoFullscreen(orientation)
            Jzvd.lastAutoFullscreenTime = System.currentTimeMillis()
        }
    }

    val pageHost = remember(activity, player, shell, viewModel) {
        object : VideoPageHost {
            override fun showCommentBadge(count: Int) {
                viewModel.setCommentBadgeCount(count)
            }

            override fun shouldEnterPip(): Boolean {
                return player.state == Jzvd.STATE_PLAYING || player.state == Jzvd.STATE_PAUSE
            }

            override fun enterPipMode() {
                val intent = PendingIntent.getBroadcast(
                    activity,
                    0,
                    Intent(MainActivity.ACTION_TOGGLE_PLAY).setPackage(activity.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val icon =
                    Icon.createWithResource(activity, R.drawable.ic_pip_pause_24)
                val action = RemoteAction(
                    icon,
                    activity.getString(R.string.play_pause),
                    activity.getString(R.string.play_pause),
                    intent,
                )
                val sourceRect = Rect()
                player.getGlobalVisibleRect(sourceRect)
                val params = PictureInPictureParams.Builder()
                    .setSourceRectHint(sourceRect)
                    .setAspectRatio(Rational(16, 9))
                    .setActions(listOf(action))
                    .build()
                activity.enterPictureInPictureMode(params)
            }

            override fun onPipModeChanged(isInPip: Boolean) {
                viewModel.setPipMode(isInPip)
                // PIP 窗口内整个 appBar 链撑满窗口（不能只把播放器设 MATCH_PARENT，
                // WRAP_CONTENT 容器链里解析失败会导致高度 0 黑屏）
                shell.setPipLayout(isInPip)
                if (isInPip) {
                    viewModel.setPlayerHeightDp(MATCH_PARENT)
                } else if (Preferences.tabletMode &&
                    activity.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
                ) {
                    viewModel.setPlayerHeightDp(350.dp)
                } else {
                    viewModel.setPlayerHeightDp(250.dp)
                }
                setPlayerHeight(viewModel.videoHostUiStateFlow.value.playerHeightDp)
                shell.setTabsVisible(!isInPip)
                player.setControlsVisible(!isInPip)
                // mpv 内核：标记 PIP 模式，纹理重建时直接重新绑定渲染 surface
                if (player.mediaInterface is MpvMediaKernel) {
                    (player.mediaInterface as? MpvMediaKernel)?.setPipMode(isInPip)
                }
                if (isInPip) {
                    updatePipAction()
                    // PIP 窗口内：进入前若 appBar 处于收起状态，CollapsingToolbarLayout
                    // 会把播放器压缩到 0 高度（无画面但有声音），先强制展开并重布局；
                    // mpv 内核还需重建 SurfaceTexture 重新绑定渲染 surface
                    // （PIP 切换时系统可能销毁重建了 TextureView 的纹理，仅重建
                    // Surface 对象无效）
                    player.postDelayed({
                        shell.setExpanded(true, false)
                        player.requestLayout()
                        if (player.mediaInterface is MpvMediaKernel) {
                            (player.mediaInterface as? MpvMediaKernel)?.rebindSurfaceWithNewTexture()
                        }
                    }, 300)
                } else {
                    // 退出 PIP：回到详情页。系统切换窗口 surface 时 TextureView 纹理
                    // 可能已被销毁重建，重建渲染 surface 避免黑屏
                    shell.setExpanded(true, false)
                    player.postDelayed({
                        (player.mediaInterface as? MpvMediaKernel)?.rebindSurfaceWithNewTexture()
                    }, 300)
                }
            }

            override fun togglePlayPause() {
                val mediaInterface = player.mediaInterface
                if (mediaInterface.isPlaying) {
                    mediaInterface.pause()
                } else {
                    mediaInterface.start()
                }
                updatePipAction()
            }
        }
    }

    DisposableEffect(
        activity,
        shell,
        player,
        pageHost,
        stringLongPressShare,
        route.videoCode,
        route.localUri
    ) {
        activity.registerCurrentVideoHost(pageHost)
        shell.setTabsHostContent {
            val videoState by viewModel.hanimeVideoStateFlow.collectAsStateWithLifecycle()
            VideoRouteContent(
                videoCode = route.videoCode,
                videoState = videoState,
                videoViewModel = viewModel,
                commentViewModel = commentViewModel,
                fromDownload = viewModel.fromDownload,
                pendingDownloadPrompt = pendingDownloadPrompt,
                onPendingDownloadPromptChange = { pendingDownloadPrompt = it },
                onRetry = { viewModel.getHanimeVideo(route.videoCode, route.localUri) },
                onOpenVideo = { item -> activity.showVideoDetailFragment(item.videoCode) },
                onOpenArtist = actions::openArtistSearch,
                onNavigateToSearch = actions::openTagSearch,
                onToggleSubscribe = actions::toggleArtistSubscription,
                onToggleFavorite = actions::toggleFavorite,
                onToggleLocalFavorite = actions::toggleLocalFavorite,
                onRateVideo = actions::rateVideo,
                onManageMyList = actions::updateMyListSelection,
                onQuickCheckIn = { record ->
                    val normalizedRecord = if (record.sideDishes.contains("\u001E")) {
                        record
                    } else {
                        record.copy(sideDishes = "${record.sideDishes}\u001E${route.videoCode}")
                    }
                    scope.launch(Dispatchers.IO) {
                        CheckInRecordDatabase.getDatabase(activity).checkInDao()
                            .insert(normalizedRecord)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(activity, R.string.checkin, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onPrepareDownload = { quality, video ->
                    checkedQuality = quality
                    video?.let(actions::startDownloadFlow)
                },
                onConfirmDownloadPrompt = { video ->
                    video?.let { actions.confirmPendingDownload(it, pendingDownloadPrompt) }
                },
                onRequestOpenOfficialDownloadPage = actions::openOfficialDownloadPage,
                onRequestOpenDownloadPermissionSettings = actions::openDownloadPermissionSettings,
                onOpenWebPage = actions::openVideoWebPage,
                onOpenOriginalComic = actions::openOriginalComic,
                onOpenShare = { content, title -> shareText(content, title) },
                onCopyText = {
                    it.copyToClipboard()
                    showShortToast(R.string.copy_to_clipboard)
                },
                onIntroductionLinkClick = actions::openIntroductionLink,
                stringLongPressShare = stringLongPressShare,
                pageHost = pageHost,
            )
        }
        onDispose {
            activity.registerCurrentVideoHost(null)
            player.onVideoStateChanged = null
            player.fullscreenListener = null
            player.onGoHomeClickListener = null
            player.onKeyframeClickListener = null
            player.onKeyframeLongClickListener = null
            shell.clear()
            Jzvd.releaseAllVideos()
        }
    }

    DisposableEffect(lifecycleOwner, activity, player, shell, route.videoCode) {
        // 进入详情页即隐藏悬浮底部导航栏（无论是否已开始播放），
        // 暂停/播完保持隐藏，离开详情页时由 onDispose 恢复
        activity.setVideoPlaying(true)
        val orientationManager = OrientationManager(activity) { orientation ->
            if (!Preferences.tabletMode &&
                Jzvd.CURRENT_JZVD != null &&
                (player.state == Jzvd.STATE_PLAYING || player.state == Jzvd.STATE_PAUSE) &&
                player.screen != Jzvd.SCREEN_TINY &&
                Jzvd.FULLSCREEN_ORIENTATION != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ) {
                if (orientation.isLandscape && player.screen == Jzvd.SCREEN_NORMAL) {
                    changeScreenFullLandscape(orientation)
                } else if (orientation == OrientationManager.ScreenOrientation.PORTRAIT &&
                    player.screen == Jzvd.SCREEN_FULLSCREEN
                ) {
                    changeScreenNormal()
                }
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val progress = player.currentPositionWhenPlaying
                    val duration = player.duration.takeIf { it > 0 } ?: 0L
                    scope.launch {
                        DatabaseRepo.WatchHistory.updateProgress(
                            route.videoCode,
                            progress,
                            duration,
                        )
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    if (!activity.isInPictureInPictureMode) {
                        changeScreenNormal()
                    }
                    Jzvd.goOnPlayOnPause()
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(orientationManager)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        activity.onBackPressedDispatcher.addCallback(lifecycleOwner, jzBackCallback)
        player.orientationManager = orientationManager
        player.onGoHomeClickListener = {
            if (activity.resources.getBoolean(R.bool.isTablet)) {
                activity.navController.popBackStack()
            }
            activity.startActivity<MainActivity>()
        }
        player.onKeyframeClickListener = { view ->
            player.clickHKeyframe(view)
        }
        player.onKeyframeLongClickListener = {
            val mediaInterface: JZMediaInterface? = player.mediaInterface
            if (mediaInterface != null && !mediaInterface.isPlaying) {
                val currentPosition = player.currentPositionWhenPlaying
                showAddHKeyframeDialog = Pair(currentPosition, videoTitle ?: "Untitled")
            } else {
                showShortToast(R.string.pause_then_long_press)
            }
        }
        player.onVideoStateChanged = { state ->
            when (state) {
                Jzvd.STATE_PREPARING -> {
                    viewModel.setScrollDisabled(true)
                    shell.withBehavior { behavior ->
                        behavior.disableScroll = true
                    }
                    shell.setExpanded(expanded = true, animate = true)
                    // 播放中保持隐藏悬浮底部导航栏，避免遮挡播放画面
                    activity.setVideoPlaying(true)
                }

                Jzvd.STATE_PLAYING -> {
                    // 只有真正开始播放才恢复为“未完成”，避免打开已看完详情时
                    // STATE_PREPARING 提前把卡片重新加入继续观看。
                    scope.launch {
                        DatabaseRepo.WatchHistory.resetCompleted(route.videoCode)
                    }
                    viewModel.setScrollDisabled(true)
                    shell.withBehavior { behavior ->
                        behavior.disableScroll = true
                    }
                    shell.setExpanded(expanded = true, animate = true)
                    activity.setVideoPlaying(true)
                }

                Jzvd.STATE_PAUSE -> {
                    viewModel.setScrollDisabled(false)
                    shell.withBehavior { behavior ->
                        behavior.disableScroll = false
                    }
                    // 暂停不恢复导航栏：详情页小窗播放期间导航栏保持隐藏，
                    // 离开详情页时由 onDispose 复位
                }

                Jzvd.STATE_AUTO_COMPLETE -> {
                    viewModel.setScrollDisabled(false)
                    shell.withBehavior { behavior ->
                        behavior.disableScroll = false
                    }
                    val duration = player.duration.takeIf { it > 0 } ?: 0L
                    scope.launch {
                        DatabaseRepo.WatchHistory.markCompleted(route.videoCode, duration)
                        if (route.autoPlayNextEpisode) {
                            val nextEpisode = LocalSeriesRepository.findNextEpisode(route.videoCode)
                            if (nextEpisode != null) {
                                delay(1_200L)
                                activity.showVideoDetailFragment(
                                    videoCode = nextEpisode.videoCode,
                                    fileUri = nextEpisode.localUri,
                                    autoPlayNextEpisode = true,
                                )
                            }
                        }
                    }
                }
            }
        }
        player.fullscreenListener = object : HJzvdStd.FullscreenListener {
            override fun onFullscreenChanged(isFullscreen: Boolean) {
                jzBackCallback.isEnabled = isFullscreen
                Log.i("JZVD screen state", isFullscreen.toString())
                if (!isFullscreen) {
                    // 退出全屏：强制恢复小屏播放器高度。Jzvd 恢复的是进入全屏前
                    // 保存的参数，若与 PIP 交叉操作可能残留 MATCH_PARENT，
                    // 播放器会被撑满全屏、悬浮导航栏盖在画面上
                    val height = if (Preferences.tabletMode) 350.dp else 250.dp
                    viewModel.setPlayerHeightDp(height)
                    setPlayerHeight(height)
                    // 若 appBar 处于收起状态，播放器会被压缩到 0 高度（黑屏但有
                    // 声音），强制展开。注意：此处不能重建 mpv 渲染 surface ——
                    // TextureView 分离时 onSurfaceTextureDestroyed 返回 false，纹理
                    // 并未释放，重新附加后 SAVED_SURFACE 会塞回原纹理，mpv 的渲染
                    // surface 自始至终未断开，画面连续；人为换新纹理反而会打断渲染
                    // （旧版 TextureView 换纹理后回调 available，新版不再回调，
                    // mpv 失去渲染目标导致画面冻结/黑屏）
                    shell.setExpanded(true, false)
                }
            }
        }
        shell.setOnOffsetChanged { totalScrollRange, verticalOffset ->
            // 验证日志：键盘弹出时 shell 若被顶起，verticalOffset 会变化（正常应保持原值）
            HLumberjack.i("INSETS", "shell offset total=$totalScrollRange offset=$verticalOffset")
            viewModel.setAppBarBottomInsetPx(totalScrollRange + verticalOffset)
            viewModel.setAppBarExpanded(route.videoCode, verticalOffset == 0)
        }
        shell.onImeBottomInsetChanged = { px ->
            ImeInsetState.keyboardBottomInsetPx.intValue = px
            viewModel.setImeBottomInsetPx(px)
        }
        shell.setExpanded(expanded = viewModel.isAppBarExpanded(route.videoCode), animate = false)
        val initialHeight = if (Preferences.tabletMode) {
            350.dp
        } else {
            250.dp
        }
        viewModel.setPlayerHeightDp(initialHeight)
        setPlayerHeight(initialHeight)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(orientationManager)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            activity.setVideoPlaying(false)
        }
    }

    LaunchedEffect(
        hostUiState.isInPipMode,
        isSideRelatedCollapsed,
    ) {
        if (hostUiState.isInPipMode) return@LaunchedEffect
        val height = if (Preferences.tabletMode) {
            if (isSideRelatedCollapsed) 500.dp else 400.dp
        } else {
            250.dp
        }
        if (hostUiState.playerHeightDp != height) {
            viewModel.setPlayerHeightDp(height)
            setPlayerHeight(height)
        }
    }

    LaunchedEffect(route.videoCode, route.localUri) {
        checkedQuality = null
        pendingDownloadPrompt = null
        videoTitle = null
        checkBadGuy(activity, R.raw.akarin)
        viewModel.videoCode = route.videoCode
        viewModel.getHanimeVideo(route.videoCode, route.localUri)
    }

    LaunchedEffect(route.videoCode, route.localUri, player, kernel, viewModel.fromDownload) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
            viewModel.hanimeVideoStateFlow.collect { state ->
                when (state) {
                    is VideoLoadingState.Error -> {
                        state.throwable.localizedMessage?.let { showShortToast(it) }
                        if (state.throwable is ParseException) {
                            activity.browse(getHanimeVideoLink(route.videoCode))
                        }
                    }

                    is VideoLoadingState.Loading -> Unit

                    is VideoLoadingState.Success -> {
                        videoTitle = state.info.title
                        if (state.info.videoUrls.isEmpty()) {
                            player.startButton.setOnClickListener {
                                showShortToast(R.string.fail_to_get_video_link)
                                activity.browse(getHanimeVideoLink(route.videoCode))
                            }
                        } else {
                            val history = if (!viewModel.fromDownload) {
                                DatabaseRepo.WatchHistory.findBy(route.videoCode)
                            } else {
                                null
                            }
                            val savedProgress = history?.progress ?: 0L
                            player.savedProgress = savedProgress
                            player.seekToInAdvance = savedProgress
                            player.setUp(
                                HanimeDataSource(state.info.title, state.info.videoUrls),
                                Jzvd.SCREEN_NORMAL,
                                kernel,
                            )
                        }
                        player.posterImageView.load(state.info.coverUrl) {
                            crossfade(true)
                        }
                        if (!viewModel.fromDownload) {
                            viewModel.insertWatchHistoryWithCover(
                                WatchHistoryEntity(
                                    state.info.coverUrl,
                                    state.info.title,
                                    state.info.uploadTimeMillis,
                                    kotlin.time.Clock.System.now().toEpochMilliseconds(),
                                    route.videoCode,
                                )
                            )
                        }
                        if (route.autoPlay) {
                            player.post {
                                player.startVideo()
                            }
                        }
                    }

                    is VideoLoadingState.NoContent -> {
                        showShortToast(R.string.video_might_not_exist)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    LaunchedEffect(route.videoCode, player) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
            viewModel.observeKeyframe(route.videoCode).collect {
                player.hKeyframe = it
                viewModel.hKeyframes = it
            }
        }
    }

    LaunchedEffect(viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
            viewModel.modifyHKeyframeFlow.collect { (_, reason) ->
                showShortToast(reason)
            }
        }
    }

    LaunchedEffect(viewModel, route.videoCode) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
            viewModel.loadDownloadedFlow.collect { entity ->
                val newQuality = checkedQuality ?: return@collect
                pendingDownloadPrompt = DownloadPromptState(
                    newQuality = newQuality,
                    oldQuality = entity?.quality,
                )
            }
        }
    }

    VideoShellContent(
        isTabletMode = Preferences.tabletMode,
        isInPipMode = hostUiState.isInPipMode,
        relatedItems = relatedItems,
        onHideRelatedInIntroChange = { viewModel.hideRelatedInIntro = it },
        onSideRelatedCollapsedChange = { isSideRelatedCollapsed = it },
        onOpenVideo = { item -> activity.showVideoDetailFragment(item.videoCode) },
        mainHostFactory = {
            shell.mainHostView.also { view ->
                (view.parent as? ViewGroup)?.removeView(view)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    showAddHKeyframeDialog?.let { (currentPosition, title) ->
        ConfirmDialog(
            visible = true,
            title = activity.getString(R.string.add_to_h_keyframe),
            message = buildString {
                appendLine(activity.getString(R.string.sure_to_add_to_h_keyframe))
                append(activity.getString(R.string.current_position_d_ms, currentPosition))
            },
            confirmText = activity.getString(R.string.confirm),
            dismissText = activity.getString(R.string.cancel),
            onConfirm = {
                viewModel.appendHKeyframe(
                    route.videoCode,
                    title,
                    HKeyframeEntity.Keyframe(position = currentPosition, prompt = null),
                )
                showAddHKeyframeDialog = null
            },
            onDismiss = { showAddHKeyframeDialog = null },
        )
    }
}


private fun createVideoPlayerView(activity: MainActivity): HJzvdStd {
    return HJzvdStd(ContextThemeWrapper(activity, activity.theme)).apply {
        layoutParams = CollapsingToolbarLayout.LayoutParams(
            MATCH_PARENT,
            250.dp,
        ).apply {
            collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PARALLAX
            parallaxMultiplier = 0.7f
        }
    }
}

private class VideoRouteShell(
    context: Context,
    private val playerView: HJzvdStd,
) {
    // rootView 剥离 IME insets，防止 fitsSystemWindows 把键盘高度应用成底部
    // padding、整页被顶起。edge-to-edge 下窗口不随键盘缩小、ime insets 在动画
    // 结束后会被系统清零，因此输入栏浮起改由键盘动画回调驱动（见
    // setWindowInsetsAnimationCallback），键盘高度经 onImeBottomInsetChanged
    // 传给 Compose，只抬升评论输入栏、不压缩评论区。lastImeBottom 缓存键盘
    // 高度：动画期间系统分发的 insets 高度可靠，稳定后的清零分发不再覆盖缓存。
    private var lastImeBottom = 0
    private var lastImeVisible = false

    // 键盘动画方向与起点：收起动画改用动画进度反推高度，避免动画末帧 IME
    // insets 提前归零、随后又被缓存值顶回去造成的下落抽搐。
    private var imeAnimationRunning = false
    private var imeAnimClosing = false
    private var imeAnimStartBottom = 0

    // IME 动画已确认键盘处于显示状态。部分系统在键盘完全展开后会再分发一次
    // IME 既不可见、也不占高度的稳定 insets；若此时把输入栏高度归零，输入框会
    // 在键盘上停留零点几秒后掉下去并被键盘遮住。该标记用于忽略这类错误分发。
    private var imeVisibleConfirmed = false

    /** 键盘高度变化回调（动画期间逐帧通知，值=当前应抬升的高度 px）。 */
    var onImeBottomInsetChanged: ((Int) -> Unit)? = null

    private var lastReportedImeBottom = -1

    private val rootView = CoordinatorLayout(context).apply {
        fitsSystemWindows = true
        layoutParams = ViewGroup.LayoutParams(
            MATCH_PARENT,
            MATCH_PARENT,
        )
    }.apply {
        // API 35+ 强制 edge-to-edge 下，fitsSystemWindows 会把 IME insets 自动应用
        // 到底部 padding：键盘弹出时整个页面（含播放器/评论区）被顶起。这里剥离
        // IME insets，键盘位置只由 Compose 侧 imePadding 处理（评论输入栏上浮），
        // 播放器与评论区保持原位。
        val imeFilter = object : View.OnApplyWindowInsetsListener {
            override fun onApplyWindowInsets(
                view: View,
                insets: WindowInsets,
            ): WindowInsets {
                val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, view)
                val imeBottom = compat.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val imeVisible =
                    imeBottom > 0 || compat.isVisible(WindowInsetsCompat.Type.ime())
                lastImeVisible = imeVisible
                if (imeBottom > 0) {
                    lastImeBottom = imeBottom
                    // 仅在没有 IME 动画回调（API < 30）时由这里直接给出高度。
                    // API 30+ 必须交给动画回调：系统有时会在 onPrepare 之前先分发
                    // 一次带满高的 IME insets，这里若抢先上报，输入框会瞬间跳到
                    // 键盘上方，并污染方向判断。
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                        imeBottom != lastReportedImeBottom
                    ) {
                        lastReportedImeBottom = imeBottom
                        onImeBottomInsetChanged?.invoke(imeBottom)
                    }
                } else if (!imeVisible && !imeAnimationRunning && !imeVisibleConfirmed) {
                    // 键盘收起后部分系统只清零 inset，不保证动画回调仍会更新状态。
                    // 这里立即归零，避免输入栏继续使用旧高度而悬空。
                    // 注意：动画进行中必须让动画回调独占更新，否则此处提前归零会被
                    // 下一帧动画值顶回去，表现为输入栏下落时的轻微抽搐。
                    lastImeBottom = 0
                    if (lastReportedImeBottom != 0) {
                        lastReportedImeBottom = 0
                        onImeBottomInsetChanged?.invoke(0)
                    }
                }
                HLumberjack.i(
                    "INSETS",
                    "shell rootView ime=$imeBottom systemBars=" +
                        compat.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
                )
                val withoutIme = WindowInsetsCompat.Builder(compat)
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                    .build()
                // 临时移除监听后走 CoordinatorLayout 默认分发（systemBars 照常），
                // 避免 listener 内调用 onApplyWindowInsets 递归
                view.setOnApplyWindowInsetsListener(null)
                val consumed = view.onApplyWindowInsets(withoutIme.toWindowInsets())
                view.setOnApplyWindowInsetsListener(this)
                return consumed
            }
        }
        setOnApplyWindowInsetsListener(imeFilter)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            installImeAnimationCallback(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun installImeAnimationCallback(view: View) {
        // 键盘弹出/收起动画驱动键盘高度状态：edge-to-edge（API 35+ 强制）
        // 下窗口不随键盘缩小，adjustResize 不生效；ime insets 又会在动画结束后
        // 被系统清零。这里在动画期间用缓存的键盘高度逐帧输出抬升值（经
        // onImeBottomInsetChanged 传给评论输入栏），动画结束按 IME 可见性定值
        // （弹出保持、收起归零）。
        view.setWindowInsetsAnimationCallback(
            object : WindowInsetsAnimation.Callback(
                WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE,
            ) {
                private fun updateBottomPadding(bottom: Int) {
                    if (lastReportedImeBottom != bottom) {
                        lastReportedImeBottom = bottom
                        onImeBottomInsetChanged?.invoke(bottom)
                    }
                }

                override fun onPrepare(animation: WindowInsetsAnimation) {
                    if (animation.typeMask and WindowInsets.Type.ime() != 0) {
                        imeAnimationRunning = true
                        // 方向只信任"上一次真正报告给输入栏的高度"：非零说明键盘
                        // 此前确实显示，本次是收起；为零则是弹出。
                        // 不能用 rootWindowInsets / lastImeVisible / lastImeBottom：
                        // 系统可能在 onPrepare 之前先分发一次带满高的 IME insets，
                        // 把这三个值提前污染成"键盘已显示"，导致弹出被误判成收起
                        // （输入框先瞬移到顶部，再随动画一路落到键盘后面）。
                        val reported = lastReportedImeBottom
                        imeAnimClosing = reported > 0
                        imeAnimStartBottom = maxOf(reported, rootImeBottomPx())
                        if (!imeAnimClosing) {
                            // 一旦确认是弹出动画，就立刻进入保护状态：此后系统
                            // 再分发"IME 不可见且不占高度"的错误 insets 也不会把
                            // 输入栏高度清掉。
                            imeVisibleConfirmed = true
                        }
                    }
                }

                override fun onProgress(
                    insets: WindowInsets,
                    runningAnimations: List<WindowInsetsAnimation>,
                ): WindowInsets {
                    val anim = runningAnimations.firstOrNull {
                        it.typeMask and WindowInsets.Type.ime() != 0
                    }
                    if (anim != null) {
                        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, view)
                        val imeBottom = compat.getInsets(WindowInsetsCompat.Type.ime()).bottom
                        val imeVisible = compat.isVisible(WindowInsetsCompat.Type.ime())
                        lastImeVisible = imeVisible || imeBottom > 0
                        val fraction = anim.interpolatedFraction.coerceIn(0f, 1f)
                        val bottom = if (imeAnimClosing && imeAnimStartBottom > 0) {
                            // 收起动画用动画进度反推高度：系统在部分设备上会在
                            // 动画末帧提前把 ime insets 清零，直接采用该值会先落到
                            // 底部再被缓存值顶回去，形成下落抽搐。
                            (imeAnimStartBottom * (1f - fraction))
                                .roundToInt()
                                .coerceIn(0, imeAnimStartBottom)
                        } else {
                            when {
                                imeBottom > 0 -> {
                                    lastImeBottom = imeBottom
                                    imeBottom
                                }
                                // 动画方向为弹出时，即使个别帧 insets 提前清零，也只
                                // 维持"已经报告给输入栏的高度"，绝不能跳回被提前分发
                                // 污染的 lastImeBottom，否则输入框会瞬间弹到顶部。
                                imeVisible || imeAnimationRunning ->
                                    lastReportedImeBottom.coerceAtLeast(0)
                                else -> 0
                            }
                        }
                        if (bottom > 0) lastImeBottom = maxOf(lastImeBottom, bottom)
                        updateBottomPadding(bottom)
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimation) {
                    if (animation.typeMask and WindowInsets.Type.ime() != 0) {
                        if (imeAnimClosing) {
                            lastImeVisible = false
                            imeVisibleConfirmed = false
                            lastImeBottom = 0
                            updateBottomPadding(0)
                        } else {
                            imeVisibleConfirmed = lastImeBottom > 0
                            updateBottomPadding(lastImeBottom)
                        }
                        imeAnimationRunning = false
                        imeAnimClosing = false
                        imeAnimStartBottom = 0
                        HLumberjack.i(
                            "INSETS",
                            "ime animation end bottom=$lastReportedImeBottom",
                        )
                    }
                }
            },
        )
    }

    /** 读取动画开始前窗口的 IME 底部 inset，作为收起动画的起始高度。 */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun rootImeBottomPx(): Int {
        val insets = rootView.rootWindowInsets ?: return 0
        return WindowInsetsCompat.toWindowInsetsCompat(insets, rootView)
            .getInsets(WindowInsetsCompat.Type.ime()).bottom
    }

    private val appBarLayout = AppBarLayout(context).apply {
        layoutParams = CoordinatorLayout.LayoutParams(
            MATCH_PARENT,
            WRAP_CONTENT,
        ).apply {
            behavior = VideoPlayerAppBarBehavior(context, null)
        }
    }

    private val collapsingToolbarLayout = CollapsingToolbarLayout(context).apply {
        layoutParams = AppBarLayout.LayoutParams(
            MATCH_PARENT,
            WRAP_CONTENT,
        ).apply {
            scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
        }
    }

    private val videoPlayerHost = FrameLayout(context).apply {
        layoutParams = CollapsingToolbarLayout.LayoutParams(
            MATCH_PARENT,
            WRAP_CONTENT,
        ).apply {
            collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PARALLAX
            parallaxMultiplier = 1f
        }
    }

    private val videoTabsHost = ComposeView(context).apply {
        layoutParams = CoordinatorLayout.LayoutParams(
            MATCH_PARENT,
            MATCH_PARENT,
        ).apply {
            behavior = AppBarLayout.ScrollingViewBehavior()
        }
    }

    init {
        videoPlayerHost.addView(playerView, ViewGroup.LayoutParams(MATCH_PARENT, 250.dp))
        collapsingToolbarLayout.addView(videoPlayerHost)
        appBarLayout.addView(collapsingToolbarLayout)
        rootView.addView(videoTabsHost)
        rootView.addView(appBarLayout)
    }

    val mainHostView: View
        get() = rootView

    fun setTabsHostContent(content: @Composable () -> Unit) {
        videoTabsHost.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        videoTabsHost.setContent(content)
    }

    fun setTabsVisible(visible: Boolean) {
        videoTabsHost.isVisible = visible
    }

    fun setExpanded(expanded: Boolean, animate: Boolean) {
        appBarLayout.post {
            appBarLayout.setExpanded(expanded, animate)
        }
    }

    fun setOnOffsetChanged(onChanged: (totalScrollRange: Int, verticalOffset: Int) -> Unit) {
        appBarLayout.addOnOffsetChangedListener { appBar, verticalOffset ->
            // 播放时（滚动禁用）AppBar 若被键盘聚焦滚动等路径意外收起，播放器
            // 会被压没、评论区整体上移：立即复位展开，保证视频始终可见
            val behavior = (appBarLayout.layoutParams as CoordinatorLayout.LayoutParams)
                .behavior as? VideoPlayerAppBarBehavior
            if (behavior?.disableScroll == true && verticalOffset != 0) {
                appBarLayout.setExpanded(true, false)
            }
            onChanged(appBar.totalScrollRange, verticalOffset)
        }
    }

    fun withBehavior(block: (VideoPlayerAppBarBehavior) -> Unit) {
        val behavior = (appBarLayout.layoutParams as CoordinatorLayout.LayoutParams)
            .behavior as? VideoPlayerAppBarBehavior ?: return
        block(behavior)
    }

    fun setPlayerHeight(height: Int) {
        val lp = playerView.layoutParams
        lp.height = height
        playerView.layoutParams = lp
        playerView.requestLayout()
    }

    /**
     * PIP 窗口内让整个 appBar 链（AppBarLayout/CollapsingToolbar/videoPlayerHost）
     * 与播放器全部撑满窗口，避免 WRAP_CONTENT 容器里解析 MATCH_PARENT 失败
     * （高度为 0 或异常撑满导致黑屏/遮挡）；退出 PIP 时恢复小屏布局。
     */
    fun setPipLayout(pip: Boolean) {
        appBarLayout.layoutParams = (appBarLayout.layoutParams as CoordinatorLayout.LayoutParams).apply {
            height = if (pip) MATCH_PARENT else WRAP_CONTENT
        }
        collapsingToolbarLayout.layoutParams =
            (collapsingToolbarLayout.layoutParams as AppBarLayout.LayoutParams).apply {
                height = if (pip) MATCH_PARENT else WRAP_CONTENT
            }
        videoPlayerHost.layoutParams =
            (videoPlayerHost.layoutParams as CollapsingToolbarLayout.LayoutParams).apply {
                height = if (pip) MATCH_PARENT else WRAP_CONTENT
            }
        val lp = playerView.layoutParams
        lp.height = if (pip) MATCH_PARENT else 250.dp
        playerView.layoutParams = lp
        appBarLayout.requestLayout()
    }

    fun clear() {
        videoTabsHost.disposeComposition()
    }
}
