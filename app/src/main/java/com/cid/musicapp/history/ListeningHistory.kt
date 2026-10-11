package com.cid.musicapp.history

import com.cid.musicapp.config.AppConstants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ประวัติการฟังในหน่วยความจำ (อ่านได้ทันทีจาก [entries]) + บันทึกลง [HistoryStore] ให้เองเบื้องหลัง
 *
 *  - การอ่าน/เขียนไฟล์ทำบน [ioDispatcher] เท่านั้น ไม่บล็อก Main
 *  - ทุกการแก้ไขผ่าน mutex เดียว: โหลดจากไฟล์ตอนเริ่มกับ record() ที่เข้ามาก่อนโหลดเสร็จจึงไม่ทับกัน
 *    (โหลดเสร็จแล้วรวมกับที่เพิ่ง record ไว้ ไม่ใช่เขียนทับ)
 *  - เขียนไฟล์แบบ debounce: ข้ามเพลงรัวๆ รวมเป็นการเขียนครั้งเดียวหลังเงียบไป [saveDebounceMillis]
 *  - เก็บสูงสุด [maxEntries] รายการ ตัดเก่าสุดทิ้ง
 */
class ListeningHistory(
    private val store: HistoryStore,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxEntries: Int = AppConstants.MAX_HISTORY_ENTRIES,
    private val saveDebounceMillis: Long = AppConstants.HISTORY_SAVE_DEBOUNCE_MILLIS
) {
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<PlayRecord>>(emptyList())

    /** ประวัติทั้งหมด เก่า→ใหม่ (ว่างจนกว่าจะโหลดจากไฟล์เสร็จ) */
    val entries: StateFlow<List<PlayRecord>> = _entries

    private var saveJob: Job? = null

    init {
        scope.launch {
            mutex.withLock {
                val persisted = withContext(ioDispatcher) { runCatching { store.load() }.getOrDefault(emptyList()) }
                _entries.value = (persisted + _entries.value).takeLast(maxEntries)
            }
        }
    }

    fun record(record: PlayRecord) {
        scope.launch {
            mutex.withLock {
                _entries.value = (_entries.value + record).takeLast(maxEntries)
                scheduleSave()
            }
        }
    }

    /** ล้างประวัติทั้งหมดทั้งในหน่วยความจำและในไฟล์ (ปุ่ม "ล้างประวัติการฟัง") */
    fun clear() {
        scope.launch {
            mutex.withLock {
                saveJob?.cancel()
                saveJob = null
                _entries.value = emptyList()
                withContext(ioDispatcher) { runCatching { store.save(emptyList()) } }
            }
        }
    }

    /** บันทึกลงไฟล์ทันที ไม่รอ debounce — เรียกตอนหยุดเล่นทั้งหมด/ปิดแอป เพื่อไม่ให้ท้ายๆ หายถ้าโดนฆ่า */
    fun flush() {
        scope.launch {
            mutex.withLock {
                saveJob?.cancel()
                saveJob = null
                saveSnapshot()
            }
        }
    }

    // เรียกเฉพาะตอนถือ mutex อยู่ — job ที่ถูกตั้งไว้จะรอ mutex เองอีกทีตอนถึงเวลาเขียน
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(saveDebounceMillis)
            mutex.withLock { saveSnapshot() }
        }
    }

    private suspend fun saveSnapshot() {
        val snapshot = _entries.value
        withContext(ioDispatcher) { runCatching { store.save(snapshot) } }
    }
}
