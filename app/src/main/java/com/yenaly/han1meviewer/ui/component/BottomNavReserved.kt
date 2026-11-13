package com.yenaly.han1meviewer.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.yenaly_libs.utils.findActivityOrNull
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 悬浮底部导航栏整体高度（dp，不含系统导航条 inset）：
 * MainActivityScaffold 中 NavigationBar(60dp) + 上下各 8dp 空隙。
 */
const val FLOATING_BOTTOM_NAV_HEIGHT: Float = 76f

/**
 * 底部导航栏模式下且当前页面显示导航栏时，底部悬浮控件（Snackbar、批量操作栏、评论输入栏等）
 * 需要抬升到悬浮导航栏上方的间距（导航栏高度 + 8dp 视觉空隙）；
 * 全局关闭导航栏或当前页面不显示导航栏时返回 0。
 */
@Composable
fun bottomNavReservedSpacing(): Dp {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivityOrNull<MainActivity>() }
    val bottomNavVisible by remember(activity) {
        activity?.bottomNavVisibleState ?: MutableStateFlow(true)
    }.collectAsStateWithLifecycle()
    return if (Preferences.useBottomNavigation && bottomNavVisible) {
        (FLOATING_BOTTOM_NAV_HEIGHT + 8).dp
    } else 0.dp
}

/**
 * 感知底部导航栏的 Snackbar 宿主：
 * 开启悬浮导航栏时自动把 Snackbar 抬升到导航栏上方，避免被遮挡。
 */
@Composable
fun BottomNavAwareSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(bottom = bottomNavReservedSpacing()),
    )
}
