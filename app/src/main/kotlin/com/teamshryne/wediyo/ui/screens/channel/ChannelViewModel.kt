package com.teamshryne.wediyo.ui.screens.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.model.UiChannelHome
import com.teamshryne.wediyo.data.model.UiChannelLive
import com.teamshryne.wediyo.data.model.UiChannelPodcasts
import com.teamshryne.wediyo.data.model.UiChannelShorts
import com.teamshryne.wediyo.data.model.UiChannelVideoChip
import com.teamshryne.wediyo.data.model.UiChannelVideos
import com.teamshryne.wediyo.data.repository.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChannelUiState(
    val browseId: String = "",
    val home: UiChannelHome? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val videos: UiChannelVideos? = null,
    val videosList: List<com.teamshryne.wediyo.data.model.UiVideo> = emptyList(),
    val videoChips: List<UiChannelVideoChip> = emptyList(),
    val selectedChip: String? = null,
    val videosContinuation: String = "",
    val isVideosLoading: Boolean = false,
    val shorts: UiChannelShorts? = null,
    val shortsList: List<com.teamshryne.wediyo.data.model.UiShort> = emptyList(),
    val shortsChips: List<UiChannelVideoChip> = emptyList(),
    val selectedShortsChip: String? = null,
    val shortsContinuation: String = "",
    val isShortsLoading: Boolean = false,
    val live: UiChannelLive? = null,
    val livesList: List<com.teamshryne.wediyo.data.model.UiVideo> = emptyList(),
    val liveChips: List<UiChannelVideoChip> = emptyList(),
    val selectedLiveChip: String? = null,
    val livesContinuation: String = "",
    val isLiveLoading: Boolean = false,
    val podcasts: UiChannelPodcasts? = null,
    val podcastsList: List<com.teamshryne.wediyo.data.model.UiChannelPodcast> = emptyList(),
    val podcastsContinuation: String = "",
    val isPodcastsLoading: Boolean = false,
    val selectedTab: String = "Home",
    val pendingShelfChip: String? = null
)

class ChannelViewModel : ViewModel() {
    private val repo = ChannelRepository()
    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state

    fun load(browseId: String) {
        if (browseId.isBlank()) return
        _state.value = _state.value.copy(browseId = browseId, isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val h = repo.fetchHome(browseId)
                _state.value = _state.value.copy(home = h, isLoading = false, selectedTab = h.tabs.firstOrNull { it.selected }?.title ?: "Home")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load channel")
            }
        }
    }

    fun loadVideos(browseId: String = _state.value.browseId, continuation: String = "") {
        if (browseId.isBlank() && continuation.isBlank()) return
        // if initial load without continuation, show loading
        if (continuation.isBlank() && _state.value.videosList.isEmpty()) {
            _state.value = _state.value.copy(isVideosLoading = true, error = null)
        } else if (continuation.isNotBlank() && _state.value.isVideosLoading) return
        else if (continuation.isNotBlank()) _state.value = _state.value.copy(isVideosLoading = true)
        viewModelScope.launch {
            try {
                val res = repo.fetchVideos(browseId, continuation)
                if (continuation.isBlank()) {
                    // initial load -> check pending shelf navigation
                    val pending = _state.value.pendingShelfChip
                    if (pending != null && res.chips.any { it.title.equals(pending, true) }) {
                        // need to immediately switch to pending chip (e.g. Popular)
                        val chip = res.chips.first { it.title.equals(pending, true) }
                        _state.value = _state.value.copy(
                            videos = res,
                            videosList = res.videos,
                            videoChips = res.chips,
                            selectedChip = res.chips.firstOrNull { it.selected }?.title,
                            videosContinuation = res.continuation,
                            isVideosLoading = false,
                            selectedTab = "Videos",
                            pendingShelfChip = null
                        )
                        // now trigger chip selection (second network call)
                        selectChip(chip)
                        return@launch
                    }
                    _state.value = _state.value.copy(
                        videos = res,
                        videosList = res.videos,
                        videoChips = res.chips,
                        selectedChip = res.chips.firstOrNull { it.selected }?.title,
                        videosContinuation = res.continuation,
                        isVideosLoading = false,
                        selectedTab = "Videos",
                        pendingShelfChip = null
                    )
                } else {
                    // check if this was a chip reload (response contains chips with new selected)
                    val isChipReload = res.chips.isNotEmpty() && res.chips.any { it.selected } && res.videos.isNotEmpty() && _state.value.videoChips.isNotEmpty()
                    if (isChipReload && continuation in _state.value.videoChips.map { it.token }) {
                        // chip filter change -> replace
                        _state.value = _state.value.copy(
                            videos = res,
                            videosList = res.videos,
                            videoChips = res.chips,
                            selectedChip = res.chips.firstOrNull { it.selected }?.title,
                            videosContinuation = res.continuation,
                            isVideosLoading = false
                        )
                    } else {
                        // pagination -> append
                        val merged = _state.value.videosList + res.videos
                        // keep chips if new response has them (append usually has no chips)
                        val chips = if (res.chips.isNotEmpty()) res.chips else _state.value.videoChips
                        val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.videosContinuation
                        _state.value = _state.value.copy(
                            videos = res,
                            videosList = merged,
                            videoChips = chips,
                            selectedChip = chips.firstOrNull { it.selected }?.title ?: _state.value.selectedChip,
                            videosContinuation = cont,
                            isVideosLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isVideosLoading = false, error = e.message ?: "Failed to load videos")
            }
        }
    }

    fun loadShorts(browseId: String = _state.value.browseId, continuation: String = "") {
        if (browseId.isBlank() && continuation.isBlank()) return
        if (continuation.isBlank() && _state.value.shortsList.isEmpty()) {
            _state.value = _state.value.copy(isShortsLoading = true, error = null)
        } else if (continuation.isNotBlank() && _state.value.isShortsLoading) return
        else if (continuation.isNotBlank()) _state.value = _state.value.copy(isShortsLoading = true)
        viewModelScope.launch {
            try {
                val res = repo.fetchShorts(browseId, continuation)
                if (continuation.isBlank()) {
                    _state.value = _state.value.copy(
                        shorts = res,
                        shortsList = res.shorts,
                        shortsChips = res.chips,
                        selectedShortsChip = res.chips.firstOrNull { it.selected }?.title,
                        shortsContinuation = res.continuation,
                        isShortsLoading = false,
                        selectedTab = "Shorts"
                    )
                } else {
                    // check if this was a chip reload (same as videos)
                    val isChipReload = res.chips.isNotEmpty() && res.chips.any { it.selected } && res.shorts.isNotEmpty() && _state.value.shortsChips.isNotEmpty()
                    if (isChipReload && continuation in _state.value.shortsChips.map { it.token }) {
                        _state.value = _state.value.copy(
                            shorts = res,
                            shortsList = res.shorts,
                            shortsChips = res.chips,
                            selectedShortsChip = res.chips.firstOrNull { it.selected }?.title,
                            shortsContinuation = res.continuation,
                            isShortsLoading = false
                        )
                    } else {
                        val merged = _state.value.shortsList + res.shorts
                        val chips = if (res.chips.isNotEmpty()) res.chips else _state.value.shortsChips
                        val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.shortsContinuation
                        _state.value = _state.value.copy(
                            shorts = res,
                            shortsList = merged,
                            shortsChips = chips,
                            selectedShortsChip = chips.firstOrNull { it.selected }?.title ?: _state.value.selectedShortsChip,
                            shortsContinuation = cont,
                            isShortsLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isShortsLoading = false, error = e.message ?: "Failed to load shorts")
            }
        }
    }

    fun selectShortsChip(chip: UiChannelVideoChip) {
        if (chip.token.isBlank()) return
        _state.value = _state.value.copy(isShortsLoading = true, selectedShortsChip = chip.title, shortsContinuation = "")
        viewModelScope.launch {
            try {
                val res = repo.fetchShorts(_state.value.browseId, chip.token)
                _state.value = _state.value.copy(
                    shorts = res,
                    shortsList = res.shorts,
                    shortsChips = res.chips.ifEmpty { _state.value.shortsChips },
                    selectedShortsChip = res.chips.firstOrNull { it.selected }?.title ?: chip.title,
                    shortsContinuation = res.continuation,
                    isShortsLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isShortsLoading = false, error = e.message ?: "Chip failed")
            }
        }
    }

    fun loadLive(browseId: String = _state.value.browseId, continuation: String = "") {
        if (browseId.isBlank() && continuation.isBlank()) return
        if (continuation.isBlank() && _state.value.livesList.isEmpty()) {
            _state.value = _state.value.copy(isLiveLoading = true, error = null)
        } else if (continuation.isNotBlank() && _state.value.isLiveLoading) return
        else if (continuation.isNotBlank()) _state.value = _state.value.copy(isLiveLoading = true)
        viewModelScope.launch {
            try {
                val res = repo.fetchLive(browseId, continuation)
                if (continuation.isBlank()) {
                    _state.value = _state.value.copy(
                        live = res,
                        livesList = res.lives,
                        liveChips = res.chips,
                        selectedLiveChip = res.chips.firstOrNull { it.selected }?.title,
                        livesContinuation = res.continuation,
                        isLiveLoading = false,
                        selectedTab = "Live"
                    )
                } else {
                    val isChipReload = res.chips.isNotEmpty() && res.chips.any { it.selected } && res.lives.isNotEmpty() && _state.value.liveChips.isNotEmpty()
                    if (isChipReload && continuation in _state.value.liveChips.map { it.token }) {
                        _state.value = _state.value.copy(
                            live = res,
                            livesList = res.lives,
                            liveChips = res.chips,
                            selectedLiveChip = res.chips.firstOrNull { it.selected }?.title,
                            livesContinuation = res.continuation,
                            isLiveLoading = false
                        )
                    } else {
                        val merged = _state.value.livesList + res.lives
                        val chips = if (res.chips.isNotEmpty()) res.chips else _state.value.liveChips
                        val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.livesContinuation
                        _state.value = _state.value.copy(
                            live = res,
                            livesList = merged,
                            liveChips = chips,
                            selectedLiveChip = chips.firstOrNull { it.selected }?.title ?: _state.value.selectedLiveChip,
                            livesContinuation = cont,
                            isLiveLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLiveLoading = false, error = e.message ?: "Failed to load live")
            }
        }
    }

    fun selectLiveChip(chip: UiChannelVideoChip) {
        if (chip.token.isBlank()) return
        _state.value = _state.value.copy(isLiveLoading = true, selectedLiveChip = chip.title, livesContinuation = "")
        viewModelScope.launch {
            try {
                val res = repo.fetchLive(_state.value.browseId, chip.token)
                _state.value = _state.value.copy(
                    live = res,
                    livesList = res.lives,
                    liveChips = res.chips.ifEmpty { _state.value.liveChips },
                    selectedLiveChip = res.chips.firstOrNull { it.selected }?.title ?: chip.title,
                    livesContinuation = res.continuation,
                    isLiveLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLiveLoading = false, error = e.message ?: "Chip failed")
            }
        }
    }

    fun loadPodcasts(browseId: String = _state.value.browseId, continuation: String = "") {
        if (browseId.isBlank() && continuation.isBlank()) return
        if (continuation.isBlank() && _state.value.podcastsList.isEmpty()) {
            _state.value = _state.value.copy(isPodcastsLoading = true, error = null)
        } else if (continuation.isNotBlank() && _state.value.isPodcastsLoading) return
        else if (continuation.isNotBlank()) _state.value = _state.value.copy(isPodcastsLoading = true)
        viewModelScope.launch {
            try {
                val res = repo.fetchPodcasts(browseId, continuation)
                if (continuation.isBlank()) {
                    _state.value = _state.value.copy(
                        podcasts = res,
                        podcastsList = res.podcasts,
                        podcastsContinuation = res.continuation,
                        isPodcastsLoading = false,
                        selectedTab = "Podcasts"
                    )
                } else {
                    val merged = _state.value.podcastsList + res.podcasts
                    val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.podcastsContinuation
                    _state.value = _state.value.copy(
                        podcasts = res,
                        podcastsList = merged,
                        podcastsContinuation = cont,
                        isPodcastsLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPodcastsLoading = false, error = e.message ?: "Failed to load podcasts")
            }
        }
    }

    fun selectVideosTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Videos", pendingShelfChip = null)
        if (_state.value.videosList.isEmpty() && !_state.value.isVideosLoading) {
            loadVideos(browseId, "")
        }
    }

    fun selectShortsTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Shorts", pendingShelfChip = null)
        if (_state.value.shortsList.isEmpty() && !_state.value.isShortsLoading) {
            loadShorts(browseId, "")
        }
    }

    fun selectLiveTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Live", pendingShelfChip = null)
        if (_state.value.livesList.isEmpty() && !_state.value.isLiveLoading) {
            loadLive(browseId, "")
        }
    }

    fun selectPodcastsTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Podcasts", pendingShelfChip = null)
        if (_state.value.podcastsList.isEmpty() && !_state.value.isPodcastsLoading) {
            loadPodcasts(browseId, "")
        }
    }

    fun selectHomeTab() {
        _state.value = _state.value.copy(selectedTab = "Home", pendingShelfChip = null)
    }

    fun openShelf(shelfTitle: String) {
        val targetChip = if (shelfTitle.contains("Popular", ignoreCase = true)) "Popular" else "Latest"
        // if already on Videos and chips loaded, directly select
        if (_state.value.selectedTab == "Videos" && _state.value.videoChips.isNotEmpty()) {
            val chip = _state.value.videoChips.find { it.title.equals(targetChip, true) }
            if (chip != null) {
                selectChip(chip)
                return
            }
        }
        // otherwise navigate to Videos and defer chip selection until videos loaded
        _state.value = _state.value.copy(selectedTab = "Videos", pendingShelfChip = targetChip)
        if (_state.value.videosList.isEmpty() && !_state.value.isVideosLoading) {
            loadVideos(_state.value.browseId, "")
        } else if (_state.value.videoChips.isNotEmpty()) {
            // videos already loaded but chips available -> select now
            val chip = _state.value.videoChips.find { it.title.equals(targetChip, true) }
            if (chip != null) {
                selectChip(chip)
                _state.value = _state.value.copy(pendingShelfChip = null)
            }
        }
    }

    fun selectChip(chip: UiChannelVideoChip) {
        if (chip.token.isBlank()) return
        // chip token is continuation for reload
        _state.value = _state.value.copy(isVideosLoading = true, selectedChip = chip.title, videosContinuation = "", pendingShelfChip = null)
        viewModelScope.launch {
            try {
                val res = repo.fetchVideos(_state.value.browseId, chip.token)
                _state.value = _state.value.copy(
                    videos = res,
                    videosList = res.videos,
                    videoChips = res.chips.ifEmpty { _state.value.videoChips },
                    selectedChip = res.chips.firstOrNull { it.selected }?.title ?: chip.title,
                    videosContinuation = res.continuation,
                    isVideosLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isVideosLoading = false, error = e.message ?: "Chip failed")
            }
        }
    }

    fun loadMoreVideos() {
        val cont = _state.value.videosContinuation
        if (cont.isBlank() || _state.value.isVideosLoading) return
        loadVideos(_state.value.browseId, cont)
    }

    fun loadMoreShorts() {
        val cont = _state.value.shortsContinuation
        if (cont.isBlank() || _state.value.isShortsLoading) return
        loadShorts(_state.value.browseId, cont)
    }

    fun loadMoreLive() {
        val cont = _state.value.livesContinuation
        if (cont.isBlank() || _state.value.isLiveLoading) return
        loadLive(_state.value.browseId, cont)
    }

    fun loadMorePodcasts() {
        val cont = _state.value.podcastsContinuation
        if (cont.isBlank() || _state.value.isPodcastsLoading) return
        loadPodcasts(_state.value.browseId, cont)
    }

    fun retry() {
        val id = _state.value.browseId
        if (id.isNotBlank()) load(id)
    }

    fun retryVideos() {
        val id = _state.value.browseId
        if (id.isNotBlank()) loadVideos(id, "")
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
