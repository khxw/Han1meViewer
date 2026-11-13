package com.yenaly.han1meviewer.logic

import android.provider.Settings
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.model.UiMotionMode
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys
import com.yenaly.yenaly_libs.utils.applicationContext

object InteractionPreferences {

    val motionMode: UiMotionMode
        get() = UiMotionMode.fromKey(
            Preferences.preferenceSp.getString(SettingsPreferenceKeys.MOTION_MODE, null)
        )

    val animationsEnabled: Boolean
        get() = when (motionMode) {
            UiMotionMode.FULL -> true
            UiMotionMode.REDUCED -> false
            UiMotionMode.FOLLOW_SYSTEM -> systemAnimationsEnabled()
        }

    val pressScaleEnabled: Boolean
        get() = Preferences.preferenceSp.getBoolean(
            SettingsPreferenceKeys.PRESS_SCALE_ENABLED,
            true,
        )

    /**
     * 按压缩放是独立的明确开关，只有“减少动画”或低动态模式会强制关闭。
     */
    val pressScaleAnimationEnabled: Boolean
        get() = pressScaleEnabled && motionMode != UiMotionMode.REDUCED

    private fun systemAnimationsEnabled(): Boolean = runCatching {
        Settings.Global.getFloat(
            applicationContext.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }.getOrDefault(true)
}
