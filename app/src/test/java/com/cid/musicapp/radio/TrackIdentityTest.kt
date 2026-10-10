package com.cid.musicapp.radio

import com.cid.musicapp.data.repository.Track
import org.junit.Assert.*
import org.junit.Test

class TrackIdentityTest {
    private val config = RadioConfig()
    private val factory = TrackIdentityFactory(config)

    private fun id(title: String, artist: String = "Uploader") =
        factory.of(Track("x", title, artist, 200, null))

    @Test fun stripsOfficialDecorationsAndArtistPrefix() {
        assertEquals("shape of you", id("Ed Sheeran - Shape of You (Official Music Video)", "Ed Sheeran").songKey)
        assertEquals("shape of you", id("Shape of You [Lyrics]", "Lyrics Chan").songKey)
    }

    @Test fun keepsBracketsThatArePartOfTheRealTitle() {
        // วงเล็บที่ไม่มีคำประกอบ/คำบอกเวอร์ชันต้องไม่ถูกตัด — "(G)" ยังอยู่ในชื่อศิลปิน
        val identity = id("(G)I-DLE - TOMBOY", "x")
        assertEquals("tomboy", identity.songKey)
        assertTrue(identity.artistTokens.contains("g"))
    }

    @Test fun reversedOrderDetectedFromUploaderName() {
        val identity = id("Great Song - The Band", "The Band")
        assertEquals("great song", identity.songKey)
    }

    @Test fun cutsTrailingPromoSegmentsAndFeat() {
        assertEquals("night", id("Night (feat. Someone) | Brand Promo Channel").songKey)
    }

    @Test fun thaiTitlesKeepToneMarks() {
        val key = id("เพลงนี้ดีจัง (เนื้อเพลง)").songKey
        assertEquals("เพลงนี้ดีจัง", key)
    }

    @Test fun artistNoiseWordsAreIgnored() {
        val a = id("Song One", "Adele - Topic")
        val b = id("Song Two", "AdeleVEVO")
        assertTrue(a.artistTokens.contains("adele"))
        assertFalse(a.artistTokens.contains("topic"))
        assertFalse(a.isSameArtistAs(b)) // "adelevevo" เป็นโทเคนเดียว ไม่ตรง "adele" — ยอมรับได้ ดีกว่าเดามั่ว
    }

    @Test fun unknownArtistIsNotTreatedAsSameArtist() {
        val noArtist = TrackIdentity("song", emptySet())
        val other = TrackIdentity("song", setOf("x"))
        assertFalse(noArtist.isSameArtistAs(other))
        assertTrue(noArtist.sharesArtist(other))
    }
}
