package com.cid.musicapp.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * เก็บค่าตั้งค่าที่ผู้ใช้ปรับเองได้ทั้งหมด (ธีม, สี, คุณภาพเสียง, พฤติกรรมเล่นเพลง/อัปเดต, dev mode)
 * ห้ามที่อื่นใน codebase อ่าน DataStore ตรงๆ — ผ่านคลาสนี้เท่านั้น
 */
class AppSettings(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT_COLOR_ARGB = intPreferencesKey("accent_color_argb")
        private val KEY_AUDIO_BITRATE_KBPS = intPreferencesKey("audio_bitrate_kbps")
        private val KEY_AUTO_ADVANCE = booleanPreferencesKey("auto_advance_enabled")
        private val KEY_AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates_enabled")
        private val KEY_DEV_MODE = booleanPreferencesKey("dev_mode_enabled")
    }

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    /** สีหลักของธีม เก็บเป็นค่า ARGB ตรงๆ — ผู้ใช้เลือกสีอะไรก็ได้ ไม่จำกัดแค่สีที่เตรียมไว้ */
    val accentColorArgbFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_ACCENT_COLOR_ARGB] ?: AppConstants.DEFAULT_ACCENT_COLOR_ARGB
    }

    /** เป้าหมายคุณภาพเสียงเป็น kbps ตรงๆ — ปรับละเอียดได้เอง ไม่ใช่แค่ 4 ระดับตายตัว */
    val audioBitrateKbpsFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_BITRATE_KBPS] ?: AppConstants.DEFAULT_AUDIO_BITRATE_KBPS
    }

    /** เพลงจบแล้วเล่นเพลงถัดไปในคิวต่อเองไหม (ปิดได้ถ้าอยากฟังทีละเพลงแล้วหยุด) */
    val autoAdvanceFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_ADVANCE] ?: true
    }

    /** เช็คอัปเดตอัตโนมัติตอนเปิดแอปไหม (ปิดได้ถ้าอยากเช็คเองจากหน้าตั้งค่าเท่านั้น) */
    val autoCheckUpdatesFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_CHECK_UPDATES] ?: true
    }

    /** โหมดนักพัฒนา — โชว์ข้อมูล debug ละเอียดในหน้าตั้งค่า */
    val devModeEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DEV_MODE] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAccentColorArgb(argb: Int) {
        dataStore.edit { prefs -> prefs[KEY_ACCENT_COLOR_ARGB] = argb }
    }

    suspend fun setAudioBitrateKbps(kbps: Int) {
        dataStore.edit { prefs -> prefs[KEY_AUDIO_BITRATE_KBPS] = kbps }
    }

    suspend fun setAutoAdvance(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_ADVANCE] = enabled }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun setDevModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_DEV_MODE] = enabled }
    }

    /** ล้างค่าตั้งค่าทั้งหมดกลับเป็นค่าเริ่มต้น (ปุ่มในโหมดนักพัฒนา) */
    suspend fun resetAll() {
        dataStore.edit { prefs -> prefs.clear() }
    }
}
