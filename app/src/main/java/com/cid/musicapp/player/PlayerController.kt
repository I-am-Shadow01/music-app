package com.cid.musicapp.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cid.musicapp.data.repository.MusicRepository
import com.cid.musicapp.data.repository.Track
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

enum class RepeatMode { OFF, ALL, ONE }

/** เพลงที่กำลังจะเล่นถัดไปในคิว พร้อมตำแหน่งใน play-order (ใช้กดข้ามไปเล่นตรงๆ ได้) */
data class UpcomingItem(val orderPosition: Int, val track: Track)

data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val isResolving: Boolean = false,
    val errorMessage: String? = null,
    val currentTitle: String? = null,
    val currentArtist: String? = null,
    val currentThumbnailUrl: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val upcoming: List<UpcomingItem> = emptyList()
)

/**
 * ตัวกลางระหว่าง UI (ViewModel) กับ PlaybackService — คุมคิวเพลง, shuffle, repeat
 * มี instance เดียวต่อแอป (สร้างจาก AppContainer) กัน connect ซ้ำหลายครั้ง
 * และอยู่ยาวตลอดอายุแอป (ไม่ผูกกับ ViewModel ไหนโดยเฉพาะ) จึงมี CoroutineScope ของตัวเอง
 */
class PlayerController(
    private val context: Context,
    private val repository: MusicRepository
) {

    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var queue: List<Track> = emptyList()

    // ลำดับการเล่นจริง (เก็บเป็น index เข้า queue) — ปกติเรียงตามลำดับ, สลับเป็น shuffle ได้
    private var order: List<Int> = emptyList()
    private var orderPosition: Int = -1

    private var isShuffleEnabled = false
    private var repeatMode = RepeatMode.OFF

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncStateFrom(player)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                scope.launch { advanceAfterTrackEnded() }
            }
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
                delay(500)
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

        scope.launch { playCurrent() }
    }

    /** กดเพลงใน "ถัดไป" โดยตรง ข้ามไปเล่นตำแหน่งนั้นใน play-order ทันที */
    fun playAtOrderPosition(targetOrderPosition: Int) {
        if (targetOrderPosition !in order.indices) return
        orderPosition = targetOrderPosition
        scope.launch { playCurrent() }
    }

    fun next() {
        moveOrderPosition(forward = true)
    }

    fun previous() {
        val player = controller
        // ถ้าเล่นเกิน 3 วิแล้ว กดย้อนกลับ = seek ไปต้นเพลงปัจจุบันก่อน (พฤติกรรมมาตรฐานของ music player)
        if (player != null && player.currentPosition > 3000L) {
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
        scope.launch { playCurrent() }
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

    private suspend fun playCurrent() {
        val queueIndex = order.getOrNull(orderPosition) ?: return
        val track = queue.getOrNull(queueIndex) ?: return

        _state.value = _state.value.copy(
            isResolving = true,
            errorMessage = null
        )
        publishUpcoming()

        try {
            val streamUrl = repository.resolveAudioStreamUrl(track)

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
                play()
            }

            _state.value = _state.value.copy(isResolving = false)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isResolving = false,
                errorMessage = "เล่นเพลงนี้ไม่ได้: ${e.message ?: "เกิดข้อผิดพลาด"}"
            )
        }
    }

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
        )
        publishUpcoming()
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
