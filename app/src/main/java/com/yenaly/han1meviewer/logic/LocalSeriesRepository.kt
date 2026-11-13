package com.yenaly.han1meviewer.logic

import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.model.LocalSeriesEpisode
import kotlinx.coroutines.flow.first

/** 从本地数据中查找某集之后的下一集，用于系列连播。 */
object LocalSeriesRepository {

    suspend fun findNextEpisode(videoCode: String): LocalSeriesEpisode? {
        val favorites = DatabaseRepo.Favorite.loadAll().first()
        val history = DatabaseRepo.WatchHistory.loadAll().first()
        val downloads = DatabaseRepo.HanimeDownload.loadAllDownloadedHanime(
            HanimeDownloadEntity.SortedBy.ID,
            ascending = false,
        ).first().map { it.video }

        val series = SeriesOrganizer.organize(
            SeriesOrganizer.buildCandidates(favorites, history, downloads),
        ).firstOrNull { item ->
            item.episodes.any { it.videoCode == videoCode }
        } ?: return null

        val current = series.episodes.firstOrNull { it.videoCode == videoCode } ?: return null
        return series.episodes.firstOrNull { episode ->
            episode.episode > current.episode && !episode.isWatched
        }
    }
}
