package com.cid.musicapp.history

import com.cid.musicapp.config.AppConstants

/** ค่าปรับแต่งการคำนวณรสนิยม — default มาจาก [AppConstants] เท่านั้น (เทสต์ส่งค่าเล็กๆ ของตัวเองได้) */
data class TasteConfig(
    val playRatio: Double = AppConstants.TASTE_PLAY_RATIO,
    val playMinListenMillis: Long = AppConstants.TASTE_PLAY_MIN_LISTEN_MILLIS,
    val completeRatio: Double = AppConstants.TASTE_COMPLETE_RATIO,
    val weightComplete: Double = AppConstants.TASTE_WEIGHT_COMPLETE,
    val weightPlay: Double = AppConstants.TASTE_WEIGHT_PLAY,
    val weightSkip: Double = AppConstants.TASTE_WEIGHT_SKIP,
    val halfLifeDays: Double = AppConstants.TASTE_HALF_LIFE_DAYS,
    val favoriteBoost: Double = AppConstants.TASTE_FAVORITE_BOOST,
    val fallbackDurationSeconds: Int = AppConstants.TASTE_FALLBACK_DURATION_SECONDS,
    val recentCooldownHours: Int = AppConstants.TASTE_RECENT_COOLDOWN_HOURS,
    val recentTracksForIdentity: Int = AppConstants.TASTE_RECENT_TRACKS_FOR_IDENTITY,
    val dislikeThreshold: Double = AppConstants.TASTE_DISLIKE_THRESHOLD,
    val minArtistScore: Double = AppConstants.TASTE_MIN_ARTIST_SCORE
)
