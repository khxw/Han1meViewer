@file:JvmName("SnackBarUtil")

package com.yenaly.yenaly_libs.utils

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import kotlin.math.roundToInt

@JvmOverloads
inline fun Activity.showSnackBar(
    message: CharSequence,
    length: Int = Snackbar.LENGTH_SHORT,
    view: View = findViewById(android.R.id.content),
    extraBottomMarginDp: Float = 0f,
    action: Snackbar.() -> Unit = {}
) {
    val snackbar = Snackbar.make(view, message, length).apply(action)
    if (extraBottomMarginDp > 0f) {
        // 抬升 Snackbar，避开悬浮底部导航栏等覆盖层
        val layoutParams = snackbar.view.layoutParams
        if (layoutParams is ViewGroup.MarginLayoutParams) {
            val density = view.resources.displayMetrics.density
            layoutParams.bottomMargin += (extraBottomMarginDp * density).roundToInt()
            snackbar.view.layoutParams = layoutParams
        }
    }
    snackbar.show()
}

@JvmOverloads
inline fun Activity.showSnackBar(
    @StringRes message: Int,
    length: Int = Snackbar.LENGTH_SHORT,
    view: View = findViewById(android.R.id.content),
    extraBottomMarginDp: Float = 0f,
    action: Snackbar.() -> Unit = {}
) {
    val snackbar = Snackbar.make(view, message, length).apply(action)
    if (extraBottomMarginDp > 0f) {
        // 抬升 Snackbar，避开悬浮底部导航栏等覆盖层
        val layoutParams = snackbar.view.layoutParams
        if (layoutParams is ViewGroup.MarginLayoutParams) {
            val density = view.resources.displayMetrics.density
            layoutParams.bottomMargin += (extraBottomMarginDp * density).roundToInt()
            snackbar.view.layoutParams = layoutParams
        }
    }
    snackbar.show()
}

@JvmOverloads
inline fun Fragment.showSnackBar(
    message: CharSequence,
    length: Int = Snackbar.LENGTH_SHORT,
    view: View = requireView(),
    action: Snackbar.() -> Unit = {}
) {
    Snackbar.make(view, message, length).apply(action).show()
}

@JvmOverloads
inline fun Fragment.showSnackBar(
    @StringRes message: Int,
    length: Int = Snackbar.LENGTH_SHORT,
    view: View = requireView(),
    action: Snackbar.() -> Unit = {}
) {
    Snackbar.make(view, message, length).apply(action).show()
}


