package com.yenaly.han1meviewer.ui.navigation.settings

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.logic.log.HLumberjack
import com.yenaly.han1meviewer.ui.screen.settings.NetworkDiagnosticsScreen
import com.yenaly.han1meviewer.ui.viewmodel.NetworkDiagnosticsViewModel

@Composable
fun NetworkDiagnosticsRouteScreen() {
    val viewModel: NetworkDiagnosticsViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    NetworkDiagnosticsScreen(
        state = state,
        onRunDiagnostics = viewModel::runDiagnostics,
        onExportLogs = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Han1meViewer Network Diagnostics")
                putExtra(Intent.EXTRA_TEXT, HLumberjack.exportText())
            }
            context.startActivity(Intent.createChooser(intent, null))
        },
    )
}
