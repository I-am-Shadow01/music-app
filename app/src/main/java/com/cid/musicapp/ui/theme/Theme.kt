package com.cid.musicapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.cid.musicapp.config.AppConstants

@Composable
fun MusicAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // ค่าเริ่มต้นดึงจาก AppConstants จุดเดียวกับ AppSettings — เดิมเคย hardcode สีเดียวกันไว้ที่นี่ซ้ำ
    accentColor: Color = Color(AppConstants.DEFAULT_ACCENT_COLOR_ARGB),
    // Material You: ดึงสีจากวอลเปเปอร์เครื่องแทนสี accent ที่เลือกเอง — ใช้ได้เฉพาะ Android 12+ (API 31)
    // เครื่องที่ต่ำกว่านี้จะ fallback กลับไปใช้ accentColor ปกติเสมอ ไม่ว่าค่านี้จะเป็นอะไร
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamicColor = useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colors = when {
        supportsDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        supportsDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme(
            primary = accentColor,
            background = BackgroundDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark
        )
        else -> lightColorScheme(primary = accentColor)
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
