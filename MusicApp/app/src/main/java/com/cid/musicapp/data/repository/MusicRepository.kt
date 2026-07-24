package com.cid.musicapp.data.repository

import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.config.AppSettings
import io.github.shalva97.initNewPipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.math.abs

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

    private fun ensureInitialized() {
        if (!initialized) {
            initNewPipe()
            initialized = true
        }
    }

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        ensureInitialized()

        val extractor = youtube.getSearchExtractor(query, emptyList(), "")
        extractor.fetchPage()

        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                Track(
                    id = item.url,
                    title = item.name,
                    artist = item.uploaderName ?: "Unknown",
                    durationSeconds = item.duration.toInt().takeIf { it > 0 },
                    thumbnailUrl = item.thumbnails.firstOrNull()?.url
                )
            }
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
