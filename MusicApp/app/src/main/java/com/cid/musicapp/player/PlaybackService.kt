package com.cid.musicapp.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cid.musicapp.config.AppConstants

/**
 * รัน ExoPlayer + MediaSession อยู่เบื้องหลัง ทำให้เพลงเล่นต่อได้แม้แอปไม่ได้อยู่ foreground
 * UI ฝั่ง ViewModel จะต่อเข้ามาผ่าน MediaController (ดู PlayerController.kt)
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // ลด buffer ขั้นต่ำก่อนเริ่มเล่น ให้เพลงเริ่มเล่นได้เร็วขึ้นแทนที่จะรอบัฟเฟอร์นานแบบค่า default
        // (ค่า default ของ ExoPlayer คือรอ ~2.5 วิ ก่อนเริ่มเล่น เราลดเหลือตามที่ตั้งใน AppConstants)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                AppConstants.MIN_BUFFER_MS,
                AppConstants.MAX_BUFFER_MS,
                AppConstants.BUFFER_FOR_PLAYBACK_MS,
                AppConstants.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        val player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .build()

        player.repeatMode = Player.REPEAT_MODE_OFF
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
