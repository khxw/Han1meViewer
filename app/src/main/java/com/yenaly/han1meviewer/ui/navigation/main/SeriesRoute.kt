package com.yenaly.han1meviewer.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.ui.screen.home.SeriesScreen
import com.yenaly.han1meviewer.ui.viewmodel.SeriesViewModel

/** 自动系列归并路由。 */
@Composable
fun SeriesRouteScreen(
    onBack: () -> Unit,
    onOpenEpisode: (String, String?) -> Unit,
    onSearchMissingEpisode: (String, Int) -> Unit,
) {
    val viewModel: SeriesViewModel = viewModel()
    SeriesScreen(
        viewModel = viewModel,
        onBack = onBack,
        onOpenEpisode = onOpenEpisode,
        onSearchMissingEpisode = onSearchMissingEpisode,
    )
}
