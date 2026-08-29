package com.teamshryne.wediyo.ui.screens.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.engine.WediyoEngine
import com.teamshryne.wediyo.data.model.UiVideoDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class VideoUiState(
    val isLoading: Boolean = true,
    val videoId: String = "",
    val detail: UiVideoDetail? = null,
    val error: String? = null,
    val expandedDesc: Boolean = false
)

class VideoViewModel : ViewModel() {
    private val _state = MutableStateFlow(VideoUiState())
    val state: StateFlow<VideoUiState> = _state

    fun load(videoId: String) {
        if (videoId.isBlank()) {
            _state.value = _state.value.copy(isLoading = false, error = "Invalid video id")
            return
        }
        _state.value = VideoUiState(isLoading = true, videoId = videoId)
        viewModelScope.launch {
            try {
                val d = WediyoEngine.fetchVideoDetail(videoId)
                _state.value = VideoUiState(isLoading = false, videoId = videoId, detail = d)
            } catch (e: Exception) {
                _state.value = VideoUiState(isLoading = false, videoId = videoId, error = e.message ?: "Failed to load video")
            }
        }
    }

    fun toggleDesc() {
        _state.value = _state.value.copy(expandedDesc = !_state.value.expandedDesc)
    }

    fun retry() {
        load(_state.value.videoId)
    }
}
