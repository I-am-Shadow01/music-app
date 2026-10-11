package com.cid.musicapp.history

import com.cid.musicapp.data.repository.Track

/**
 * การฟังหนึ่งครั้ง: เพลงไหน, เริ่มเมื่อไหร่ (เวลานาฬิกาจริง), ฟังไปกี่มิลลิวินาทีจริงๆ (ไม่นับช่วงหยุดชั่วคราว)
 * เก็บ [Track] เต็มๆ ไม่ใช่แค่ id เพราะต้องเอาไปโชว์/ใช้เป็นเพลงตั้งต้นของ Radio ได้โดยไม่ต้องไปดึงใหม่
 */
data class PlayRecord(val track: Track, val playedAtMillis: Long, val listenedMs: Long)
