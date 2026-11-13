package com.yenaly.han1meviewer.ui.navigation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.ui.screen.settings.NetworkDiagnosticsScreen
import com.yenaly.han1meviewer.ui.viewmodel.NetworkDiagnosticsViewModel

@Composable
fun NetworkDiagnosticsRouteScreen() {
    val viewModel: NetworkDiagnosticsViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    NetworkDiagnosticsScreen(
        state = state,
        onRunDiagnostics = viewModel::runDiagnostics,
    )
}