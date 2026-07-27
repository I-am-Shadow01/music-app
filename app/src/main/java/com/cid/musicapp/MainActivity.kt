package com.cid.musicapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.config.ThemeMode
import com.cid.musicapp.ui.navigation.AppNavHost
import com.cid.musicapp.ui.theme.MusicAppTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    // RECORD_AUDIO จำเป็นสำหรับ android.media.audiofx.Visualizer (waveform ในหน้ากำลังเล่น) เท่านั้น
    // ไม่ได้ใช้บันทึกเสียงจริงๆ — ถ้าผู้ใช้ไม่อนุญาต แอปยังเล่นเพลงได้ปกติ แค่ไม่แสดงคลื่นเสียง
    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        requestRecordAudioPermissionIfNeeded()

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

    private fun requestRecordAudioPermissionIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
