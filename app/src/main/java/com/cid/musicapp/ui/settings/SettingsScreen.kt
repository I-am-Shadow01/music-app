package com.cid.musicapp.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cid.musicapp.BuildConfig
import com.cid.musicapp.R
import com.cid.musicapp.config.AudioQuality
import com.cid.musicapp.config.ThemeMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        SettingsSection(title = stringResource(R.string.settings_theme_title)) {
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

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(title = stringResource(R.string.settings_audio_quality_title)) {
            AudioQuality.entries.forEach { quality ->
                SettingsRadioRow(
                    label = when (quality) {
                        AudioQuality.LOW -> stringResource(R.string.settings_audio_quality_low)
                        AudioQuality.MEDIUM -> stringResource(R.string.settings_audio_quality_medium)
                        AudioQuality.HIGH -> stringResource(R.string.settings_audio_quality_high)
                        AudioQuality.BEST -> stringResource(R.string.settings_audio_quality_best)
                    },
                    selected = uiState.audioQuality == quality,
                    onClick = { viewModel.setAudioQuality(quality) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(title = stringResource(R.string.settings_cache_title)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { viewModel.clearCache() }) {
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
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(title = stringResource(R.string.settings_about_title)) {
            Text(
                stringResource(R.string.settings_build_number, BuildConfig.BUILD_NUMBER),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/I-am-Shadow01/music-app/releases")
                )
                context.startActivity(intent)
            }) {
                Text(stringResource(R.string.settings_view_releases))
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    content()
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
