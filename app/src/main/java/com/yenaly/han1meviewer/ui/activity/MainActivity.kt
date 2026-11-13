package com.yenaly.han1meviewer.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.yenaly.han1meviewer.HanimeConstants.ANIME_URL
import com.yenaly.han1meviewer.HanimeConstants.HANIME_URL
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.ui.component.FLOATING_BOTTOM_NAV_HEIGHT
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logout
import com.yenaly.han1meviewer.logic.HomePageCache
import com.yenaly.han1meviewer.ui.bridge.VideoPageHost
import com.yenaly.han1meviewer.PermissionRequester
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.main.AccountRoute
import com.yenaly.han1meviewer.ui.navigation.main.VideoRoute
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys
import com.yenaly.han1meviewer.ui.screen.main.MainActivityContent
import com.yenaly.han1meviewer.ui.screen.home.homepage.HomePageViewModel
import com.yenaly.han1meviewer.util.DisplayModeManager
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.han1meviewer.videoUrlRegex
import com.yenaly.yenaly_libs.ActivityManager
import com.yenaly.yenaly_libs.base.frame.FrameActivity
import com.yenaly.yenaly_libs.utils.showSnackBar
import com.yenaly.yenaly_libs.utils.textFromClipboard
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/08 008 17:35
 */
class MainActivity : FrameActivity(), PermissionRequester {

    val viewModel by viewModels<HomePageViewModel>()

    lateinit var navController: NavHostController
    private var showAuthGuard by mutableStateOf(true)
    private val pendingNavigationRequests = Channel<Intent>(Channel.BUFFERED)
    private val pendingNavigationRequestsFlow = pendingNavigationRequests.receiveAsFlow()
    private var currentVideoHost: VideoPageHost? = null

    /**
     * 视频详情页小屏播放中（用于隐藏悬浮底部导航栏，避免遮挡播放画面）。
     * 由详情页在播放/暂停时更新，离开详情页时复位。
     */
    val videoPlayingState = MutableStateFlow(false)

    fun setVideoPlaying(playing: Boolean) {
        videoPlayingState.value = playing
    }

    /**
     * 当前页面是否显示悬浮底部导航栏（由 MainActivityContent 随路由同步更新）。
     * 供底部悬浮控件（Snackbar、批量操作栏等）判断是否需要抬升。
     */
    val bottomNavVisibleState = MutableStateFlow(true)

    fun setBottomNavVisible(visible: Boolean) {
        bottomNavVisibleState.value = visible
    }

    companion object {
        private const val REQUEST_WRITE_EXTERNAL_STORAGE = 1234
        const val ACTION_TOGGLE_PLAY = "com.yenaly.han1meviewer.ACTION_TOGGLE_PLAY"
    }

    // 登錄完了後讓activity刷新主頁
    private val loginDataLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.getHomePage()
            }
        }
    private var hasAuthenticated = false
    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i("pipmode", "✅ onReceive called with action: ${intent?.action}")
            when (intent?.action) {
                ACTION_TOGGLE_PLAY -> {
                    Log.i("pipmode", "🎬 ACTION_TOGGLE_PLAY triggered")
                    togglePlayPause()
                }
            }
        }
    }

    private fun initData() {
        setContent {
            MainActivityContent(
                activity = this,
                viewModel = viewModel,
                pendingNavigationRequests = pendingNavigationRequestsFlow,
                showAuthGuard = showAuthGuard,
                onOpenAccount = { navController.navigateSafely(AccountRoute) },
                onLogoutClick = { showLogoutConfirmDialog() },
                onRequireLogin = { gotoLoginActivity() },
                onSwitchSiteClick = { showSiteSwitchDialog() },
                onNavigateControllerReady = { controller -> navController = controller },
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installSplashScreen().apply {
                setKeepOnScreenCondition { !hasAuthenticated }
            }
        }
        enableEdgeToEdge()
        // Android 15 起系统会给导航条区域自动叠加对比度遮罩，悬浮导航栏下方会露出暗色条，需禁用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useLock = prefs.getBoolean("use_lock_screen", false)

        if (useLock && isDeviceSecureCompat(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                authenticate(
                    this,
                    onSuccess = {
                        hasAuthenticated = true
                        showAuthGuard = false
                        initData()
                    },
                    onFailed = {
                        finish()
                    }
                )
            } else {
                // Android 7~8，不支持 BiometricPrompt
                Toast.makeText(this, R.string.not_compact_lock_screen, Toast.LENGTH_SHORT).show()
                hasAuthenticated = true
                showAuthGuard = false
                initData()
            }
        } else {
            hasAuthenticated = true
            showAuthGuard = false
            initData()
        }
        pendingNavigationRequests.trySend(intent)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyAppLocale(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavigationRequests.trySend(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun isDeviceSecureCompat(context: Context): Boolean {
        val km = context.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        return km.isDeviceSecure
    }

    private fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    // 指纹被识别但不匹配（单次）
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // 取消、锁定、连续失败后触发
                    onFailed()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.auth_request))
            .setSubtitle(getString(R.string.unlock_method))
            .setDescription(getString(R.string.unlock_desc))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    override fun onStart() {
        super.onStart()
        DisplayModeManager.apply(
            this,
            Preferences.preferenceSp.getInt(
                SettingsPreferenceKeys.DISPLAY_REFRESH_RATE_MODE_ID,
                DisplayModeManager.SYSTEM_MODE_ID,
            ),
        )
        registerPipReceiver()
        window.decorView.post {
            if (!Preferences.clipboardLinkDetect) return@post
            val clipboardText = textFromClipboard?.let { it.toString() } ?: return@post
            if (clipboardText == Preferences.lastDetectedClipboard) return@post
            videoUrlRegex.find(clipboardText)?.groupValues?.get(1)?.let { videoCode ->
                Preferences.lastDetectedClipboard = clipboardText
                showFindRelatedLinkSnackBar(videoCode)
            }
        }
    }

    private fun registerPipReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_PLAY)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, filter, RECEIVER_NOT_EXPORTED)
            Log.i("pipmode", "✅ registerReceiver with RECEIVER_NOT_EXPORTED")
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipActionReceiver, filter)
            Log.i("pipmode", "✅ registerReceiver (legacy)")
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pipActionReceiver)
    }

    private fun applyAppLocale(context: Context): Context {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val lang = prefs.getString("app_language", "system") ?: "system"

        val newLocale = when (lang) {
            "zh-rCN" -> Locale.SIMPLIFIED_CHINESE
            "zh" -> Locale.TRADITIONAL_CHINESE
            "en" -> Locale.ENGLISH
            "ja" -> Locale.JAPANESE
            else -> Resources.getSystem().configuration.locales.get(0)
        }

        Locale.setDefault(newLocale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(newLocale)
        return context.createConfigurationContext(config)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun showFindRelatedLinkSnackBar(videoCode: String) {
        // 当前页面显示悬浮导航栏时抬升 Snackbar，避免被导航栏遮挡
        val extraMargin = if (Preferences.useBottomNavigation && bottomNavVisibleState.value) {
            FLOATING_BOTTOM_NAV_HEIGHT + 8f
        } else 0f
        showSnackBar(
            R.string.detect_ha1_related_link_in_clipboard,
            Snackbar.LENGTH_LONG,
            extraBottomMarginDp = extraMargin,
        ) {
            setAction(R.string.enter) {
                showVideoDetailFragment(videoCode)
            }
        }
    }

    private fun showSiteSwitchDialog() {
        val currentSite = Preferences.baseUrl
        showAlertDialog {
            setTitle(R.string.confirm_switch_site)
            setPositiveButton(R.string.sure) { _, _ ->
                val avSite = HANIME_URL[3]
                val selectedBaseUrl = Preferences.selectedBaseUrl
                if (currentSite in ANIME_URL) {
                    Preferences.preferenceSp.edit(true) {
                        putString(SettingsPreferenceKeys.SELECTED_BASE_URL, currentSite)
                        putString(SettingsPreferenceKeys.DOMAIN_NAME, avSite)
                    }
                } else {
                    Preferences.preferenceSp.edit(true) {
                        putString(SettingsPreferenceKeys.SELECTED_BASE_URL, selectedBaseUrl)
                        putString(SettingsPreferenceKeys.DOMAIN_NAME, selectedBaseUrl)
                    }
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    // 切换站点后重启，清首页缓存避免展示旧站点数据
                    HomePageCache.clear()
                    ActivityManager.restart(killProcess = true)
                }, 500)
            }
            setNegativeButton(R.string.no, null)
        }
    }

    fun gotoLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        loginDataLauncher.launch(intent)
    }

    fun showLogoutConfirmDialog(closeCurrentPageOnConfirm: Boolean = false) {
        showAlertDialog {
            setTitle(R.string.sure_to_logout)
            setPositiveButton(R.string.sure) { _, _ ->
                if (closeCurrentPageOnConfirm) {
                    navController.popBackStack()
                }
                logoutWithRefresh()
            }
            setNegativeButton(R.string.no, null)
        }
    }

    fun logoutWithRefresh() {
        logout()
        viewModel.getHomePage()
    }

    fun showVideoDetailFragment(
        videoCode: String,
        fileUri: String? = null,
        autoPlayNextEpisode: Boolean = false,
    ) {
        navController.navigateSafely(
            VideoRoute(
                videoCode = videoCode,
                localUri = fileUri,
                autoPlay = autoPlayNextEpisode,
                autoPlayNextEpisode = autoPlayNextEpisode,
            ),
        )
    }

    fun registerCurrentVideoHost(host: VideoPageHost?) {
        currentVideoHost = host
    }

    private var onGranted: (() -> Unit)? = null
    private var onDenied: (() -> Unit)? = null
    private var onPermanentlyDenied: (() -> Unit)? = null
    override fun requestStoragePermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit,
        onPermanentlyDenied: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                onGranted()
            } else {
                this.onGranted = onGranted
                this.onDenied = onDenied
                this.onPermanentlyDenied = onPermanentlyDenied
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    REQUEST_WRITE_EXTERNAL_STORAGE
                )
            }
        } else {
            onGranted() // Android 10+ 不需要权限
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE) {
            val permission = permissions.getOrNull(0)
            val grantResult = grantResults.getOrNull(0)

            if (permission == Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                when {
                    grantResult == PackageManager.PERMISSION_GRANTED -> {
                        onGranted?.invoke()
                    }

                    shouldShowRequestPermissionRationale(permission) -> {
                        onDenied?.invoke()
                    }

                    else -> {
                        // 永久拒绝（勾选“不再询问”）
                        onPermanentlyDenied?.invoke()
                    }
                }
                // 清除引用，防止内存泄露
                onGranted = null
                onDenied = null
                onPermanentlyDenied = null
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val currentFragment = currentVideoHost

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val allowPip = prefs.getBoolean("allow_pip_mode", true)

        Log.i("pipmode", "enter pip mode?\n$currentFragment\nallowpip:$allowPip\n")

        if (currentFragment?.shouldEnterPip() == true && allowPip) {
            Log.i("pipmode", "enter pip mode")
            currentFragment.enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        val currentFragment = currentVideoHost

        currentFragment?.onPipModeChanged(isInPictureInPictureMode)
    }

    fun togglePlayPause() {
        currentVideoHost?.togglePlayPause()
    }
}
