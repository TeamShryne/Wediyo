package com.teamshryne.wediyo.ui.screens.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.model.UiCourseDetail
import com.teamshryne.wediyo.data.model.UiCourseVideo
import com.teamshryne.wediyo.data.repository.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CourseUiState(
    val playlistId: String = "",
    val detail: UiCourseDetail? = null,
    val videos: List<UiCourseVideo> = emptyList(),
    val continuation: String = "",
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val error: String? = null
)

class CourseViewModel : ViewModel() {
    private val repo = ChannelRepository()
    private val _state = MutableStateFlow(CourseUiState())
    val state: StateFlow<CourseUiState> = _state

    fun load(playlistId: String) {
        if (playlistId.isBlank()) return
        val cleanId = playlistId.removePrefix("VL")
        _state.value = _state.value.copy(playlistId = cleanId, isLoading = true, error = null, videos = emptyList(), continuation = "")
        viewModelScope.launch {
            try {
                val res = repo.fetchCourse(cleanId, "")
                _state.value = _state.value.copy(detail = res, videos = res.videos, continuation = res.continuation, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load course")
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
                val res = repo.fetchCourse(pid, cont)
                _state.value = _state.value.copy(videos = _state.value.videos + res.videos, continuation = res.continuation, isPaginating = false)
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
