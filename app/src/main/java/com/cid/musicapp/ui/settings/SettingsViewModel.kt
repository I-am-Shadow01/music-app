package com.cid.musicapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.config.AudioQuality
import com.cid.musicapp.config.ThemeMode
import com.cid.musicapp.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val audioQuality: AudioQuality = AudioQuality.BEST,
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
            appSettings.audioQualityFlow.collect { quality ->
                _uiState.value = _uiState.value.copy(audioQuality = quality)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appSettings.setThemeMode(mode) }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch { appSettings.setAudioQuality(quality) }
    }

    fun clearCache() {
        musicRepository.clearStreamCache()
        _uiState.value = _uiState.value.copy(cacheClearedJustNow = true)
    }
}
