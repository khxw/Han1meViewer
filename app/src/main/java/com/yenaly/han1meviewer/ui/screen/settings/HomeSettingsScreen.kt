package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.HorizontalCardCountConfig
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SearchGridColumnsConfig
import com.yenaly.han1meviewer.ui.component.ChoiceDialog
import com.yenaly.han1meviewer.ui.component.SettingInfoItem
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.ui.component.SettingSliderItem
import com.yenaly.han1meviewer.ui.component.SettingSwitchItem
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import com.yenaly.han1meviewer.ui.screen.settings.dialog.BlockedTagsDialog
import com.yenaly.han1meviewer.ui.screen.settings.dialog.HomeCategoryLayoutDialog
import com.yenaly.han1meviewer.ui.screen.settings.dialog.HorizontalCardCountDialog
import com.yenaly.han1meviewer.ui.screen.settings.dialog.SearchGridColumnsDialog
import com.yenaly.han1meviewer.ui.screen.settings.dialog.TagManagerDialog
import com.yenaly.han1meviewer.ui.screen.settings.model.HomeSettingsUiState
import com.yenaly.han1meviewer.ui.theme.ThemeColorPreset
import com.yenaly.han1meviewer.util.TagLocalizer

private enum class HomeSettingsChoiceDialog {
    VideoLanguage,
    VideoQuality,
    DarkMode,
    AppLanguage,
    ThemeColor,
    AnnouncementFetchInterval,
}

@Composable
fun HomeSettingsScreen(
    state: HomeSettingsUiState,
    onVideoLanguageChange: (String) -> Unit,
    onVideoQualityChange: (String) -> Unit,
    onDarkModeChange: (String) -> Unit,
    onAllowPipModeChange: (Boolean) -> Unit,
    onAllowResumePlaybackChange: (Boolean) -> Unit,
    onShowPlayedIndicatorChange: (Boolean) -> Unit,
    onClipboardLinkDetectChange: (Boolean) -> Unit,
    onBlockTagsEnabledChange: (Boolean) -> Unit,
    onBlockedTagsChange: (Set<String>) -> Unit,
    onBlockKeywordsEnabledChange: (Boolean) -> Unit,
    onBlockedKeywordsChange: (Set<String>) -> Unit,
    onCommentKeywordsEnabledChange: (Boolean) -> Unit,
    onBlockedCommentKeywordsChange: (Set<String>) -> Unit,
    onSearchArtistIgnoreVideoTypeChange: (Boolean) -> Unit,
    onDisableMobileDataWarningChange: (Boolean) -> Unit,
    onTabletModeChange: (Boolean) -> Unit,
    onUseBottomNavigationChange: (Boolean) -> Unit,
    onUseCompactModeChange: (Boolean) -> Unit,
    onDisableCommentsChange: (Boolean) -> Unit,
    onCollapseDownloadedGroupChange: (Boolean) -> Unit,
    onSearchGridColumnsConfigChange: (SearchGridColumnsConfig) -> Unit,
    onHorizontalCardCountConfigChange: (HorizontalCardCountConfig) -> Unit,
    onUseCIUpdateChannelChange: (Boolean) -> Unit,
    onUseLockScreenChange: (Boolean) -> Unit,
    onThemeColorChange: (String) -> Unit,
    onHomeCategoryPreferencesChange: (List<String>, Set<String>) -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenInteractionSettings: () -> Unit,
    onOpenHKeyframeSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenStorageManager: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onOpenAppLanguageSettings: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onUpdatePopupIntervalDaysChange: (Int) -> Unit,
    onAnnouncementFetchIntervalHoursChange: (Int) -> Unit,
    onOpenApplyDeepLinks: () -> Unit,
    onOpenFakeLauncherIcon: () -> Unit,
    onOpenOpenSourceLicense: () -> Unit,
    onOpenAbout: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onSubmitBug: () -> Unit,
    onOpenForum: () -> Unit,
) {
    var activeDialog by rememberSaveable { mutableStateOf<HomeSettingsChoiceDialog?>(null) }
    var showSearchGridColumnsDialog by rememberSaveable { mutableStateOf(false) }
    var showHorizontalCardCountDialog by rememberSaveable { mutableStateOf(false) }
    var showHomeCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showBlockedTagsDialog by rememberSaveable { mutableStateOf(false) }
    var showBlockedKeywordsDialog by rememberSaveable { mutableStateOf(false) }
    var showBlockedCommentKeywordsDialog by rememberSaveable { mutableStateOf(false) }
    val allTags = remember { TagLocalizer.allLocalizedTags() }

    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.VideoLanguage,
        title = stringResource(R.string.video_language),
        options = listOf(
            stringResource(R.string.traditional_chinese) to "zht",
            stringResource(R.string.simplified_chinese) to "zhs",
        ),
        selectedValue = state.videoLanguage,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onVideoLanguageChange(it)
        },
    )

    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.VideoQuality,
        title = stringResource(R.string.default_video_quilty),
        options = listOf("480P" to "480P", "720P" to "720P", "1080P" to "1080P"),
        selectedValue = state.defaultVideoQuality,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onVideoQualityChange(it)
        },
    )

    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.DarkMode,
        title = stringResource(R.string.dark_theme),
        options = listOf(
            stringResource(R.string.follow_system) to "follow_system",
            stringResource(R.string.always_off) to "always_off",
            stringResource(R.string.always_on) to "always_on",
            stringResource(R.string.always_amoled) to "always_amoled",
        ),
        selectedValue = state.darkMode,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onDarkModeChange(it)
        },
    )

    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.AppLanguage,
        title = stringResource(R.string.app_lang),
        options = listOf(
            stringResource(R.string.follow_system) to "system",
            stringResource(R.string.simplified_chinese) to "zh-rCN",
            stringResource(R.string.traditional_chinese) to "zh",
            stringResource(R.string.japanese_lang) to "ja",
            stringResource(R.string.english_lang) to "en",
        ),
        selectedValue = state.appLanguage,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onOpenAppLanguageSettings(it)
        },
    )

    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.ThemeColor,
        title = stringResource(R.string.theme_color),
        options = ThemeColorPreset.entries
            .filter { state.dynamicColorEnabled || it != ThemeColorPreset.SYSTEM }
            .map { stringResource(it.displayNameRes) to it.key },
        selectedValue = state.themeColorKey,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onThemeColorChange(it)
        },
    )

    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.AnnouncementFetchInterval,
        title = stringResource(R.string.announcement_fetch_interval),
        options = listOf(
            stringResource(R.string.announcement_fetch_interval_hours_format, 3) to "3",
            stringResource(R.string.announcement_fetch_interval_hours_format, 6) to "6",
            stringResource(R.string.announcement_fetch_interval_hours_format, 12) to "12",
            stringResource(R.string.announcement_fetch_interval_hours_format, 23) to "23",
            stringResource(R.string.announcement_fetch_interval_off) to "0",
        ),
        selectedValue = state.announcementFetchIntervalHours.toString(),
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onAnnouncementFetchIntervalHoursChange(it.toInt())
        },
    )

    if (showSearchGridColumnsDialog) {
        SearchGridColumnsDialog(
            initialConfig = state.searchGridColumnsConfig,
            onDismiss = { showSearchGridColumnsDialog = false },
            onConfirm = {
                showSearchGridColumnsDialog = false
                onSearchGridColumnsConfigChange(it)
            },
        )
    }

    if (showHorizontalCardCountDialog) {
        HorizontalCardCountDialog(
            initialConfig = state.horizontalCardCountConfig,
            onDismiss = { showHorizontalCardCountDialog = false },
            onConfirm = {
                showHorizontalCardCountDialog = false
                onHorizontalCardCountConfigChange(it)
            },
        )
    }

    if (showHomeCategoryDialog) {
        HomeCategoryLayoutDialog(
            state = state,
            onDismiss = { showHomeCategoryDialog = false },
            onConfirm = { order, hiddenKeys ->
                showHomeCategoryDialog = false
                onHomeCategoryPreferencesChange(order, hiddenKeys)
            },
        )
    }

    if (showBlockedTagsDialog) {
        TagManagerDialog(
            initialTags = state.blockedTags,
            allTags = allTags,
            onDismiss = { showBlockedTagsDialog = false },
            onConfirm = { tags ->
                showBlockedTagsDialog = false
                onBlockedTagsChange(tags)
            },
        )
    }

    if (showBlockedKeywordsDialog) {
        BlockedTagsDialog(
            initialTags = state.blockedKeywords,
            onDismiss = { showBlockedKeywordsDialog = false },
            onConfirm = { keywords ->
                showBlockedKeywordsDialog = false
                onBlockedKeywordsChange(keywords)
            },
            titleRes = R.string.blocked_keywords_dialog_title,
            summaryRes = R.string.blocked_keywords_dialog_summary,
            emptyRes = R.string.blocked_keywords_empty,
            inputHintRes = R.string.blocked_keywords_input_hint,
            duplicatedRes = R.string.blocked_keywords_duplicated,
        )
    }

    if (showBlockedCommentKeywordsDialog) {
        BlockedTagsDialog(
            initialTags = state.blockedCommentKeywords,
            onDismiss = { showBlockedCommentKeywordsDialog = false },
            onConfirm = { keywords ->
                showBlockedCommentKeywordsDialog = false
                onBlockedCommentKeywordsChange(keywords)
            },
            titleRes = R.string.blocked_comment_keywords_dialog_title,
            summaryRes = R.string.blocked_comment_keywords_dialog_summary,
            emptyRes = R.string.blocked_comment_keywords_empty,
            inputHintRes = R.string.blocked_comment_keywords_input_hint,
            duplicatedRes = R.string.blocked_comment_keywords_duplicated,
        )
    }

    // 底部导航栏模式下为悬浮导航栏预留空间，保证列表末尾可滚至导航栏上方
    val bottomNavReserved = if (Preferences.useBottomNavigation) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp
    } else 0.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 8.dp + bottomNavReserved),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── 外观 ──
        item { SettingsGroupTitle(stringResource(R.string.theme)) }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.dark_theme),
                valueText = state.darkModeLabel,
                iconRes = R.drawable.ic_baseline_moon_24,
                onClick = { activeDialog = HomeSettingsChoiceDialog.DarkMode },
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.theme_color),
                valueText = state.themeColorName,
                iconRes = R.drawable.ic_baseline_theme_24,
                onClick = { activeDialog = HomeSettingsChoiceDialog.ThemeColor },
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.app_lang),
                summary = stringResource(R.string.app_lang_sum),
                valueText = state.appLanguageLabel,
                iconRes = R.drawable.ic_setting_lang,
                onClick = { activeDialog = HomeSettingsChoiceDialog.AppLanguage },
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.horizontal_card_count_title),
                summary = stringResource(R.string.horizontal_card_count_summary),
                valueText = state.horizontalCardCountSummary,
                iconRes = R.drawable.baseline_row_24,
                onClick = { showHorizontalCardCountDialog = true },
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.home_category_layout),
                summary = stringResource(
                    R.string.home_category_layout_summary,
                    state.homeCategoryItems.size - state.hiddenHomeCategoryKeys.size,
                    state.homeCategoryItems.size,
                ),
                iconRes = R.drawable.baseline_sort_24,
                onClick = { showHomeCategoryDialog = true },
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.use_bottom_navigation),
                summary = stringResource(R.string.use_bottom_navigation_summary),
                checked = state.useBottomNavigation,
                iconRes = R.drawable.ic_baseline_home_24,
                onCheckedChange = onUseBottomNavigationChange,
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.compact_mode),
                summary = stringResource(R.string.compact_mode_summary),
                checked = state.useCompactMode,
                iconRes = R.drawable.baseline_grid_24,
                onCheckedChange = onUseCompactModeChange,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.interaction_settings),
                summary = stringResource(R.string.interaction_settings_summary),
                iconRes = R.drawable.baseline_touch_24,
                onClick = onOpenInteractionSettings,
            )
        }

        // ── 播放 ──
        item { SettingsGroupTitle(stringResource(R.string.playback)) }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.video_language),
                valueText = state.videoLanguageLabel,
                iconRes = R.drawable.baseline_simp_to_trad_24,
                onClick = { activeDialog = HomeSettingsChoiceDialog.VideoLanguage },
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.default_video_quilty),
                valueText = state.defaultVideoQuality,
                iconRes = R.drawable.ic_video_quilty,
                onClick = { activeDialog = HomeSettingsChoiceDialog.VideoQuality },
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.allow_pip_title),
                summary = stringResource(R.string.allow_pip_disc),
                checked = state.allowPipMode,
                iconRes = R.drawable.ic_pip_mode,
                onCheckedChange = onAllowPipModeChange,
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.resume_playback_title),
                summary = stringResource(R.string.resume_playback_summary),
                checked = state.allowResumePlayback,
                iconRes = R.drawable.ic_baseline_skip_24,
                onCheckedChange = onAllowResumePlaybackChange,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.player_settings),
                iconRes = R.drawable.ic_baseline_play_circle_outline_24,
                onClick = onOpenPlayerSettings,
            )
        }

        // ── 内容与浏览 ──
        item { SettingsGroupTitle(stringResource(R.string.content_and_browse)) }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.show_played_indicator),
                summary = stringResource(R.string.show_played_indicator_summary),
                checked = state.showPlayedIndicator,
                iconRes = R.drawable.ic_baseline_history_24,
                onCheckedChange = onShowPlayedIndicatorChange,
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.clipboard_link_detect),
                summary = stringResource(R.string.clipboard_link_detect_summary),
                checked = state.clipboardLinkDetect,
                iconRes = R.drawable.ic_baseline_scan_24,
                onCheckedChange = onClipboardLinkDetectChange,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.h_keyframe_settings),
                iconRes = R.drawable.baseline_h_24,
                onClick = onOpenHKeyframeSettings,
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.disable_comments_title),
                summary = stringResource(R.string.disable_comments_sum),
                checked = state.disableComments,
                iconRes = R.drawable.ic_comments,
                onCheckedChange = onDisableCommentsChange,
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.search_artist_ignore_video_type),
                summary = stringResource(R.string.search_artist_ignore_video_type_summary),
                checked = state.searchArtistIgnoreVideoType,
                iconRes = R.drawable.baseline_prohibit_24,
                onCheckedChange = onSearchArtistIgnoreVideoTypeChange,
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.tablet_mode),
                summary = stringResource(R.string.tablet_mode_summary),
                checked = state.tabletMode,
                iconRes = R.drawable.ic_baseline_tablet_24,
                onCheckedChange = onTabletModeChange,
            )
        }
        if (state.tabletMode) {
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.search_grid_columns_title),
                    summary = stringResource(R.string.search_grid_columns_summary),
                    valueText = state.searchGridColumnsSummary,
                    iconRes = R.drawable.baseline_grid_24,
                    onClick = { showSearchGridColumnsDialog = true },
                )
            }
        }

        // ── 内容过滤 ──
        item { SettingsGroupTitle(stringResource(R.string.content_filter)) }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.block_tags_title),
                summary = stringResource(R.string.block_tags_summary),
                checked = state.blockTagsEnabled,
                iconRes = R.drawable.baseline_prohibit_24,
                onCheckedChange = onBlockTagsEnabledChange,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.blocked_tags_manage),
                summary = pluralStringResource(R.plurals.blocked_tags_manage_summary, state.blockedTagsCount, state.blockedTagsCount),
                iconRes = R.drawable.ic_baseline_clear_all_24,
                onClick = { showBlockedTagsDialog = true },
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.block_keywords_title),
                summary = stringResource(R.string.block_keywords_summary),
                checked = state.blockKeywordsEnabled,
                iconRes = R.drawable.baseline_prohibit_24,
                onCheckedChange = onBlockKeywordsEnabledChange,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.blocked_keywords_manage),
                summary = pluralStringResource(R.plurals.blocked_keywords_manage_summary, state.blockedKeywordsCount, state.blockedKeywordsCount),
                iconRes = R.drawable.ic_baseline_clear_all_24,
                onClick = { showBlockedKeywordsDialog = true },
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.comment_keywords_title),
                summary = stringResource(R.string.comment_keywords_summary),
                checked = state.commentKeywordsEnabled,
                iconRes = R.drawable.baseline_prohibit_24,
                onCheckedChange = onCommentKeywordsEnabledChange,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.blocked_comment_keywords_manage),
                summary = pluralStringResource(R.plurals.blocked_comment_keywords_manage_summary, state.blockedCommentKeywordsCount, state.blockedCommentKeywordsCount),
                iconRes = R.drawable.ic_baseline_clear_all_24,
                onClick = { showBlockedCommentKeywordsDialog = true },
            )
        }

        // ── 下载 ──
        item { SettingsGroupTitle(stringResource(R.string.download)) }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.download_settings),
                iconRes = R.drawable.ic_baseline_download_24,
                onClick = onOpenDownloadSettings,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.storage_manager),
                summary = stringResource(R.string.storage_manager_summary),
                iconRes = R.drawable.baseline_storage_24,
                onClick = onOpenStorageManager,
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.collapse_downloaded_groups),
                summary = stringResource(R.string.collapse_downloaded_groups_summary),
                checked = state.collapseDownloadedGroup,
                iconRes = R.drawable.ic_baseline_fold_24,
                onCheckedChange = onCollapseDownloadedGroupChange,
            )
        }

        // ── 网络 ──
        item { SettingsGroupTitle(stringResource(R.string.network)) }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.network_settings),
                iconRes = R.drawable.ic_baseline_language_24,
                onClick = onOpenNetworkSettings,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.apply_deep_links),
                summary = stringResource(R.string.apply_deep_links_summary),
                iconRes = R.drawable.baseline_add_link_24,
                onClick = onOpenApplyDeepLinks,
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.disable_mobile_data_warning),
                summary = stringResource(R.string.disable_mobile_data_warning_summary),
                checked = state.disableMobileDataWarning,
                iconRes = R.drawable.baseline_mobile_data_24,
                onCheckedChange = onDisableMobileDataWarningChange,
            )
        }

        // ── 更新 ──
        item { SettingsGroupTitle(stringResource(R.string.update)) }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.check_update),
                summary = state.updateSummary,
                iconRes = R.drawable.ic_baseline_update_24,
                onClick = onCheckUpdate,
            )
        }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.use_ci_update_channel),
                checked = state.useCIUpdateChannel,
                iconRes = R.drawable.ic_setting_ci,
                onCheckedChange = onUseCIUpdateChannelChange,
            )
        }
        item {
            SettingSliderItem(
                title = stringResource(R.string.update_popup_interval_days),
                summary = state.updatePopupIntervalSummary,
                value = state.updatePopupIntervalDays,
                valueRange = 0..30,
                iconRes = R.drawable.ic_clock,
                onValueChange = onUpdatePopupIntervalDaysChange,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.announcement_fetch_interval),
                valueText = state.announcementFetchIntervalLabel,
                iconRes = R.drawable.ic_baseline_campaign_24,
                onClick = { activeDialog = HomeSettingsChoiceDialog.AnnouncementFetchInterval },
            )
        }

        // ── 隐私与安全 ──
        item { SettingsGroupTitle(stringResource(R.string.privacy)) }
        item {
            SettingSwitchItem(
                title = stringResource(R.string.use_lock_screen),
                summary = stringResource(R.string.use_lock_screen_sum),
                checked = state.useLockScreen,
                iconRes = R.drawable.ic_setting_applock,
                onCheckedChange = onUseLockScreenChange,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.fake_app_icon),
                summary = stringResource(R.string.select_fake_icon),
                valueText = state.fakeLauncherIconName,
                iconRes = R.drawable.ic_baseline_mask,
                onClick = onOpenFakeLauncherIcon,
            )
        }

        // ── 数据与关于 ──
        item { SettingsGroupTitle(stringResource(R.string.data_and_about)) }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.backup_export_title),
                summary = stringResource(R.string.backup_export_summary),
                iconRes = R.drawable.baseline_backup_24,
                onClick = onExportBackup,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.backup_import_title),
                summary = stringResource(R.string.backup_import_summary),
                iconRes = R.drawable.baseline_restore_24,
                onClick = onImportBackup,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.submit_bug),
                summary = stringResource(R.string.submit_bug_summary),
                iconRes = R.drawable.baseline_bug_report_24,
                onClick = onSubmitBug,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.forum),
                summary = stringResource(R.string.forum_summary),
                iconRes = R.drawable.baseline_forum_24,
                onClick = onOpenForum,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.open_source_license),
                iconRes = R.drawable.ic_oss,
                onClick = onOpenOpenSourceLicense,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.about),
                summary = state.versionSummary,
                iconRes = R.drawable.ic_baseline_info_24,
                onClick = onOpenAbout,
            )
        }
    }
}

@Composable
private fun SettingsGroupTitle(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 1000)
@Composable
private fun HomeSettingsScreenPreview() {
    ComponentPreview {
        HomeSettingsScreen(
            state = HomeSettingsUiState(
                videoLanguage = "zhs",
                videoLanguageLabel = "简体中文",
                defaultVideoQuality = "1080P",
                darkMode = "follow_system",
                darkModeLabel = "跟随系统",
                appLanguage = "system",
                appLanguageLabel = "跟随系统",
                allowPipMode = true,
                allowResumePlayback = true,
                showPlayedIndicator = true,
                clipboardLinkDetect = true,
                blockTagsEnabled = true,
                blockedTags = setOf("萝莉", "催眠"),
                blockedTagsCount = 2,
                blockKeywordsEnabled = true,
                blockedKeywords = setOf("NTR"),
                blockedKeywordsCount = 1,
                searchArtistIgnoreVideoType = false,
                disableMobileDataWarning = false,
                tabletMode = false,
                disableComments = false,
                collapseDownloadedGroup = false,
                useDynamicColor = true,
                useCIUpdateChannel = false,
                useLockScreen = false,
                fakeLauncherIconName = "Han1meViewer",
                updateSummary = "已經是最新版本！",
                        versionSummary = "當前版本：v1.0.0",
                updatePopupIntervalSummary = "7天\n最近還沒跳出過更新視窗哦",
                updatePopupIntervalDays = 7,
                announcementFetchIntervalHours = 6,
                announcementFetchIntervalLabel = "6 小時",
                dynamicColorEnabled = true,
                themeColorKey = "default",
                themeColorName = "預設（暖紅）",
                searchGridColumnsSummary = "2 / 3 / 4 / 5",
                searchGridColumnsConfig = SearchGridColumnsConfig(),
                horizontalCardCountSummary = "1.5 / 2.1 / 4.1 / 5.1",
                horizontalCardCountConfig = HorizontalCardCountConfig(),
                homeCategoryItems = emptyList(),
                homeCategoryOrder = emptyList(),
                hiddenHomeCategoryKeys = emptySet(),
                useAvHomeCategoryTitles = false,
                useBottomNavigation = false,
                useCompactMode = false,
                commentKeywordsEnabled = true,
                blockedCommentKeywords = setOf("催婚"),
                blockedCommentKeywordsCount = 1,
            ),
            onVideoLanguageChange = {},
            onVideoQualityChange = {},
            onDarkModeChange = {},
            onAllowPipModeChange = {},
            onAllowResumePlaybackChange = {},
            onShowPlayedIndicatorChange = {},
            onClipboardLinkDetectChange = {},
            onBlockTagsEnabledChange = {},
            onBlockedTagsChange = {},
            onBlockKeywordsEnabledChange = {},
            onBlockedKeywordsChange = {},
            onCommentKeywordsEnabledChange = {},
            onBlockedCommentKeywordsChange = {},
            onSearchArtistIgnoreVideoTypeChange = {},
            onDisableMobileDataWarningChange = {},
            onTabletModeChange = {},
            onUseBottomNavigationChange = {},
            onUseCompactModeChange = {},
            onDisableCommentsChange = {},
            onCollapseDownloadedGroupChange = {},
            onSearchGridColumnsConfigChange = {},
            onHorizontalCardCountConfigChange = {},
            onUseCIUpdateChannelChange = {},
            onUseLockScreenChange = {},
            onThemeColorChange = {},
            onHomeCategoryPreferencesChange = { _, _ -> },
            onOpenPlayerSettings = {},
            onOpenInteractionSettings = {},
            onOpenHKeyframeSettings = {},
            onOpenDownloadSettings = {},
            onOpenStorageManager = {},
            onOpenNetworkSettings = {},
            onOpenAppLanguageSettings = {},
            onCheckUpdate = {},
            onUpdatePopupIntervalDaysChange = {},
            onAnnouncementFetchIntervalHoursChange = {},
            onOpenApplyDeepLinks = {},
            onOpenFakeLauncherIcon = {},
            onOpenOpenSourceLicense = {},
            onOpenAbout = {},
            onExportBackup = {},
            onImportBackup = {},
            onSubmitBug = {},
            onOpenForum = {},
        )
    }
}
