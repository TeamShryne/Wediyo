package com.teamshryne.wediyo.ui.screens.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.model.*
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
    val videosList: List<UiVideo> = emptyList(),
    val videoChips: List<UiChannelVideoChip> = emptyList(),
    val selectedChip: String? = null,
    val videosContinuation: String = "",
    val isVideosLoading: Boolean = false,
    val shorts: UiChannelShorts? = null,
    val shortsList: List<UiShort> = emptyList(),
    val shortsChips: List<UiChannelVideoChip> = emptyList(),
    val selectedShortsChip: String? = null,
    val shortsContinuation: String = "",
    val isShortsLoading: Boolean = false,
    val live: UiChannelLive? = null,
    val livesList: List<UiVideo> = emptyList(),
    val liveChips: List<UiChannelVideoChip> = emptyList(),
    val selectedLiveChip: String? = null,
    val livesContinuation: String = "",
    val isLiveLoading: Boolean = false,
    val podcasts: UiChannelPodcasts? = null,
    val podcastsList: List<UiChannelPodcast> = emptyList(),
    val podcastsContinuation: String = "",
    val isPodcastsLoading: Boolean = false,
    val playlists: UiChannelPlaylists? = null,
    val playlistsList: List<UiChannelPlaylist> = emptyList(),
    val playlistsContinuation: String = "",
    val isPlaylistsLoading: Boolean = false,
    val posts: UiChannelPosts? = null,
    val postsList: List<UiChannelPost> = emptyList(),
    val postsContinuation: String = "",
    val isPostsLoading: Boolean = false,
    val store: UiChannelStore? = null,
    val storeList: List<UiChannelStoreProduct> = emptyList(),
    val storeContinuation: String = "",
    val isStoreLoading: Boolean = false,
    val courses: UiChannelCourses? = null,
    val coursesList: List<UiChannelCourse> = emptyList(),
    val coursesContinuation: String = "",
    val isCoursesLoading: Boolean = false,
    val shows: UiChannelShows? = null,
    val showsList: List<UiChannelShow> = emptyList(),
    val showsContinuation: String = "",
    val isShowsLoading: Boolean = false,
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
        if (continuation.isBlank() && _state.value.videosList.isEmpty()) {
            _state.value = _state.value.copy(isVideosLoading = true, error = null)
        } else if (continuation.isNotBlank() && _state.value.isVideosLoading) return
        else if (continuation.isNotBlank()) _state.value = _state.value.copy(isVideosLoading = true)
        viewModelScope.launch {
            try {
                val res = repo.fetchVideos(browseId, continuation)
                if (continuation.isBlank()) {
                    val pending = _state.value.pendingShelfChip
                    if (pending != null && res.chips.any { it.title.equals(pending, true) }) {
                        val chip = res.chips.first { it.title.equals(pending, true) }
                        _state.value = _state.value.copy(
                            videos = res, videosList = res.videos, videoChips = res.chips,
                            selectedChip = res.chips.firstOrNull { it.selected }?.title,
                            videosContinuation = res.continuation, isVideosLoading = false,
                            selectedTab = "Videos", pendingShelfChip = null
                        )
                        selectChip(chip)
                        return@launch
                    }
                    _state.value = _state.value.copy(
                        videos = res, videosList = res.videos, videoChips = res.chips,
                        selectedChip = res.chips.firstOrNull { it.selected }?.title,
                        videosContinuation = res.continuation, isVideosLoading = false,
                        selectedTab = "Videos", pendingShelfChip = null
                    )
                } else {
                    val isChipReload = res.chips.isNotEmpty() && res.chips.any { it.selected } && res.videos.isNotEmpty() && _state.value.videoChips.isNotEmpty()
                    if (isChipReload && continuation in _state.value.videoChips.map { it.token }) {
                        _state.value = _state.value.copy(
                            videos = res, videosList = res.videos, videoChips = res.chips,
                            selectedChip = res.chips.firstOrNull { it.selected }?.title,
                            videosContinuation = res.continuation, isVideosLoading = false
                        )
                    } else {
                        val merged = _state.value.videosList + res.videos
                        val chips = if (res.chips.isNotEmpty()) res.chips else _state.value.videoChips
                        val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.videosContinuation
                        _state.value = _state.value.copy(
                            videos = res, videosList = merged, videoChips = chips,
                            selectedChip = chips.firstOrNull { it.selected }?.title ?: _state.value.selectedChip,
                            videosContinuation = cont, isVideosLoading = false
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
                        shorts = res, shortsList = res.shorts, shortsChips = res.chips,
                        selectedShortsChip = res.chips.firstOrNull { it.selected }?.title,
                        shortsContinuation = res.continuation, isShortsLoading = false, selectedTab = "Shorts"
                    )
                } else {
                    val isChipReload = res.chips.isNotEmpty() && res.chips.any { it.selected } && res.shorts.isNotEmpty() && _state.value.shortsChips.isNotEmpty()
                    if (isChipReload && continuation in _state.value.shortsChips.map { it.token }) {
                        _state.value = _state.value.copy(
                            shorts = res, shortsList = res.shorts, shortsChips = res.chips,
                            selectedShortsChip = res.chips.firstOrNull { it.selected }?.title,
                            shortsContinuation = res.continuation, isShortsLoading = false
                        )
                    } else {
                        val merged = _state.value.shortsList + res.shorts
                        val chips = if (res.chips.isNotEmpty()) res.chips else _state.value.shortsChips
                        val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.shortsContinuation
                        _state.value = _state.value.copy(
                            shorts = res, shortsList = merged, shortsChips = chips,
                            selectedShortsChip = chips.firstOrNull { it.selected }?.title ?: _state.value.selectedShortsChip,
                            shortsContinuation = cont, isShortsLoading = false
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
                    shorts = res, shortsList = res.shorts, shortsChips = res.chips.ifEmpty { _state.value.shortsChips },
                    selectedShortsChip = res.chips.firstOrNull { it.selected }?.title ?: chip.title,
                    shortsContinuation = res.continuation, isShortsLoading = false
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
                        live = res, livesList = res.lives, liveChips = res.chips,
                        selectedLiveChip = res.chips.firstOrNull { it.selected }?.title,
                        livesContinuation = res.continuation, isLiveLoading = false, selectedTab = "Live"
                    )
                } else {
                    val isChipReload = res.chips.isNotEmpty() && res.chips.any { it.selected } && res.lives.isNotEmpty() && _state.value.liveChips.isNotEmpty()
                    if (isChipReload && continuation in _state.value.liveChips.map { it.token }) {
                        _state.value = _state.value.copy(
                            live = res, livesList = res.lives, liveChips = res.chips,
                            selectedLiveChip = res.chips.firstOrNull { it.selected }?.title,
                            livesContinuation = res.continuation, isLiveLoading = false
                        )
                    } else {
                        val merged = _state.value.livesList + res.lives
                        val chips = if (res.chips.isNotEmpty()) res.chips else _state.value.liveChips
                        val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.livesContinuation
                        _state.value = _state.value.copy(
                            live = res, livesList = merged, liveChips = chips,
                            selectedLiveChip = chips.firstOrNull { it.selected }?.title ?: _state.value.selectedLiveChip,
                            livesContinuation = cont, isLiveLoading = false
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
                    live = res, livesList = res.lives, liveChips = res.chips.ifEmpty { _state.value.liveChips },
                    selectedLiveChip = res.chips.firstOrNull { it.selected }?.title ?: chip.title,
                    livesContinuation = res.continuation, isLiveLoading = false
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
                    _state.value = _state.value.copy(podcasts = res, podcastsList = res.podcasts, podcastsContinuation = res.continuation, isPodcastsLoading = false, selectedTab = "Podcasts")
                } else {
                    val merged = _state.value.podcastsList + res.podcasts
                    val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.podcastsContinuation
                    _state.value = _state.value.copy(podcasts = res, podcastsList = merged, podcastsContinuation = cont, isPodcastsLoading = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPodcastsLoading = false, error = e.message ?: "Failed to load podcasts")
            }
        }
    }

    fun loadPlaylists(browseId: String = _state.value.browseId, continuation: String = "") {
        if (browseId.isBlank() && continuation.isBlank()) return
        if (continuation.isBlank() && _state.value.playlistsList.isEmpty()) {
            _state.value = _state.value.copy(isPlaylistsLoading = true, error = null)
        } else if (continuation.isNotBlank() && _state.value.isPlaylistsLoading) return
        else if (continuation.isNotBlank()) _state.value = _state.value.copy(isPlaylistsLoading = true)
        viewModelScope.launch {
            try {
                val res = repo.fetchPlaylists(browseId, continuation)
                if (continuation.isBlank()) {
                    _state.value = _state.value.copy(playlists = res, playlistsList = res.playlists, playlistsContinuation = res.continuation, isPlaylistsLoading = false, selectedTab = "Playlists")
                } else {
                    val merged = _state.value.playlistsList + res.playlists
                    val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.playlistsContinuation
                    _state.value = _state.value.copy(playlists = res, playlistsList = merged, playlistsContinuation = cont, isPlaylistsLoading = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPlaylistsLoading = false, error = e.message ?: "Failed to load playlists")
            }
        }
    }

    fun loadPosts(browseId: String = _state.value.browseId, continuation: String = "") {
        if (browseId.isBlank() && continuation.isBlank()) return
        if (continuation.isBlank() && _state.value.postsList.isEmpty()) {
            _state.value = _state.value.copy(isPostsLoading = true, error = null)
        } else if (continuation.isNotBlank() && _state.value.isPostsLoading) return
        else if (continuation.isNotBlank()) _state.value = _state.value.copy(isPostsLoading = true)
        viewModelScope.launch {
            try {
                val res = repo.fetchPosts(browseId, continuation)
                if (continuation.isBlank()) {
                    _state.value = _state.value.copy(posts = res, postsList = res.posts, postsContinuation = res.continuation, isPostsLoading = false, selectedTab = "Posts")
                } else {
                    val merged = _state.value.postsList + res.posts
                    val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.postsContinuation
                    _state.value = _state.value.copy(posts = res, postsList = merged, postsContinuation = cont, isPostsLoading = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPostsLoading = false, error = e.message ?: "Failed to load posts")
            }
        }
    }

    fun loadStore(browseId: String = _state.value.browseId, continuation: String = "") {
        if (browseId.isBlank() && continuation.isBlank()) return
        if (continuation.isBlank() && _state.value.storeList.isEmpty()) {
            _state.value = _state.value.copy(isStoreLoading = true, error = null)
        } else if (continuation.isNotBlank() && _state.value.isStoreLoading) return
        else if (continuation.isNotBlank()) _state.value = _state.value.copy(isStoreLoading = true)
        viewModelScope.launch {
            try {
                val res = repo.fetchStore(browseId, continuation)
                if (continuation.isBlank()) {
                    _state.value = _state.value.copy(store = res, storeList = res.products, storeContinuation = res.continuation, isStoreLoading = false, selectedTab = "Store")
                } else {
                    val merged = _state.value.storeList + res.products
                    val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.storeContinuation
                    _state.value = _state.value.copy(store = res, storeList = merged, storeContinuation = cont, isStoreLoading = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isStoreLoading = false, error = e.message ?: "Failed to load store")
            }
        }
    }

    fun loadCourses(browseId: String = _state.value.browseId, continuation: String = "") {
        if (browseId.isBlank() && continuation.isBlank()) return
        if (continuation.isBlank() && _state.value.coursesList.isEmpty()) {
            _state.value = _state.value.copy(isCoursesLoading = true, error = null)
        } else if (continuation.isNotBlank() && _state.value.isCoursesLoading) return
        else if (continuation.isNotBlank()) _state.value = _state.value.copy(isCoursesLoading = true)
        viewModelScope.launch {
            try {
                val res = repo.fetchCourses(browseId, continuation)
                if (continuation.isBlank()) {
                    _state.value = _state.value.copy(courses = res, coursesList = res.courses, coursesContinuation = res.continuation, isCoursesLoading = false, selectedTab = "Courses")
                } else {
                    val merged = _state.value.coursesList + res.courses
                    val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.coursesContinuation
                    _state.value = _state.value.copy(courses = res, coursesList = merged, coursesContinuation = cont, isCoursesLoading = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isCoursesLoading = false, error = e.message ?: "Failed to load courses")
            }
        }
    }

    fun loadShows(browseId: String = _state.value.browseId, continuation: String = "") {
        if (browseId.isBlank() && continuation.isBlank()) return
        if (continuation.isBlank() && _state.value.showsList.isEmpty()) {
            _state.value = _state.value.copy(isShowsLoading = true, error = null)
        } else if (continuation.isNotBlank() && _state.value.isShowsLoading) return
        else if (continuation.isNotBlank()) _state.value = _state.value.copy(isShowsLoading = true)
        viewModelScope.launch {
            try {
                val res = repo.fetchShows(browseId, continuation)
                if (continuation.isBlank()) {
                    _state.value = _state.value.copy(shows = res, showsList = res.shows, showsContinuation = res.continuation, isShowsLoading = false, selectedTab = "Shows")
                } else {
                    val merged = _state.value.showsList + res.shows
                    val cont = if (res.continuation.isNotBlank()) res.continuation else _state.value.showsContinuation
                    _state.value = _state.value.copy(shows = res, showsList = merged, showsContinuation = cont, isShowsLoading = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isShowsLoading = false, error = e.message ?: "Failed to load shows")
            }
        }
    }

    fun selectVideosTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Videos", pendingShelfChip = null)
        if (_state.value.videosList.isEmpty() && !_state.value.isVideosLoading) loadVideos(browseId, "")
    }
    fun selectShortsTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Shorts", pendingShelfChip = null)
        if (_state.value.shortsList.isEmpty() && !_state.value.isShortsLoading) loadShorts(browseId, "")
    }
    fun selectLiveTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Live", pendingShelfChip = null)
        if (_state.value.livesList.isEmpty() && !_state.value.isLiveLoading) loadLive(browseId, "")
    }
    fun selectPodcastsTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Podcasts", pendingShelfChip = null)
        if (_state.value.podcastsList.isEmpty() && !_state.value.isPodcastsLoading) loadPodcasts(browseId, "")
    }
    fun selectPlaylistsTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Playlists", pendingShelfChip = null)
        if (_state.value.playlistsList.isEmpty() && !_state.value.isPlaylistsLoading) loadPlaylists(browseId, "")
    }
    fun selectPostsTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Posts", pendingShelfChip = null)
        if (_state.value.postsList.isEmpty() && !_state.value.isPostsLoading) loadPosts(browseId, "")
    }
    fun selectStoreTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Store", pendingShelfChip = null)
        if (_state.value.storeList.isEmpty() && !_state.value.isStoreLoading) loadStore(browseId, "")
    }
    fun selectCoursesTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Courses", pendingShelfChip = null)
        if (_state.value.coursesList.isEmpty() && !_state.value.isCoursesLoading) loadCourses(browseId, "")
    }
    fun selectShowsTab(browseId: String = _state.value.browseId) {
        _state.value = _state.value.copy(selectedTab = "Shows", pendingShelfChip = null)
        if (_state.value.showsList.isEmpty() && !_state.value.isShowsLoading) loadShows(browseId, "")
    }
    fun selectHomeTab() {
        _state.value = _state.value.copy(selectedTab = "Home", pendingShelfChip = null)
    }
    fun selectGenericTab(title: String, browseId: String = _state.value.browseId) {
        when (title) {
            "Videos" -> selectVideosTab(browseId)
            "Shorts" -> selectShortsTab(browseId)
            "Live" -> selectLiveTab(browseId)
            "Streams" -> selectLiveTab(browseId)
            "Podcasts" -> selectPodcastsTab(browseId)
            "Playlists" -> selectPlaylistsTab(browseId)
            "Posts" -> selectPostsTab(browseId)
            "Community" -> selectPostsTab(browseId)
            "Store" -> selectStoreTab(browseId)
            "Courses" -> selectCoursesTab(browseId)
            "Shows" -> selectShowsTab(browseId)
            "Home" -> selectHomeTab()
            else -> selectHomeTab()
        }
    }

    fun openShelf(shelfTitle: String) {
        val targetChip = if (shelfTitle.contains("Popular", ignoreCase = true)) "Popular" else "Latest"
        if (_state.value.selectedTab == "Videos" && _state.value.videoChips.isNotEmpty()) {
            val chip = _state.value.videoChips.find { it.title.equals(targetChip, true) }
            if (chip != null) { selectChip(chip); return }
        }
        _state.value = _state.value.copy(selectedTab = "Videos", pendingShelfChip = targetChip)
        if (_state.value.videosList.isEmpty() && !_state.value.isVideosLoading) {
            loadVideos(_state.value.browseId, "")
        } else if (_state.value.videoChips.isNotEmpty()) {
            val chip = _state.value.videoChips.find { it.title.equals(targetChip, true) }
            if (chip != null) { selectChip(chip); _state.value = _state.value.copy(pendingShelfChip = null) }
        }
    }

    fun selectChip(chip: UiChannelVideoChip) {
        if (chip.token.isBlank()) return
        _state.value = _state.value.copy(isVideosLoading = true, selectedChip = chip.title, videosContinuation = "", pendingShelfChip = null)
        viewModelScope.launch {
            try {
                val res = repo.fetchVideos(_state.value.browseId, chip.token)
                _state.value = _state.value.copy(
                    videos = res, videosList = res.videos,
                    videoChips = res.chips.ifEmpty { _state.value.videoChips },
                    selectedChip = res.chips.firstOrNull { it.selected }?.title ?: chip.title,
                    videosContinuation = res.continuation, isVideosLoading = false
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
    fun loadMorePlaylists() {
        val cont = _state.value.playlistsContinuation
        if (cont.isBlank() || _state.value.isPlaylistsLoading) return
        loadPlaylists(_state.value.browseId, cont)
    }
    fun loadMorePosts() {
        val cont = _state.value.postsContinuation
        if (cont.isBlank() || _state.value.isPostsLoading) return
        loadPosts(_state.value.browseId, cont)
    }
    fun loadMoreStore() {
        val cont = _state.value.storeContinuation
        if (cont.isBlank() || _state.value.isStoreLoading) return
        loadStore(_state.value.browseId, cont)
    }
    fun loadMoreCourses() {
        val cont = _state.value.coursesContinuation
        if (cont.isBlank() || _state.value.isCoursesLoading) return
        loadCourses(_state.value.browseId, cont)
    }
    fun loadMoreShows() {
        val cont = _state.value.showsContinuation
        if (cont.isBlank() || _state.value.isShowsLoading) return
        loadShows(_state.value.browseId, cont)
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
