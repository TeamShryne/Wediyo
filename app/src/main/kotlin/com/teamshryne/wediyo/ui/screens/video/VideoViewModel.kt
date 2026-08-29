package com.teamshryne.wediyo.ui.screens.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.engine.WediyoEngine
import com.teamshryne.wediyo.data.model.UiComment
import com.teamshryne.wediyo.data.model.UiCommentSortFilter
import com.teamshryne.wediyo.data.model.UiVideo
import com.teamshryne.wediyo.data.model.UiVideoDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class VideoUiState(
    val isLoading: Boolean = true,
    val videoId: String = "",
    val detail: UiVideoDetail? = null,
    val related: List<UiVideo> = emptyList(),
    val relatedContinuation: String? = null,
    val relatedLoading: Boolean = false,
    val comments: List<UiComment> = emptyList(),
    val commentsContinuation: String? = null,
    val commentsCount: String? = null,
    val commentsSortFilters: List<UiCommentSortFilter> = emptyList(),
    val commentsLoading: Boolean = false,
    val error: String? = null,
    val expandedDesc: Boolean = false,
    val showDetailsSheet: Boolean = false
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
                _state.value = VideoUiState(
                    isLoading = false,
                    videoId = videoId,
                    detail = d,
                    related = d.relatedVideos,
                    relatedContinuation = d.relatedContinuation.takeIf { it.isNotBlank() },
                    commentsContinuation = d.commentsContinuation.takeIf { it.isNotBlank() },
                    commentsCount = d.commentsCountText.takeIf { it.isNotBlank() }
                )
                // auto-load first comments page via Mediyo flow if token available
                d.commentsContinuation.takeIf { it.isNotBlank() }?.let { loadComments(it, initial = true) }
            } catch (e: Exception) {
                _state.value = VideoUiState(isLoading = false, videoId = videoId, error = e.message ?: "Failed to load video")
            }
        }
    }

    fun loadMoreRelated() {
        val cont = _state.value.relatedContinuation ?: return
        if (_state.value.relatedLoading) return
        _state.value = _state.value.copy(relatedLoading = true)
        viewModelScope.launch {
            try {
                val more = WediyoEngine.fetchRelated(cont)
                _state.value = _state.value.copy(
                    related = _state.value.related + more.relatedVideos,
                    relatedContinuation = more.relatedContinuation.takeIf { it.isNotBlank() },
                    relatedLoading = false
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(relatedLoading = false)
            }
        }
    }

    fun loadComments(continuation: String, initial: Boolean = false) {
        if (continuation.isBlank()) return
        if (_state.value.commentsLoading) return
        _state.value = _state.value.copy(commentsLoading = true)
        viewModelScope.launch {
            try {
                val page = WediyoEngine.fetchComments(continuation)
                _state.value = _state.value.copy(
                    comments = if (initial) page.comments else _state.value.comments + page.comments,
                    commentsContinuation = page.continuation,
                    commentsCount = page.count ?: _state.value.commentsCount,
                    commentsSortFilters = if (page.sortFilters.isNotEmpty()) page.sortFilters else _state.value.commentsSortFilters,
                    commentsLoading = false
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(commentsLoading = false)
            }
        }
    }

    fun loadMoreComments() {
        val cont = _state.value.commentsContinuation ?: return
        loadComments(cont)
    }

    fun switchCommentsSort(token: String) {
        if (token.isBlank()) return
        _state.value = _state.value.copy(comments = emptyList(), commentsContinuation = token, commentsLoading = true)
        viewModelScope.launch {
            try {
                val page = WediyoEngine.fetchComments(token)
                _state.value = _state.value.copy(
                    comments = page.comments,
                    commentsContinuation = page.continuation,
                    commentsCount = page.count ?: _state.value.commentsCount,
                    commentsLoading = false
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(commentsLoading = false)
            }
        }
    }

    fun loadReplies(continuation: String, onResult: (List<UiComment>) -> Unit) {
        if (continuation.isBlank()) return
        viewModelScope.launch {
            try {
                val page = WediyoEngine.fetchReplies(continuation)
                onResult(page.comments)
            } catch (_: Exception) {
                onResult(emptyList())
            }
        }
    }

    fun toggleDesc() {
        _state.value = _state.value.copy(expandedDesc = !_state.value.expandedDesc)
    }

    fun setDetailsSheet(show: Boolean) {
        _state.value = _state.value.copy(showDetailsSheet = show)
    }

    fun retry() {
        load(_state.value.videoId)
    }
}
