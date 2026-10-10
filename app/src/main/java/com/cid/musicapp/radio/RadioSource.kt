package com.cid.musicapp.radio

import com.cid.musicapp.data.repository.Track

/** ตัวชี้ตำแหน่งต่อของแหล่งเพลงแบบแบ่งหน้า — ทึบสำหรับ engine (แต่ละแหล่งใส่อะไรข้างในก็ได้) */
interface RadioCursor

/** ผลหนึ่งหน้าจากแหล่งเพลงแบบแบ่งหน้า [next] = null หมายถึงหมดแล้ว */
data class RadioPage(val tracks: List<Track>, val next: RadioCursor?)

/**
 * แหล่งเพลงแนะนำ — แยกเป็น interface เพื่อให้ [RadioEngine] ไม่ผูกกับ NewPipe/เครือข่าย (เทสต์ด้วยของปลอมได้)
 * ทุกฟังก์ชันโยน exception ได้ตามปกติ — engine เป็นคนจับและข้ามไปแหล่งสำรองเอง
 * (CancellationException ต้องไม่ถูกกลืน: engine rethrow เสมอ)
 */
interface RadioSource {

    /** หน้าถัดไปของ "เพลงแนะนำต่อเนื่อง" ที่ตั้งต้นจาก [seed]; [cursor] = null คือหน้าแรก */
    suspend fun mixPage(seed: Track, cursor: RadioCursor?): RadioPage

    /** เพลงที่เกี่ยวข้องกับ [track] (แนะนำหลังเพลงนี้) */
    suspend fun related(track: Track): List<Track>

    /** เพลงอื่นของศิลปินเดียวกับ [track] — แหล่งสำรองสุดท้ายเมื่อสองแหล่งแรกไม่ได้ผล */
    suspend fun byArtist(track: Track): List<Track>
}
