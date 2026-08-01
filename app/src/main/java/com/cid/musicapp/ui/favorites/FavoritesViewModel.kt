package com.cid.musicapp.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.data.repository.Track
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** จัดการแท็บ "เพลงโปรด" — แสดงเพลงที่กดใจไว้ทั้งหมด เรียงใหม่สุดอยู่บนสุด (ดู AppSettings.favoriteTracksFlow) */
class FavoritesViewModel(private val appSettings: AppSettings) : ViewModel() {

    val favorites: StateFlow<List<Track>> = appSettings.favoriteTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeFavorite(trackId: String) {
        viewModelScope.launch { appSettings.removeFavorite(trackId) }
    }
}
