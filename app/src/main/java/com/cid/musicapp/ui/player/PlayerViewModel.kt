package com.cid.musicapp.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.data.repository.Track
import com.cid.musicapp.player.PlaybackMode
import com.cid.musicapp.player.PlayerController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val playerController: PlayerController,
    private val appSettings: AppSettings
) : ViewModel() {

    val state = playerController.state

    /** เพลงที่กดใจไว้ทั้งหมด เก็บแค่ id ไว้เทียบว่าเพลงที่กำลังเล่นอยู่ตอนนี้กดใจไว้หรือยัง */
    val favoriteIds: StateFlow<Set<String>> = appSettings.favoriteTracksFlow
        .map { tracks -> tracks.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        viewModelScope.launch { playerController.connect() }
    }

    fun togglePlayPause() = playerController.togglePlayPause()

    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    /** ปุ่ม +10s/-10s — deltaMs ติดลบ = ถอยหลัง */
    fun seekBy(deltaMs: Long) = playerController.seekBy(deltaMs)

    fun next() = playerController.next()

    fun previous() = playerController.previous()

    fun toggleShuffle() = playerController.toggleShuffle()

    fun cycleRepeatMode() = playerController.cycleRepeatMode()

    fun playAtOrderPosition(orderPosition: Int) = playerController.playAtOrderPosition(orderPosition)

    fun setPlaybackMode(mode: PlaybackMode) = playerController.setPlaybackMode(mode)

    fun setPlaybackSpeed(speed: Float) = playerController.setPlaybackSpeed(speed)

    fun removeFromQueue(orderPosition: Int) = playerController.removeFromQueue(orderPosition)

    fun moveQueueItem(fromOrderPosition: Int, toOrderPosition: Int) =
        playerController.moveQueueItem(fromOrderPosition, toOrderPosition)

    /** ใช้ผูกกับ PlayerView ตอนโหมดวิดีโอเท่านั้น */
    fun rawPlayer(): Player? = playerController.rawPlayer()

    fun setSleepTimer(durationMs: Long) = playerController.setSleepTimer(durationMs)

    fun cancelSleepTimer() = playerController.cancelSleepTimer()

    /** สลับสถานะกดใจของเพลงที่กำลังเล่นอยู่ตอนนี้ — ไม่ทำอะไรถ้ายังไม่มีเพลงเล่นอยู่ */
    fun toggleFavoriteCurrent() {
        val current = state.value
        val trackId = current.currentTrackId ?: return
        val track = Track(
            id = trackId,
            title = current.currentTitle ?: "",
            artist = current.currentArtist ?: "",
            durationSeconds = if (current.durationMs > 0) (current.durationMs / 1000).toInt() else null,
            thumbnailUrl = current.currentThumbnailUrl
        )
        viewModelScope.launch { appSettings.toggleFavorite(track) }
    }
}
