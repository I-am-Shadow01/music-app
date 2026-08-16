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
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
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
    // ConcurrentHashMap แทน mutableMapOf ธรรมดา — resolveAudioStreamUrl/resolveVideoStreamUrl เขียนแคช
    // นี้จาก Dispatchers.IO ซึ่งอาจมีมากกว่า 1 coroutine ทำงานทับซ้อนกันได้จริงในบางจังหวะ (ดูเหตุผลเต็มๆ
    // ที่ comment ของ searchGeneration ด้านล่าง — root cause เดียวกัน) HashMap ธรรมดาไม่ thread-safe
    // ต่อการเขียนพร้อมกันจากหลาย thread
    private val streamUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val cacheTtlMillis = AppConstants.STREAM_CACHE_TTL_MILLIS

    // session ของการค้นหาปัจจุบัน — ต้องเก็บ extractor instance เดิมไว้เพราะ getPage(nextPage)
    // เป็น method ของ extractor ตัวเดิมเท่านั้น (สร้างตัวใหม่แล้วเรียก getPage จะ error)
    // หมายเหตุ: ตั้งใจให้รองรับแค่ 1 การค้นหาที่ active อยู่ในแต่ละครั้ง (แอปนี้มีหน้าค้นหาเดียว
    // ไม่มีหลาย search session พร้อมกัน) — ถ้าจะเพิ่ม multi-session ในอนาคตค่อยเปลี่ยนเป็น map ตาม query
    private var activeSearchExtractor: SearchExtractor? = null
    private var nextSearchPage: Page? = null

    // นับรุ่นของ search session — ป้องกัน race ที่ SearchViewModel.searchJob?.cancel() เพียงอย่างเดียว
    // ปิดไม่สนิท: extractor.fetchPage()/getPage() เป็น blocking call ของ NewPipeExtractor ไม่ใช่ suspend
    // fun ที่เช็ค cancellation ระหว่างทาง เรียก cancel() แล้วตัว thread ที่ block รอ network อยู่ "ไม่หยุด
    // ทันที" — มันจะรันจนจบ block ก่อน ค่อยโดน CancellationException ตอน resume กลับ ถ้าคำค้นหาเก่ากว่า
    // (ที่โดน cancel ไปแล้ว) ตอบกลับมาช้ากว่าคำค้นหาใหม่ กฎ "ต้องเป็น search ล่าสุดเท่านั้นถึงจะเขียนทับ
    // activeSearchExtractor/nextSearchPage" นี้คือด่านที่สองที่ปิดช่องโหว่จริง — ใช้ AtomicInteger เพราะ
    // ตัวเลขนี้ increment/read ข้าม thread ของ Dispatchers.IO ได้
    private val searchGeneration = AtomicInteger(0)

    private fun ensureInitialized() {
        if (!initialized) {
            initNewPipe()
            initialized = true
        }
    }

    /** ค้นหาหน้าแรก — เริ่ม session ใหม่เสมอ (ทิ้ง session ค้นหาก่อนหน้า ถ้ามี) */
    suspend fun search(query: String): SearchResultPage = withContext(Dispatchers.IO) {
        ensureInitialized()

        // จองรุ่นของตัวเองไว้ก่อนเริ่ม blocking call — ดู comment ที่ field searchGeneration
        val myGeneration = searchGeneration.incrementAndGet()

        val extractor = youtube.getSearchExtractor(query, emptyList(), "")
        extractor.fetchPage()

        val page = extractor.initialPage

        // เขียนทับ session state ร่วมได้ก็ต่อเมื่อยังเป็น search รุ่นล่าสุดจริงตอนนี้เท่านั้น
        // ถ้ามี search ใหม่กว่าเริ่มไปแล้วระหว่างที่ตัวนี้ยัง block รอ network อยู่ (พิมพ์คำค้นหาใหม่เร็วๆ)
        // ต้องทิ้งผลลัพธ์เก่าที่มาช้ากว่านี้ไป ไม่งั้น activeSearchExtractor/nextSearchPage จะกลายเป็นของ
        // คำค้นหาเก่า ทั้งที่ UI กำลังโชว์ผลของคำค้นหาใหม่อยู่ — ทำให้ loadMore() ไปดึงหน้าถัดไปผิดคำ
        if (myGeneration == searchGeneration.get()) {
            activeSearchExtractor = extractor
            nextSearchPage = page.nextPage
        }

        SearchResultPage(
            tracks = page.items.toTracks(),
            hasMore = page.nextPage != null
        )
    }

    /** โหลดผลค้นหาหน้าถัดไปของ session ที่ค้นหาไว้ล่าสุดด้วย search() — เรียกตอนเลื่อนจนใกล้สุดลิสต์ */
    suspend fun loadMoreSearchResults(): SearchResultPage = withContext(Dispatchers.IO) {
        // จำรุ่นของ session ที่กำลังจะต่อหน้าไว้ ณ ตอนเริ่ม (ไม่ใช่ session ใหม่ เลยไม่ increment)
        val myGeneration = searchGeneration.get()
        val extractor = activeSearchExtractor
        val page = nextSearchPage

        if (extractor == null || page == null) {
            return@withContext SearchResultPage(tracks = emptyList(), hasMore = false)
        }

        val nextInfoPage = extractor.getPage(page)

        // เช็คแบบเดียวกับใน search() ด้านบน — กัน session เก่าที่ getPage() เพิ่งตอบกลับมาช้า ไปเขียนทับ
        // nextSearchPage ของ search รุ่นใหม่กว่าที่เริ่มไปแล้วระหว่างรอ (ผลลัพธ์ของ call นี้เองถูกทิ้งไป
        // อยู่แล้วที่ฝั่ง SearchViewModel ผ่าน CancellationException — จุดนี้กันแค่ field ภายในไม่ให้เพี้ยน)
        if (myGeneration == searchGeneration.get()) {
            nextSearchPage = nextInfoPage.nextPage
        }

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

        val cached = streamUrlCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.second < cacheTtlMillis) {
            return@withContext cached.first
        }

        val extractor = youtube.getStreamExtractor(track.id)
        extractor.fetchPage()

        val chosen = selectStreamForHeight(extractor.videoStreams, targetHeightPx)
            ?: throw IllegalStateException("ไม่พบสตรีมวิดีโอสำหรับเพลงนี้")

        streamUrlCache[cacheKey] = chosen.content to now
        chosen.content
    }

    /** เลือกสตรีมวิดีโอที่ความสูง (px) ใกล้เคียงเป้าหมายที่สุด */
    private fun selectStreamForHeight(streams: List<VideoStream>, targetHeightPx: Int): VideoStream? {
        if (streams.isEmpty()) return null
        return streams.minByOrNull { abs(parseResolutionHeight(it.resolution) - targetHeightPx) }
    }

    /** แปลง resolution string ของ NewPipeExtractor (เช่น "720p60", "480p") เป็นความสูง px ล้วนๆ */
    private fun parseResolutionHeight(resolution: String): Int =
        resolution.takeWhile { it.isDigit() }.toIntOrNull() ?: 0

    /** ล้างแคชลิงก์เสียงที่ resolve ไว้ทั้งหมด (เรียกจากหน้าตั้งค่า) */
    fun clearStreamCache() {
        streamUrlCache.clear()
    }

    /** จำนวนลิงก์เสียงที่แคชไว้ตอนนี้ (ไว้โชว์ในโหมดนักพัฒนา) */
    fun cachedStreamCount(): Int = streamUrlCache.size
}
