package com.yenaly.han1meviewer.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.yenaly.han1meviewer.logic.InteractionPreferences

/**
 * 按压缩放反馈。
 *
 * 需与 clickable / combinedClickable 共享同一个 [interactionSource]：
 * 按压时轻微缩小，松手后弹性回弹，提升交互手感。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.93f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scaleAnimationEnabled = InteractionPreferences.pressScaleAnimationEnabled
    val scale by animateFloatAsState(
        targetValue = if (pressed && scaleAnimationEnabled) pressedScale else 1f,
        animationSpec = if (scaleAnimationEnabled) {
            if (pressed) {
                tween(durationMillis = 90, easing = FastOutSlowInEasing)
            } else {
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                )
            }
        } else {
            snap()
        },
        label = "press-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
