package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.ui.component.SettingSliderItem
import com.yenaly.han1meviewer.ui.component.SettingSwitchItem
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn
import com.yenaly.han1meviewer.ui.preview.ComponentPreview

data class DownloadSettingsUiState(
    val downloadPathSummary: String,
    val downloadCountLimit: Int,
    val downloadCountLimitSummary: String,
    val downloadSpeedLimitIndex: Int,
    val downloadSpeedLimitSummary: String,
    val downloadWifiOnly: Boolean,
    val autoDiscoveryEnabled: Boolean,
)

@Composable
fun DownloadSettingsScreen(
    state: DownloadSettingsUiState,
    maxDownloadCountLimit: Int,
    maxDownloadSpeedLimitIndex: Int,
    onOpenDownloadPath: () -> Unit,
    onRestoreDefaultPath: () -> Unit,
    onImportDownloadedFiles: () -> Unit,
    onDownloadCountLimitChange: (Int) -> Unit,
    onDownloadSpeedLimitChange: (Int) -> Unit,
    onDownloadWifiOnlyChange: (Boolean) -> Unit,
    onAutoDiscoveryEnabledChange: (Boolean) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            SettingNavigationItem(
                title = stringResource(R.string.download_path),
                summary = state.downloadPathSummary,
                iconRes = R.drawable.baseline_path_24,
                onClick = onOpenDownloadPath,
            )
        }

        item {
            SettingNavigationItem(
                title = stringResource(R.string.pref_export_downloads_title),
                summary = stringResource(R.string.pref_export_downloads_summary),
                iconRes = R.drawable.baseline_export_24,
                onClick = onImportDownloadedFiles,
            )
        }

        item {
            SettingSliderItem(
                title = stringResource(R.string.download_count_limit),
                summary = state.downloadCountLimitSummary,
                value = state.downloadCountLimit,
                valueRange = 0..maxDownloadCountLimit,
                iconRes = R.drawable.baseline_count_24,
                onValueChange = onDownloadCountLimitChange,
            )
        }

        item {
            SettingSliderItem(
                title = stringResource(R.string.download_speed_limit),
                summary = state.downloadSpeedLimitSummary,
                value = state.downloadSpeedLimitIndex,
                valueRange = 0..maxDownloadSpeedLimitIndex,
                iconRes = R.drawable.baseline_speed2_24,
                onValueChange = onDownloadSpeedLimitChange,
            )
        }

        item {
            SettingSwitchItem(
                title = stringResource(R.string.download_auto_discovery),
                summary = stringResource(R.string.download_auto_discovery_summary),
                checked = state.autoDiscoveryEnabled,
                iconRes = R.drawable.baseline_screen_rotation_24,
                onCheckedChange = onAutoDiscoveryEnabledChange,
            )
        }

        item {
            SettingSwitchItem(
                title = stringResource(R.string.download_wifi_only),
                summary = stringResource(R.string.download_wifi_only_summary),
                checked = state.downloadWifiOnly,
                iconRes = R.drawable.baseline_mobile_data_24,
                onCheckedChange = onDownloadWifiOnlyChange,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DownloadSettingsScreenPreview() {
    ComponentPreview {
        DownloadSettingsScreen(
            state = DownloadSettingsUiState(
                downloadPathSummary = "/storage/emulated/0/Android/data/.../files",
                downloadCountLimit = 2,
                downloadCountLimitSummary = "2",
                downloadSpeedLimitIndex = 0,
                downloadSpeedLimitSummary = "无限制",
                downloadWifiOnly = false,
                autoDiscoveryEnabled = true,
            ),
            maxDownloadCountLimit = 10,
            maxDownloadSpeedLimitIndex = 5,
            onOpenDownloadPath = {},
            onRestoreDefaultPath = {},
            onImportDownloadedFiles = {},
            onDownloadCountLimitChange = {},
            onDownloadSpeedLimitChange = {},
            onDownloadWifiOnlyChange = {},
            onAutoDiscoveryEnabledChange = {},
        )
    }
}
