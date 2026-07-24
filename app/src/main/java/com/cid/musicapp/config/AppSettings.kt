package com.cid.musicapp.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** ระดับคุณภาพเสียงที่จะเลือกตอนเล่น — ยิ่งสูงยิ่งกินเน็ต/ที่เก็บ cache มากขึ้น */
enum class AudioQuality { LOW, MEDIUM, HIGH, BEST }

/** สีหลักของธีม เลือกได้เอง ไม่ผูกกับสีม่วงตายตัวอีกต่อไป */
enum class AccentColor { PURPLE, BLUE, GREEN, ORANGE, RED }

/**
 * เก็บค่าตั้งค่าที่ผู้ใช้ปรับเองได้ทั้งหมด (ธีม, สี, คุณภาพเสียง, พฤติกรรมเล่นเพลง/อัปเดต)
 * ห้ามที่อื่นใน codebase อ่าน DataStore ตรงๆ — ผ่านคลาสนี้เท่านั้น
 */
class AppSettings(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val KEY_AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        private val KEY_AUTO_ADVANCE = booleanPreferencesKey("auto_advance_enabled")
        private val KEY_AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates_enabled")
    }

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    val accentColorFlow: Flow<AccentColor> = dataStore.data.map { prefs ->
        prefs[KEY_ACCENT_COLOR]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
            ?: AccentColor.PURPLE
    }

    val audioQualityFlow: Flow<AudioQuality> = dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_QUALITY]?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
            ?: AudioQuality.BEST
    }

    /** เพลงจบแล้วเล่นเพลงถัดไปในคิวต่อเองไหม (ปิดได้ถ้าอยากฟังทีละเพลงแล้วหยุด) */
    val autoAdvanceFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_ADVANCE] ?: true
    }

    /** เช็คอัปเดตอัตโนมัติตอนเปิดแอปไหม (ปิดได้ถ้าอยากเช็คเองจากหน้าตั้งค่าเท่านั้น) */
    val autoCheckUpdatesFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_CHECK_UPDATES] ?: true
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAccentColor(color: AccentColor) {
        dataStore.edit { prefs -> prefs[KEY_ACCENT_COLOR] = color.name }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { prefs -> prefs[KEY_AUDIO_QUALITY] = quality.name }
    }

    suspend fun setAutoAdvance(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_ADVANCE] = enabled }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_CHECK_UPDATES] = enabled }
    }
}
