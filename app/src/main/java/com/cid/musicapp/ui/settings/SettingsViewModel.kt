package com.cid.musicapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.config.ThemeMode
import com.cid.musicapp.data.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val settingsResetJustNow: Boolean = false,
    val dynamicColorEnabled: Boolean = false,
    val videoHeightPx: Int = AppConstants.DEFAULT_VIDEO_HEIGHT_PX
)

class SettingsViewModel(
    private val appSettings: AppSettings,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    // job ของข้อความ "เสร็จแล้ว" ชั่วคราว (ล้างแคชแล้ว / รีเซ็ตแล้ว) — cancel ตัวเก่าทิ้งก่อนเริ่มนับใหม่
    // กันข้อความค้างอยู่ตลอดไปถ้าไม่มีอะไรมาเปลี่ยน uiState อีก (เช่น กดล้างแคชแล้วไม่แตะอะไรต่อ)
    private var cacheClearedMessageJob: Job? = null
    private var settingsResetMessageJob: Job? = null

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
        viewModelScope.launch {
            appSettings.dynamicColorEnabledFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(dynamicColorEnabled = enabled)
            }
        }
        viewModelScope.launch {
            appSettings.videoHeightPxFlow.collect { heightPx ->
                _uiState.value = _uiState.value.copy(videoHeightPx = heightPx)
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

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettings.setDynamicColorEnabled(enabled) }
    }

    fun setVideoHeightPx(heightPx: Int) {
        viewModelScope.launch { appSettings.setVideoHeightPx(heightPx) }
    }

    fun clearCache() {
        musicRepository.clearStreamCache()
        _uiState.value = _uiState.value.copy(cacheClearedJustNow = true)

        cacheClearedMessageJob?.cancel()
        cacheClearedMessageJob = viewModelScope.launch {
            delay(AppConstants.SETTINGS_CONFIRMATION_MESSAGE_MILLIS)
            _uiState.value = _uiState.value.copy(cacheClearedJustNow = false)
        }
    }

    fun cachedStreamCount(): Int = musicRepository.cachedStreamCount()

    fun resetAllSettings() {
        viewModelScope.launch {
            appSettings.resetAll()
            _uiState.value = _uiState.value.copy(settingsResetJustNow = true)

            settingsResetMessageJob?.cancel()
            settingsResetMessageJob = viewModelScope.launch {
                delay(AppConstants.SETTINGS_CONFIRMATION_MESSAGE_MILLIS)
                _uiState.value = _uiState.value.copy(settingsResetJustNow = false)
            }
        }
    }
}
