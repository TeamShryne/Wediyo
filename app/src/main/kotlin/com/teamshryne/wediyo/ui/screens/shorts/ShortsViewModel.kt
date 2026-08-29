package com.teamshryne.wediyo.ui.screens.shorts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.model.UiShort
import com.teamshryne.wediyo.data.model.UiVideoDetail
import com.teamshryne.wediyo.data.model.UiCommentsPage
import com.teamshryne.wediyo.data.engine.WediyoEngine
import com.teamshryne.wediyo.util.FilterParamsBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ShortsUiState(
    val isLoading: Boolean = true,
    val isPaginating: Boolean = false,
    val shorts: List<UiShort> = emptyList(),
    val continuation: String? = null,
    val error: String? = null,
    val details: Map<String, UiVideoDetail> = emptyMap(),
    val comments: Map<String, UiCommentsPage> = emptyMap(),
    val expandedCommentsFor: String? = null
)

class ShortsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ShortsUiState())
    val state: StateFlow<ShortsUiState> = _state

    private val shortsParams by lazy { FilterParamsBuilder.build(type = "Shorts", duration = "", uploadDate = "", features = emptyList(), prioritize = "") }

    fun loadInitial() {
        if (_state.value.shorts.isNotEmpty() && !_state.value.isLoading) return
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                // Try Shorts filter first, fallback to plain "shorts" search
                val queries = listOf("shorts" to shortsParams, "trending shorts" to shortsParams, "shorts" to "")
                var lastErr: Exception? = null
                for ((q, p) in queries) {
                    try {
                        val res = WediyoEngine.search(q, "", p)
                        // repo.search wraps searchWithParams; directly use WediyoEngine via search
                        // Use distinct call via WediyoEngine.search handles params
                        val shorts = res.shorts
                        if (shorts.isNotEmpty()) {
                            _state.value = ShortsUiState(
                                isLoading = false,
                                shorts = shorts,
                                continuation = res.continuation.takeIf { it.isNotBlank() },
                            )
                            // prefetch detail for first 3
                            shorts.take(3).forEach { fetchDetail(it.videoId) }
                            return@launch
                        }
                    } catch (e: Exception) { lastErr = e }
                }
                // fallback: fetch via search without params using a generic popular query
                val fallback = WediyoEngine.search("a", "", "")
                val shorts = fallback.shorts
                if (shorts.isNotEmpty()) {
                    _state.value = ShortsUiState(isLoading = false, shorts = shorts, continuation = fallback.continuation.takeIf { it.isNotBlank() })
                    shorts.take(3).forEach { fetchDetail(it.videoId) }
                } else {
                    _state.value = _state.value.copy(isLoading = false, error = lastErr?.message ?: "No shorts found")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load shorts")
            }
        }
    }

    fun loadMore() {
        val cont = _state.value.continuation ?: return
        if (_state.value.isPaginating || _state.value.isLoading) return
        _state.value = _state.value.copy(isPaginating = true)
        viewModelScope.launch {
            try {
                val cur = _state.value
                // Determine if we had shortsParams originally
                val res = try {
                    // try continuation via chip/search path — engine treats continuation token generically
                    WediyoEngine.search("", cont, "")
                    // WediyoEngine.search expects query+continuation; using empty query with continuation token works via search
                } catch (_: Exception) {
                    // fallback to searchWithChip style
                    WediyoEngine.search("shorts", cont, shortsParams)
                }
                // For search continuation, WediyoEngine handles token as query= "" continuation
                // But SearchRepository does repo.searchChip(token) -> Wediyo.search("", token)
                // So we try direct
                val nextShorts = res.shorts
                if (nextShorts.isEmpty()) {
                    // try alternative: use searchChip
                    val alt = WediyoEngine.search("", cont, "")
                    _state.value = _state.value.copy(
                        shorts = cur.shorts + alt.shorts,
                        continuation = alt.continuation.takeIf { it.isNotBlank() },
                        isPaginating = false
                    )
                    alt.shorts.take(2).forEach { fetchDetail(it.videoId) }
                } else {
                    _state.value = _state.value.copy(
                        shorts = cur.shorts + nextShorts,
                        continuation = res.continuation.takeIf { it.isNotBlank() },
                        isPaginating = false
                    )
                    nextShorts.take(2).forEach { fetchDetail(it.videoId) }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPaginating = false, error = e.message)
            }
        }
    }

    // Alternative pagination using direct searchChip handling — used by UI when near end
    fun loadMoreContinuation(cont: String) {
        if (_state.value.isPaginating) return
        _state.value = _state.value.copy(isPaginating = true)
        viewModelScope.launch {
            try {
                val cur = _state.value
                val next = WediyoEngine.search("", cont, "")
                // If shorts empty, try with query "shorts"
                val shorts = if (next.shorts.isNotEmpty()) next.shorts else {
                    val alt = WediyoEngine.search("shorts", cont, shortsParams)
                    alt.shorts
                }
                val cont2 = next.continuation.takeIf { it.isNotBlank() } ?: ""
                _state.value = cur.copy(
                    shorts = cur.shorts + shorts,
                    continuation = cont2.takeIf { it.isNotBlank() },
                    isPaginating = false
                )
                shorts.take(2).forEach { fetchDetail(it.videoId) }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPaginating = false)
            }
        }
    }

    fun fetchDetail(videoId: String) {
        if (videoId.isBlank() || _state.value.details.containsKey(videoId)) return
        viewModelScope.launch {
            try {
                val det = WediyoEngine.fetchVideoDetail(videoId)
                _state.value = _state.value.copy(details = _state.value.details + (videoId to det))
                // auto fetch first comments page if available
                det.commentsContinuation.takeIf { it.isNotBlank() }?.let { fetchComments(videoId, it) }
            } catch (_: Exception) {}
        }
    }

    fun fetchComments(videoId: String, continuation: String) {
        if (continuation.isBlank()) return
        viewModelScope.launch {
            try {
                val page = WediyoEngine.fetchComments(continuation)
                _state.value = _state.value.copy(comments = _state.value.comments + (videoId to page))
            } catch (_: Exception) {}
        }
    }

    fun loadMoreComments(videoId: String) {
        val page = _state.value.comments[videoId] ?: return
        val cont = page.continuation ?: return
        if (cont.isBlank()) return
        viewModelScope.launch {
            try {
                val next = WediyoEngine.fetchComments(cont)
                val merged = page.copy(comments = page.comments + next.comments, continuation = next.continuation)
                _state.value = _state.value.copy(comments = _state.value.comments + (videoId to merged))
            } catch (_: Exception) {}
        }
    }

    fun setCommentsSheet(videoId: String?) {
        _state.value = _state.value.copy(expandedCommentsFor = videoId)
    }

    fun retry() {
        _state.value = ShortsUiState()
        loadInitial()
    }
}
