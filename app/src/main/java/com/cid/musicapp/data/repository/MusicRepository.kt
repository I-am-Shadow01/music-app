package com.cid.musicapp.data.repository

import io.github.shalva97.initNewPipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * ค้นหา + ดึงลิงก์เสียงจาก YouTube โดยตรงในแอป ผ่าน NewPipeExtractor (ห่อด้วย NewValve)
 * ไม่มี backend แยกอีกต่อไป — ทุกอย่างทำงานในเครื่องเดียวกับแอป
 */
class MusicRepository {

    private val youtube = ServiceList.YouTube
    private var initialized = false

    // แคชลิงก์เสียงที่ resolve แล้ว กันดึงซ้ำถ้ากดเพลงเดิมอีกรอบเร็วๆ (ลิงก์จริงจาก YouTube
    // มีอายุหลายชั่วโมง แต่กันไว้แค่ 20 นาทีพอ เผื่อกรณีเปลี่ยน IP/region แล้วลิงก์ใช้ไม่ได้)
    private val streamUrlCache = mutableMapOf<String, Pair<String, Long>>()
    private val cacheTtlMillis = 20 * 60 * 1000L

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
     * เลือกสตรีมเสียงล้วน (audio-only) ที่บิตเรตสูงสุดที่มี
     */
    suspend fun resolveAudioStreamUrl(track: Track): String = withContext(Dispatchers.IO) {
        ensureInitialized()

        val cached = streamUrlCache[track.id]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.second < cacheTtlMillis) {
            return@withContext cached.first
        }

        val extractor = youtube.getStreamExtractor(track.id)
        extractor.fetchPage()

        val bestAudio = extractor.audioStreams
            .maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("ไม่พบสตรีมเสียงสำหรับเพลงนี้")

        streamUrlCache[track.id] = bestAudio.content to now
        bestAudio.content
    }
}
