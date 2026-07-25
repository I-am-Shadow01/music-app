package com.cid.musicapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.data.repository.MusicRepository
import com.cid.musicapp.data.repository.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
    val recentSearches: List<String> = emptyList()
)

class SearchViewModel(
    private val repository: MusicRepository,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    // job ของการค้นหา/โหลดเพิ่มที่กำลังทำงานอยู่ (ถ้ามี) — เก็บไว้เพื่อ cancel ตัวเก่าทิ้งเวลามีคำสั่งใหม่เข้ามา
    // กัน race condition: ถ้าไม่ cancel แล้วผู้ใช้ค้นหาซ้อนกันเร็วๆ ผลของคำค้นหาที่ตอบช้ากว่าอาจมาทับ
    // ผลของคำค้นหาล่าสุดที่ตอบเร็วกว่า ทำให้ผู้ใช้เห็นผลลัพธ์ผิดคำ
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            appSettings.recentSearchesFlow.collect { recent ->
                _uiState.update { it.copy(recentSearches = recent) }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
    }

    fun search() {
        runSearch(_uiState.value.query)
    }

    /** เลือกคำค้นหาจากประวัติ — เติมลงช่องค้นหาแล้วค้นหาให้เลย ไม่ต้องพิมพ์ซ้ำ */
    fun onRecentSearchSelected(query: String) {
        _uiState.update { it.copy(query = query) }
        runSearch(query)
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { appSettings.removeRecentSearch(query) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { appSettings.clearRecentSearches() }
    }

    /** ลองค้นหาคำเดิมอีกครั้งหลัง error (ปุ่ม "ลองอีกครั้ง") */
    fun retry() {
        runSearch(_uiState.value.query)
    }

    private fun runSearch(query: String) {
        if (query.isBlank()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val page = repository.search(query)
                _uiState.update {
                    it.copy(
                        results = page.tracks,
                        canLoadMore = page.hasMore,
                        isLoading = false
                    )
                }
                appSettings.addRecentSearch(query)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "เกิดข้อผิดพลาด"
                    )
                }
            }
        }
    }

    /** โหลดผลค้นหาหน้าถัดไปต่อจากลิสต์ปัจจุบัน — เรียกตอนเลื่อนใกล้สุดลิสต์ (ดู SearchScreen) */
    fun loadMore() {
        val state = _uiState.value
        if (!state.canLoadMore || state.isLoading || state.isLoadingMore) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val page = repository.loadMoreSearchResults()
                _uiState.update {
                    it.copy(
                        results = it.results + page.tracks,
                        canLoadMore = page.hasMore,
                        isLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                // โหลดเพิ่มพลาด — ไม่ล้างผลลัพธ์เดิมที่มีอยู่แล้ว แค่หยุด loading และปิด canLoadMore
                // กันผู้ใช้เห็นลิสต์กระตุกหาย ผู้ใช้ยังเลื่อนดูของเดิมได้ตามปกติ
                _uiState.update { it.copy(isLoadingMore = false, canLoadMore = false) }
            }
        }
    }
}
