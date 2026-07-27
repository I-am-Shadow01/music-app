package com.cid.musicapp.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.cid.musicapp.player.PlaybackMode
import com.cid.musicapp.player.PlayerController
import kotlinx.coroutines.launch

class PlayerViewModel(private val playerController: PlayerController) : ViewModel() {

    val state = playerController.state

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
}
