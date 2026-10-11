package com.cid.musicapp.radio

import com.cid.musicapp.data.repository.Track
import com.cid.musicapp.history.PlayRecord
import com.cid.musicapp.history.TasteConfig
import com.cid.musicapp.history.TasteProfile
import com.cid.musicapp.history.TasteProfileBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RadioTasteTest {
    private val radioConfig = RadioConfig(
        batchSize = 8, maxPagesPerFill = 2, tasteArtistEvery = 4, tasteArtistsTriedPerFill = 2,
        tasteAffinityWeight = 0.6, artistWindow = 4, maxSameArtistInWindow = 2
    )
    private val identities = TrackIdentityFactory(radioConfig)
    private val selector = RadioSelector(radioConfig, identities)
    private val builder = TasteProfileBuilder(TasteConfig(recentCooldownHours = 1, minArtistScore = 0.5), identities)
    private val now = 1_000L * HOUR

    private fun track(id: String, artist: String, title: String = "Unique Song Title $id") =
        Track(id, title, artist, 200, null)

    private fun heard(track: Track, seconds: Int = 190, ageMillis: Long = 0L) =
        PlayRecord(track, now - ageMillis, seconds * 1000L)

    private fun profile(records: List<PlayRecord>, favorites: List<Track> = emptyList()): TasteProfile =
        builder.build(records, favorites, now)

    // ---------- RadioSelector + taste ----------

    @Test fun favouriteArtistIsRankedAheadOfSourceOrder() {
        val taste = profile(List(3) { heard(track("h$it", "Loved", "Heard Song $it")) })
        // ลิสต์ขนาดใกล้เคียงหน้า Mix จริง: โบนัสความชอบ 0.6 ยกเพลงขึ้นได้ราว 60% ของความยาวลิสต์
        // (ศิลปินโปรดที่อยู่ลำดับ 5 จาก 10 ต้องแซงลำดับ 1; แต่ถ้าอยู่ท้ายสุดต้องไม่แซง — ยังเคารพความเกี่ยวข้องจากแหล่ง)
        val strangers = (0 until 9).map { track("s$it", "Stranger $it") }
        val candidates = strangers.take(4) + track("loved", "Loved") + strangers.drop(4)
        assertEquals("loved", selector.select(candidates, emptyList(), 3, taste).first().id)
        // ไม่ใส่รสนิยม = คงลำดับแหล่งเดิม
        assertEquals("s0", selector.select(candidates, emptyList(), 3).first().id)

        val lovedLast = strangers + track("loved", "Loved")
        assertEquals("s0", selector.select(lovedLast, emptyList(), 3, taste).first().id)
    }

    @Test fun recentlyPlayedAndDislikedTracksAreDropped() {
        val recent = track("recent", "A"); val disliked = track("bad", "B"); val fine = track("ok", "C")
        val taste = profile(listOf(heard(recent, ageMillis = HOUR / 2), heard(disliked, 5), heard(disliked, 5)))
        val picked = selector.select(listOf(recent, disliked, fine), emptyList(), 5, taste).map { it.id }
        assertEquals(listOf("ok"), picked)
    }

    @Test fun sameSongUploadedElsewhereAsARecentPlayIsDropped() {
        val played = Track("p", "Artist - A Very Distinct Long Song Name (Official MV)", "Artist", 200, null)
        val taste = profile(listOf(heard(played, ageMillis = HOUR / 4)))
        val reupload = Track("q", "A Very Distinct Long Song Name [Lyrics]", "Lyric Channel", 200, null)
        assertTrue(selector.select(listOf(reupload), emptyList(), 3, taste).isEmpty())
    }

    @Test fun artistSpreadStillAppliesToFavouriteArtist() {
        val taste = profile(List(3) { heard(track("h$it", "Loved", "Heard Song $it")) })
        val candidates = (1..5).map { track("l$it", "Loved") } + track("x", "Other")
        val picked = selector.select(candidates, emptyList(), 4, taste).map { it.id }
        assertEquals(4, picked.size)
        assertEquals("x", picked[2]) // Loved ได้ 2 เพลงติดกันแล้วต้องมีคนอื่นแทรก
    }

    @Test fun identicalRankingWithoutDataKeepsSourceOrder() {
        val taste = profile(emptyList())
        val candidates = listOf(track("a", "A"), track("b", "B"), track("c", "C"))
        assertEquals(listOf("a", "b", "c"), selector.select(candidates, emptyList(), 3, taste).map { it.id })
    }

    // ---------- RadioEngine + taste ----------

    private class TasteSource(
        private val mix: List<Track>,
        private val byName: Map<String, List<Track>>
    ) : RadioSource {
        val artistQueries = ArrayList<String>()
        override suspend fun mixPage(seed: Track, cursor: RadioCursor?) = RadioPage(mix, null)
        override suspend fun related(track: Track) = emptyList<Track>()
        override suspend fun artistTracks(artistName: String): List<Track> {
            artistQueries += artistName
            return byName[artistName].orEmpty()
        }
    }

    private fun engine(source: RadioSource, taste: TasteProfile?) =
        RadioEngine(source, radioConfig, selector, identities, tasteProvider = { taste })

    @Test fun engineInterleavesFavouriteArtistTracksIntoTheMix() = runBlocking {
        val taste = profile(List(3) { heard(track("h$it", "Loved", "Heard Song $it")) })
        val mix = (1..10).map { track("m$it", "Mix Artist $it") }
        val loved = (1..3).map { track("L$it", "Loved") }
        val source = TasteSource(mix, mapOf("Loved" to loved + track("imposter", "Someone Else")))
        val seed = track("seed", "Seed Artist")
        val e = engine(source, taste).also { it.start(seed) }
        val batch = e.nextBatch(listOf(seed))

        assertEquals(radioConfig.batchSize, batch.size)
        val lovedIds = batch.filter { it.id.startsWith("L") }.map { it.id }
        assertEquals(2, lovedIds.size) // ceil(8 / 4) = 2 โควตา
        assertTrue(batch.none { it.id == "imposter" }) // ผลค้นหาที่ไม่ใช่ศิลปินนั้นจริงต้องถูกกรอง
        // แทรกเป็นระยะ ไม่กองอยู่ท้าย: เพลงโปรดเพลงแรกอยู่ก่อนครึ่งหลังของชุด
        assertTrue(batch.indexOfFirst { it.id.startsWith("L") } < batch.size / 2 + 1)
    }

    @Test fun engineWithoutTasteNeverQueriesArtists() = runBlocking {
        val source = TasteSource((1..10).map { track("m$it", "Mix Artist $it") }, emptyMap())
        val seed = track("seed", "S")
        val e = engine(source, null).also { it.start(seed) }
        val batch = e.nextBatch(listOf(seed))
        assertEquals(radioConfig.batchSize, batch.size)
        assertTrue(source.artistQueries.isEmpty())
    }

    @Test fun engineRotatesThroughFavouriteArtistsAcrossFills() = runBlocking {
        val records = List(3) { heard(track("a$it", "Alpha", "Alpha Heard $it")) } +
            List(2) { heard(track("b$it", "Beta", "Beta Heard $it")) }
        val taste = profile(records)
        val source = TasteSource(
            (1..30).map { track("m$it", "Mix Artist $it") },
            mapOf("Alpha" to listOf(track("A1", "Alpha")), "Beta" to listOf(track("B1", "Beta")))
        )
        val seed = track("seed", "S")
        val e = engine(source, taste).also { it.start(seed) }
        val first = e.nextBatch(listOf(seed))
        e.nextBatch(listOf(seed) + first)
        // สองรอบแรกต้องไม่ถามแต่ "Alpha" อย่างเดียว
        assertTrue(source.artistQueries.toSet().containsAll(listOf("Alpha", "Beta")))
    }

    @Test fun failingArtistSearchFallsBackToTheMixWithoutLosingTheBatch() = runBlocking {
        val taste = profile(List(3) { heard(track("h$it", "Loved", "Heard Song $it")) })
        val source = object : RadioSource {
            override suspend fun mixPage(seed: Track, cursor: RadioCursor?) =
                RadioPage((1..10).map { track("m$it", "Mix Artist $it") }, null)
            override suspend fun related(track: Track) = emptyList<Track>()
            override suspend fun artistTracks(artistName: String): List<Track> = throw IllegalStateException("offline")
        }
        val seed = track("seed", "S")
        val e = engine(source, taste).also { it.start(seed) }
        assertEquals(radioConfig.batchSize, e.nextBatch(listOf(seed)).size)
    }

    // ---------- artistKeys ----------

    @Test fun artistKeysIgnoreNoiseWordsAndWordOrder() {
        val a = identities.artistKeys(Track("1", "Song", "Adele - Topic", 200, null)).first()
        val b = identities.artistKeys(Track("2", "Song", "ADELE", 200, null)).first()
        assertEquals(a.key, b.key)
        val c = identities.artistKeys(Track("3", "Song", "Band The", 200, null)).first()
        val d = identities.artistKeys(Track("4", "Song", "The Band", 200, null)).first()
        assertEquals(c.key, d.key)
    }

    @Test fun titleDerivedArtistIsSecondaryAndSkippedWhenItDuplicatesTheUploader() {
        val keys = identities.artistKeys(Track("1", "Real Artist - Great Song", "Lyrics Chan", 200, null))
        assertEquals(2, keys.size)
        assertEquals(1.0, keys[0].weight, 1e-9)
        assertEquals(radioConfig.titleArtistWeight, keys[1].weight, 1e-9)
        assertEquals("Real Artist", keys[1].displayName)

        val same = identities.artistKeys(Track("2", "Adele - Hello", "Adele - Topic", 200, null))
        assertEquals(1, same.size)
    }

    @Test fun longTitlePrefixIsNotMistakenForAnArtist() {
        val keys = identities.artistKeys(
            Track("1", "This Is A Very Long Sentence Not A Name - Part 2", "Chan", 200, null)
        )
        assertEquals(1, keys.size)
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
