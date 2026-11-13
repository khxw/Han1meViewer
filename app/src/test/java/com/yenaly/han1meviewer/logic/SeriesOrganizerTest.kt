package com.yenaly.han1meviewer.logic

import com.yenaly.han1meviewer.logic.model.LocalSeries
import com.yenaly.han1meviewer.logic.model.LocalSeriesEpisode
import com.yenaly.han1meviewer.logic.model.SeriesCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesOrganizerTest {

    @Test
    fun groupsEpisodesAndFindsMissingNumbers() {
        val series = SeriesOrganizer.organize(
            listOf(
                candidate("a", "【新作】小悪魔の補習計画 - 第1話"),
                candidate("c", "【新作】小悪魔の補習計画 - 第3話"),
            ),
        )

        assertEquals(1, series.size)
        assertEquals(listOf(1, 3), series.single().episodes.map { it.episode })
        assertEquals(listOf(2), series.single().missingEpisodes)
        assertEquals(1, series.single().nextEpisode?.episode)
    }

    @Test
    fun parsesChineseFullWidthAndEnglishMarkers() {
        assertEquals(12, SeriesOrganizer.parseTitle("作品 第１２話")?.episode)
        assertEquals(7, SeriesOrganizer.parseTitle("Series EP. 7")?.episode)
        assertEquals(2, SeriesOrganizer.parseTitle("Series - Part 2")?.episode)
        assertEquals(2, SeriesOrganizer.parseTitle("Series 後篇")?.episode)
    }

    @Test
    fun keepsAllThreeSpecialMarkers() {
        val series = SeriesOrganizer.organize(
            listOf(
                candidate("a", "作品 前篇"),
                candidate("b", "作品 中篇"),
                candidate("c", "作品 後篇"),
            ),
        )

        assertEquals(1, series.size)
        assertEquals(listOf(1, 2, 3), series.single().episodes.map { it.episode })
        assertTrue(series.single().missingEpisodes.isEmpty())
    }

    @Test
    fun treatsNinetyPercentProgressAsWatched() {
        val episode = LocalSeriesEpisode(
            videoCode = "a",
            title = "作品 第1话",
            coverUrl = "",
            episode = 1,
            progress = 90L,
            duration = 100L,
            isCompleted = false,
            isFavorite = false,
            isDownloaded = false,
            localUri = null,
        )
        val series = LocalSeries(
            id = "series",
            title = "作品",
            coverUrl = "",
            episodes = listOf(episode),
            missingEpisodes = emptyList(),
            latestTimestamp = 0L,
        )

        assertTrue(episode.isWatched)
        assertEquals(1, series.completedCount)
        assertNull(series.nextEpisode)
    }

    @Test
    fun ignoresStandaloneVideos() {
        val result = SeriesOrganizer.organize(
            listOf(
                candidate("a", "普通单集作品 第1话"),
                candidate("b", "另一部普通作品"),
            ),
        )

        assertTrue(result.isEmpty())
        assertNull(SeriesOrganizer.parseTitle("普通作品"))
    }

    private fun candidate(code: String, title: String) = SeriesCandidate(
        videoCode = code,
        title = title,
        coverUrl = "",
        timestamp = 0L,
    )
}
