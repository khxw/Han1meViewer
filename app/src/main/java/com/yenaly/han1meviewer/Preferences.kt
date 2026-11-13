package com.yenaly.han1meviewer

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.yenaly.han1meviewer.HanimeConstants.HANIME_URL
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.logic.network.interceptor.SpeedLimitInterceptor
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys
import com.yenaly.han1meviewer.ui.view.video.HJzvdStd
import com.yenaly.han1meviewer.ui.view.video.HMediaKernel
import com.yenaly.han1meviewer.util.CookieString
import com.yenaly.han1meviewer.util.SafFileManager
import com.yenaly.han1meviewer.worker.HanimeDownloadManagerV2
import com.yenaly.yenaly_libs.utils.applicationContext
import com.yenaly.yenaly_libs.utils.getSpValue
import com.yenaly.yenaly_libs.utils.putSpValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

object Preferences {
    private const val USAGE_NOTICE_ACCEPTED = "usage_notice_accepted"

    /** 是否已经询问过"首次启动自动开启内置 Hosts"，无论用户同意还是拒绝都置为 true。 */
    private const val HOSTS_AUTO_PROMPT_SHOWN = "hosts_auto_prompt_shown"

    /**
     * [Preference][androidx.preference.PreferenceFragmentCompat]自帶的SP
     */
    val preferenceSp: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(
            applicationContext
        )

    // app 相關

    /**
     * 是否登入，一般跟[loginCookie]一起賦值
     */
    var isAlreadyLogin: Boolean
        get() = getSpValue(ALREADY_LOGIN, false)
        set(value) {
            loginStateFlow.value = value
            putSpValue(ALREADY_LOGIN, value)
        }

    val loginStateFlow = MutableStateFlow(isAlreadyLogin)

    var usageNoticeAccepted: Boolean
        get() = preferenceSp.getBoolean(USAGE_NOTICE_ACCEPTED, false)
        set(value) = preferenceSp.edit { putBoolean(USAGE_NOTICE_ACCEPTED, value) }

    var hostsAutoPromptShown: Boolean
        get() = preferenceSp.getBoolean(HOSTS_AUTO_PROMPT_SHOWN, false)
        set(value) = preferenceSp.edit { putBoolean(HOSTS_AUTO_PROMPT_SHOWN, value) }

    /**
     * 以下三个同步写入方法专供"写完可能立刻重启进程"的场景使用。
     *
     * 默认的 `edit {}` 走 apply()，是异步落盘；若紧接着 killProcess 重启，
     * 未落盘的写入会丢失，导致出现过"内置 Hosts 已开启、但使用须知又弹出来"的
     * 状态不一致。这里统一用 commit = true 同步落盘。
     */
    fun markUsageNoticeAcceptedSync() {
        preferenceSp.edit(commit = true) { putBoolean(USAGE_NOTICE_ACCEPTED, true) }
    }

    fun markHostsAutoPromptShownSync() {
        preferenceSp.edit(commit = true) { putBoolean(HOSTS_AUTO_PROMPT_SHOWN, true) }
    }

    /**
     * 写入内置 Hosts 开关。
     *
     * 防御性约束：**未同意使用须知时，禁止把内置 Hosts 打开**。
     * 正常流程下 Hosts 只能在"读完须知并同意 → 弹窗 → 用户点开启并重启"之后开启，
     * 这里再加一道闸，任何调用路径都不可能绕过须知把 Hosts 提前打开。
     */
    fun setUseBuiltInHostsSync(value: Boolean) {
        if (value && !usageNoticeAccepted) return
        preferenceSp.edit(commit = true) {
            putBoolean(SettingsPreferenceKeys.USE_BUILT_IN_HOSTS, value)
        }
    }

    /**
     * 修正历史遗留的不一致状态：旧版本用 apply() 落盘，重启时会丢失"须知已同意"
     * 却保留"Hosts 已开启"。
     *
     * 注意修正方向：内置 Hosts 只可能在"同意须知 → 弹窗 → 点击开启"之后才会被打开，
     * 所以这个组合说明**丢的是须知标记**，而不是 Hosts 被误开。因此这里补回"已同意"
     * 标记，而不是关掉 Hosts —— 否则老版本用户升级后会被强制关闭内置 Hosts，
     * 还要手动重新开启。
     *
     * 返回 true 表示本次确实修正过。
     */
    fun repairInconsistentHostsState(): Boolean {
        if (!usageNoticeAccepted && useBuiltInHosts) {
            preferenceSp.edit(commit = true) {
                putBoolean(USAGE_NOTICE_ACCEPTED, true)
            }
            return true
        }
        return false
    }

    val savedUserId: String
        get() = preferenceSp.getString(SAVED_USER_ID,"") ?: ""

    /**
     * 保存的string格式的登入cookie
     */
    var loginCookie
        get() = CookieString(getSpValue(LOGIN_COOKIE, EMPTY_STRING))
        set(value) {
            loginCookieStateFlow.value = value
            putSpValue(LOGIN_COOKIE, value.cookie)
        }

    val loginCookieStateFlow = MutableStateFlow(loginCookie)

    var cloudFlareCookie
        get() = CookieString(getSpValue(CLOUDFLARE_COOKIE   , EMPTY_STRING))
        set(value) {
            cloudFlareCookieStateFlow.value = value
            putSpValue(CLOUDFLARE_COOKIE, value.cookie)
        }
    val cloudFlareCookieStateFlow = MutableStateFlow(cloudFlareCookie)

    /**
     * 手动导入 Cloudflare Cookie 时使用的 User-Agent 覆盖值。
     *
     * Cloudflare 的 cf_clearance 与生成它的 User-Agent 绑定，
     * 手动从外部浏览器导入 cf_clearance 时必须同时携带对应 UA，否则会被拒。
     * 为空表示不覆盖，使用默认 [USER_AGENT]。
     */
    private const val CLOUDFLARE_UA_OVERRIDE = "cf_user_agent_override"

    var cloudFlareUserAgentOverride: String
        get() = getSpValue(CLOUDFLARE_UA_OVERRIDE, EMPTY_STRING)
        set(value) = putSpValue(CLOUDFLARE_UA_OVERRIDE, value)

    /**
     * 实际用于网络请求与验证 WebView 的 User-Agent：
     * 有手动覆盖值则用覆盖值，否则用默认 [USER_AGENT]。
     */
    val effectiveUserAgent: String
        get() = cloudFlareUserAgentOverride.ifBlank { USER_AGENT }

    // 更新 相關

    private const val UPDATE_NODE_ID = "update_node_id"

    var updateNodeId: String
        get() = getSpValue(UPDATE_NODE_ID, EMPTY_STRING)
        set(value) = putSpValue(UPDATE_NODE_ID, value)

    var lastUpdatePopupTime
        get() = getSpValue(SettingsPreferenceKeys.LAST_UPDATE_POPUP_TIME, 0L)
        set(value) = putSpValue(SettingsPreferenceKeys.LAST_UPDATE_POPUP_TIME, value)

    val updatePopupIntervalDays
        get() = preferenceSp.getInt(SettingsPreferenceKeys.UPDATE_POPUP_INTERVAL_DAYS, 0)

    /**
     * 公告拉取间隔（小时）。0 表示不拉取公告，默认 6 小时。
     */
    val announcementFetchIntervalHours
        get() = preferenceSp.getInt(SettingsPreferenceKeys.ANNOUNCEMENT_FETCH_INTERVAL_HOURS, 6)

    val useCIUpdateChannel
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.USE_CI_UPDATE_CHANNEL, false)

    // Check if show update dialog.
    @OptIn(ExperimentalTime::class)    val isUpdateDialogVisible: Boolean
        get() {
            val now = kotlin.time.Clock.System.now()
            val lastCheckTime = kotlin.time.Instant.fromEpochSeconds(lastUpdatePopupTime)
            val interval = updatePopupIntervalDays
            return now > lastCheckTime + interval.days
        }

    // 設定 相關

    val switchPlayerKernel: String
        get() = preferenceSp.getString(
            SettingsPreferenceKeys.SWITCH_PLAYER_KERNEL,
            HMediaKernel.Type.ExoPlayer.name
        ) ?: HMediaKernel.Type.ExoPlayer.name

    val showBottomProgress: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.SHOW_BOTTOM_PROGRESS,
            true
        )

    val playerSpeed: Float
        get() = preferenceSp.getString(
            SettingsPreferenceKeys.PLAYER_SPEED,
            HJzvdStd.DEF_SPEED.toString()
        )?.toFloat() ?: HJzvdStd.DEF_SPEED

    val slideSensitivity: Int
        get() = preferenceSp.getInt(
            SettingsPreferenceKeys.SLIDE_SENSITIVITY,
            HJzvdStd.DEF_PROGRESS_SLIDE_SENSITIVITY
        )

    val longPressSpeedTime: Float
        get() = preferenceSp.getString(
            SettingsPreferenceKeys.LONG_PRESS_SPEED_TIMES,
            HJzvdStd.DEF_LONG_PRESS_SPEED_TIMES.toString()
        )?.toFloat() ?: HJzvdStd.DEF_LONG_PRESS_SPEED_TIMES

    val videoLanguage: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.VIDEO_LANGUAGE, "zhs") ?: "zht"

    val videoQuality: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.DEFAULT_VIDEO_QUALITY, "1080P") ?: "1080P"

    val showPlayedIndicator: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.SHOW_PLAYED_INDICATOR,true)

    val searchGridColumnsConfig: SearchGridColumnsConfig
        get() = SearchGridColumnsConfig(
            compactColumns = preferenceSp.getInt(
                SettingsPreferenceKeys.SEARCH_GRID_COLUMNS_COMPACT,
                SearchGridColumnsConfig.DEFAULT_COMPACT_COLUMNS,
            ),
            mediumColumns = preferenceSp.getInt(
                SettingsPreferenceKeys.SEARCH_GRID_COLUMNS_MEDIUM,
                SearchGridColumnsConfig.DEFAULT_MEDIUM_COLUMNS,
            ),
            expandedColumns = preferenceSp.getInt(
                SettingsPreferenceKeys.SEARCH_GRID_COLUMNS_EXPANDED,
                SearchGridColumnsConfig.DEFAULT_EXPANDED_COLUMNS,
            ),
            largeColumns = preferenceSp.getInt(
                SettingsPreferenceKeys.SEARCH_GRID_COLUMNS_LARGE,
                SearchGridColumnsConfig.DEFAULT_LARGE_COLUMNS,
            ),
        )

    val horizontalCardCountConfig: HorizontalCardCountConfig
        get() = HorizontalCardCountConfig(
            narrowCount = preferenceSp.getString(
                SettingsPreferenceKeys.HORIZONTAL_CARD_COUNT_NARROW,
                HorizontalCardCountConfig.DEFAULT_NARROW_COUNT.toString(),
            )?.toFloatOrNull() ?: HorizontalCardCountConfig.DEFAULT_NARROW_COUNT,
            compactCount = preferenceSp.getString(
                SettingsPreferenceKeys.HORIZONTAL_CARD_COUNT_COMPACT,
                HorizontalCardCountConfig.DEFAULT_COMPACT_COUNT.toString(),
            )?.toFloatOrNull() ?: HorizontalCardCountConfig.DEFAULT_COMPACT_COUNT,
            mediumCount = preferenceSp.getString(
                SettingsPreferenceKeys.HORIZONTAL_CARD_COUNT_MEDIUM,
                HorizontalCardCountConfig.DEFAULT_MEDIUM_COUNT.toString(),
            )?.toFloatOrNull() ?: HorizontalCardCountConfig.DEFAULT_MEDIUM_COUNT,
            expandedCount = preferenceSp.getString(
                SettingsPreferenceKeys.HORIZONTAL_CARD_COUNT_EXPANDED,
                HorizontalCardCountConfig.DEFAULT_EXPANDED_COUNT.toString(),
            )?.toFloatOrNull() ?: HorizontalCardCountConfig.DEFAULT_EXPANDED_COUNT,
        )

    val fakeLauncherIcon: String
        get() = preferenceSp.getString(
            SettingsPreferenceKeys.FAKE_LAUNCHER_ICON,
            "com.yenaly.han1meviewer.LauncherAliasDefault") ?: "com.yenaly.han1meviewer.LauncherAliasDefault"

    val baseUrl: String
        get() {
            if (useCustomMirrorSite && customMirrorSite.isNotBlank()) {
                val url = if (appendCustomMirrorPath) customMirrorSite else customMirrorSite.toRootUrl()
                return url.toRetrofitBaseUrl()
            }
            return preferenceSp.getString(SettingsPreferenceKeys.DOMAIN_NAME, HANIME_URL[0])
                ?: HANIME_URL[0]
        }

    val homeUrl: String
        get() {
            if (useCustomMirrorSite && customMirrorSite.isNotBlank()) return customMirrorSite
            return baseUrl
        }

    val useCustomMirrorSite: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.USE_CUSTOM_MIRROR_SITE, false)

    val customMirrorSite: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.CUSTOM_MIRROR_SITE, EMPTY_STRING).orEmpty()

    val appendCustomMirrorPath: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.APPEND_CUSTOM_MIRROR_PATH, true)

    private fun String.toRetrofitBaseUrl(): String = if (endsWith('/')) this else "$this/"

    private fun String.toRootUrl(): String {
        val uri = toUri()
        return "${uri.scheme}://${uri.encodedAuthority}"
    }

    val selectedBaseUrl: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.SELECTED_BASE_URL, HANIME_URL[0])
            ?: HANIME_URL[0]

    val useBuiltInHosts: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.USE_BUILT_IN_HOSTS, false)

    val customHostsData: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.CUSTOM_HOSTS_DATA, EMPTY_STRING).orEmpty()

    val useDoH: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.USE_DOH, false)

    val dohPreset: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.DOH_PRESET, "alidns") ?: "alidns"

    val dohCustomUrl: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.DOH_CUSTOM_URL, EMPTY_STRING).orEmpty()

    val dohBootstrapIps: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.DOH_BOOTSTRAP_IPS, EMPTY_STRING).orEmpty()

    val dohTimeoutSeconds: Int
        get() = preferenceSp.getInt(SettingsPreferenceKeys.DOH_TIMEOUT_SECONDS, 10)

    /**
     * 是否记录网络日志（DNS 解析与 HTTP 请求结果）。
     * 关闭后停止记录新日志，但保留已有记录，供排查问题时查看。
     */
    val enableLogging: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.ENABLE_LOGGING, true)

    // 關鍵H幀 相關

    val whenCountdownRemind: Int
        get() = preferenceSp.getInt(
            SettingsPreferenceKeys.WHEN_COUNTDOWN_REMIND,
            HJzvdStd.DEF_COUNTDOWN_SEC
        ) * 1_000 // 越不了界，最大就30_000ms而已

    val showCommentWhenCountdown: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.SHOW_COMMENT_WHEN_COUNTDOWN,
            false
        )

    val hKeyframesEnable: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.H_KEYFRAMES_ENABLE,
            true
        )

    val sharedHKeyframesEnable: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.SHARED_H_KEYFRAMES_ENABLE,
            true
        )

    val sharedHKeyframesUseFirst: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.SHARED_H_KEYFRAMES_USE_FIRST,
            false
        )

    // 代理 相關

    val proxyType: Int
        get() = preferenceSp.getInt(
            SettingsPreferenceKeys.PROXY_TYPE,
            HProxySelector.TYPE_SYSTEM
        )

    val proxyIp: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.PROXY_IP, EMPTY_STRING).orEmpty()

    val proxyPort: Int
        get() = preferenceSp.getInt(SettingsPreferenceKeys.PROXY_PORT, -1)

    // 下载 相關

    val downloadCountLimit: Int
        get() = preferenceSp.getInt(
            SettingsPreferenceKeys.DOWNLOAD_COUNT_LIMIT,
            HanimeDownloadManagerV2.MAX_CONCURRENT_DOWNLOAD_DEF
        )

    val collapseDownloadedGroup: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.COLLAPSE_DOWNLOADED_GROUP,false)

    val downloadWifiOnly: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.DOWNLOAD_WIFI_ONLY, false)

    /**
     * 自动发现自定义目录中新增或移动过的本地视频。
     */
    val downloadAutoDiscoveryEnabled: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.DOWNLOAD_AUTO_DISCOVERY_ENABLED,
            true,
        )

    // 剪贴板相关

    /**
     * 是否在启动时检测剪贴板中的 Hanime 链接
     */
    val clipboardLinkDetect
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.CLIPBOARD_LINK_DETECT, true)

    /**
     * 上次检测到链接的剪贴板内容，用于去重（同一内容不重复提示）
     */
    var lastDetectedClipboard: String
        get() = getSpValue(SettingsPreferenceKeys.LAST_DETECTED_CLIPBOARD, EMPTY_STRING)
        set(value) = putSpValue(SettingsPreferenceKeys.LAST_DETECTED_CLIPBOARD, value)

    // 随机视频相关

    /**
     * 随机视频：是否启用屏蔽标签（开启后不会随机到带屏蔽标签的视频）
     */
    val blockTagsEnabled: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.BLOCK_TAGS_ENABLED, false)

    /**
     * 随机视频：屏蔽标签列表（本地化后的标签文本，逗号分隔存储）
     */
    val blockedTags: Set<String>
        get() = preferenceSp.getString(SettingsPreferenceKeys.BLOCKED_TAGS, null)
            ?.split(',')
            .orEmpty()
            .filter { it.isNotBlank() }
            .toSet()

    /**
     * 标题关键词屏蔽：是否启用（开启后搜索/首页/随机中过滤标题含关键词的视频）
     */
    val blockKeywordsEnabled: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.BLOCK_KEYWORDS_ENABLED, false)

    /**
     * 标题关键词屏蔽：关键词列表（逗号分隔存储）
     */
    val blockedKeywords: Set<String>
        get() = preferenceSp.getString(SettingsPreferenceKeys.BLOCKED_KEYWORDS, null)
            ?.split(',')
            .orEmpty()
            .filter { it.isNotBlank() }
            .toSet()

    /**
     * 评论关键词屏蔽：是否启用（开启后评论区隐藏内容含关键词的评论）
     */
    val commentKeywordsEnabled: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.COMMENT_KEYWORDS_ENABLED, false)

    /**
     * 评论关键词屏蔽：关键词列表（逗号分隔存储）
     */
    val blockedCommentKeywords: Set<String>
        get() = preferenceSp.getString(SettingsPreferenceKeys.BLOCKED_COMMENT_KEYWORDS, null)
            ?.split(',')
            .orEmpty()
            .filter { it.isNotBlank() }
            .toSet()

    /**
     * 底部导航栏模式：开启后主界面底部显示导航栏（首页/搜索/下载/签到/设置）
     */
    val useBottomNavigation: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.USE_BOTTOM_NAVIGATION, false)

    /**
     * 紧凑模式：开启后视频卡片间距缩小、网格列数增加，一屏显示更多内容
     */
    val useCompactMode: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.COMPACT_MODE, false)

    val isUsePrivateStorage: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.USE_PRIVATE_STORAGE,true)
    val safDownloadPath: String?
        get() = preferenceSp.getString(SafFileManager.KEY_TREE_URI,null)

    val useDarkMode: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.USE_DARK_MODE,"always_off") ?: "always_off"
    val useDynamicColor: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.USE_DYNAMIC_COLOR,false)
    val themeColor: String?
        get() = preferenceSp.getString(SettingsPreferenceKeys.THEME_COLOR, null)
    val allowResumePlayback: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.ALLOW_RESUME_PLAYBACK,true)

    val searchArtistIgnoreVideoType: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.SEARCH_ARTIST_IGNORE_VIDEO_TYPE,false)

    val disableMobileDataWarning: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.DISABLE_MOBILE_DATA_WARNING,false)

    val tabletMode: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.TABLET_MODE, false)

    /**
     * MPV播放器设置
     */
    val mpvProfile: String // 预设模式
        get() = preferenceSp.getString(SettingsPreferenceKeys.MPV_PROFILE, "fast") ?: "fast"

    val enableGPUNextRenderer: Boolean // gpu-next 渲染器
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.ENABLE_GPU_NEXT_RENDERER, false)

    val mpvInterpolation: Boolean  // 插帧相关
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.MPV_INTERPOLATION, false)

    val mpvDeband: Boolean  // 去色带
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.MPV_DEBAND, true)

    val mpvFramedrop: Boolean  // GPU 繁忙时允许丢帧
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.MPV_FRAMEDROP, true)

    val mpvHwdec: String  // 硬件解码
        get() = preferenceSp.getString(SettingsPreferenceKeys.MPV_HWDEC, "Auto")?: "Auto"

    val mpvCacheSecs: Int  // 预缓存秒数
        get() = preferenceSp.getInt(SettingsPreferenceKeys.MPV_CACHE_SECS, 60)

    val mpvTlsVerify: Boolean  // 忽略证书验证
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.MPV_TLS_VERIFY, true)

    val mpvNetworkTimeout: Int  // 请求超时
        get() = preferenceSp.getInt(SettingsPreferenceKeys.MPV_NETWORK_TIMEOUT, 10)

    val customMpvParams: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.CUSTOM_PARAMS,"")?: ""

    /**
     * 对应关系详见 [SpeedLimitInterceptor.SPEED_BYTES]
     */
    val downloadSpeedLimit: Long
        get() {
            val index = preferenceSp.getInt(
                SettingsPreferenceKeys.DOWNLOAD_SPEED_LIMIT,
                SpeedLimitInterceptor.NO_LIMIT_INDEX
            )
            return SpeedLimitInterceptor.speedBytesAt(index)
        }
}
