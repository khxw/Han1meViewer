package com.yenaly.han1meviewer

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.google.android.material.color.DynamicColors
import com.yenaly.han1meviewer.logic.log.HLumberjack
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.util.AnimeShaders
import com.yenaly.han1meviewer.util.ThemeUtils
import com.yenaly.yenaly_libs.base.YenalyApplication
import com.yenaly.yenaly_libs.utils.LanguageHelper
import `is`.xyz.mpv.MPVLib
import java.io.File
import java.net.ProxySelector

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/08 008 17:32
 */
class HanimeApplication : YenalyApplication(), SingletonImageLoader.Factory {

    companion object {
        const val TAG = "HanimeApplication"
    }

    /**
     * 已经在 [HInitializer] 中处理了
     */
    override val isDefaultCrashHandlerEnabled: Boolean = false

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()

    override fun onCreate() {
        super.onCreate()
        cleanupLegacyProxyData()
        ThemeUtils.applyDarkModeFromPreferences(this)
        // 恢复上次运行期间落盘的网络日志，供用户反馈问题时查看
        HLumberjack.loadFromDisk()
        if (Preferences.useDynamicColor){
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
        ProxySelector.setDefault(HProxySelector())
        HProxySelector.rebuildNetwork()
        initNotificationChannel()
        MPVLib.create(applicationContext)
        MPVLib.init()

        if (AnimeShaders.copyShaderAssets(applicationContext) <= 0) {
            Log.w(TAG, "Shader 复制失败")
        }
        if (AnimeShaders.copyCertAssets(applicationContext) <= 0) {
            Log.w(TAG, "cert 复制失败")
        }
        val selected = Preferences.fakeLauncherIcon
        switchLauncher(selected)
    }

    /**
     * 一次性清理已移除的内置代理留下的本地数据。
     *
     * 2.1.122 起内置代理功能已整体删除，但旧版本会把这些东西落在本机：
     * - `comic_proxy` SharedPreferences：订阅链接、当前节点名、节点列表、订阅原文；
     * - `filesDir/mihomo/config.yaml`：订阅里的节点凭据（uuid / 密码等）。
     *
     * 现在已没有任何代码读取它们，属于应该主动清掉的敏感残留。
     * 清空后再次启动时 `all` 为空、目录不存在，都是空操作。
     */
    private fun cleanupLegacyProxyData() {
        runCatching {
            val legacy = getSharedPreferences("comic_proxy", Context.MODE_PRIVATE)
            if (legacy.all.isNotEmpty()) {
                legacy.edit().clear().commit()
                Log.i(TAG, "legacy proxy prefs cleared")
            }
        }.onFailure {
            Log.w(TAG, "clear legacy proxy prefs failed: ${it.message}")
        }
        runCatching {
            val home = File(filesDir, "mihomo")
            if (home.exists()) {
                home.deleteRecursively()
                Log.i(TAG, "legacy mihomo home removed")
            }
        }.onFailure {
            Log.w(TAG, "remove legacy mihomo home failed: ${it.message}")
        }
    }

    private fun initNotificationChannel() {
        val nm = NotificationManagerCompat.from(this)

        val hanimeDownloadChannel = NotificationChannelCompat.Builder(
            DOWNLOAD_NOTIFICATION_CHANNEL,
            NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName("Hanime Download").build()
        nm.createNotificationChannel(hanimeDownloadChannel)

        val appUpdateChannel = NotificationChannelCompat.Builder(
            UPDATE_NOTIFICATION_CHANNEL,
            NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName("App Update").build()
        nm.createNotificationChannel(appUpdateChannel)
    }
    fun switchLauncher(alias: String) {
        val pm = packageManager

        val allAliases = listOf(
            "com.yenaly.han1meviewer.LauncherAliasDefault",
            "com.yenaly.han1meviewer.LauncherFakeCalc",
            "com.yenaly.han1meviewer.LauncherFakeCornhub",
            "com.yenaly.han1meviewer.LauncherFakeXxt"
        )

        allAliases.forEach { a ->
            val state = if (a == alias)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            pm.setComponentEnabledSetting(
                ComponentName(this, a),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
