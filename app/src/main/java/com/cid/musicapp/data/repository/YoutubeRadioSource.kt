package com.cid.musicapp.data.repository

import com.cid.musicapp.radio.RadioCursor
import com.cid.musicapp.radio.RadioPage
import com.cid.musicapp.radio.RadioSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor

/**
 * แหล่งเพลงแนะนำของ Radio จาก YouTube ผ่าน NewPipeExtractor
 *
 * สร้าง extractor ของตัวเองทุกครั้ง **ไม่แตะ search session ของ MusicRepository** — ไม่งั้นการเติมคิวเบื้องหลัง
 * จะไปทำให้ผลค้นหา/การโหลดหน้าถัดไปบนหน้าค้นหาถูกยกเลิก (repository มี session ค้นหาที่ active ได้ตัวเดียว)
 */
class YoutubeRadioSource(private val repository: MusicRepository) : RadioSource {

    private val youtube = ServiceList.YouTube

    /** ตัวชี้ต่อของ Mix: ต้องถือ extractor ตัวเดิมไว้ด้วย เพราะ YouTube ผูกหน้าถัดไปกับ cookie ของ session นั้น (กันเพลงซ้ำ) */
    private class MixCursor(val extractor: PlaylistExtractor, val nextPage: Page) : RadioCursor

    /**
     * YouTube Mix ของวิดีโอ (playlist id = "RD" + videoId) — เพลงแนะนำต่อเนื่องที่ YouTube เรียงให้เอง
     * แบ่งหน้าได้เรื่อยๆ; หน้าแรกจะมีเพลงตั้งต้นรวมอยู่ด้วย (ตัวคัดเลือกกันซ้ำให้เอง)
     */
    override suspend fun mixPage(seed: Track, cursor: RadioCursor?): RadioPage = withContext(Dispatchers.IO) {
        repository.ensureInitialized()

        val (extractor, page) = if (cursor == null) {
            val videoId = youtube.streamLHFactory.getId(seed.id)
            val mixExtractor = youtube.getPlaylistExtractor("$WATCH_URL_PREFIX$videoId&list=$MIX_ID_PREFIX$videoId")
            mixExtractor.fetchPage()
            mixExtractor to mixExtractor.initialPage
        } else {
            val mix = cursor as? MixCursor
                ?: throw IllegalArgumentException("cursor ไม่ได้มาจาก YoutubeRadioSource")
            mix.extractor to mix.extractor.getPage(mix.nextPage)
        }

        currentCoroutineContext().ensureActive()
        val next = page.nextPage?.takeIf { Page.isValid(it) }
        RadioPage(page.items.toTracks(), next?.let { MixCursor(extractor, it) })
    }

    /** รายการแนะนำข้างวิดีโอ (related) — ได้ null/ว่างได้ถ้าวิดีโอจำกัดอายุหรือ YouTube ไม่ส่งมา */
    override suspend fun related(track: Track): List<Track> = withContext(Dispatchers.IO) {
        repository.ensureInitialized()
        val extractor = youtube.getStreamExtractor(track.id)
        extractor.fetchPage()
        currentCoroutineContext().ensureActive()
        extractor.relatedItems?.items.orEmpty().toTracks()
    }

    /** ค้นหาด้วยชื่อศิลปิน/ช่องของเพลงนี้ — แหล่งสำรองสุดท้าย */
    override suspend fun byArtist(track: Track): List<Track> = withContext(Dispatchers.IO) {
        repository.ensureInitialized()
        val extractor = youtube.getSearchExtractor(track.artist, emptyList(), "")
        extractor.fetchPage()
        currentCoroutineContext().ensureActive()
        extractor.initialPage.items.toTracks()
    }

    private companion object {
        // รูปแบบ URL ที่ YoutubePlaylistLinkHandlerFactory รับสำหรับ Mix: watch?v={videoId}&list=RD{videoId}
        const val WATCH_URL_PREFIX = "https://www.youtube.com/watch?v="
        const val MIX_ID_PREFIX = "RD"
    }
}
