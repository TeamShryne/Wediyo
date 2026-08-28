package com.teamshryne.wediyo.ui.screens.podcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.model.UiPodcastDetail
import com.teamshryne.wediyo.data.model.UiPodcastEpisode
import com.teamshryne.wediyo.data.repository.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PodcastUiState(
    val playlistId: String = "",
    val detail: UiPodcastDetail? = null,
    val episodes: List<UiPodcastEpisode> = emptyList(),
    val continuation: String = "",
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val error: String? = null
)

class PodcastViewModel : ViewModel() {
    private val repo = ChannelRepository()
    private val _state = MutableStateFlow(PodcastUiState())
    val state: StateFlow<PodcastUiState> = _state

    fun load(playlistId: String) {
        if (playlistId.isBlank()) return
        val cleanId = playlistId.removePrefix("VL")
        _state.value = _state.value.copy(playlistId = cleanId, isLoading = true, error = null, episodes = emptyList(), continuation = "")
        viewModelScope.launch {
            try {
                val res = repo.fetchPodcast(cleanId, "")
                _state.value = _state.value.copy(detail = res, episodes = res.episodes, continuation = res.continuation, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load podcast")
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
                val res = repo.fetchPodcast(pid, cont)
                _state.value = _state.value.copy(episodes = _state.value.episodes + res.episodes, continuation = res.continuation, isPaginating = false)
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
