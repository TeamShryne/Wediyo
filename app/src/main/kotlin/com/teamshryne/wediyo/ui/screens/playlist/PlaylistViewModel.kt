package com.teamshryne.wediyo.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.model.UiPlaylistDetail
import com.teamshryne.wediyo.data.model.UiPlaylistVideo
import com.teamshryne.wediyo.data.repository.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlaylistUiState(
    val playlistId: String = "",
    val header: UiPlaylistDetail? = null,
    val detail: UiPlaylistDetail? = null,
    val videos: List<UiPlaylistVideo> = emptyList(),
    val continuation: String = "",
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val error: String? = null
)

class PlaylistViewModel : ViewModel() {
    private val repo = ChannelRepository()
    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state

    fun load(playlistId: String) {
        if (playlistId.isBlank()) return
        val cleanId = playlistId.removePrefix("VL")
        _state.value = _state.value.copy(playlistId = cleanId, isLoading = true, error = null, videos = emptyList(), continuation = "")
        viewModelScope.launch {
            try {
                val res = repo.fetchPlaylist(cleanId, "")
                _state.value = _state.value.copy(
                    header = res,
                    detail = res,
                    videos = res.videos,
                    continuation = res.continuation,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load playlist")
            }
        }
    }

    fun loadMore() {
        val pid = _state.value.playlistId
        val cont = _state.value.continuation
        if (pid.isBlank() || cont.isBlank() || _state.value.isPaginating) return
        _state.value = _state.value.copy(isPaginating = true)
        viewModelScope.launch {
            try {
                val res = repo.fetchPlaylist(pid, cont)
                val merged = _state.value.videos + res.videos
                _state.value = _state.value.copy(
                    videos = merged,
                    continuation = res.continuation,
                    isPaginating = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPaginating = false, error = e.message ?: "Failed to load more")
            }
        }
    }

    fun retry() {
        val pid = _state.value.playlistId
        if (pid.isNotBlank()) load(pid)
    }
}
