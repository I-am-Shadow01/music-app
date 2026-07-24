package com.cid.musicapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cid.musicapp.config.AccentColor
import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.config.AudioQuality
import com.cid.musicapp.config.ThemeMode
import com.cid.musicapp.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.PURPLE,
    val audioQuality: AudioQuality = AudioQuality.BEST,
    val autoAdvanceEnabled: Boolean = true,
    val autoCheckUpdatesEnabled: Boolean = true,
    val cacheClearedJustNow: Boolean = false
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
            appSettings.accentColorFlow.collect { color ->
                _uiState.value = _uiState.value.copy(accentColor = color)
            }
        }
        viewModelScope.launch {
            appSettings.audioQualityFlow.collect { quality ->
                _uiState.value = _uiState.value.copy(audioQuality = quality)
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
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appSettings.setThemeMode(mode) }
    }

    fun setAccentColor(color: AccentColor) {
        viewModelScope.launch { appSettings.setAccentColor(color) }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch { appSettings.setAudioQuality(quality) }
    }

    fun setAutoAdvance(enabled: Boolean) {
        viewModelScope.launch { appSettings.setAutoAdvance(enabled) }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch { appSettings.setAutoCheckUpdates(enabled) }
    }

    fun clearCache() {
        musicRepository.clearStreamCache()
        _uiState.value = _uiState.value.copy(cacheClearedJustNow = true)
    }
}
