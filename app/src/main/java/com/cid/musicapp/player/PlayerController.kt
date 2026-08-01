package com.cid.musicapp.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.data.repository.MusicRepository
import com.cid.musicapp.data.repository.Track
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

enum class RepeatMode { OFF, ALL, ONE }

/** โหมดการเล่น — AUDIO เล่นเสียงล้วน (ปกติ), VIDEO เล่นวิดีโอ (มีเสียงในตัว) ให้ดูภาพประกอบด้วย */
enum class PlaybackMode { AUDIO, VIDEO }

/** เพลงที่กำลังจะเล่นถัดไปในคิว พร้อมตำแหน่งใน play-order (ใช้กดข้ามไปเล่นตรงๆ ได้) */
data class UpcomingItem(val orderPosition: Int, val track: Track)

data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val isResolving: Boolean = false,
    val errorMessage: String? = null,
    val currentTitle: String? = null,
    val currentArtist: String? = null,
    val currentThumbnailUrl: String? = null,
    // id ของ track ที่กำลังเล่นอยู่ตอนนี้ — ใช้เทียบกับรายการเพลงโปรดเพื่อโชว์สถานะหัวใจใน PlayerScreen
    val currentTrackId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val upcoming: List<UpcomingItem> = emptyList(),
    val playbackMode: PlaybackMode = PlaybackMode.AUDIO,
    val playbackSpeed: Float = AppConstants.DEFAULT_PLAYBACK_SPEED,
    // เวลาที่เหลือก่อนเพลงจะหยุดเองอัตโนมัติ (sleep timer) — null = ไม่ได้ตั้งไว้
    val sleepTimerRemainingMs: Long? = null,
    // session id ของ ExoPlayer ตอนนี้ — ใช้ผูก android.media.audiofx.Visualizer สำหรับ waveform เท่านั้น
    // เปลี่ยนค่าทุกครั้งที่ต่อ MediaController ใหม่หรือสลับ media item บางกรณี
    val audioSessionId: Int = 0
)

/**
 * ตัวกลางระหว่าง UI (ViewModel) กับ PlaybackService — คุมคิวเพลง, shuffle, repeat
 * มี instance เดียวต่อแอป (สร้างจาก AppContainer) กัน connect ซ้ำหลายครั้ง
 * และอยู่ยาวตลอดอายุแอป (ไม่ผูกกับ ViewModel ไหนโดยเฉพาะ) จึงมี CoroutineScope ของตัวเอง
 */
class PlayerController(
    private val context: Context,
    private val repository: MusicRepository,
    private val appSettings: AppSettings
) {

    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var queue: List<Track> = emptyList()

    // ลำดับการเล่นจริง (เก็บเป็น index เข้า queue) — ปกติเรียงตามลำดับ, สลับเป็น shuffle ได้
    private var order: List<Int> = emptyList()
    private var orderPosition: Int = -1

    private var isShuffleEnabled = false
    private var repeatMode = RepeatMode.OFF
    private var playbackMode = PlaybackMode.AUDIO
    private var playbackSpeed = AppConstants.DEFAULT_PLAYBACK_SPEED

    // job ของ playCurrent() ที่กำลังทำงานอยู่ (ถ้ามี) — cancel ตัวเก่าทิ้งทุกครั้งก่อนเริ่มตัวใหม่
    // กัน race condition: ถ้าผู้ใช้กด next/previous รัวๆ เร็วกว่าที่ resolveAudioStreamUrl() แต่ละครั้ง
    // จะตอบกลับ คำขอเก่าที่ตอบช้ากว่าอาจมาทับผลของคำขอล่าสุดที่ตอบเร็วกว่า ทำให้เพลงที่เล่นจริงกลาย
    // เป็นเพลงผิดตัวจากที่ orderPosition ชี้ไว้ (เทียบเท่า pattern เดียวกับ searchJob ใน SearchViewModel)
    private var playJob: Job? = null

    private var sleepTimerJob: Job? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncStateFrom(player)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                scope.launch {
                    if (repeatMode == RepeatMode.ONE || appSettings.autoAdvanceFlow.first()) {
                        advanceAfterTrackEnded()
                    }
                }
            }
        }

        // audioSessionId ไม่ใช่ getter บน Player เฉยๆ (มีแค่ใน ExoPlayer โดยเฉพาะ ซึ่ง MediaController
        // ไม่ implement) ต้องดักจาก callback นี้แทนถึงจะได้ค่าที่ถูกต้องผ่าน MediaController
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            _state.value = _state.value.copy(audioSessionId = audioSessionId)
        }
    }

    suspend fun connect() {
        if (controller != null) return

        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        controller = suspendCancellableCoroutine { cont ->
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener(
                {
                    val c = future.get()
                    c.addListener(listener)
                    if (cont.isActive) cont.resume(c)
                },
                MoreExecutors.directExecutor()
            )
        }

        // รับคำสั่ง next/previous ที่มาจากนอกแอป (หน้าจอล็อก, บลูทูธ, ปุ่มหูฟัง) ผ่าน PlaybackBridge
        // ดู PlaybackService (ForwardingPlayer) และ PlaybackBridge.kt สำหรับรายละเอียดเต็มๆ
        PlaybackBridge.listener = object : PlaybackBridge.QueueNavigationListener {
            override fun onSkipToNext() = next()
            override fun onSkipToPrevious() = previous()
        }

        startPositionTicker()
    }

    /** อัปเดตตำแหน่งเพลงทุกครึ่งวินาทีระหว่างเล่น กัน seek bar ค้าง/ไม่ขยับ */
    private fun startPositionTicker() {
        scope.launch {
            while (isActive) {
                val player = controller
                if (player != null && player.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.coerceAtLeast(0L)
                    )
                }
                delay(AppConstants.POSITION_TICKER_INTERVAL_MILLIS)
            }
        }
    }

    /** เริ่มเล่นทั้งลิสต์เป็นคิว โดยเริ่มจาก track ที่ผู้ใช้กด (startIndex) */
    fun playQueue(tracks: List<Track>, startIndex: Int) {
        queue = tracks
        order = tracks.indices.toList()
        orderPosition = order.indexOf(startIndex).coerceAtLeast(0)

        if (isShuffleEnabled) {
            shuffleOrderKeepingCurrent()
        }

        launchPlayCurrent()
    }

    /** เพิ่ม track ต่อท้ายคิว (เล่นหลังสุด) — ถ้ายังไม่มีคิวอยู่เลย ให้เริ่มเล่นทันทีแทน */
    fun addToQueue(track: Track) {
        if (queue.isEmpty()) {
            playQueue(listOf(track), 0)
            return
        }
        val newQueueIndex = queue.size
        queue = queue + track
        order = order + newQueueIndex
        publishUpcoming()
    }

    /** แทรก track ให้เล่นเป็นเพลงถัดไปทันที (ก่อนเพลงอื่นๆ ที่ต่อคิวไว้) — ถ้ายังไม่มีคิว ให้เริ่มเล่นทันทีแทน */
    fun playNext(track: Track) {
        if (queue.isEmpty()) {
            playQueue(listOf(track), 0)
            return
        }
        val newQueueIndex = queue.size
        queue = queue + track
        val insertAt = (orderPosition + 1).coerceAtMost(order.size)
        order = order.toMutableList().apply { add(insertAt, newQueueIndex) }
        publishUpcoming()
    }

    /** กดเพลงใน "ถัดไป" โดยตรง ข้ามไปเล่นตำแหน่งนั้นใน play-order ทันที */
    fun playAtOrderPosition(targetOrderPosition: Int) {
        if (targetOrderPosition !in order.indices) return
        orderPosition = targetOrderPosition
        launchPlayCurrent()
    }

    fun next() {
        moveOrderPosition(forward = true)
    }

    fun previous() {
        val player = controller
        // ถ้าเล่นเกิน 3 วิแล้ว กดย้อนกลับ = seek ไปต้นเพลงปัจจุบันก่อน (พฤติกรรมมาตรฐานของ music player)
        if (player != null && player.currentPosition > AppConstants.SEEK_TO_RESTART_THRESHOLD_MILLIS) {
            player.seekTo(0)
            return
        }
        moveOrderPosition(forward = false)
    }

    /** เรียกตอนเพลงจบเองตามธรรมชาติ (ไม่ใช่ผู้ใช้กด next) — เคารพ repeat = ONE เป็นพิเศษ */
    private fun advanceAfterTrackEnded() {
        if (repeatMode == RepeatMode.ONE) {
            controller?.apply {
                seekTo(0)
                play()
            }
            return
        }
        moveOrderPosition(forward = true, isAutoAdvance = true)
    }

    private fun moveOrderPosition(forward: Boolean, isAutoAdvance: Boolean = false) {
        if (order.isEmpty()) return

        var next = orderPosition + if (forward) 1 else -1

        if (next > order.lastIndex) {
            if (repeatMode == RepeatMode.ALL) next = 0 else return
        } else if (next < 0) {
            if (repeatMode == RepeatMode.ALL) next = order.lastIndex else return
        }

        orderPosition = next
        launchPlayCurrent()
    }

    /** cancel job ของ playCurrent() ตัวก่อนหน้าเสมอก่อนเริ่มตัวใหม่ — ดูคอมเมนต์ที่ field playJob ด้านบน */
    private fun launchPlayCurrent(resumeAtMs: Long = 0L) {
        playJob?.cancel()
        playJob = scope.launch { playCurrent(resumeAtMs) }
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        if (isShuffleEnabled) {
            shuffleOrderKeepingCurrent()
        } else {
            // กลับไปเรียงตามลำดับเดิม โดยให้เพลงที่กำลังเล่นอยู่คงตำแหน่งปัจจุบันไว้
            val currentQueueIndex = order.getOrNull(orderPosition)
            order = queue.indices.toList()
            orderPosition = currentQueueIndex?.let { order.indexOf(it) } ?: 0
        }
        publishUpcoming()
    }

    private fun shuffleOrderKeepingCurrent() {
        val currentQueueIndex = order.getOrNull(orderPosition) ?: 0
        val rest = queue.indices.filter { it != currentQueueIndex }.shuffled()
        order = listOf(currentQueueIndex) + rest
        orderPosition = 0
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = repeatMode)
    }

    /** @param resumeAtMs ตำแหน่งที่จะ seek ไปทันทีหลังโหลดเสร็จ — ใช้ตอนสลับโหมดเสียง/วิดีโอกลางเพลง ไม่ใช่เริ่มเพลงใหม่ปกติ (ค่า default 0) */
    private suspend fun playCurrent(resumeAtMs: Long = 0L) {
        val queueIndex = order.getOrNull(orderPosition) ?: return
        val track = queue.getOrNull(queueIndex) ?: return

        _state.value = _state.value.copy(
            isResolving = true,
            errorMessage = null
        )
        publishUpcoming()

        try {
            val streamUrl = when (playbackMode) {
                PlaybackMode.AUDIO -> repository.resolveAudioStreamUrl(track)
                PlaybackMode.VIDEO -> repository.resolveVideoStreamUrl(track)
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .apply { track.thumbnailUrl?.let { setArtworkUri(android.net.Uri.parse(it)) } }
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaMetadata(metadata)
                .build()

            controller?.apply {
                setMediaItem(mediaItem)
                prepare()
                if (resumeAtMs > 0L) seekTo(resumeAtMs)
                setPlaybackSpeed(playbackSpeed)
                play()
            }

            _state.value = _state.value.copy(isResolving = false, currentTrackId = track.id)
        } catch (e: Exception) {
            val modeLabel = if (playbackMode == PlaybackMode.VIDEO) "วิดีโอ" else "เสียง"
            _state.value = _state.value.copy(
                isResolving = false,
                errorMessage = "เล่น${modeLabel}นี้ไม่ได้: ${e.message ?: "เกิดข้อผิดพลาด"}"
            )
        }
    }

    /** สลับโหมดเสียง/วิดีโอ — โหลดสตรีมใหม่ตามโหมดที่เลือก แล้ว resume ต่อจากตำแหน่งเดิมที่ฟัง/ดูค้างไว้ */
    fun setPlaybackMode(mode: PlaybackMode) {
        if (playbackMode == mode) return
        playbackMode = mode
        _state.value = _state.value.copy(playbackMode = mode)
        val resumeAtMs = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
        launchPlayCurrent(resumeAtMs)
    }

    /** ปรับความเร็วเล่นเพลง (1.0 = ปกติ) — มีผลทันทีกับเพลงที่กำลังเล่นอยู่ */
    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        controller?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    /** เลื่อนตำแหน่งเพลงไปข้างหน้า/ถอยหลังจากตำแหน่งปัจจุบัน (ค่าติดลบ = ถอยหลัง) เช่นปุ่ม +10s/-10s */
    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
        seekTo(target)
    }

    /**
     * ลบเพลงออกจากคิว "ถัดไป" ตามตำแหน่งใน play-order — ตั้งใจไม่ให้ลบเพลงที่กำลังเล่นอยู่ตรงนี้
     * (ผู้ใช้ต้องกด next เองก่อนถ้าอยากข้าม) กัน state ของเพลงที่กำลังเล่นเพี้ยนกลางทาง
     */
    fun removeFromQueue(targetOrderPosition: Int) {
        if (targetOrderPosition !in order.indices || targetOrderPosition == orderPosition) return

        order = order.toMutableList().apply { removeAt(targetOrderPosition) }
        if (targetOrderPosition < orderPosition) {
            orderPosition -= 1
        }
        publishUpcoming()
    }

    /**
     * ย้ายตำแหน่งเพลงในคิว "ถัดไป" (ลากสลับลำดับ) — เช่นเดียวกับ removeFromQueue ไม่ให้ย้าย
     * เพลงที่กำลังเล่นอยู่ตรงนี้ผ่านทางนี้
     */
    fun moveQueueItem(fromOrderPosition: Int, toOrderPosition: Int) {
        if (fromOrderPosition !in order.indices || toOrderPosition !in order.indices) return
        if (fromOrderPosition == orderPosition || toOrderPosition == orderPosition) return
        if (fromOrderPosition == toOrderPosition) return

        order = order.toMutableList().apply { add(toOrderPosition, removeAt(fromOrderPosition)) }
        publishUpcoming()
    }

    /**
     * ตั้งเวลาปิดเพลงอัตโนมัติ (sleep timer) — ยกเลิกตัวเก่าทิ้งเสมอก่อนเริ่มนับใหม่ (ตั้งซ้ำ = รีเซ็ตเวลา)
     * นับถอยหลังจริงด้วย wall-clock timestamp (ไม่ใช่แค่หัก duration ทุก tick) กันเวลาคลาดเคลื่อนสะสม
     * ถ้า coroutine โดน delay ช้ากว่าที่ตั้งไว้บ้าง (เช่นระบบไปหน่วง background work)
     */
    fun setSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        val endAtMillis = System.currentTimeMillis() + durationMs
        sleepTimerJob = scope.launch {
            while (isActive) {
                val remaining = endAtMillis - System.currentTimeMillis()
                if (remaining <= 0L) {
                    controller?.pause()
                    _state.value = _state.value.copy(sleepTimerRemainingMs = null)
                    break
                }
                _state.value = _state.value.copy(sleepTimerRemainingMs = remaining)
                delay(AppConstants.SLEEP_TIMER_TICK_MILLIS)
            }
        }
    }

    /** ยกเลิก sleep timer ที่ตั้งไว้ (ปุ่ม "ปิด" ในเมนูตั้งเวลา) */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _state.value = _state.value.copy(sleepTimerRemainingMs = null)
    }

    /** เปิดให้ UI ผูก Player เข้ากับ PlayerView ตอนโหมดวิดีโอ (MediaController implement Player อยู่แล้ว) */
    fun rawPlayer(): Player? = controller

    private fun publishUpcoming() {
        val upcoming = ((orderPosition + 1)..order.lastIndex).mapNotNull { pos ->
            queue.getOrNull(order[pos])?.let { UpcomingItem(pos, it) }
        }
        _state.value = _state.value.copy(
            hasNext = orderPosition < order.lastIndex || repeatMode == RepeatMode.ALL,
            hasPrevious = orderPosition > 0 || repeatMode == RepeatMode.ALL,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            upcoming = upcoming
        )
    }

    fun togglePlayPause() {
        controller?.apply {
            if (isPlaying) pause() else play()
        }
    }

    /** หยุดเล่นเพลง ล้างคิวทั้งหมด และซ่อน mini player bar (กดปุ่มปิดที่ mini player) */
    fun stopAndDismiss() {
        playJob?.cancel()
        sleepTimerJob?.cancel()
        controller?.apply {
            pause()
            stop()
            clearMediaItems()
        }
        queue = emptyList()
        order = emptyList()
        orderPosition = -1
        _state.value = PlaybackUiState()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun syncStateFrom(player: Player) {
        _state.value = _state.value.copy(
            isPlaying = player.isPlaying,
            currentTitle = player.mediaMetadata.title?.toString(),
            currentArtist = player.mediaMetadata.artist?.toString(),
            currentThumbnailUrl = player.mediaMetadata.artworkUri?.toString(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L)
            // audioSessionId ไม่ได้อัปเดตตรงนี้ — มาจาก onAudioSessionIdChanged callback ด้านบนแทน
        )
        publishUpcoming()
    }

    fun release() {
        if (PlaybackBridge.listener != null) {
            PlaybackBridge.listener = null
        }
        playJob?.cancel()
        sleepTimerJob?.cancel()
        controller?.release()
        controller = null
    }
}
