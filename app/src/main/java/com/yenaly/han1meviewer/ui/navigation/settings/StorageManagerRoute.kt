package com.yenaly.han1meviewer.ui.navigation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.ui.screen.settings.StorageManagerScreen
import com.yenaly.han1meviewer.ui.viewmodel.StorageManagerViewModel

@Composable
fun StorageManagerRouteScreen() {
    val viewModel: StorageManagerViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    StorageManagerScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onClearCache = viewModel::clearCache,
        onClearInvalidRecords = viewModel::clearInvalidRecords,
        onMergeDuplicateRecords = viewModel::mergeDuplicateRecords,
        onDeleteOrphanedFolders = viewModel::deleteOrphanedFolders,
        onDismissMessage = viewModel::dismissMessage,
    )
}