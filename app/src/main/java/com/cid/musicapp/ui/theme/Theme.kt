package com.cid.musicapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cid.musicapp.config.AccentColor

private fun accentColorFor(accent: AccentColor): Pair<Color, Color> = when (accent) {
    // Pair(สีสำหรับธีมมืด, สีสำหรับธีมสว่าง) — โทนมืดสว่างกว่าเดิมเล็กน้อยให้ตัดกับพื้นหลังเข้ม
    AccentColor.PURPLE -> Color(0xFFB388FF) to Color(0xFF7C4DFF)
    AccentColor.BLUE -> Color(0xFF82B1FF) to Color(0xFF2979FF)
    AccentColor.GREEN -> Color(0xFFB9F6CA) to Color(0xFF00C853)
    AccentColor.ORANGE -> Color(0xFFFFD180) to Color(0xFFFF6D00)
    AccentColor.RED -> Color(0xFFFF8A80) to Color(0xFFD50000)
}

@Composable
fun MusicAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: AccentColor = AccentColor.PURPLE,
    content: @Composable () -> Unit
) {
    val (darkAccent, lightAccent) = accentColorFor(accentColor)

    val colors = if (darkTheme) {
        darkColorScheme(
            primary = darkAccent,
            background = BackgroundDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark
        )
    } else {
        lightColorScheme(primary = lightAccent)
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
