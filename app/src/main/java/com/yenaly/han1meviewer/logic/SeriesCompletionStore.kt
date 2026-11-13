package com.yenaly.han1meviewer.logic

import androidx.core.content.edit
import com.yenaly.han1meviewer.Preferences

/** 系列补全向导中被标记为“不存在”的缺失集，按系列本地持久化。 */
object SeriesCompletionStore {
    private const val KEY_IGNORED_SERIES_EPISODES = "ignored_series_episodes"

    fun ignoredEpisodes(seriesId: String): Set<Int> {
        return Preferences.preferenceSp
            .getStringSet(KEY_IGNORED_SERIES_EPISODES, emptySet())
            .orEmpty()
            .asSequence()
            .mapNotNull { token ->
                val separator = token.lastIndexOf('#')
                if (separator <= 0 || token.substring(0, separator) != seriesId) {
                    null
                } else {
                    token.substring(separator + 1).toIntOrNull()
                }
            }
            .toSet()
    }

    fun setIgnored(seriesId: String, episode: Int, ignored: Boolean) {
        val token = "$seriesId#$episode"
        val current = Preferences.preferenceSp
            .getStringSet(KEY_IGNORED_SERIES_EPISODES, emptySet())
            .orEmpty()
            .toMutableSet()

        if (ignored) current.add(token) else current.remove(token)

        Preferences.preferenceSp.edit {
            putStringSet(KEY_IGNORED_SERIES_EPISODES, current)
        }
    }
}
