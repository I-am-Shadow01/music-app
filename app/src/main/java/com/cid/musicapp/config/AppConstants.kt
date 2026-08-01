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

    // --- ปรับ buffer ของ ExoPlayer ให้เริ่มเล่นเร็วขึ้น (ไม่ต้องรอโหลดทั้งไฟล์) ---
    const val MIN_BUFFER_MS = 15_000
    const val MAX_BUFFER_MS = 30_000
    const val BUFFER_FOR_PLAYBACK_MS = 800 // ค่า default ของ ExoPlayer คือ 2500ms — ลดให้เริ่มเล่นไวขึ้น
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1500

    // --- ช่วงคุณภาพเสียงที่ผู้ใช้ปรับได้ในหน้าตั้งค่า (kbps) ---
    const val MIN_AUDIO_BITRATE_KBPS = 32
    const val MAX_AUDIO_BITRATE_KBPS = 320 // ตั้งสูงเกินสตรีมจริงที่มีไว้เลย = เท่ากับเลือกคุณภาพสูงสุดที่มี
    const val DEFAULT_AUDIO_BITRATE_KBPS = 256

    // --- สีหลักของธีม (ARGB) ---
    const val DEFAULT_ACCENT_COLOR_ARGB = 0xFF7C4DFF.toInt()

    // --- ประวัติการค้นหา ---
    const val MAX_RECENT_SEARCHES = 10 // เก็บคำค้นหาล่าสุดกี่คำ (ใหม่สุดอยู่บนสุด, ไม่ซ้ำคำเดิม)

    // --- โหลดผลค้นหาหน้าถัดไปอัตโนมัติ (infinite scroll) ---
    // เลื่อนจนเหลือรายการท้ายลิสต์เท่านี้ตัว ให้เริ่มโหลดหน้าถัดไปล่วงหน้า กันผู้ใช้เห็นลิสต์ค้างตอนเลื่อนสุด
    const val SEARCH_LOAD_MORE_THRESHOLD_ITEMS = 5

    // --- ระยะ crossfade ตอนโหลดรูป thumbnail เสร็จ (Coil) ---
    const val IMAGE_CROSSFADE_MILLIS = 200

    // --- ค้นหาอัตโนมัติระหว่างพิมพ์ (debounce) ---
    const val SEARCH_DEBOUNCE_MILLIS = 450L
    const val SEARCH_AUTO_MIN_QUERY_LENGTH = 2 // พิมพ์สั้นกว่านี้ยังไม่ auto-search กันยิง request ถี่เกินไปโดยเปล่าประโยชน์

    // --- ปุ่ม seek ไว/ถอยในหน้ากำลังเล่น ---
    const val SEEK_STEP_MILLIS = 10_000L

    // --- ตัวเลือกความเร็วเล่นเพลง (คูณ) วนไปเรื่อยๆ ทีละปุ่มกด ---
    val PLAYBACK_SPEED_PRESETS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    const val DEFAULT_PLAYBACK_SPEED = 1.0f

    // --- ระยะปัดที่ mini player ถือว่าเป็นการสั่งข้าม/ย้อนเพลง (px, แปลงจาก dp ตอนใช้งานจริง) ---
    const val MINI_PLAYER_SWIPE_THRESHOLD_DP = 90f

    // --- ช่วงคุณภาพวิดีโอที่ผู้ใช้ปรับได้ในหน้าตั้งค่า (ความสูงเป็น px เช่น 720 = 720p) ---
    const val MIN_VIDEO_HEIGHT_PX = 144
    const val MAX_VIDEO_HEIGHT_PX = 1080
    const val DEFAULT_VIDEO_HEIGHT_PX = 480

    // --- Waveform visualizer (android.media.audiofx.Visualizer) ---
    // ต้องเป็นเลขยกกำลัง 2 ตามสเปคของคลาสนี้ (ดู Visualizer.getCaptureSizeRange())
    const val WAVEFORM_CAPTURE_SIZE = 256
    const val WAVEFORM_BAR_COUNT = 32
    const val WAVEFORM_CAPTURE_RATE_HZ = 15

    // --- ตั้งเวลาปิดเพลงอัตโนมัติ (sleep timer) ---
    const val SLEEP_TIMER_TICK_MILLIS = 1000L
    val SLEEP_TIMER_PRESET_MINUTES = listOf(15, 30, 45, 60)

    // --- เพลงโปรด (favorites) ---
    // เก็บได้สูงสุดกี่เพลง กันไฟล์ preferences บวมถ้าผู้ใช้กดใจเยอะมากๆ ตลอดหลายปี
    const val MAX_FAVORITE_TRACKS = 500
}
