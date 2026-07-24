package com.cid.musicapp.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** ระดับคุณภาพเสียงที่จะเลือกตอนเล่น — ยิ่งสูงยิ่งกินเน็ต/ที่เก็บ cache มากขึ้น */
enum class AudioQuality { LOW, MEDIUM, HIGH, BEST }

/**
 * เก็บค่าตั้งค่าที่ผู้ใช้ปรับเองได้ทั้งหมด (ธีม, คุณภาพเสียง)
 * ห้ามที่อื่นใน codebase อ่าน DataStore ตรงๆ — ผ่านคลาสนี้เท่านั้น
 */
class AppSettings(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_AUDIO_QUALITY = stringPreferencesKey("audio_quality")
    }

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    val audioQualityFlow: Flow<AudioQuality> = dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_QUALITY]?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
            ?: AudioQuality.BEST
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { prefs -> prefs[KEY_AUDIO_QUALITY] = quality.name }
    }
}
