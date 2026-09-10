package com.cid.musicapp.data.repository

import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.config.AppSettings
import io.github.shalva97.initNewPipe
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** ผลค้นหาหนึ่งหน้า พร้อมบอกว่ายังมีหน้าถัดไปให้โหลดเพิ่มไหม (ใช้ทำ infinite scroll) */
data class SearchResultPage(val tracks: List<Track>, val hasMore: Boolean)

/**
 * ค้นหา + ดึงลิงก์เสียงจาก YouTube โดยตรงในแอป ผ่าน NewPipeExtractor (ห่อด้วย NewValve)
 * ไม่มี backend แยกอีกต่อไป — ทุกอย่างทำงานในเครื่องเดียวกับแอป
 */
class MusicRepository(private val appSettings: AppSettings) {

    private val youtube = ServiceList.YouTube

    // NewPipe ต้อง init ครั้งเดียวต่อโปรเซส — แต่ resolve/search ทำงานบน Dispatchers.IO ซึ่งมีหลาย
    // thread เข้าถึงพร้อมกันได้จริง การเช็ค Boolean ธรรมดาแบบเดิมมีช่องให้สอง thread เห็น initialized=false
    // พร้อมกันแล้ว init ซ้ำทั้งคู่ จึงใช้ double-checked locking (@Volatile + synchronized) แทน
    // (ตั้ง flag หลัง init สำเร็จเสมอ — ถ้า init โยน exception รอบหน้าจะลองใหม่ได้)
    @Volatile
    private var initialized = false
    private val initLock = Any()

    // แคชลิงก์เสียง/วิดีโอที่ resolve แล้ว แยกตาม kbps/ความสูงที่เลือก — กันดึงซ้ำถ้ากดเพลงเดิมอีกรอบเร็วๆ
    // (ลิงก์จริงจาก YouTube มีอายุหลายชั่วโมง แต่กันไว้แค่ STREAM_CACHE_TTL_MILLIS พอ เผื่อกรณีลิงก์ใช้ไม่ได้)
    // นโยบายแคช (TTL + จำกัดจำนวน + LRU eviction) อยู่ในคลาส StreamUrlCache ทั้งหมด
    private val streamUrlCache = StreamUrlCache(
        ttlMillis = AppConstants.STREAM_CACHE_TTL_MILLIS,
        maxEntries = AppConstants.MAX_STREAM_CACHE_ENTRIES
    )

    private val searchSessions = SearchSessionStore()

    /** Called immediately on query edits, including clear and below-debounce-length queries. */
    fun invalidateSearch() { searchSessions.invalidate() }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(initLock) {
            if (!initialized) {
                initNewPipe()
                initialized = true
            }
        }
    }

    /** ค้นหาหน้าแรก — เริ่ม session ใหม่เสมอ (ทิ้ง session ค้นหาก่อนหน้า ถ้ามี) */
    suspend fun search(query: String): SearchResultPage {
        // Reserve before dispatch: IO scheduling must not decide which query is newest.
        val generation = searchSessions.invalidate()
        return withContext(Dispatchers.IO) {
            ensureInitialized()
            val extractor = youtube.getSearchExtractor(query, emptyList(), "")
            extractor.fetchPage()
            val page = extractor.initialPage
            currentCoroutineContext().ensureActive()
            searchSessions.publish(SearchSessionStore.Session(generation, extractor, page.nextPage))
            SearchResultPage(page.items.toTracks(), Page.isValid(page.nextPage))
        }
    }

    /** Continue the captured session; stale completions cannot advance a newer cursor. */
    suspend fun loadMoreSearchResults(): SearchResultPage = withContext(Dispatchers.IO) {
        val session = searchSessions.snapshot()
            ?: return@withContext SearchResultPage(emptyList(), false)
        if (!Page.isValid(session.nextPage)) {
            return@withContext SearchResultPage(emptyList(), false)
        }
        val page = session.extractor.getPage(session.nextPage)
        currentCoroutineContext().ensureActive()
        searchSessions.advance(session, page.nextPage)
        SearchResultPage(page.items.toTracks(), Page.isValid(page.nextPage))
    }

    private fun List<InfoItem>.toTracks(): List<Track> =
        filterIsInstance<StreamInfoItem>().map { item ->
            Track(
                id = item.url,
                title = item.name,
                artist = item.uploaderName ?: "Unknown",
                durationSeconds = item.duration.toInt().takeIf { it > 0 },
                thumbnailUrl = item.thumbnails.bestThumbnailUrl()
            )
        }

    /**
     * เลือก thumbnail ที่ "พอดี" กับการใช้งาน — เดิมใช้ firstOrNull() ซึ่งรายการจาก YouTube มักเรียง
     * รูปเล็กสุดไว้หน้าแรก (~90px ขยายมาโชว์ 56dp แล้วพร่ามัว และตอนขึ้นหน้ากำลังเล่นภาพใหญ่ก็ยิ่งแตก)
     * ในทางกลับกันถ้าเอารูปใหญ่สุด (maxres 1280px+) มาแสดงเป็น thumbnail เล็กๆ ก็เปลือง
     * bandwidth/หน่วยความจำเปล่าๆ — เลือกรูปตัวเล็กสุดที่สูง >= THUMBNAIL_MIN_HEIGHT_PX (พอดีตัว)
     * ถ้าไม่มีตัวไหนผ่านเกณฑ์เลย ค่อยเอาตัวสูงสุดที่มีแทน (ดีกว่าไม่มีรูป)
     * รายการที่ไม่รู้ขนาด (HEIGHT_UNKNOWN) ถือว่าไม่ผ่านเกณฑ์ขนาด แต่ยังใช้เป็น fallback ได้
     */
    private fun List<Image>.bestThumbnailUrl(): String? {
        if (isEmpty()) return null
        val knownSize = filter { it.height != Image.HEIGHT_UNKNOWN }
        val pool = if (knownSize.isEmpty()) this else knownSize
        return (pool.filter { it.height >= AppConstants.THUMBNAIL_MIN_HEIGHT_PX }
            .minByOrNull { it.height }
            ?: pool.maxByOrNull { it.height })
            ?.url
    }

    /**
     * ดึงลิงก์เสียงตรง (progressive stream) สำหรับ track หนึ่งตัว
     * เลือกสตรีมเสียงล้วน (audio-only) ที่บิตเรตใกล้เคียงเป้าหมาย (kbps) ที่ตั้งไว้ในหน้าตั้งค่าที่สุด
     */
    suspend fun resolveAudioStreamUrl(track: Track): String = withContext(Dispatchers.IO) {
        ensureInitialized()

        val targetKbps = appSettings.audioBitrateKbpsFlow.first()
        val cacheKey = "${track.id}:$targetKbps"

        val cacheGeneration = streamUrlCache.generation()
        streamUrlCache.get(cacheKey)?.let { return@withContext it }

        val extractor = youtube.getStreamExtractor(track.id)
        extractor.fetchPage()

        val chosen = StreamSelector.audio(extractor.audioStreams, targetKbps)
            ?: throw IllegalStateException("ไม่พบสตรีมเสียงสำหรับเพลงนี้")

        currentCoroutineContext().ensureActive()
        streamUrlCache.put(cacheKey, chosen.content, cacheGeneration)
        chosen.content
    }

    /**
     * ดึงลิงก์วิดีโอ (มีเสียงในตัว) สำหรับโหมดวิดีโอ
     * ใช้เฉพาะ `videoStreams` (muxed audio+video) เท่านั้น — ไม่ใช้ `videoOnlyStreams` (ที่คุณภาพสูงกว่า
     * แต่แยกไฟล์เสียง/วิดีโอคนละสตรีม) เพราะการเล่นคู่กันต้องใช้ MergingMediaSource เพิ่ม ซึ่งเพิ่มความซับซ้อน
     * ของ PlaybackService ไม่คุ้มกับโปรเจกต์ขนาดนี้ตอนนี้
     * TODO(debt): ถ้าต้องการวิดีโอความละเอียดสูงกว่า 720p ในอนาคต ค่อยเปลี่ยนมาใช้ videoOnlyStreams
     * + MergingMediaSource(videoSource, audioSource) แทน
     */
    suspend fun resolveVideoStreamUrl(track: Track): String = withContext(Dispatchers.IO) {
        ensureInitialized()

        val targetHeightPx = appSettings.videoHeightPxFlow.first()
        val cacheKey = "${track.id}:video:$targetHeightPx"

        val cacheGeneration = streamUrlCache.generation()
        streamUrlCache.get(cacheKey)?.let { return@withContext it }

        val extractor = youtube.getStreamExtractor(track.id)
        extractor.fetchPage()

        val chosen = StreamSelector.video(extractor.videoStreams, targetHeightPx)
            ?: throw IllegalStateException("ไม่พบสตรีมวิดีโอสำหรับเพลงนี้")

        currentCoroutineContext().ensureActive()
        streamUrlCache.put(cacheKey, chosen.content, cacheGeneration)
        chosen.content
    }

    /** ล้างแคชลิงก์เสียงที่ resolve ไว้ทั้งหมด (เรียกจากหน้าตั้งค่า) */
    fun clearStreamCache() {
        streamUrlCache.clear()
    }

    /** จำนวนลิงก์เสียงที่แคชไว้ตอนนี้ (ไว้โชว์ในโหมดนักพัฒนา) */
    fun cachedStreamCount(): Int = streamUrlCache.size()
}
