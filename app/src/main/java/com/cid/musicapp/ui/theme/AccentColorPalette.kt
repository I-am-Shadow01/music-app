package com.cid.musicapp.ui.theme

import androidx.compose.ui.graphics.Color
import com.cid.musicapp.config.AccentColor

/**
 * ค่าสีจริงของแต่ละ AccentColor อยู่ที่นี่จุดเดียว — ทั้งธีมจริง (MusicAppTheme)
 * และตัวอย่างสีในหน้าตั้งค่า (SettingsScreen) ต้องดึงจากที่นี่ ห้ามพิมพ์ hex ซ้ำที่อื่น
 */
data class AccentColorSwatch(val dark: Color, val light: Color) {
    /** สีตัวแทนตัวเดียวไว้โชว์เป็นวงกลมตัวอย่างในหน้าตั้งค่า */
    val swatch: Color get() = light
}

fun AccentColor.swatchColors(): AccentColorSwatch = when (this) {
    AccentColor.PURPLE -> AccentColorSwatch(dark = Color(0xFFB388FF), light = Color(0xFF7C4DFF))
    AccentColor.BLUE -> AccentColorSwatch(dark = Color(0xFF82B1FF), light = Color(0xFF2979FF))
    AccentColor.GREEN -> AccentColorSwatch(dark = Color(0xFFB9F6CA), light = Color(0xFF00C853))
    AccentColor.ORANGE -> AccentColorSwatch(dark = Color(0xFFFFD180), light = Color(0xFFFF6D00))
    AccentColor.RED -> AccentColorSwatch(dark = Color(0xFFFF8A80), light = Color(0xFFD50000))
}
