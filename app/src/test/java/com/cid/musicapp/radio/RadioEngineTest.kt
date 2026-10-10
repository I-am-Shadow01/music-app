package com.cid.musicapp.radio

import com.cid.musicapp.data.repository.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RadioEngineTest {

    private fun track(id: String, artist: String = "Chan $id") =
        Track(id, "Unique Song Title $id", artist, 200, null)

    private class Cursor(val page: Int) : RadioCursor

    private class FakeSource(
        private val mixPages: List<List<Track>> = emptyList(),
        private val relatedTracks: List<Track> = emptyList(),
        private val artistTracks: List<Track> = emptyList(),
        private val mixFails: Boolean = false
    ) : RadioSource {
        var mixCalls = 0
        var relatedCalls = 0
        var artistCalls = 0

        override suspend fun mixPage(seed: Track, cursor: RadioCursor?): RadioPage {
            mixCalls++
            if (mixFails) throw IllegalStateException("mix down")
            val index = (cursor as? Cursor)?.page ?: 0
            val next = if (index + 1 < mixPages.size) Cursor(index + 1) else null
            return RadioPage(mixPages[index], next)
        }
        override suspend fun related(track: Track): List<Track> { relatedCalls++; return relatedTracks }
        override suspend fun byArtist(track: Track): List<Track> { artistCalls++; return artistTracks }
    }

    private val config = RadioConfig(batchSize = 3, maxPagesPerFill = 2, retryBackoffMillis = 1_000)

    private fun engine(source: RadioSource, now: () -> Long = { 0L }) =
        RadioEngine(source, config, clockMillis = now)

    @Test fun inactiveEngineReturnsNothing() = runBlocking {
        val source = FakeSource(mixPages = listOf(listOf(track("a"))))
        assertTrue(engine(source).nextBatch(emptyList()).isEmpty())
        assertEquals(0, source.mixCalls)
    }

    @Test fun mixIsFilteredAgainstSeedAndQueue() = runBlocking {
        val seed = track("seed")
        val source = FakeSource(mixPages = listOf(listOf(seed, track("a"), track("b"), track("c"), track("d"))))
        val engine = engine(source).also { it.start(seed) }
        val batch = engine.nextBatch(listOf(seed))
        assertEquals(listOf("a", "b", "c"), batch.map { it.id })
    }

    @Test fun mixPaginatesAcrossCallsWithoutRepeating() = runBlocking {
        val seed = track("seed")
        val source = FakeSource(mixPages = listOf(
            listOf(track("a"), track("b")), listOf(track("c"), track("d")), listOf(track("e"))
        ))
        val engine = engine(source).also { it.start(seed) }
        val first = engine.nextBatch(listOf(seed))
        assertEquals(listOf("a", "b", "c"), first.map { it.id })
        val second = engine.nextBatch(listOf(seed) + first)
        assertEquals("d", second.first().id)
        assertTrue(second.none { it.id in first.map { t -> t.id } })
    }

    @Test fun fallsBackToRelatedThenArtistWhenMixFails() = runBlocking {
        val seed = track("seed")
        val source = FakeSource(
            mixFails = true,
            relatedTracks = listOf(track("r1")),
            artistTracks = listOf(track("m1"), track("m2"))
        )
        val engine = engine(source).also { it.start(seed) }
        val batch = engine.nextBatch(listOf(seed))
        assertEquals(listOf("r1", "m1", "m2"), batch.map { it.id })
        assertEquals(1, source.relatedCalls)
        assertEquals(1, source.artistCalls)
    }

    @Test fun relatedIsQueriedOncePerAnchorTrack() = runBlocking {
        val seed = track("seed")
        val source = FakeSource(mixFails = true, relatedTracks = listOf(track("r1")))
        val engine = engine(source).also { it.start(seed) }
        engine.nextBatch(listOf(seed))
        engine.nextBatch(listOf(seed)) // anchor เดิม → ไม่ยิงซ้ำ
        assertEquals(1, source.relatedCalls)
    }

    @Test fun emptyResultTriggersBackoffUntilClockAdvances() = runBlocking {
        var now = 0L
        val seed = track("seed")
        val source = FakeSource(mixFails = true)
        val engine = engine(source) { now }.also { it.start(seed) }
        assertTrue(engine.nextBatch(listOf(seed)).isEmpty())
        val callsAfterFirst = source.relatedCalls + source.artistCalls
        assertTrue(engine.nextBatch(listOf(seed, track("x"))).isEmpty()) // ยังอยู่ในช่วงรอ
        assertEquals(callsAfterFirst, source.relatedCalls + source.artistCalls)
        now = config.retryBackoffMillis
        engine.nextBatch(listOf(seed, track("y"))) // พ้นช่วงรอแล้วต้องกลับมาลองจริง
        assertTrue(source.relatedCalls + source.artistCalls > callsAfterFirst)
    }

    @Test fun startingNewSessionResetsMixCursor() = runBlocking {
        val source = FakeSource(mixPages = listOf(listOf(track("a"), track("b"), track("c"))))
        val engine = engine(source)
        engine.start(track("s1"))
        engine.nextBatch(listOf(track("s1")))
        engine.start(track("s2"))
        val batch = engine.nextBatch(listOf(track("s2")))
        assertEquals(listOf("a", "b", "c"), batch.map { it.id })
    }

    @Test fun stopMakesEngineInactive() = runBlocking {
        val engine = engine(FakeSource(mixPages = listOf(listOf(track("a"))))).also { it.start(track("s")) }
        assertTrue(engine.isActive)
        engine.stop()
        assertFalse(engine.isActive)
        assertTrue(engine.nextBatch(emptyList()).isEmpty())
    }
}
