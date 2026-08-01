package com.cid.musicapp.player

/**
 * สะพานเชื่อมระหว่าง [PlaybackService] (ที่รับคำสั่งจากปุ่ม next/previous บนหน้าจอล็อก, บลูทูธ,
 * ปุ่มหูฟัง ผ่าน MediaSession) กับ [PlayerController] (ที่คุมคิว/shuffle/repeat จริงฝั่งแอป)
 *
 * ทำไมต้องมีสะพานนี้: ExoPlayer ในเซอร์วิสเล่นทีละ MediaItem เดียวเสมอ (PlayerController.playCurrent()
 * เรียก setMediaItem ทีละตัว ไม่ได้ยัดคิวทั้งหมดเข้า ExoPlayer ตรงๆ) — "คิวจริง" (queue/order/shuffle/repeat)
 * อยู่ใน PlayerController ฝั่งแอปเท่านั้น ถ้าไม่มีสะพานนี้ ปุ่ม next/previous ที่มาจากนอกแอป (ไม่ผ่าน UI
 * ของเราเอง) จะเรียก ExoPlayer.seekToNext()/seekToPrevious() ตรงๆ ซึ่งไม่มีผลอะไรเลยเพราะมีแค่ 1 item
 * ในเพลย์ลิสต์ของ ExoPlayer เสมอ — ดู PlaybackService ที่ห่อ ExoPlayer ด้วย ForwardingPlayer แล้วส่ง
 * คำสั่งมาทางนี้แทน
 *
 * ใช้วิธีนี้ได้เพราะแอปนี้รัน PlaybackService ในโปรเซสเดียวกับแอป (ไม่ได้ตั้ง android:process แยกใน
 * AndroidManifest) ถ้าในอนาคตแยกโปรเซส ต้องเปลี่ยนไปส่งผ่าน custom SessionCommand ของ MediaSession แทน
 */
object PlaybackBridge {
    interface QueueNavigationListener {
        fun onSkipToNext()
        fun onSkipToPrevious()
    }

    @Volatile
    var listener: QueueNavigationListener? = null
}
