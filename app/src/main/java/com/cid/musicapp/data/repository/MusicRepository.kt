package com.cid.musicapp.data.repository

import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.config.AppSettings
import io.github.shalva97.initNewPipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.math.abs

/** ผลค้นหาหนึ่งหน้า พร้อมบอกว่ายังมีหน้าถัดไปให้โหลดเพิ่มไหม (ใช้ทำ infinite scroll) */
data class SearchResultPage(val tracks: List<Track>, val hasMore: Boolean)

/**
 * ค้นหา + ดึงลิงก์เสียงจาก YouTube โดยตรงในแอป ผ่าน NewPipeExtractor (ห่อด้วย NewValve)
 * ไม่มี backend แยกอีกต่อไป — ทุกอย่างทำงานในเครื่องเดียวกับแอป
 */
class MusicRepository(private val appSettings: AppSettings) {

    private val youtube = ServiceList.YouTube
    private var initialized = false

    // แคชลิงก์เสียงที่ resolve แล้ว (แยกตาม kbps ที่เลือกด้วย) กันดึงซ้ำถ้ากดเพลงเดิมอีกรอบเร็วๆ
    // (ลิงก์จริงจาก YouTube มีอายุหลายชั่วโมง แต่กันไว้แค่ 20 นาทีพอ เผื่อกรณีลิงก์ใช้ไม่ได้)
    private val streamUrlCache = mutableMapOf<String, Pair<String, Long>>()
    private val cacheTtlMillis = AppConstants.STREAM_CACHE_TTL_MILLIS

    // session ของการค้นหาปัจจุบัน — ต้องเก็บ extractor instance เดิมไว้เพราะ getPage(nextPage)
    // เป็น method ของ extractor ตัวเดิมเท่านั้น (สร้างตัวใหม่แล้วเรียก getPage จะ error)
    // หมายเหตุ: ตั้งใจให้รองรับแค่ 1 การค้นหาที่ active อยู่ในแต่ละครั้ง (แอปนี้มีหน้าค้นหาเดียว
    // ไม่มีหลาย search session พร้อมกัน) — ถ้าจะเพิ่ม multi-session ในอนาคตค่อยเปลี่ยนเป็น map ตาม query
    private var activeSearchExtractor: SearchExtractor? = null
    private var nextSearchPage: Page? = null

    private fun ensureInitialized() {
        if (!initialized) {
            initNewPipe()
            initialized = true
        }
    }

    /** ค้นหาหน้าแรก — เริ่ม session ใหม่เสมอ (ทิ้ง session ค้นหาก่อนหน้า ถ้ามี) */
    suspend fun search(query: String): SearchResultPage = withContext(Dispatchers.IO) {
        ensureInitialized()

        val extractor = youtube.getSearchExtractor(query, emptyList(), "")
        extractor.fetchPage()

        val page = extractor.initialPage
        activeSearchExtractor = extractor
        nextSearchPage = page.nextPage

        SearchResultPage(
            tracks = page.items.toTracks(),
            hasMore = page.nextPage != null
        )
    }

    /** โหลดผลค้นหาหน้าถัดไปของ session ที่ค้นหาไว้ล่าสุดด้วย search() — เรียกตอนเลื่อนจนใกล้สุดลิสต์ */
    suspend fun loadMoreSearchResults(): SearchResultPage = withContext(Dispatchers.IO) {
        val extractor = activeSearchExtractor
        val page = nextSearchPage

        if (extractor == null || page == null) {
            return@withContext SearchResultPage(tracks = emptyList(), hasMore = false)
        }

        val nextInfoPage = extractor.getPage(page)
        nextSearchPage = nextInfoPage.nextPage

        SearchResultPage(
            tracks = nextInfoPage.items.toTracks(),
            hasMore = nextInfoPage.nextPage != null
        )
    }

    private fun List<InfoItem>.toTracks(): List<Track> =
        filterIsInstance<StreamInfoItem>().map { item ->
            Track(
                id = item.url,
                title = item.name,
                artist = item.uploaderName ?: "Unknown",
                durationSeconds = item.duration.toInt().takeIf { it > 0 },
                thumbnailUrl = item.thumbnails.firstOrNull()?.url
            )
        }

    /**
     * ดึงลิงก์เสียงตรง (progressive stream) สำหรับ track หนึ่งตัว
     * เลือกสตรีมเสียงล้วน (audio-only) ที่บิตเรตใกล้เคียงเป้าหมาย (kbps) ที่ตั้งไว้ในหน้าตั้งค่าที่สุด
     */
    suspend fun resolveAudioStreamUrl(track: Track): String = withContext(Dispatchers.IO) {
        ensureInitialized()

        val targetKbps = appSettings.audioBitrateKbpsFlow.first()
        val cacheKey = "${track.id}:$targetKbps"

        val cached = streamUrlCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.second < cacheTtlMillis) {
            return@withContext cached.first
        }

        val extractor = youtube.getStreamExtractor(track.id)
        extractor.fetchPage()

        val chosen = selectStreamForBitrate(extractor.audioStreams, targetKbps)
            ?: throw IllegalStateException("ไม่พบสตรีมเสียงสำหรับเพลงนี้")

        streamUrlCache[cacheKey] = chosen.content to now
        chosen.content
    }

    /** เลือกสตรีมที่บิตเรตใกล้เคียงเป้าหมายที่สุด (ถ้าตั้ง kbps สูงเกินที่มีจริง จะได้ตัวสูงสุดที่มีโดยอัตโนมัติ) */
    private fun selectStreamForBitrate(streams: List<AudioStream>, targetKbps: Int): AudioStream? {
        if (streams.isEmpty()) return null
        val targetBps = targetKbps * 1000
        return streams.minByOrNull { abs(it.averageBitrate - targetBps) }
    }

    /** ล้างแคชลิงก์เสียงที่ resolve ไว้ทั้งหมด (เรียกจากหน้าตั้งค่า) */
    fun clearStreamCache() {
        streamUrlCache.clear()
    }

    /** จำนวนลิงก์เสียงที่แคชไว้ตอนนี้ (ไว้โชว์ในโหมดนักพัฒนา) */
    fun cachedStreamCount(): Int = streamUrlCache.size
}
