package com.cid.musicapp.data.repository

import android.os.SystemClock
import com.cid.musicapp.config.AppConstants

/** Bounded LRU with monotonic TTL and a clear-generation barrier for in-flight resolves. */
class StreamUrlCache(
    private val ttlMillis: Long,
    private val maxEntries: Int,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime
) {
    private data class Entry(val url: String, val storedAtMillis: Long)
    private val lock = Any()
    private var generation = 0L
    private val entries = LinkedHashMap<String, Entry>(
        AppConstants.STREAM_CACHE_INITIAL_CAPACITY, AppConstants.STREAM_CACHE_LOAD_FACTOR, true
    )

    init {
        require(ttlMillis > 0)
        require(maxEntries > 0)
    }

    fun generation(): Long = synchronized(lock) { generation }

    fun get(key: String): String? = synchronized(lock) {
        pruneExpired()
        entries[key]?.url
    }

    fun put(key: String, url: String, expectedGeneration: Long) = synchronized(lock) {
        if (expectedGeneration == generation) {
            pruneExpired()
            entries[key] = Entry(url, nowMillis())
            while (entries.size > maxEntries) {
                val iterator = entries.iterator()
                iterator.next()
                iterator.remove()
            }
        }
    }

    fun clear() = synchronized(lock) {
        generation++
        entries.clear()
    }

    fun size(): Int = synchronized(lock) {
        pruneExpired()
        entries.size
    }

    private fun pruneExpired() {
        val now = nowMillis()
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().storedAtMillis >= ttlMillis) iterator.remove()
        }
    }
}
