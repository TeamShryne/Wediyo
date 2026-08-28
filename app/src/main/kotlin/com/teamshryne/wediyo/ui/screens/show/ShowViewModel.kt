package com.teamshryne.wediyo.ui.screens.show

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.model.UiShowDetail
import com.teamshryne.wediyo.data.model.UiShowEpisode
import com.teamshryne.wediyo.data.repository.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ShowUiState(
    val playlistId: String = "",
    val detail: UiShowDetail? = null,
    val episodes: List<UiShowEpisode> = emptyList(),
    val continuation: String = "",
    val currentParams: String = "",
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val isSeasonSwitching: Boolean = false,
    val error: String? = null
)

class ShowViewModel : ViewModel() {
    private val repo = ChannelRepository()
    private val _state = MutableStateFlow(ShowUiState())
    val state: StateFlow<ShowUiState> = _state

    fun load(playlistId: String) {
        if (playlistId.isBlank()) return
        val cleanId = playlistId.removePrefix("VL")
        _state.value = _state.value.copy(playlistId = cleanId, isLoading = true, error = null, episodes = emptyList(), continuation = "", currentParams = "")
        viewModelScope.launch {
            try {
                val res = repo.fetchShow(cleanId, "")
                _state.value = _state.value.copy(detail = res, episodes = res.episodes, continuation = res.continuation, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load show")
            }
        }
    }

    fun loadMore() {
        val pid = _state.value.playlistId
        val cont = _state.value.continuation
        if (pid.isBlank() || cont.isBlank() || _state.value.isPaginating || _state.value.isSeasonSwitching) return
        _state.value = _state.value.copy(isPaginating = true)
        viewModelScope.launch {
            try {
                val res = repo.fetchShow(pid, cont)
                _state.value = _state.value.copy(episodes = _state.value.episodes + res.episodes, continuation = res.continuation, isPaginating = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPaginating = false, error = e.message ?: "Failed to load more")
            }
        }
    }

    fun switchSeason(params: String) {
        val pid = _state.value.playlistId
        if (pid.isBlank() || params.isBlank()) return
        _state.value = _state.value.copy(isSeasonSwitching = true, error = null)
        viewModelScope.launch {
            try {
                val res = repo.fetchShowWithSeason(pid, params)
                _state.value = _state.value.copy(detail = res, episodes = res.episodes, continuation = res.continuation, currentParams = params, isSeasonSwitching = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSeasonSwitching = false, error = e.message ?: "Failed to switch season")
            }
        }
    }

    fun retry() {
        val pid = _state.value.playlistId
        if (pid.isNotBlank()) load(pid)
    }
}
