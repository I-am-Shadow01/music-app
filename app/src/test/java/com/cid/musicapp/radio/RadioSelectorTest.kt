package com.cid.musicapp.radio

import com.cid.musicapp.data.repository.Track
import org.junit.Assert.*
import org.junit.Test

class RadioSelectorTest {
    private val config = RadioConfig(
        artistWindow = 4, maxSameArtistInWindow = 2,
        minDurationSeconds = 60, maxDurationSeconds = 600
    )
    private val selector = RadioSelector(config)

    private fun track(id: String, title: String, artist: String = "Chan $id", seconds: Int? = 200) =
        Track(id = id, title = title, artist = artist, durationSeconds = seconds, thumbnailUrl = null)

    @Test fun skipsTracksAlreadyInQueueById() {
        val queued = listOf(track("a", "Song A"))
        val result = selector.select(listOf(track("a", "Song A"), track("b", "Song B")), queued, 5)
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test fun skipsSameSongUploadedByAnotherChannel() {
        val queued = listOf(track("a", "Artist - Long Distinct Song Name (Official MV)", "Artist"))
        val dup = track("b", "Long Distinct Song Name [Lyrics]", "Some Lyrics Channel")
        assertTrue(selector.select(listOf(dup), queued, 5).isEmpty())
    }

    @Test fun shortGenericTitleNeedsSameArtistToCountAsDuplicate() {
        val queued = listOf(track("a", "Home", "Alpha"))
        val otherArtist = track("b", "Home", "Beta")
        assertEquals(listOf("b"), selector.select(listOf(otherArtist), queued, 5).map { it.id })
        val sameArtist = track("c", "Home", "Alpha")
        assertTrue(selector.select(listOf(sameArtist), queued, 5).isEmpty())
    }

    @Test fun dropsUnknownDurationAndOutOfRange() {
        val result = selector.select(
            listOf(
                track("live", "Radio Stream", seconds = null),
                track("short", "Teaser", seconds = 20),
                track("long", "Ten Hour Loop", seconds = 36_000),
                track("ok", "Fine Song", seconds = 180)
            ), emptyList(), 5
        )
        assertEquals(listOf("ok"), result.map { it.id })
    }

    @Test fun dropsRejectedTitlePhrasesAsWholePhrases() {
        val result = selector.select(
            listOf(
                track("1", "Best Hits FULL ALBUM 2024"),
                track("2", "Song (Karaoke Version)"),
                track("3", "Playlistless Wonder") // "playlist" เป็นส่วนของคำ ไม่ใช่วลีเต็ม → ต้องไม่โดนตัด
            ), emptyList(), 5
        )
        assertEquals(listOf("3"), result.map { it.id })
    }

    @Test fun spreadsArtistsButStillFillsWhenOnlyOneArtistExists() {
        val same = (1..5).map { track("s$it", "Same Artist Song $it", "Solo") }
        val mixed = same + track("o1", "Other One", "Other")
        val spread = selector.select(mixed, emptyList(), 4).map { it.id }
        // รอบแรกรับ Solo ได้แค่ 2 เพลงแล้ว Other มาแทรกก่อนเติมที่เหลือ
        assertEquals(listOf("s1", "s2", "o1", "s3"), spread)

        val onlySolo = selector.select(same, emptyList(), 4)
        assertEquals(4, onlySolo.size) // ไม่ปล่อยคิวว่างเพราะกฎกระจายศิลปิน
    }

    @Test fun acceptedTracksDoNotDuplicateEachOtherInTheSameBatch() {
        val result = selector.select(
            listOf(
                track("a", "Artist - Unique Long Song Title", "Artist"),
                track("b", "Unique Long Song Title (Live)", "Another Chan"),
                track("a", "Artist - Unique Long Song Title", "Artist")
            ), emptyList(), 5
        )
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test fun respectsLimit() {
        val result = selector.select((1..10).map { track("t$it", "Song Number $it") }, emptyList(), 3)
        assertEquals(3, result.size)
        assertTrue(selector.select(listOf(track("x", "X Song")), emptyList(), 0).isEmpty())
    }
}
