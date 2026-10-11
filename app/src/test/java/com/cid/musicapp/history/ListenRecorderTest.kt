package com.cid.musicapp.history

import com.cid.musicapp.data.repository.Track
import org.junit.Assert.*
import org.junit.Test

class ListenRecorderTest {
    private var elapsed = 0L
    private var wall = 1_000_000L
    private val recorder = ListenRecorder(elapsedMillis = { elapsed }, wallMillis = { wall }, minListenMillis = 2_000L)
    private val track = Track("t", "Song", "Artist", 200, null)

    @Test fun countsOnlyTimeSpentPlaying() {
        recorder.begin(track, isPlaying = false)
        elapsed = 5_000
        recorder.onPlayingChanged(true)
        elapsed = 15_000
        recorder.onPlayingChanged(false) // เล่นอยู่ 10 วิ
        elapsed = 60_000                 // หยุดชั่วคราวนาน ไม่นับ
        recorder.onPlayingChanged(true)
        elapsed = 65_000
        val record = recorder.finish()
        assertEquals(15_000L, record?.listenedMs)
        assertEquals(1_000_000L, record?.playedAtMillis)
    }

    @Test fun beginWhilePlayingStartsCountingImmediately() {
        recorder.begin(track, isPlaying = true)
        elapsed = 8_000
        assertEquals(8_000L, recorder.finish()?.listenedMs)
    }

    @Test fun tooShortListenIsNotRecorded() {
        recorder.begin(track, isPlaying = true)
        elapsed = 1_999
        assertNull(recorder.finish())
    }

    @Test fun finishWithoutBeginOrTwiceReturnsNull() {
        assertNull(recorder.finish())
        recorder.begin(track, isPlaying = true)
        elapsed = 10_000
        assertNotNull(recorder.finish())
        assertNull(recorder.finish())
    }

    @Test fun playingEventsWithoutATrackAreIgnoredAndRepeatedPlayDoesNotDoubleCount() {
        recorder.onPlayingChanged(true) // ไม่มีเพลง → ไม่ทำอะไร
        recorder.begin(track, isPlaying = true)
        elapsed = 4_000
        recorder.onPlayingChanged(true) // ซ้ำ → ต้องไม่รีเซ็ตจุดเริ่ม
        elapsed = 6_000
        assertEquals(6_000L, recorder.finish()?.listenedMs)
    }

    @Test fun beginDiscardsUnfinishedPreviousTrack() {
        recorder.begin(track, isPlaying = true)
        elapsed = 10_000
        recorder.begin(Track("u", "Other", "B", 100, null), isPlaying = false)
        elapsed = 20_000
        assertNull(recorder.finish()) // เพลงใหม่ยังไม่ได้เล่นเลย
    }
}
