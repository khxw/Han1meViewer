package com.yenaly.han1meviewer.util

import android.app.Activity
import android.os.Build
import android.view.Display
import kotlin.math.roundToInt

data class RefreshRateMode(
    val modeId: Int,
    val refreshRate: Float,
    val width: Int,
    val height: Int,
) {
    val refreshRateHz: Int
        get() = refreshRate.roundToInt()

    val displayLabel: String
        get() = "$refreshRateHz Hz"
}

object DisplayModeManager {

    const val SYSTEM_MODE_ID = 0

    fun supportedModes(activity: Activity): List<RefreshRateMode> {
        val display = activity.currentDisplay() ?: return emptyList()
        val currentMode = display.mode ?: return emptyList()
        val sameResolutionModes = display.supportedModes.filter { mode ->
            mode.physicalWidth == currentMode.physicalWidth &&
                mode.physicalHeight == currentMode.physicalHeight
        }
        val candidates = if (sameResolutionModes.isNotEmpty()) {
            sameResolutionModes
        } else {
            display.supportedModes.toList()
        }
        return candidates
            .map { mode ->
                RefreshRateMode(
                    modeId = mode.modeId,
                    refreshRate = mode.refreshRate,
                    width = mode.physicalWidth,
                    height = mode.physicalHeight,
                )
            }
            .distinctBy { it.refreshRateHz }
            .sortedBy { it.refreshRateHz }
    }

    fun currentRefreshRate(activity: Activity): Int =
        activity.currentDisplay()?.refreshRate?.roundToInt() ?: 0

    fun apply(activity: Activity, preferredModeId: Int) {
        val selected = supportedModes(activity).firstOrNull { it.modeId == preferredModeId }
        val attrs = activity.window.attributes
        if (selected == null) {
            attrs.preferredDisplayModeId = SYSTEM_MODE_ID
            attrs.preferredRefreshRate = 0f
        } else {
            attrs.preferredDisplayModeId = selected.modeId
            attrs.preferredRefreshRate = selected.refreshRate
        }
        activity.window.attributes = attrs
    }

    private fun Activity.currentDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
}
