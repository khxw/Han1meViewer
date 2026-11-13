package com.yenaly.han1meviewer.ui.navigation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.InteractionPreferences
import com.yenaly.han1meviewer.logic.model.UiMotionMode
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.screen.settings.InteractionSettingsScreen
import com.yenaly.han1meviewer.ui.screen.settings.InteractionSettingsUiState
import com.yenaly.han1meviewer.util.DisplayModeManager
import com.yenaly.yenaly_libs.utils.findActivity

@Composable
fun InteractionSettingsRouteScreen() {
    val context = LocalContext.current
    val activity = context.findActivity<MainActivity>()
    var refreshKey by remember { mutableIntStateOf(0) }
    val displayModes = remember(refreshKey, context) {
        DisplayModeManager.supportedModes(activity)
    }
    val selectedDisplayModeId = remember(refreshKey, context) {
        Preferences.preferenceSp.getInt(
            SettingsPreferenceKeys.DISPLAY_REFRESH_RATE_MODE_ID,
            DisplayModeManager.SYSTEM_MODE_ID,
        )
    }
    val currentRefreshRate = remember(refreshKey, displayModes) {
        DisplayModeManager.currentRefreshRate(activity)
    }
    val systemModeLabel = stringResource(R.string.display_refresh_rate_system)
    val displayModeSummary = if (displayModes.isEmpty()) {
        stringResource(R.string.display_refresh_rate_unavailable)
    } else {
        stringResource(R.string.display_refresh_rate_summary, currentRefreshRate)
    }
    val currentMotionMode = InteractionPreferences.motionMode
    val state = remember(
        refreshKey,
        displayModes,
        selectedDisplayModeId,
        currentMotionMode,
        displayModeSummary,
        systemModeLabel,
    ) {
        val selectedDisplayMode = displayModes.firstOrNull { it.modeId == selectedDisplayModeId }
        InteractionSettingsUiState(
            motionMode = currentMotionMode,
            pressScaleEnabled = Preferences.preferenceSp.getBoolean(
                SettingsPreferenceKeys.PRESS_SCALE_ENABLED,
                true,
            ),
            animationsEnabled = InteractionPreferences.animationsEnabled,
            displayModeId = selectedDisplayModeId,
            displayModeLabel = selectedDisplayMode?.displayLabel ?: systemModeLabel,
            displayModeSummary = displayModeSummary,
        )
    }

    InteractionSettingsScreen(
        state = state,
        motionModeOptions = listOf(
            stringResource(R.string.motion_mode_follow_system) to UiMotionMode.FOLLOW_SYSTEM.key,
            stringResource(R.string.motion_mode_full) to UiMotionMode.FULL.key,
            stringResource(R.string.motion_mode_reduced) to UiMotionMode.REDUCED.key,
        ),
        displayModeOptions = listOf(
            systemModeLabel to DisplayModeManager.SYSTEM_MODE_ID.toString(),
        ) + displayModes.map { mode ->
            mode.displayLabel to mode.modeId.toString()
        },
        onMotionModeChange = {
            saveString(SettingsPreferenceKeys.MOTION_MODE, it.key)
            if (it == UiMotionMode.REDUCED) {
                saveBoolean(SettingsPreferenceKeys.PRESS_SCALE_ENABLED, false)
            }
            refreshKey++
        },
        onPressScaleEnabledChange = {
            saveBoolean(SettingsPreferenceKeys.PRESS_SCALE_ENABLED, it)
            refreshKey++
        },
        onLowMotionModeChange = { enabled ->
            saveString(
                SettingsPreferenceKeys.MOTION_MODE,
                if (enabled) UiMotionMode.REDUCED.key else UiMotionMode.FOLLOW_SYSTEM.key,
            )
            saveBoolean(SettingsPreferenceKeys.PRESS_SCALE_ENABLED, !enabled)
            refreshKey++
        },
        onDisplayModeChange = { modeId ->
            saveInt(SettingsPreferenceKeys.DISPLAY_REFRESH_RATE_MODE_ID, modeId)
            DisplayModeManager.apply(activity, modeId)
            refreshKey++
        },
    )
}
