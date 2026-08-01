package com.cid.musicapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.config.ThemeMode
import com.cid.musicapp.ui.navigation.AppNavHost
import com.cid.musicapp.ui.theme.MusicAppTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        // หมายเหตุ: RECORD_AUDIO (สำหรับ waveform) ไม่ได้ขอตรงนี้แล้วโดยตั้งใจ — แอปเพลงที่เพิ่งเปิดมา
        // ยังไม่ทันได้ทำอะไรแล้วเจอ popup ขอสิทธิ์ไมโครโฟนทันทีดูน่าสงสัย/เสียความเชื่อใจผู้ใช้
        // (ทั้งที่ไม่ได้อัดเสียงจริง) จึงย้ายไปขอแบบมีบริบทตอนเข้าหน้ากำลังเล่นครั้งแรกแทน
        // (ดู WaveformVisualizer.kt) และขอแค่ครั้งเดียวต่อการเปิดแอปหนึ่งรอบ

        val container = (application as MusicApp).container

        setContent {
            val themeMode by container.appSettings.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            val accentColorArgb by container.appSettings.accentColorArgbFlow.collectAsStateWithLifecycle(
                initialValue = AppConstants.DEFAULT_ACCENT_COLOR_ARGB
            )
            val dynamicColorEnabled by container.appSettings.dynamicColorEnabledFlow.collectAsStateWithLifecycle(
                initialValue = false
            )
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // สีไอคอนแถบสถานะ/แถบนำทางต้องตามธีมที่ "คำนวณแล้วจริงๆ" (darkTheme ด้านบน) ไม่ใช่แค่
            // ตามระบบเฉยๆ เพราะผู้ใช้เลือก Light/Dark เองในแอปได้โดยไม่ขึ้นกับโหมดของระบบ — ถ้าไม่ sync
            // ตรงนี้ เช่น เลือกธีมมืดในแอปทั้งที่ระบบเป็นโหมดสว่าง ไอคอนแถบสถานะจะกลายเป็นสีเข้มมองไม่เห็น
            // บนพื้นหลังเข้มของแอป
            val view = LocalView.current
            SideEffect {
                val window = (view.context as ComponentActivity).window
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }

            MusicAppTheme(
                darkTheme = darkTheme,
                accentColor = Color(accentColorArgb),
                useDynamicColor = dynamicColorEnabled
            ) {
                AppNavHost(container = container)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
