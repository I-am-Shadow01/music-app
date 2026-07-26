package com.cid.musicapp.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cid.musicapp.BuildConfig
import com.cid.musicapp.R
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.config.ThemeMode
import com.cid.musicapp.player.PlaybackUiState
import com.cid.musicapp.ui.theme.ACCENT_PRESET_COLORS
import com.cid.musicapp.ui.update.UpdateViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    updateViewModel: UpdateViewModel,
    playbackState: PlaybackUiState
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showResetConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        SettingsSection(title = stringResource(R.string.settings_theme_title), icon = Icons.Default.Brightness6) {
            ThemeMode.entries.forEach { mode ->
                SettingsRadioRow(
                    label = when (mode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                    },
                    selected = uiState.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = stringResource(R.string.settings_accent_color_title), icon = Icons.Default.Palette) {
            AccentColorPicker(
                currentArgb = uiState.accentColorArgb,
                onColorChange = { viewModel.setAccentColorArgb(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = stringResource(R.string.settings_audio_quality_title), icon = Icons.Default.GraphicEq) {
            // ตำแหน่งระหว่างลากนิ้ว ใช้ค่านี้แทนค่าจริงชั่วคราว กันยิง setAudioBitrateKbps ถี่เกินไป
            var dragKbps by remember { mutableStateOf<Float?>(null) }
            val displayedKbps = dragKbps?.toInt() ?: uiState.audioBitrateKbps

            Text(
                stringResource(R.string.settings_audio_quality_value, displayedKbps),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = dragKbps ?: uiState.audioBitrateKbps.toFloat(),
                valueRange = AppConstants.MIN_AUDIO_BITRATE_KBPS.toFloat()..AppConstants.MAX_AUDIO_BITRATE_KBPS.toFloat(),
                onValueChange = { dragKbps = it },
                onValueChangeFinished = {
                    dragKbps?.let { viewModel.setAudioBitrateKbps(it.toInt()) }
                    dragKbps = null
                }
            )
            Text(
                stringResource(R.string.settings_audio_quality_hint),
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = stringResource(R.string.settings_playback_title), icon = Icons.Default.PlayCircleOutline) {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_auto_advance),
                checked = uiState.autoAdvanceEnabled,
                onCheckedChange = { viewModel.setAutoAdvance(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = stringResource(R.string.settings_cache_title), icon = Icons.Default.DeleteSweep) {
            val cachedCount = viewModel.cachedStreamCount()
            Text(
                stringResource(R.string.settings_cache_count, cachedCount),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { showClearCacheConfirm = true },
                    enabled = cachedCount > 0
                ) {
                    Text(stringResource(R.string.settings_clear_cache_button))
                }
                if (uiState.cacheClearedJustNow) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.settings_cache_cleared),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (showClearCacheConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearCacheConfirm = false },
                    title = { Text(stringResource(R.string.settings_clear_cache_confirm_title)) },
                    text = { Text(stringResource(R.string.settings_clear_cache_confirm_body, cachedCount)) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.clearCache()
                            showClearCacheConfirm = false
                        }) {
                            Text(stringResource(R.string.settings_clear_cache_button))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearCacheConfirm = false }) {
                            Text(stringResource(R.string.settings_cancel))
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = stringResource(R.string.settings_update_title), icon = Icons.Default.SystemUpdate) {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_auto_check_updates),
                checked = uiState.autoCheckUpdatesEnabled,
                onCheckedChange = { viewModel.setAutoCheckUpdates(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { updateViewModel.checkNow() },
                    enabled = !updateUiState.isChecking
                ) {
                    Text(stringResource(R.string.settings_check_now_button))
                }

                if (updateUiState.isChecking) {
                    Spacer(modifier = Modifier.width(12.dp))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (updateUiState.justConfirmedUpToDate) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.settings_up_to_date),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = stringResource(R.string.settings_about_title), icon = Icons.Default.Info) {
            Text(
                stringResource(R.string.settings_build_number, BuildConfig.BUILD_NUMBER),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(AppConstants.GITHUB_RELEASES_PAGE_URL)
                )
                context.startActivity(intent)
            }) {
                Text(stringResource(R.string.settings_view_releases))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = stringResource(R.string.settings_dev_mode_title), icon = Icons.Default.DeveloperMode) {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_dev_mode_toggle),
                checked = uiState.devModeEnabled,
                onCheckedChange = { viewModel.setDevModeEnabled(it) }
            )

            if (uiState.devModeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                DeveloperPanel(
                    viewModel = viewModel,
                    uiState = uiState,
                    playbackState = playbackState,
                    showResetConfirm = showResetConfirm,
                    onRequestReset = { showResetConfirm = true },
                    onDismissResetConfirm = { showResetConfirm = false }
                )
            }
        }
    }
}

@Composable
private fun DeveloperPanel(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    playbackState: PlaybackUiState,
    showResetConfirm: Boolean,
    onRequestReset: () -> Unit,
    onDismissResetConfirm: () -> Unit
) {
    Column {
        Text(stringResource(R.string.settings_dev_app_info_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        DevInfoLine("applicationId", BuildConfig.APPLICATION_ID)
        DevInfoLine("versionName", BuildConfig.VERSION_NAME)
        DevInfoLine("versionCode", BuildConfig.VERSION_CODE.toString())
        DevInfoLine("BUILD_NUMBER", BuildConfig.BUILD_NUMBER.toString())
        DevInfoLine("DEBUG", BuildConfig.DEBUG.toString())
        DevInfoLine("Android SDK", Build.VERSION.SDK_INT.toString())
        DevInfoLine("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        DevInfoLine("Cached stream URLs", viewModel.cachedStreamCount().toString())
        DevInfoLine("Accent color ARGB", "#" + Integer.toHexString(uiState.accentColorArgb))

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(R.string.settings_dev_playback_info_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        DevInfoLine("isPlaying", playbackState.isPlaying.toString())
        DevInfoLine("isResolving", playbackState.isResolving.toString())
        DevInfoLine("position/duration (ms)", "${playbackState.positionMs} / ${playbackState.durationMs}")
        DevInfoLine("currentTitle", playbackState.currentTitle ?: "-")
        DevInfoLine("shuffle", playbackState.isShuffleEnabled.toString())
        DevInfoLine("repeatMode", playbackState.repeatMode.toString())
        DevInfoLine("upcoming count", playbackState.upcoming.size.toString())

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onRequestReset) {
                Text(stringResource(R.string.settings_dev_reset_button))
            }
            if (uiState.settingsResetJustNow) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(R.string.settings_dev_reset_done),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = onDismissResetConfirm,
                title = { Text(stringResource(R.string.settings_dev_reset_confirm_title)) },
                text = { Text(stringResource(R.string.settings_dev_reset_confirm_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.resetAllSettings()
                        onDismissResetConfirm()
                    }) {
                        Text(stringResource(R.string.settings_dev_reset_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissResetConfirm) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun DevInfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AccentColorPicker(currentArgb: Int, onColorChange: (Int) -> Unit) {
    // แปลง ARGB ปัจจุบันเป็น HSV เพื่อโชว์ slider ให้ตรงกับสีที่ตั้งไว้จริง
    val hsv = remember(currentArgb) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(currentArgb, out)
        out
    }

    val isPreset = remember(currentArgb) { ACCENT_PRESET_COLORS.any { it.toArgb() == currentArgb } }
    // เปิด custom picker ไว้ล่วงหน้าถ้าสีปัจจุบันไม่ตรงกับ preset ไหนเลย กันผู้ใช้งงว่าสีตัวเองหายไปไหน
    var customExpanded by remember { mutableStateOf(!isPreset) }

    var dragHue by remember { mutableStateOf<Float?>(null) }
    var dragSaturation by remember { mutableStateOf<Float?>(null) }
    var dragLightness by remember { mutableStateOf<Float?>(null) }

    val hue = dragHue ?: hsv[0]
    val saturation = dragSaturation ?: hsv[1]
    val lightness = dragLightness ?: hsv[2]

    fun commit() {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, lightness))
        onColorChange(argb)
        dragHue = null
        dragSaturation = null
        dragLightness = null
    }

    Text(stringResource(R.string.settings_accent_custom_hint), style = MaterialTheme.typography.labelSmall)
    Spacer(modifier = Modifier.height(10.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ACCENT_PRESET_COLORS.forEach { preset ->
            val presetArgb = preset.toArgb()
            val selected = presetArgb == currentArgb
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(preset, shape = CircleShape)
                    .then(
                        if (selected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onColorChange(presetArgb) },
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    TextButton(onClick = { customExpanded = !customExpanded }) {
        Text(stringResource(R.string.settings_accent_custom_toggle))
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (customExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null
        )
    }

    if (customExpanded) {
        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(currentArgb), shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("#" + Integer.toHexString(currentArgb).uppercase(), style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(stringResource(R.string.settings_accent_hue), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = hue,
            valueRange = 0f..360f,
            onValueChange = { dragHue = it },
            onValueChangeFinished = { commit() }
        )

        Text(stringResource(R.string.settings_accent_saturation), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = saturation,
            valueRange = 0f..1f,
            onValueChange = { dragSaturation = it },
            onValueChangeFinished = { commit() }
        )

        Text(stringResource(R.string.settings_accent_lightness), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = lightness,
            valueRange = 0f..1f,
            onValueChange = { dragLightness = it },
            onValueChangeFinished = { commit() }
        )
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
