package com.cid.musicapp.radio

import com.cid.musicapp.data.repository.Track
import com.cid.musicapp.history.TasteProfile
import kotlinx.coroutines.CancellationException

/**
 * สมองของ Radio: ตัดสินว่าเพลงถัดไปควรมาจากไหน แล้วส่งให้ [RadioSelector] กรอง/กันซ้ำ/จัดอันดับ
 *
 * ลำดับแหล่ง (ไล่ต่อเมื่อแหล่งก่อนหน้าหมด/ล้มเหลว และยังเติมไม่ครบ):
 *  0. ศิลปินที่ผู้ใช้ฟังบ่อย — แทรกหนึ่งเพลงทุกๆ [RadioConfig.tasteArtistEvery] เพลง (เมื่อมีรสนิยมจากประวัติ)
 *  1. Mix ที่ตั้งต้นจากเพลงที่ผู้ใช้เลือก (แบ่งหน้าต่อเนื่องได้ ไม่ซ้ำ และเปลี่ยนไปตามที่เล่นมา)
 *  2. เพลงที่เกี่ยวข้องกับ "เพลงท้ายคิว" ตอนนี้ (autoplay แบบต่อเพลงต่อเพลง)
 *  3. เพลงอื่นของศิลปินท้ายคิว
 *
 * ไม่มีสถานะคิว — ผู้เรียกส่งคิวปัจจุบันเข้ามาทุกครั้ง (ไม่ต้องเก็บประวัติซ้ำสองที่)
 * เรียกจาก thread เดียวตามลำดับ (PlayerController เรียกจาก Main) แต่ session ที่โดนรีเซ็ตระหว่างรอเครือข่าย
 * จะไม่เขียนทับ state ของ session ใหม่ (เช็ค [sessionId] ทุกครั้งหลังกลับจาก suspend)
 *
 * @param tasteProvider ขอรสนิยมปัจจุบันทุกครั้งที่เติม (null = ไม่ใช้/ไม่มีข้อมูล → ทำงานแบบไม่ปรับตามรสนิยม)
 */
class RadioEngine(
    private val source: RadioSource,
    private val config: RadioConfig = RadioConfig(),
    private val selector: RadioSelector = RadioSelector(config),
    private val identities: TrackIdentityFactory = TrackIdentityFactory(config),
    private val tasteProvider: suspend () -> TasteProfile? = { null },
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

    // ศิลปินโปรดคนถัดไปที่จะลองในรอบเติมหน้า (วนไปเรื่อยๆ ไม่ให้ซ้ำแต่คนแรกทุกรอบ)
    private var tasteArtistCursor = 0
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
        tasteArtistCursor = 0
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

        val taste = guarded { tasteProvider() }
        if (session != sessionId) return emptyList()

        val anchor = queued.lastOrNull() ?: root
        val tasteTracks = ArrayList<Track>()
        val accepted = ArrayList<Track>(config.batchSize)

        fun absorb(candidates: List<Track>, into: MutableList<Track>, limit: Int, keepRemainder: Boolean = false) {
            val selection = selector.selectWithRemainder(candidates, queued + tasteTracks + accepted, limit, taste)
            into += selection.accepted
            if (keepRemainder) pool = selection.remainder.take(poolCap)
        }

        // 0) เพลงจากศิลปินที่ฟังบ่อย — เก็บแยกไว้ก่อน แล้วค่อยสลับแทรกกับเพลงจากแหล่งอื่นตอนท้าย
        if (taste != null) {
            val quota = tasteQuota()
            if (quota > 0) {
                collectFavoriteArtistTracks(taste, quota) { candidates ->
                    val before = tasteTracks.size
                    absorb(candidates, tasteTracks, quota - tasteTracks.size)
                    tasteTracks.size - before
                }
                if (session != sessionId) return emptyList()
            }
        }

        fun room() = config.batchSize - tasteTracks.size - accepted.size

        // ของเหลือจากรอบก่อน (ต้องผ่านตัวกรองกับคิวปัจจุบันใหม่ เผื่อผู้ใช้เพิ่มเพลงเข้าคิวระหว่างนั้น)
        if (pool.isNotEmpty() && room() > 0) {
            val leftovers = pool
            pool = emptyList()
            absorb(leftovers, accepted, room(), keepRemainder = true)
        }

        // 1) Mix
        var pages = 0
        while (room() > 0 && !mixExhausted && pages < config.maxPagesPerFill) {
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
            absorb(pool + page.tracks, accepted, room(), keepRemainder = true)
        }

        // 2) เพลงที่เกี่ยวข้องกับท้ายคิว (ใช้เมื่อ Mix หมด/ใช้ไม่ได้เท่านั้น)
        if (room() > 0 && mixExhausted && relatedTried.add(anchor.id)) {
            val related = guarded { source.related(anchor) }
            if (session != sessionId) return emptyList()
            if (related != null) absorb(related, accepted, room())
        }

        // 3) เพลงอื่นของศิลปินท้ายคิว
        if (room() > 0 && mixExhausted && artistTried.add(anchor.artist.lowercase())) {
            val byArtist = guarded { source.byArtist(anchor) }
            if (session != sessionId) return emptyList()
            if (byArtist != null) absorb(byArtist, accepted, room())
        }

        val batch = interleave(accepted, tasteTracks)
        if (batch.isEmpty()) retryNotBeforeMillis = clockMillis() + config.retryBackoffMillis
        return batch
    }

    /** จำนวนเพลงจากศิลปินโปรดต่อหนึ่งชุด: หนึ่งเพลงทุกๆ tasteArtistEvery (ปัดขึ้น); ตั้งค่า ≤ 0 = ปิดการแทรก */
    private fun tasteQuota(): Int =
        if (config.tasteArtistEvery <= 0) 0
        else (config.batchSize + config.tasteArtistEvery - 1) / config.tasteArtistEvery

    /**
     * ไล่ศิลปินโปรดทีละคน (หมุนเวียนข้ามรอบ) ค้นหาเพลงของเขา กรองให้เหลือเฉพาะเพลงที่ศิลปินนั้นจริงๆ
     * แล้วส่งให้ [consume] — หยุดเมื่อได้ครบ [quota] หรือลองครบ [RadioConfig.tasteArtistsTriedPerFill] คนแล้ว
     */
    private suspend fun collectFavoriteArtistTracks(
        taste: TasteProfile, quota: Int, consume: (List<Track>) -> Int
    ) {
        val top = taste.topArtists(config.tasteTopArtists)
        if (top.isEmpty()) return
        var got = 0
        val attempts = minOf(config.tasteArtistsTriedPerFill, top.size)
        repeat(attempts) {
            if (got >= quota) return
            val artist = top[tasteArtistCursor % top.size]
            tasteArtistCursor++
            val results = guarded { source.artistTracks(artist.name) } ?: return@repeat
            val byThatArtist = results.filter { track -> identities.artistKeys(track).any { it.key == artist.key } }
            got += consume(byThatArtist) // คืนจำนวนที่ตัวคัดเลือกรับจริง (หลังกรองซ้ำ/เล่นไม่ได้/เพิ่งฟัง)
        }
    }

    /** สลับแทรก: เพลงจากศิลปินโปรดหนึ่งเพลงหลังทุกๆ (tasteArtistEvery - 1) เพลงของแหล่งหลัก ที่เหลือต่อท้าย */
    private fun interleave(main: List<Track>, favorite: List<Track>): List<Track> {
        if (favorite.isEmpty()) return main
        val gap = (config.tasteArtistEvery - 1).coerceAtLeast(1)
        val result = ArrayList<Track>(main.size + favorite.size)
        val favoriteIterator = favorite.iterator()
        main.forEachIndexed { index, track ->
            result += track
            if ((index + 1) % gap == 0 && favoriteIterator.hasNext()) result += favoriteIterator.next()
        }
        while (favoriteIterator.hasNext()) result += favoriteIterator.next()
        return result
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
