package com.yenaly.han1meviewer.ui.navigation

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

fun NavController.canNavigateSafely(): Boolean {
    val currentState = currentBackStackEntry?.lifecycle?.currentState
    Log.i("nav_state", currentState?.toString() ?: "BackStack is empty (null)")
    return currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true
}

fun <T : Any> NavController.navigateSafely(
    route: T,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (!canNavigateSafely()) return
    navigate(route) {
        launchSingleTop = false
        builder()
    }
}

fun NavController.popBackStackSafely(): Boolean {
    val currentEntry = currentBackStackEntry ?: return false
    // 返回动画期间继续点击时，不能把起始页也弹出，否则 NavHost 会变成空栈并显示黑屏。
    if (currentEntry.destination.id == graph.startDestinationId) return false
    if (currentEntry.lifecycle.currentState != Lifecycle.State.RESUMED) return false
    if (previousBackStackEntry == null) return false
    return popBackStack()
}
