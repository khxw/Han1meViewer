package com.yenaly.han1meviewer.logic

import androidx.core.content.edit
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.model.RandomVideoOptions
import com.yenaly.han1meviewer.logic.model.RandomVideoSource
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys

object RandomVideoPreferences {

    fun loadOptions(): RandomVideoOptions {
        val prefs = Preferences.preferenceSp
        val sources = RandomVideoSource.parseKeys(
            prefs.getString(SettingsPreferenceKeys.RANDOM_VIDEO_SOURCE, null)
        ).ifEmpty { setOf(RandomVideoSource.LATEST_HANIME) }
        return RandomVideoOptions(
            sources = sources,
            excludeWatched = prefs.getBoolean(
                SettingsPreferenceKeys.RANDOM_VIDEO_EXCLUDE_WATCHED,
                false,
            ),
            excludeDownloaded = prefs.getBoolean(
                SettingsPreferenceKeys.RANDOM_VIDEO_EXCLUDE_DOWNLOADED,
                false,
            ),
            excludeFavorited = prefs.getBoolean(
                SettingsPreferenceKeys.RANDOM_VIDEO_EXCLUDE_FAVORITED,
                false,
            ),
            avoidRecent = prefs.getBoolean(
                SettingsPreferenceKeys.RANDOM_VIDEO_AVOID_RECENT,
                true,
            ),
        )
    }

    fun saveOptions(options: RandomVideoOptions) {
        Preferences.preferenceSp.edit {
            putString(
                SettingsPreferenceKeys.RANDOM_VIDEO_SOURCE,
                options.sources.joinToString(",") { it.key },
            )
            putBoolean(
                SettingsPreferenceKeys.RANDOM_VIDEO_EXCLUDE_WATCHED,
                options.excludeWatched,
            )
            putBoolean(
                SettingsPreferenceKeys.RANDOM_VIDEO_EXCLUDE_DOWNLOADED,
                options.excludeDownloaded,
            )
            putBoolean(
                SettingsPreferenceKeys.RANDOM_VIDEO_EXCLUDE_FAVORITED,
                options.excludeFavorited,
            )
            putBoolean(SettingsPreferenceKeys.RANDOM_VIDEO_AVOID_RECENT, options.avoidRecent)
        }
    }

    fun loadRecentVideoCodes(): List<String> =
        Preferences.preferenceSp.getString(
            SettingsPreferenceKeys.RANDOM_VIDEO_RECENT_CODES,
            null,
        )
            ?.split(',')
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotEmpty)

    fun saveRecentVideoCodes(codes: List<String>) {
        Preferences.preferenceSp.edit {
            putString(
                SettingsPreferenceKeys.RANDOM_VIDEO_RECENT_CODES,
                codes.distinct().joinToString(","),
            )
        }
    }
}
