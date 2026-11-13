package com.yenaly.han1meviewer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.SeriesOrganizer
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.model.LocalSeries
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.yenaly_libs.base.YenalyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** 从本地收藏、观看历史和已下载视频中自动归并系列。 */
class SeriesViewModel(application: Application) : YenalyViewModel(application) {

    private val _uiState =
        MutableStateFlow<PageLoadingState<List<LocalSeries>>>(PageLoadingState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                DatabaseRepo.Favorite.loadAll(),
                DatabaseRepo.WatchHistory.loadAll(),
                DatabaseRepo.HanimeDownload.loadAllDownloadedHanime(
                    HanimeDownloadEntity.SortedBy.ID,
                    ascending = false,
                ),
            ) { favorites, history, downloads ->
                SeriesOrganizer.organize(
                    SeriesOrganizer.buildCandidates(
                        favorites = favorites,
                        history = history,
                        downloads = downloads.map { it.video },
                    ),
                )
            }
                .flowOn(Dispatchers.Default)
                .catch { throwable ->
                    _uiState.value = PageLoadingState.Error(throwable)
                }
                .collect { series ->
                    _uiState.value = PageLoadingState.Success(series)
                }
        }
    }
}
