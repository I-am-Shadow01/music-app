package com.cid.musicapp.data.repository

import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

class StreamSelectorTest {
    @Test fun audioTargetUsesKilobitsNotBitsPerSecond() {
        val low = audio(LOW_KBPS)
        val high = audio(HIGH_KBPS)
        assertSame(low, StreamSelector.audio(listOf(low, high), LOW_KBPS))
        assertSame(high, StreamSelector.audio(listOf(low, high), HIGH_KBPS))
    }

    @Test fun rejectsManifestContentAndUnsupportedDelivery() {
        val manifest = audio(LOW_KBPS, isUrl = false)
        val dash = audio(LOW_KBPS, delivery = DeliveryMethod.DASH)
        val playable = audio(HIGH_KBPS)
        assertSame(playable, StreamSelector.audio(listOf(manifest, dash, playable), LOW_KBPS))
        assertNull(StreamSelector.audio(listOf(manifest, dash), LOW_KBPS))
    }

    @Test fun unknownBitrateDoesNotBeatKnownBitrate() {
        val unknown = audio(AudioStream.UNKNOWN_BITRATE)
        val known = audio(HIGH_KBPS)
        assertSame(known, StreamSelector.audio(listOf(unknown, known), LOW_KBPS))
        assertSame(unknown, StreamSelector.audio(listOf(unknown), LOW_KBPS))
    }

    @Test fun videoResolutionHandlesFrameRateSuffix() {
        val low = video("480p")
        val high = video("720p60")
        assertSame(high, StreamSelector.video(listOf(low, high), VIDEO_HEIGHT))
    }

    @Test fun emptyStreamsReturnNull() {
        assertNull(StreamSelector.audio(emptyList(), LOW_KBPS))
        assertNull(StreamSelector.video(emptyList(), VIDEO_HEIGHT))
    }

    private fun audio(kbps: Int, isUrl: Boolean = true,
                      delivery: DeliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP): AudioStream =
        AudioStream.Builder().setId(kbps.toString()).setContent(FIXTURE_URL, isUrl)
            .setAverageBitrate(kbps).setDeliveryMethod(delivery).build()

    private fun video(resolution: String): VideoStream = VideoStream.Builder()
        .setId(resolution).setContent(FIXTURE_URL, true).setIsVideoOnly(false)
        .setResolution(resolution).build()

    companion object {
        private const val FIXTURE_URL = "https://example.invalid/stream"
        private const val LOW_KBPS = 48
        private const val HIGH_KBPS = 160
        private const val VIDEO_HEIGHT = 720
    }
}
