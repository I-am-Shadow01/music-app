package com.cid.musicapp.ui.util

/** แปลงวินาที/มิลลิวินาทีเป็น m:ss หรือ h:mm:ss ให้อ่านง่าย */
fun formatDurationSeconds(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatDurationMillis(totalMillis: Long): String =
    formatDurationSeconds((totalMillis / 1000).toInt().coerceAtLeast(0))
