package com.cid.musicapp.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cid.musicapp.player.PlayerController
import kotlinx.coroutines.launch

class PlayerViewModel(private val playerController: PlayerController) : ViewModel() {

    val state = playerController.state

    init {
        viewModelScope.launch { playerController.connect() }
    }

    fun togglePlayPause() = playerController.togglePlayPause()

    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun next() = playerController.next()

    fun previous() = playerController.previous()

    fun toggleShuffle() = playerController.toggleShuffle()

    fun cycleRepeatMode() = playerController.cycleRepeatMode()

    fun playAtOrderPosition(orderPosition: Int) = playerController.playAtOrderPosition(orderPosition)
}
