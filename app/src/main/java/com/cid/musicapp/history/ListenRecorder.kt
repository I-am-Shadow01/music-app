package com.cid.musicapp.history

import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.data.repository.Track

/**
 * นับ "เวลาที่ฟังจริง" ของเพลงที่กำลังเล่น — pure logic ไม่แตะ Android (เทสต์ด้วยนาฬิกาปลอมได้)
 *
 * ผู้เรียก (PlayerController) แจ้งเหตุการณ์สามอย่าง: [begin] เริ่มเพลงใหม่, [onPlayingChanged] เล่น/หยุดชั่วคราว,
 * [finish] เพลงจบ/ถูกข้าม/หยุดทั้งหมด — [finish] คืน [PlayRecord] ถ้าฟังนานพอจะบันทึก (กดพลาดไม่นับ)
 *
 * เวลาที่ฟังวัดจากนาฬิกา monotonic ([elapsedMillis]) กันนาฬิกาเครื่องถูกปรับ; ส่วน [PlayRecord.playedAtMillis]
 * ใช้นาฬิกาจริง ([wallMillis]) เพราะต้องเอาไปคำนวณอายุของการฟังข้ามวัน/ข้ามการเปิดแอป
 */
class ListenRecorder(
    private val elapsedMillis: () -> Long,
    private val wallMillis: () -> Long,
    private val minListenMillis: Long = AppConstants.HISTORY_MIN_LISTEN_TO_RECORD_MILLIS
) {
    private var track: Track? = null
    private var startedAtWall = 0L
    private var listenedMs = 0L
    private var playingSince: Long? = null

    /** เริ่มจับเวลาเพลงใหม่ — ทิ้งเพลงเดิมที่ยังค้างอยู่ (ผู้เรียกต้อง [finish] เพลงเดิมก่อนถ้าอยากเก็บ) */
    fun begin(track: Track, isPlaying: Boolean) {
        this.track = track
        startedAtWall = wallMillis()
        listenedMs = 0L
        playingSince = if (isPlaying) elapsedMillis() else null
    }

    /** เล่น/หยุดชั่วคราวเปลี่ยน — ไม่มีเพลงที่กำลังจับเวลาอยู่ก็ไม่ทำอะไร */
    fun onPlayingChanged(isPlaying: Boolean) {
        if (track == null) return
        val now = elapsedMillis()
        val since = playingSince
        if (isPlaying) {
            if (since == null) playingSince = now
        } else if (since != null) {
            listenedMs += (now - since).coerceAtLeast(0L)
            playingSince = null
        }
    }

    /** จบการฟังเพลงปัจจุบัน — คืนค่า null ถ้าไม่มีเพลงที่จับเวลาอยู่ หรือฟังสั้นกว่าเกณฑ์ */
    fun finish(): PlayRecord? {
        val current = track ?: return null
        onPlayingChanged(isPlaying = false)
        val record = PlayRecord(current, startedAtWall, listenedMs)
        track = null
        listenedMs = 0L
        playingSince = null
        return record.takeIf { it.listenedMs >= minListenMillis }
    }
}
