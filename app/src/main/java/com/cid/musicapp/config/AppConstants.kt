package com.cid.musicapp.config

/**
 * ค่าคงที่ทั้งหมดของแอปรวมไว้จุดเดียว — ห้ามพิมพ์ตัวเลข/URL พวกนี้ซ้ำที่อื่นในโค้ด
 * ไฟล์ไหนต้องใช้ค่าพวกนี้ ให้ import จากที่นี่เท่านั้น
 */
object AppConstants {

    // --- GitHub repo ที่ใช้อ้างอิงทั้งเช็คอัปเดตและลิงก์ในหน้าตั้งค่า ---
    const val GITHUB_REPO_OWNER = "I-am-Shadow01"
    const val GITHUB_REPO_NAME = "music-app"
    const val GITHUB_RELEASES_API_URL =
        "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
    const val GITHUB_RELEASES_PAGE_URL =
        "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases"

    // --- แคชลิงก์เสียงที่ resolve แล้ว ---
    const val STREAM_CACHE_TTL_MILLIS = 20 * 60 * 1000L // 20 นาที

    // --- พฤติกรรมเครื่องเล่น ---
    const val POSITION_TICKER_INTERVAL_MILLIS = 500L
    const val SEEK_TO_RESTART_THRESHOLD_MILLIS = 3000L // กด previous หลังเล่นเกินนี้ = seek ไปต้นเพลงแทนย้อนเพลง
}
