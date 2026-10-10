package com.cid.musicapp.data.repository

import com.cid.musicapp.config.AppConstants
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * แปลงผลจาก NewPipeExtractor เป็น [Track] — แยกออกมาจาก MusicRepository เพราะทั้งผลค้นหา (MusicRepository)
 * และแหล่งเพลง Radio (YoutubeRadioSource) ใช้ร่วมกัน; รับเฉพาะ StreamInfoItem (ข้าม playlist/channel)
 */
internal fun List<InfoItem>.toTracks(): List<Track> =
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
internal fun List<Image>.bestThumbnailUrl(): String? {
    if (isEmpty()) return null
    val knownSize = filter { it.height != Image.HEIGHT_UNKNOWN }
    val pool = if (knownSize.isEmpty()) this else knownSize
    return (pool.filter { it.height >= AppConstants.THUMBNAIL_MIN_HEIGHT_PX }
        .minByOrNull { it.height }
        ?: pool.maxByOrNull { it.height })
        ?.url
}
