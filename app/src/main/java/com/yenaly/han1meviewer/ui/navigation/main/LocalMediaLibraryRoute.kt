package com.yenaly.han1meviewer.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.ui.screen.home.LocalMediaLibraryScreen
import com.yenaly.han1meviewer.ui.viewmodel.LocalMediaLibraryViewModel

@Composable
fun LocalMediaLibraryRouteScreen(
    onBack: () -> Unit,
    onNavigateToLocalVideo: (String, String?) -> Unit,
) {
    val viewModel: LocalMediaLibraryViewModel = viewModel()
    LocalMediaLibraryScreen(
        viewModel = viewModel,
        onBack = onBack,
        onOpenLocalVideo = onNavigateToLocalVideo,
    )
}
