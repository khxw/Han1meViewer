package com.yenaly.han1meviewer.ui.view.video

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.appbar.AppBarLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout

class VideoPlayerAppBarBehavior @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppBarLayout.Behavior(context, attrs) {

    var disableScroll = false

    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: AppBarLayout,
        ev: MotionEvent
    ): Boolean {
        return if (disableScroll) false else super.onInterceptTouchEvent(parent, child, ev)
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        return if (disableScroll) false else super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, axes, type)
    }

    override fun onRequestChildRectangleOnScreen(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        rectangle: Rect,
        immediate: Boolean
    ): Boolean {
        // 播放时（滚动禁用）阻断聚焦滚动的收起请求：输入框聚焦会持续请求把
        // 自身滚入可视区，默认行为是收起 AppBar 挤出空间，与展开锁定每帧对抗
        // 造成界面抽搐。输入框可见性由评论区底部 padding 保证，此处直接阻断。
        return if (disableScroll) true
        else super.onRequestChildRectangleOnScreen(coordinatorLayout, child, rectangle, immediate)
    }
}
