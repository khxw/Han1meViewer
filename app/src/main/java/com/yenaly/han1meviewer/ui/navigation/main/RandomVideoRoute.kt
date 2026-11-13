package com.yenaly.han1meviewer.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.ui.screen.home.RandomVideoScreen
import com.yenaly.han1meviewer.ui.viewmodel.RandomVideoViewModel

/**
 * 随机视频路由
 *
 * @author Yenaly Liew
 */
@Composable
fun RandomVideoRouteScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
) {
    val viewModel: RandomVideoViewModel = viewModel()
    RandomVideoScreen(
        viewModel = viewModel,
        onBack = onBack,
        onOpenVideo = onNavigateToVideo,
    )
}
