package com.cid.musicapp.history

import com.cid.musicapp.data.repository.Track
import com.cid.musicapp.radio.RadioConfig
import com.cid.musicapp.radio.TrackIdentityFactory
import org.junit.Assert.*
import org.junit.Test

class TasteProfileTest {
    private val tasteConfig = TasteConfig(
        halfLifeDays = 10.0, favoriteBoost = 2.0, recentCooldownHours = 1,
        dislikeThreshold = -0.9, minArtistScore = 0.5
    )
    private val builder = TasteProfileBuilder(tasteConfig, TrackIdentityFactory(RadioConfig()))

    private val now = 100L * DAY
    private fun track(id: String, artist: String, title: String = "Song $id", seconds: Int = 200) =
        Track(id, title, artist, seconds, null)

    private fun play(track: Track, listenedSeconds: Int, ageMillis: Long = 0L) =
        PlayRecord(track, now - ageMillis, listenedSeconds * 1000L)

    @Test fun completeBeatsPlayBeatsSkip() {
        val t = track("a", "A")
        assertEquals(1.0, builder.weightOf(play(t, 180)), 1e-9)   // 90% → ฟังจบ
        assertEquals(0.6, builder.weightOf(play(t, 110)), 1e-9)   // 55% → ฟังจริง
        assertEquals(0.6, builder.weightOf(play(t, 31)), 1e-9)    // เกิน 30 วิ → ฟังจริง แม้ไม่ถึง 50%
        assertEquals(-0.5, builder.weightOf(play(t, 10)), 1e-9)   // ข้ามทิ้ง
    }

    @Test fun artistThatIsPlayedMoreRanksHigherAndIsNormalised() {
        val a = track("a1", "Alpha"); val b = track("b1", "Beta")
        val profile = builder.build(
            listOf(play(a, 190), play(a, 190), play(a, 190), play(b, 190)), emptyList(), now
        )
        val top = profile.topArtists(5)
        assertEquals(listOf("Alpha", "Beta"), top.map { it.name })
        assertEquals(1.0, profile.affinity(a), 1e-9)
        assertEquals(1.0 / 3.0, profile.affinity(b), 1e-9)
        assertEquals(0.0, profile.affinity(track("z", "Unknown")), 1e-9)
    }

    @Test fun oldListeningCountsLessThanRecent() {
        val old = track("o", "Old"); val fresh = track("f", "Fresh")
        val profile = builder.build(
            listOf(play(old, 190, ageMillis = 30 * DAY), play(fresh, 190, ageMillis = 0)), emptyList(), now
        )
        // 30 วัน = 3 half-life → 0.125 ; ศิลปินเก่าต่ำกว่า minArtistScore (0.5) จึงไม่ติดอันดับ
        assertEquals(listOf("Fresh"), profile.topArtists(5).map { it.name })
        assertTrue(profile.affinity(old) < profile.affinity(fresh))
    }

    @Test fun skipsHurtTheTrackButNotTheArtist() {
        val liked = track("l", "Alpha"); val skipped = track("s", "Alpha", "Other Alpha Song")
        val profile = builder.build(
            listOf(play(liked, 190), play(skipped, 5), play(skipped, 5)), emptyList(), now
        )
        assertTrue(profile.isDisliked("s"))
        assertFalse(profile.isDisliked("l"))
        assertEquals(1.0, profile.topArtists(1).first().score, 1e-9) // ศิลปินได้คะแนนจาก "l" อย่างเดียว
    }

    @Test fun completingADislikedTrackClearsTheDislike() {
        val t = track("s", "Alpha")
        val profile = builder.build(listOf(play(t, 5), play(t, 5), play(t, 190)), emptyList(), now)
        assertFalse(profile.isDisliked("s")) // -0.5 -0.5 +1.0 = 0
    }

    @Test fun recentlyPlayedWithinCooldownOnly() {
        val recent = track("r", "A"); val older = track("o", "A", "Older Song")
        val profile = builder.build(
            listOf(play(older, 190, ageMillis = 2 * HOUR), play(recent, 190, ageMillis = HOUR / 2)), emptyList(), now
        )
        assertTrue(profile.isRecentlyPlayed("r"))
        assertFalse(profile.isRecentlyPlayed("o"))
        assertEquals(listOf("r"), profile.recentTracks.map { it.id })
    }

    @Test fun skippedTrackIsNotCountedAsRecentlyPlayed() {
        val t = track("s", "A")
        val profile = builder.build(listOf(play(t, 5)), emptyList(), now)
        assertFalse(profile.isRecentlyPlayed("s"))
    }

    @Test fun favoritesBoostArtistsAndAreNeverDisliked() {
        val fav = track("f", "Fav")
        val profile = builder.build(listOf(play(fav, 5), play(fav, 5)), listOf(fav), now)
        assertEquals("Fav", profile.topArtists(1).first().name)
        assertFalse(profile.isDisliked("f")) // -1.0 + 2.0
        assertTrue(profile.seedCandidates(5).any { it.id == "f" })
    }

    @Test fun lyricChannelStillCreditsTheRealArtistFromTheTitle() {
        val t = Track("x", "Real Artist - Great Song [Lyrics]", "Some Lyrics Channel", 200, null)
        val profile = builder.build(listOf(play(t, 190), play(t, 190)), emptyList(), now)
        val names = profile.topArtists(5).map { it.name }
        assertTrue(names.contains("Real Artist"))
        assertTrue(names.contains("Some Lyrics Channel"))
        // เดาจากชื่อคลิปน้ำหนักครึ่งเดียว → ช่องที่อัปอยู่อันดับสูงกว่า
        assertEquals("Some Lyrics Channel", names.first())
    }

    @Test fun emptyInputHasNoData() {
        val profile = builder.build(emptyList(), emptyList(), now)
        assertFalse(profile.hasData)
        assertTrue(profile.topArtists(5).isEmpty())
        assertEquals(0.0, profile.affinity(track("a", "A")), 1e-9)
    }

    @Test fun futureTimestampsDoNotInflateScore() {
        val t = track("a", "A")
        val profile = builder.build(listOf(PlayRecord(t, now + 5 * DAY, 190_000L)), emptyList(), now)
        assertEquals(1.0, profile.topArtists(1).first().score, 1e-9)
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
