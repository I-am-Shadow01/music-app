package com.cid.musicapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.cid.musicapp.config.AccentColor

@Composable
fun MusicAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: AccentColor = AccentColor.PURPLE,
    content: @Composable () -> Unit
) {
    val palette = accentColor.swatchColors()

    val colors = if (darkTheme) {
        darkColorScheme(
            primary = palette.dark,
            background = BackgroundDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark
        )
    } else {
        lightColorScheme(primary = palette.light)
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
