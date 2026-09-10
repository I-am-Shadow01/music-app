package com.cid.musicapp.data.repository

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

/** Selection for the existing progressive audio / muxed video playback path. */
internal object StreamSelector {
    fun audio(streams: List<AudioStream>, targetKbps: Int): AudioStream? {
        val playable = streams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotBlank()
        }
        // NewPipe v0.26.5 averageBitrate uses kbps (ItagItem), NOT bits per second.
        return playable.filter { it.averageBitrate > 0 }
            .minByOrNull { abs(it.averageBitrate.toLong() - targetKbps) }
            ?: playable.firstOrNull()
    }

    fun video(streams: List<VideoStream>, targetHeightPx: Int): VideoStream? {
        val playable = streams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotBlank()
        }
        return playable.filter { height(it) > 0 }
            .minByOrNull { abs(height(it).toLong() - targetHeightPx) }
            ?: playable.firstOrNull()
    }

    private fun height(stream: VideoStream): Int =
        stream.resolution.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
}
