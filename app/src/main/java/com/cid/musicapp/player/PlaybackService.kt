package com.cid.musicapp.player

import android.content.Intent
import androidx.media3.common.ForwardingPlayer
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

        val exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .build()

        exoPlayer.repeatMode = Player.REPEAT_MODE_OFF

        // ครอบ ExoPlayer ด้วย ForwardingPlayer เพื่อให้ปุ่ม next/previous ที่มาจากนอกแอป (หน้าจอล็อก,
        // บลูทูธ, ปุ่มหูฟัง, Android Auto ฯลฯ) ทำงานถูกต้อง — ปกติปุ่มพวกนี้จะสั่ง ExoPlayer ให้
        // seekToNext()/seekToPrevious() ตรงๆ ซึ่งไม่มีผลอะไรเลย เพราะ PlayerController สั่ง
        // setMediaItem() ทีละตัวเสมอ (ไม่เคยมี "เพลงถัดไป" อยู่ในเพลย์ลิสต์จริงของ ExoPlayer)
        // จึงต้องดักคำสั่งพวกนี้แล้วส่งต่อไปให้ PlayerController จัดการคิว/shuffle/repeat จริงแทน
        // ผ่าน PlaybackBridge (ดูคอมเมนต์อธิบายเต็มๆ ในไฟล์นั้น)
        //
        // สำคัญ: การ override getAvailableCommands() อย่างเดียวไม่พอ — MediaSession ไม่ได้ถาม
        // getAvailableCommands() ใหม่ทุกครั้ง แต่อาศัย event onAvailableCommandsChanged/onEvents ที่
        // ExoPlayer ตัวจริงยิงออกมาเอง ซึ่ง ForwardingPlayer.addListener() ค่าเริ่มต้นจะเอา listener
        // ไปลงทะเบียนตรงกับ ExoPlayer ตัวจริง (ข้าม override ของเราไปเลย) ทำให้ MediaSession เห็นค่า
        // "จำกัดสิทธิ์" ของ ExoPlayer ตัวจริงอยู่ดี ปุ่ม next/previous บนหน้าจอล็อก/แจ้งเตือนจะยังโชว์
        // เป็นปิดใช้งาน (แม้ override getAvailableCommands() ไว้แล้วก็ตาม) — ต้อง override
        // addListener()/removeListener() ห่อ callback ที่ยิงกลับมาด้วย ให้ commands ที่ส่งออกไปเป็น
        // ค่าที่บังคับไว้เสมอทั้งสองทาง (ตอนถาม + ตอน push event)
        lateinit var forwardingPlayer: Player

        forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
            // เก็บคู่ (listener ตัวจริงที่ถูกเรียก addListener เข้ามา) -> (ตัวห่อที่เราลงทะเบียนแทน)
            // ไว้ใช้ตอน removeListener ต้องถอดตัวห่อตัวเดิมออกให้ตรง ไม่งั้น listener จะค้างอยู่ใน
            // ExoPlayer ตลอดไปแม้ MediaSession จะเลิกสนใจไปแล้ว (listener/memory leak)
            private val listenerWrappers = java.util.IdentityHashMap<Player.Listener, Player.Listener>()

            private fun forceCommands(base: Player.Commands): Player.Commands {
                val builder = base.buildUpon()
                FORCED_QUEUE_COMMANDS.forEach { command ->
                    if (!base.contains(command)) builder.add(command)
                }
                return builder.build()
            }

            override fun getAvailableCommands(): Player.Commands = forceCommands(super.getAvailableCommands())

            override fun addListener(listener: Player.Listener) {
                val wrapped = object : Player.Listener by listener {
                    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                        listener.onAvailableCommandsChanged(forceCommands(availableCommands))
                    }

                    override fun onEvents(player: Player, events: Player.Events) {
                        // ส่งต่อ forwardingPlayer (ตัวห่อของเรา) แทน player ตัวจริงที่ ExoPlayer แนบ
                        // มาเอง กัน MediaSession เผลออ่าน getAvailableCommands() จาก ExoPlayer ตัวจริง
                        // ต่อจาก event นี้แทนที่จะอ่านจากตัวห่อที่ forced commands ไว้แล้ว
                        listener.onEvents(forwardingPlayer, events)
                    }
                }
                listenerWrappers[listener] = wrapped
                super.addListener(wrapped)
            }

            override fun removeListener(listener: Player.Listener) {
                val wrapped = listenerWrappers.remove(listener) ?: listener
                super.removeListener(wrapped)
            }

            override fun seekToNext() {
                PlaybackBridge.listener?.onSkipToNext()
            }

            override fun seekToNextMediaItem() {
                PlaybackBridge.listener?.onSkipToNext()
            }

            override fun seekToPrevious() {
                PlaybackBridge.listener?.onSkipToPrevious()
            }

            override fun seekToPreviousMediaItem() {
                PlaybackBridge.listener?.onSkipToPrevious()
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * ผู้ใช้ปัดแอปทิ้งออกจาก recent tasks — ถ้าไม่ได้เล่นเพลงอยู่ ให้ปิดเซอร์วิสไปด้วยเลย
     * (isPlaybackOngoing() เป็นตัวช่วยของ MediaSessionService เอง เช็คทั้งว่ากำลังเล่นอยู่ไหม
     * และเซอร์วิสอยู่ใน foreground อยู่ไหม) กันเซอร์วิสค้างกิน memory/battery อยู่เบื้องหลัง
     * ทั้งที่ผู้ใช้ปิดแอปไปแล้วและไม่ได้ฟังเพลงอยู่ — ถ้ากำลังเล่นอยู่ ปล่อยให้เล่นต่อตามปกติ
     * (พฤติกรรมมาตรฐานของแอปเพลงทั่วไป เช่น Spotify)
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!isPlaybackOngoing()) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        PlaybackBridge.listener = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private companion object {
        val FORCED_QUEUE_COMMANDS = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )
    }
}
