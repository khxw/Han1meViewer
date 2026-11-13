package com.yenaly.han1meviewer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.model.DeviceVideoItem
import com.yenaly.han1meviewer.util.DeviceVideoScanner
import com.yenaly.han1meviewer.util.LocalVideoAvailability
import com.yenaly.yenaly_libs.base.YenalyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocalMediaLibraryViewModel(application: Application) : YenalyViewModel(application) {

    val appDownloads = DatabaseRepo.HanimeDownload.loadAllDownloadedHanime(
        sortedBy = HanimeDownloadEntity.SortedBy.TITLE,
        ascending = true,
    )
        .map { list ->
            list.map { it.video }
                .filter { LocalVideoAvailability.isAvailable(getApplication(), it.videoUri) }
        }
        .catch { it.printStackTrace() }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _deviceVideos = MutableStateFlow<List<DeviceVideoItem>>(emptyList())
    val deviceVideos = _deviceVideos.asStateFlow()

    fun refreshDeviceVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            _deviceVideos.value = DeviceVideoScanner.scan(getApplication())
        }
    }
}
