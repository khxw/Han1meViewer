package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.UiMotionMode
import com.yenaly.han1meviewer.ui.component.ChoiceDialog
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.ui.component.SettingSwitchItem
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn
import com.yenaly.han1meviewer.ui.preview.ComponentPreview

data class InteractionSettingsUiState(
    val motionMode: UiMotionMode,
    val pressScaleEnabled: Boolean,
    val animationsEnabled: Boolean,
    val displayModeId: Int,
    val displayModeLabel: String,
    val displayModeSummary: String,
)

private enum class InteractionChoiceDialog {
    MotionMode,
    DisplayMode,
}

@Composable
fun InteractionSettingsScreen(
    state: InteractionSettingsUiState,
    motionModeOptions: List<Pair<String, String>>,
    displayModeOptions: List<Pair<String, String>>,
    onMotionModeChange: (UiMotionMode) -> Unit,
    onPressScaleEnabledChange: (Boolean) -> Unit,
    onLowMotionModeChange: (Boolean) -> Unit,
    onDisplayModeChange: (Int) -> Unit,
) {
    var activeDialog by rememberSaveable { mutableStateOf<InteractionChoiceDialog?>(null) }
    val lowMotionMode = state.motionMode == UiMotionMode.REDUCED

    ChoiceDialog(
        visible = activeDialog == InteractionChoiceDialog.MotionMode,
        title = stringResource(R.string.motion_mode),
        options = motionModeOptions,
        selectedValue = state.motionMode.key,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onMotionModeChange(UiMotionMode.fromKey(it))
        },
    )

    ChoiceDialog(
        visible = activeDialog == InteractionChoiceDialog.DisplayMode,
        title = stringResource(R.string.display_refresh_rate),
        options = displayModeOptions,
        selectedValue = state.displayModeId.toString(),
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onDisplayModeChange(it.toIntOrNull() ?: 0)
        },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { InteractionGroupTitle(stringResource(R.string.motion_settings)) }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.motion_mode),
                summary = motionModeSummary(state),
                valueText = motionModeLabel(state.motionMode),
                iconRes = R.drawable.baseline_speed_24,
                onClick = { activeDialog = InteractionChoiceDialog.MotionMode },
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.display_refresh_rate),
                summary = state.displayModeSummary,
                valueText = state.displayModeLabel,
                iconRes = R.drawable.ic_baseline_settings_24,
                onClick = { activeDialog = InteractionChoiceDialog.DisplayMode },
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.press_scale_enabled),
                summary = stringResource(R.string.press_scale_enabled_summary),
                checked = state.pressScaleEnabled,
                iconRes = R.drawable.baseline_touch_24,
                enabled = state.motionMode != UiMotionMode.REDUCED,
                onCheckedChange = onPressScaleEnabledChange,
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.low_motion_mode),
                summary = stringResource(R.string.low_motion_mode_summary),
                checked = lowMotionMode,
                iconRes = R.drawable.ic_baseline_settings_24,
                onCheckedChange = onLowMotionModeChange,
            )
        }
    }
}

@Composable
private fun motionModeLabel(mode: UiMotionMode): String = when (mode) {
    UiMotionMode.FOLLOW_SYSTEM -> stringResource(R.string.motion_mode_follow_system)
    UiMotionMode.FULL -> stringResource(R.string.motion_mode_full)
    UiMotionMode.REDUCED -> stringResource(R.string.motion_mode_reduced)
}

@Composable
private fun motionModeSummary(state: InteractionSettingsUiState): String = when (state.motionMode) {
    UiMotionMode.FOLLOW_SYSTEM -> stringResource(R.string.motion_mode_summary_follow_system)
    UiMotionMode.FULL -> stringResource(R.string.motion_mode_summary_full)
    UiMotionMode.REDUCED -> stringResource(R.string.motion_mode_summary_reduced)
}

@Composable
private fun InteractionGroupTitle(title: String) {
    androidx.compose.material3.Text(
        text = title,
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Preview(showBackground = true, widthDp = 420, heightDp = 620)
@Composable
private fun InteractionSettingsScreenPreview() {
    ComponentPreview {
        InteractionSettingsScreen(
            state = InteractionSettingsUiState(
                motionMode = UiMotionMode.FOLLOW_SYSTEM,
                pressScaleEnabled = true,
                animationsEnabled = true,
                displayModeId = 0,
                displayModeLabel = "跟随系统",
                displayModeSummary = "仅当前应用生效，当前 120 Hz",
            ),
            motionModeOptions = listOf(
                "跟随系统" to UiMotionMode.FOLLOW_SYSTEM.key,
                "完整动画" to UiMotionMode.FULL.key,
                "减少动画" to UiMotionMode.REDUCED.key,
            ),
            displayModeOptions = listOf(
                "跟随系统" to "0",
                "120 Hz" to "1",
            ),
            onMotionModeChange = {},
            onPressScaleEnabledChange = {},
            onLowMotionModeChange = {},
            onDisplayModeChange = {},
        )
    }
}
