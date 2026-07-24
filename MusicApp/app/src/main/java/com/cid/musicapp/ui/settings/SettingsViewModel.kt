package com.cid.musicapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.config.ThemeMode
import com.cid.musicapp.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColorArgb: Int = AppConstants.DEFAULT_ACCENT_COLOR_ARGB,
    val audioBitrateKbps: Int = AppConstants.DEFAULT_AUDIO_BITRATE_KBPS,
    val autoAdvanceEnabled: Boolean = true,
    val autoCheckUpdatesEnabled: Boolean = true,
    val devModeEnabled: Boolean = false,
    val cacheClearedJustNow: Boolean = false,
    val settingsResetJustNow: Boolean = false
)

class SettingsViewModel(
    private val appSettings: AppSettings,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            appSettings.themeModeFlow.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            appSettings.accentColorArgbFlow.collect { argb ->
                _uiState.value = _uiState.value.copy(accentColorArgb = argb)
            }
        }
        viewModelScope.launch {
            appSettings.audioBitrateKbpsFlow.collect { kbps ->
                _uiState.value = _uiState.value.copy(audioBitrateKbps = kbps)
            }
        }
        viewModelScope.launch {
            appSettings.autoAdvanceFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(autoAdvanceEnabled = enabled)
            }
        }
        viewModelScope.launch {
            appSettings.autoCheckUpdatesFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(autoCheckUpdatesEnabled = enabled)
            }
        }
        viewModelScope.launch {
            appSettings.devModeEnabledFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(devModeEnabled = enabled)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appSettings.setThemeMode(mode) }
    }

    fun setAccentColorArgb(argb: Int) {
        viewModelScope.launch { appSettings.setAccentColorArgb(argb) }
    }

    fun setAudioBitrateKbps(kbps: Int) {
        viewModelScope.launch { appSettings.setAudioBitrateKbps(kbps) }
    }

    fun setAutoAdvance(enabled: Boolean) {
        viewModelScope.launch { appSettings.setAutoAdvance(enabled) }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch { appSettings.setAutoCheckUpdates(enabled) }
    }

    fun setDevModeEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettings.setDevModeEnabled(enabled) }
    }

    fun clearCache() {
        musicRepository.clearStreamCache()
        _uiState.value = _uiState.value.copy(cacheClearedJustNow = true)
    }

    fun cachedStreamCount(): Int = musicRepository.cachedStreamCount()

    fun resetAllSettings() {
        viewModelScope.launch {
            appSettings.resetAll()
            _uiState.value = _uiState.value.copy(settingsResetJustNow = true)
        }
    }
}
