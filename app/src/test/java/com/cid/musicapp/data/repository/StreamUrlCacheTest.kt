package com.cid.musicapp.data.repository

import org.junit.Assert.*
import org.junit.Test

class StreamUrlCacheTest {
    private var now = 0L
    private val cache = StreamUrlCache(ttlMillis = TTL, maxEntries = CAPACITY, nowMillis = { now })

    @Test fun expiresAtBoundaryAndCountPrunesUnreadEntries() {
        cache.put("a", "url-a", cache.generation())
        now = TTL - 1
        assertEquals("url-a", cache.get("a"))
        now = TTL
        assertEquals(0, cache.size())
        assertNull(cache.get("a"))
    }

    @Test fun readPromotesEntryAndEvictionIsBounded() {
        cache.put("a", "url-a", cache.generation())
        cache.put("b", "url-b", cache.generation())
        cache.get("a")
        cache.put("c", "url-c", cache.generation())
        assertNull(cache.get("b"))
        assertEquals("url-a", cache.get("a"))
        assertEquals(CAPACITY, cache.size())
    }

    @Test fun clearRejectsAnOlderResolveCompletion() {
        val oldRequest = cache.generation()
        cache.clear()
        cache.put("a", "stale", oldRequest)
        assertEquals(0, cache.size())
        cache.put("a", "fresh", cache.generation())
        assertEquals("fresh", cache.get("a"))
    }

    @Test fun overwriteRestartsTtlAtInsertion() {
        cache.put("a", "old", cache.generation())
        now = TTL - 1
        cache.put("a", "new", cache.generation())
        now = TTL
        assertEquals("new", cache.get("a"))
    }

    companion object {
        private const val TTL = 100L
        private const val CAPACITY = 2
    }
}
