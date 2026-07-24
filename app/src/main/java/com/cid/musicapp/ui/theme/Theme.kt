package com.cid.musicapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MusicAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: Color = Color(0xFF7C4DFF),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = accentColor,
            background = BackgroundDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark
        )
    } else {
        lightColorScheme(primary = accentColor)
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
