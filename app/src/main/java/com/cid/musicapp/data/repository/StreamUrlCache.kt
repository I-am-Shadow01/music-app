package com.cid.musicapp.data.repository

/**
 * แคชลิงก์สตรีม (URL + เวลาที่เก็บ) — แยกเป็นคลาสของตัวเองเพื่อให้ [MusicRepository] โฟกัสเรื่อง
 * การคุยกับ NewPipeExtractor ส่วนนโยบายแคช (TTL + จำกัดจำนวนรายการ) อยู่ที่นี่ทั้งหมด
 *
 * ทำไมต้องจำกัดจำนวนรายการ: เดิมแคชโตแบบไม่มีวันหด — TTL ถูกเช็คเฉพาะตอน "อ่าน" เท่านั้น
 * รายการหมดอายุที่ไม่ถูกอ่านซ้ำก็ยังค้างอยู่ในแมพจนปิดแอป เซสชันยาวๆ ที่เล่นเพลงเยอะๆ จึงสะสม
 * หน่วยความจำขึ้นเรื่อยๆ เปล่าๆ — ตอนนี้ถ้าเกิน AppConstants.MAX_STREAM_CACHE_ENTRIES
 * จะตัดรายการที่ไม่ได้ใช้นานที่สุด (LRU) ทิ้งโดยอัตโนมัติ
 *
 * Thread-safety: อ่าน/เขียนจาก Dispatchers.IO ซึ่งมีหลาย thread (เหตุผลเดียวกับที่ต้องใช้
 * ConcurrentHashMap/AtomicInteger ใน MusicRepository) — ใช้ LinkedHashMap แบบ accessOrder=true
 * (ให้ตัวมันจัดการความใหม่-เก่าจากการ get() เอง) แล้วคุมด้วย lock เดียวทุกการเข้าถึง
 * (การเข้าถึงเกิดเฉพาะตอนกดเล่นเพลง ไม่ใช่ hot path ที่ lock จะชนกันจนกระทบประสิทธิภาพ)
 */
class StreamUrlCache(private val ttlMillis: Long, private val maxEntries: Int) {

    private data class Entry(val url: String, val storedAtMillis: Long)

    // accessOrder = true → การ get() จะจัดให้รายการที่เพิ่งอ่านกลายเป็น "ใช้ล่าสุด" เอง
    // removeEldestEntry จึงตัด "ตัวที่ไม่ได้ใช้นานสุด" ทิ้งเมื่อขนาดเกิน maxEntries พอดี
    private val lruMap = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }

    private val lock = Any()

    /** คืน URL ที่แคชไว้ถ้ายังไม่หมดอายุ — ถ้าหมดอายุลบทิ้งแล้วคืน null (เหมือนไม่มีแคช) */
    fun get(key: String): String? = synchronized(lock) {
        val entry = lruMap[key] ?: return@synchronized null
        if (System.currentTimeMillis() - entry.storedAtMillis >= ttlMillis) {
            lruMap.remove(key)
            return@synchronized null
        }
        entry.url
    }

    /**
     * เก็บ URL ลงแคช — stamp เวลา "ตอนเก็บจริง" เสมอ (เดิมโค้ด stamp ตอนเริ่ม fetch ทำให้ TTL จริง
     * สั้นกว่าที่ตั้งไว้เท่ากับเวลาที่รอ network ตอน resolve)
     */
    fun put(key: String, url: String) = synchronized(lock) {
        lruMap[key] = Entry(url, System.currentTimeMillis())
    }

    /** ล้างแคชทั้งหมด (เรียกจากหน้าตั้งค่า) */
    fun clear() = synchronized(lock) { lruMap.clear() }

    /** จำนวนรายการในแคชตอนนี้ (ไว้โชว์ในโหมดนักพัฒนา) */
    fun size(): Int = synchronized(lock) { lruMap.size }
}
