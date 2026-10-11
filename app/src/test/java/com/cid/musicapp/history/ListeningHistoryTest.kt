package com.cid.musicapp.history

import com.cid.musicapp.data.repository.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ListeningHistoryTest {
    private class FakeStore(var persisted: List<PlayRecord> = emptyList(), var failLoad: Boolean = false) : HistoryStore {
        var saves = 0
        override fun load(): List<PlayRecord> { if (failLoad) throw IllegalStateException("boom"); return persisted }
        override fun save(records: List<PlayRecord>) { saves++; persisted = records }
    }

    // Unconfined: ทุก launch ทำงานทันทีบน thread ของเทสต์ ไม่ต้องรอ — ผลลัพธ์จึงตายตัว ไม่ขึ้นกับเวลา
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    @After fun tearDown() = scope.cancel()

    private fun record(id: String, at: Long = 1L) = PlayRecord(Track(id, "T$id", "A", 100, null), at, 10_000L)

    private fun history(store: FakeStore, max: Int = 3) = ListeningHistory(
        store, scope, ioDispatcher = Dispatchers.Unconfined, maxEntries = max, saveDebounceMillis = 60_000L
    )

    @Test fun loadsPersistedEntriesOnStart() {
        val h = history(FakeStore(listOf(record("a"), record("b"))))
        assertEquals(listOf("a", "b"), h.entries.value.map { it.track.id })
    }

    @Test fun recordAppendsAndKeepsOnlyNewestEntries() {
        val h = history(FakeStore(listOf(record("a"), record("b"))))
        h.record(record("c")); h.record(record("d"))
        assertEquals(listOf("b", "c", "d"), h.entries.value.map { it.track.id })
    }

    @Test fun corruptOrFailingLoadStartsEmptyInsteadOfCrashing() {
        val h = history(FakeStore(failLoad = true))
        assertTrue(h.entries.value.isEmpty())
        h.record(record("a"))
        assertEquals(1, h.entries.value.size)
    }

    @Test fun flushWritesSnapshotImmediately() {
        val store = FakeStore()
        val h = history(store)
        h.record(record("a"))
        assertEquals(0, store.saves) // ยังอยู่ใน debounce
        h.flush()
        assertEquals(listOf("a"), store.persisted.map { it.track.id })
    }

    @Test fun clearEmptiesMemoryAndStore() {
        val store = FakeStore(listOf(record("a")))
        val h = history(store)
        h.clear()
        assertTrue(h.entries.value.isEmpty())
        assertTrue(store.persisted.isEmpty())
    }
}
