package com.teamshryne.wediyo.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.model.UiSearchResult
import com.teamshryne.wediyo.data.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val params: String = "",
    val result: UiSearchResult? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val chipsToken: String? = null
)

class SearchViewModel : ViewModel() {
    private val repo = SearchRepository()
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    fun setQuery(q: String) { _state.value = _state.value.copy(query = q) }
    fun setParams(p: String) { _state.value = _state.value.copy(params = p) }

    fun search(newQuery: String? = null, newParams: String? = null) {
        val q = newQuery ?: _state.value.query
        val p = newParams ?: _state.value.params
        if (q.isBlank()) return
        _state.value = _state.value.copy(isLoading = true, error = null, query = q, params = p)
        viewModelScope.launch {
            try {
                val r = repo.search(q, p, "")
                _state.value = _state.value.copy(result = r, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Search failed")
            }
        }
    }

    fun searchChip(token: String) {
        if (token.isBlank()) return
        _state.value = _state.value.copy(isLoading = true, error = null, chipsToken = token)
        viewModelScope.launch {
            try {
                val r = repo.searchChip(token)
                _state.value = _state.value.copy(result = r, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Chip failed")
            }
        }
    }

    fun loadMore() {
        val cur = _state.value.result ?: return
        val cont = cur.continuation
        if (cont.isBlank() || _state.value.isLoading) return
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val q = _state.value.query
                val p = _state.value.params
                val chipTok = _state.value.chipsToken
                val next = if (!chipTok.isNullOrBlank()) {
                    // chip pagination via token continuation (search chip)
                    repo.searchChip(cont) // actually continuation from chip result is just next token; using searchChip with cont works as search("",cont)
                    // fallback to repo.search with chip token logic: WediyoEngine handles continuation as search
                    // we use searchChip(cont) which internally does Wediyo.search("",cont)
                } else if (p.isNotBlank()) {
                    repo.search(q, p, cont)
                } else {
                    repo.search(q, "", cont)
                }
                // merge
                val merged = cur.copy(
                    videos = cur.videos + next.videos,
                    channels = cur.channels + next.channels,
                    shorts = cur.shorts + next.shorts,
                    playlists = cur.playlists + next.playlists,
                    continuation = next.continuation,
                    chips = next.chips.ifEmpty { cur.chips },
                    filterGroups = next.filterGroups.ifEmpty { cur.filterGroups }
                )
                _state.value = _state.value.copy(result = merged, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
