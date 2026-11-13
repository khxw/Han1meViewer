package com.yenaly.han1meviewer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.Preferences

private val HanimeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun HanimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when (val preset = ThemeColorPreset.fromKey(Preferences.themeColor)) {
        ThemeColorPreset.SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicColorScheme(darkTheme)
            } else {
                ThemeColorPreset.DEFAULT.colorScheme(darkTheme)
            }
        }

        else -> preset.colorScheme(darkTheme)
    }

    // AMOLED 純黑模式：僅在深色主題下生效
    val amoled = Preferences.useDarkMode == "always_amoled" && darkTheme

    MaterialTheme(
        colorScheme = if (amoled) colorScheme.toAmoled() else colorScheme,
        typography = Typography(),
        shapes = HanimeShapes,
        content = content,
    )
}
