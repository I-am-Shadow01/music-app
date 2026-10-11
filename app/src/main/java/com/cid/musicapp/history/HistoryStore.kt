package com.cid.musicapp.history

import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.data.repository.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** ที่เก็บประวัติการฟัง — แยกเป็น interface เพื่อให้ [ListeningHistory] เทสต์ด้วยของปลอมในหน่วยความจำได้ */
interface HistoryStore {
    /** อ่านทั้งหมด (เก่า→ใหม่); ไม่มีไฟล์/ไฟล์พัง = ลิสต์ว่าง ไม่โยน exception ใส่ผู้เรียก */
    fun load(): List<PlayRecord>

    fun save(records: List<PlayRecord>)
}

/**
 * เก็บเป็นไฟล์ JSON ไฟล์เดียวในที่เก็บข้อมูลส่วนตัวของแอป
 *  - เขียนไฟล์ชั่วคราวก่อนแล้วค่อย rename ทับ (atomic) → แอปโดนฆ่ากลางทางไฟล์เดิมก็ยังอ่านได้ครบ ไม่เหลือไฟล์ครึ่งๆ กลางๆ
 *  - ไฟล์พังทั้งไฟล์ → ย้ายไป .bad เก็บไว้ดู แล้วเริ่มใหม่ (ไม่ทำให้แอปเปิดไม่ขึ้น); แถวเดียวพัง → ข้ามแถวนั้น
 *
 * TODO(debt): ถ้ามีฟีเจอร์ที่ต้อง query ประวัติหนัก (เช่น สถิติรายเดือน) ควรย้ายไป Room/SQLite — ตอนนี้เขียนทั้งไฟล์ทุกครั้ง
 * (ไม่กี่ร้อย KB และมี debounce) ยังไม่เป็นปัญหา จึงไม่เพิ่ม dependency ใหม่
 */
class FileHistoryStore(private val file: File) : HistoryStore {

    override fun load(): List<PlayRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val array = root.getJSONArray(KEY_ENTRIES)
            (0 until array.length()).mapNotNull { index ->
                runCatching { decode(array.getJSONObject(index)) }.getOrNull()
            }
        } catch (e: Exception) {
            quarantineCorruptFile()
            emptyList()
        }
    }

    override fun save(records: List<PlayRecord>) {
        file.parentFile?.mkdirs()
        val root = JSONObject()
            .put(KEY_VERSION, AppConstants.HISTORY_FILE_FORMAT_VERSION)
            .put(KEY_ENTRIES, JSONArray().also { array -> records.forEach { array.put(encode(it)) } })

        val temp = File(file.parentFile, file.name + TEMP_SUFFIX)
        temp.writeText(root.toString(), Charsets.UTF_8)
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun quarantineCorruptFile() {
        runCatching {
            Files.move(
                file.toPath(), File(file.parentFile, file.name + CORRUPT_SUFFIX).toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun encode(record: PlayRecord): JSONObject {
        val track = record.track
        return JSONObject()
            .put(KEY_ID, track.id)
            .put(KEY_TITLE, track.title)
            .put(KEY_ARTIST, track.artist)
            .put(KEY_PLAYED_AT, record.playedAtMillis)
            .put(KEY_LISTENED, record.listenedMs)
            .also { obj ->
                track.durationSeconds?.let { obj.put(KEY_DURATION, it) }
                track.thumbnailUrl?.let { obj.put(KEY_THUMBNAIL, it) }
            }
    }

    private fun decode(obj: JSONObject): PlayRecord = PlayRecord(
        track = Track(
            id = obj.getString(KEY_ID),
            title = obj.getString(KEY_TITLE),
            artist = obj.getString(KEY_ARTIST),
            durationSeconds = obj.optInt(KEY_DURATION, NO_DURATION).takeIf { it != NO_DURATION },
            thumbnailUrl = obj.optString(KEY_THUMBNAIL, "").takeIf { it.isNotBlank() }
        ),
        playedAtMillis = obj.getLong(KEY_PLAYED_AT),
        listenedMs = obj.getLong(KEY_LISTENED)
    )

    private companion object {
        const val KEY_VERSION = "v"
        const val KEY_ENTRIES = "entries"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_DURATION = "dur"
        const val KEY_THUMBNAIL = "thumb"
        const val KEY_PLAYED_AT = "at"
        const val KEY_LISTENED = "ms"
        const val NO_DURATION = -1
        const val TEMP_SUFFIX = ".tmp"
        const val CORRUPT_SUFFIX = ".bad"
    }
}
