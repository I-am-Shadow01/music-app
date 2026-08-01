package com.cid.musicapp.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cid.musicapp.data.repository.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

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
        private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches_json")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        private val KEY_VIDEO_HEIGHT_PX = intPreferencesKey("video_height_px")
        private val KEY_FAVORITE_TRACKS = stringPreferencesKey("favorite_tracks_json")
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

    /** คำค้นหาล่าสุด เรียงใหม่สุดอยู่บนสุด — เก็บเป็น JSON array ในค่า string เดียว (ไม่ใช้ Set เพราะ Set ไม่รักษาลำดับ) */
    val recentSearchesFlow: Flow<List<String>> = dataStore.data.map { prefs ->
        decodeRecentSearches(prefs[KEY_RECENT_SEARCHES])
    }

    /** ใช้สีธีมจากวอลเปเปอร์เครื่อง (Material You) แทนสี accent ที่เลือกเอง — มีผลเฉพาะ Android 12+ เท่านั้น */
    val dynamicColorEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: false
    }

    /** คุณภาพวิดีโอเป้าหมายตอนเล่นโหมดวิดีโอ เก็บเป็นความสูง px ตรงๆ (เช่น 480 = 480p) */
    val videoHeightPxFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_VIDEO_HEIGHT_PX] ?: AppConstants.DEFAULT_VIDEO_HEIGHT_PX
    }

    /** เพลงโปรด (กดใจ) เรียงใหม่สุดอยู่บนสุด — เก็บเป็น JSON array ของ track object เต็มๆ (id/title/artist/...)
     * เพราะเวลาแสดงในแท็บ "เพลงโปรด" ต้องมีข้อมูลครบ ไม่ใช่แค่ id เฉยๆ (ต่างจาก recentSearches ที่เก็บแค่ string) */
    val favoriteTracksFlow: Flow<List<Track>> = dataStore.data.map { prefs ->
        decodeFavoriteTracks(prefs[KEY_FAVORITE_TRACKS])
    }

    private fun decodeFavoriteTracks(raw: String?): List<Track> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                Track(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    durationSeconds = obj.optInt("durationSeconds", -1).takeIf { it >= 0 },
                    thumbnailUrl = obj.optString("thumbnailUrl", "").takeIf { it.isNotBlank() }
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeFavoriteTracks(tracks: List<Track>): String {
        val array = JSONArray()
        tracks.forEach { track ->
            val obj = JSONObject()
            obj.put("id", track.id)
            obj.put("title", track.title)
            obj.put("artist", track.artist)
            track.durationSeconds?.let { obj.put("durationSeconds", it) }
            track.thumbnailUrl?.let { obj.put("thumbnailUrl", it) }
            array.put(obj)
        }
        return array.toString()
    }

    /** สลับสถานะกดใจของ track — มีอยู่แล้วให้เอาออก, ยังไม่มีให้เพิ่มไว้บนสุด */
    suspend fun toggleFavorite(track: Track) {
        dataStore.edit { prefs ->
            val current = decodeFavoriteTracks(prefs[KEY_FAVORITE_TRACKS])
            val alreadyFavorite = current.any { it.id == track.id }
            val updated = if (alreadyFavorite) {
                current.filterNot { it.id == track.id }
            } else {
                (listOf(track) + current).take(AppConstants.MAX_FAVORITE_TRACKS)
            }
            prefs[KEY_FAVORITE_TRACKS] = encodeFavoriteTracks(updated)
        }
    }

    suspend fun removeFavorite(trackId: String) {
        dataStore.edit { prefs ->
            val current = decodeFavoriteTracks(prefs[KEY_FAVORITE_TRACKS])
            prefs[KEY_FAVORITE_TRACKS] = encodeFavoriteTracks(current.filterNot { it.id == trackId })
        }
    }

    private fun decodeRecentSearches(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
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

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setVideoHeightPx(heightPx: Int) {
        dataStore.edit { prefs -> prefs[KEY_VIDEO_HEIGHT_PX] = heightPx }
    }

    /** เพิ่มคำค้นหาไว้บนสุด, ตัดคำซ้ำเดิมทิ้ง (ไม่สนตัวพิมพ์เล็ก/ใหญ่), ตัดให้เหลือแค่ MAX_RECENT_SEARCHES คำ */
    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        dataStore.edit { prefs ->
            val current = decodeRecentSearches(prefs[KEY_RECENT_SEARCHES])
            val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(AppConstants.MAX_RECENT_SEARCHES)
            prefs[KEY_RECENT_SEARCHES] = JSONArray(updated).toString()
        }
    }

    suspend fun removeRecentSearch(query: String) {
        dataStore.edit { prefs ->
            val current = decodeRecentSearches(prefs[KEY_RECENT_SEARCHES])
            val updated = current.filterNot { it == query }
            prefs[KEY_RECENT_SEARCHES] = JSONArray(updated).toString()
        }
    }

    suspend fun clearRecentSearches() {
        dataStore.edit { prefs -> prefs.remove(KEY_RECENT_SEARCHES) }
    }

    /** ล้างค่าตั้งค่าทั้งหมดกลับเป็นค่าเริ่มต้น (ปุ่มในโหมดนักพัฒนา) */
    suspend fun resetAll() {
        dataStore.edit { prefs -> prefs.clear() }
    }
}
