package com.cid.musicapp.radio

import com.cid.musicapp.config.AppConstants

/**
 * ค่าปรับแต่งของ Radio ทั้งหมดรวมไว้ที่เดียว — ค่า default มาจาก [AppConstants] เท่านั้น
 * (แยกเป็น data class เพื่อให้เทสต์ส่งค่าเล็กๆ ของตัวเองเข้ามาได้ โดยไม่ต้องแก้ค่าจริงของแอป)
 */
data class RadioConfig(
    val lowWatermark: Int = AppConstants.RADIO_LOW_WATERMARK,
    val batchSize: Int = AppConstants.RADIO_BATCH_SIZE,
    val maxPagesPerFill: Int = AppConstants.RADIO_MAX_PAGES_PER_FILL,
    val retryBackoffMillis: Long = AppConstants.RADIO_RETRY_BACKOFF_MILLIS,
    val minDurationSeconds: Int = AppConstants.RADIO_MIN_DURATION_SECONDS,
    val maxDurationSeconds: Int = AppConstants.RADIO_MAX_DURATION_SECONDS,
    val artistWindow: Int = AppConstants.RADIO_ARTIST_WINDOW,
    val maxSameArtistInWindow: Int = AppConstants.RADIO_MAX_SAME_ARTIST_IN_WINDOW,
    val minSongKeyLength: Int = AppConstants.RADIO_MIN_SONG_KEY_LENGTH,
    val artistFreeSongKeyLength: Int = AppConstants.RADIO_ARTIST_FREE_SONG_KEY_LENGTH,
    val noiseWords: List<String> = AppConstants.RADIO_NOISE_WORDS,
    val versionWords: List<String> = AppConstants.RADIO_VERSION_WORDS,
    val artistNoiseWords: List<String> = AppConstants.RADIO_ARTIST_NOISE_WORDS,
    val rejectTitlePhrases: List<String> = AppConstants.RADIO_REJECT_TITLE_PHRASES
)
