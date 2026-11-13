package com.yenaly.han1meviewer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.StorageManager
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.yenaly_libs.base.YenalyViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageManagerUiState(
    val snapshot: StorageManager.Snapshot? = null,
    val isScanning: Boolean = false,
    val isWorking: Boolean = false,
    val message: String? = null,
)

class StorageManagerViewModel(application: Application) : YenalyViewModel(application) {

    private val dao = DownloadDatabase.instance.hanimeDownloadDao
    private val _uiState = MutableStateFlow(StorageManagerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isScanning || _uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, message = null) }
            try {
                val snapshot = StorageManager.scan(application, dao)
                _uiState.update { it.copy(snapshot = snapshot, isScanning = false) }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        message = operationFailedMessage(error),
                    )
                }
            }
        }
    }

    fun clearCache() = launchAction {
        StorageManager.clearAppCache(application)
        application.getString(R.string.storage_manager_cache_cleared)
    }

    fun clearInvalidRecords() = launchAction {
        val removed = StorageManager.clearInvalidDownloadRecords(application, dao)
        application.resources.getQuantityString(R.plurals.storage_manager_invalid_cleared, removed, removed)
    }

    fun mergeDuplicateRecords() = launchAction {
        val removed = StorageManager.mergeDuplicateDownloadRecords(application, dao)
        application.resources.getQuantityString(R.plurals.storage_manager_duplicates_merged, removed, removed)
    }

    fun deleteOrphanedFolders() = launchAction {
        val removed = StorageManager.deleteOrphanedDownloadFolders(application, dao)
        application.resources.getQuantityString(R.plurals.storage_manager_orphans_deleted, removed, removed)
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun launchAction(action: suspend () -> String) {
        if (_uiState.value.isScanning || _uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, message = null) }
            try {
                val message = action()
                val snapshot = StorageManager.scan(application, dao)
                _uiState.update {
                    it.copy(
                        snapshot = snapshot,
                        isWorking = false,
                        message = message,
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        message = operationFailedMessage(error),
                    )
                }
            }
        }
    }

    private fun operationFailedMessage(error: Throwable): String =
        application.getString(
            R.string.storage_manager_operation_failed,
            error.message.orEmpty(),
        )
}