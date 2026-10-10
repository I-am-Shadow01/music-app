package com.cid.musicapp.radio

import com.cid.musicapp.data.repository.Track
import kotlinx.coroutines.CancellationException

/**
 * สมองของ Radio: ตัดสินว่าเพลงถัดไปควรมาจากไหน แล้วส่งให้ [RadioSelector] กรอง/กันซ้ำ
 *
 * ลำดับแหล่ง (ไล่ต่อเมื่อแหล่งก่อนหน้าหมด/ล้มเหลว และยังเติมไม่ครบ):
 *  1. Mix ที่ตั้งต้นจากเพลงที่ผู้ใช้เลือก (แบ่งหน้าต่อเนื่องได้ ไม่ซ้ำ และเปลี่ยนไปตามที่เล่นมา)
 *  2. เพลงที่เกี่ยวข้องกับ "เพลงท้ายคิว" ตอนนี้ (autoplay แบบต่อเพลงต่อเพลง)
 *  3. เพลงอื่นของศิลปินท้ายคิว
 *
 * ไม่มีสถานะคิว — ผู้เรียกส่งคิวปัจจุบันเข้ามาทุกครั้ง (ไม่ต้องเก็บประวัติซ้ำสองที่)
 * เรียกจาก thread เดียวตามลำดับ (PlayerController เรียกจาก Main) แต่ session ที่โดนรีเซ็ตระหว่างรอเครือข่าย
 * จะไม่เขียนทับ state ของ session ใหม่ (เช็ค [sessionId] ทุกครั้งหลังกลับจาก suspend)
 */
class RadioEngine(
    private val source: RadioSource,
    private val config: RadioConfig = RadioConfig(),
    private val selector: RadioSelector = RadioSelector(config),
    private val clockMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }
) {

    // ขนาดสูงสุดของ pool — พอรองรับที่เหลือจากหนึ่งรอบเติมเต็มๆ (หน้า Mix ละหลายสิบเพลง) โดยไม่โตไม่จำกัด
    private val poolCap = config.batchSize * config.maxPagesPerFill

    private var sessionId = 0
    private var seed: Track? = null
    private var mixCursor: RadioCursor? = null
    private var mixExhausted = false
    // เพลงที่ดึงมาแล้วแต่ยังไม่ได้ใช้ (รับครบก่อน/โดนเลื่อนเพราะโควตาศิลปิน) — ใช้ก่อนไปดึงหน้าใหม่ในรอบหน้า
    // ไม่งั้น cursor ที่เดินผ่านไปแล้วจะทำให้เพลงพวกนี้หายไปเฉยๆ
    private var pool: List<Track> = emptyList()
    private val relatedTried = HashSet<String>()
    private val artistTried = HashSet<String>()
    private var retryNotBeforeMillis = Long.MIN_VALUE

    /** เริ่ม session ใหม่จากเพลงที่ผู้ใช้เลือก — ล้างสถานะของ session เก่าทั้งหมด */
    fun start(seed: Track) {
        reset()
        this.seed = seed
    }

    /** ปิด Radio (เล่นลิสต์แบบปกติ/หยุดเล่น) — fill ที่ค้างอยู่จะถูกทิ้งผลเอง */
    fun stop() = reset()

    val isActive: Boolean get() = seed != null

    private fun reset() {
        sessionId++
        seed = null
        mixCursor = null
        mixExhausted = false
        pool = emptyList()
        relatedTried.clear()
        artistTried.clear()
        retryNotBeforeMillis = Long.MIN_VALUE
    }

    /**
     * ดึงเพลงแนะนำชุดใหม่ (สูงสุด [RadioConfig.batchSize] เพลง) ที่ไม่ซ้ำกับ [queued]
     * @param queued คิวทั้งหมดตอนนี้ เรียงตามลำดับเล่น (ท้ายสุด = เพลงที่จะเล่นหลังสุด)
     * @return ลิสต์ว่างได้เสมอเมื่อหมดแหล่ง/เครือข่ายล้ม/ยังอยู่ในช่วงรอลองใหม่ — ไม่โยน exception (ยกเว้น cancel)
     */
    suspend fun nextBatch(queued: List<Track>): List<Track> {
        val session = sessionId
        val root = seed ?: return emptyList()
        if (clockMillis() < retryNotBeforeMillis) return emptyList()

        val anchor = queued.lastOrNull() ?: root
        val accepted = ArrayList<Track>(config.batchSize)

        // ใช้ผลคัดเลือกเดียวกันทุกแหล่ง; ส่วนที่ยังไม่ได้ตัดสินจากแหล่ง Mix เก็บเข้า pool (จำกัดขนาดกัน pool โตไม่หยุด)
        fun absorb(candidates: List<Track>, keepRemainder: Boolean = false) {
            val selection = selector.selectWithRemainder(
                candidates, queued + accepted, config.batchSize - accepted.size
            )
            accepted += selection.accepted
            if (keepRemainder) pool = selection.remainder.take(poolCap)
        }

        // 0) ของเหลือจากรอบก่อน (ต้องผ่านตัวกรองกับคิวปัจจุบันใหม่ เผื่อผู้ใช้เพิ่มเพลงเข้าคิวระหว่างนั้น)
        if (pool.isNotEmpty()) {
            val leftovers = pool
            pool = emptyList()
            absorb(leftovers, keepRemainder = true)
        }

        // 1) Mix
        var pages = 0
        while (accepted.size < config.batchSize && !mixExhausted && pages < config.maxPagesPerFill) {
            pages++
            val page = guarded { source.mixPage(root, mixCursor) }
            if (session != sessionId) return emptyList()
            if (page == null) {
                mixExhausted = true // ล้มเหลว → เลิกใช้ Mix ใน session นี้ ไปแหล่งสำรอง
                break
            }
            mixCursor = page.next
            if (page.next == null) mixExhausted = true
            // รวมกับ pool ที่ยังเหลือ (ถ้ารอบนี้ pool ยังไม่หมด) เพื่อไม่ให้เขียนทับของเก่าที่ยังไม่ได้ใช้
            absorb(pool + page.tracks, keepRemainder = true)
        }

        // 2) เพลงที่เกี่ยวข้องกับท้ายคิว (ใช้เมื่อ Mix หมด/ใช้ไม่ได้เท่านั้น)
        if (accepted.size < config.batchSize && mixExhausted && relatedTried.add(anchor.id)) {
            val related = guarded { source.related(anchor) }
            if (session != sessionId) return emptyList()
            if (related != null) absorb(related)
        }

        // 3) เพลงอื่นของศิลปินท้ายคิว
        if (accepted.size < config.batchSize && mixExhausted && artistTried.add(anchor.artist.lowercase())) {
            val byArtist = guarded { source.byArtist(anchor) }
            if (session != sessionId) return emptyList()
            if (byArtist != null) absorb(byArtist)
        }

        if (accepted.isEmpty()) retryNotBeforeMillis = clockMillis() + config.retryBackoffMillis
        return accepted
    }

    /** รัน [block] โดยกลืน exception ทั่วไป (คืน null) แต่ปล่อย cancel ผ่านเสมอ ไม่งั้น job ที่โดนยกเลิกจะไม่หยุดจริง */
    private inline fun <T> guarded(block: () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
