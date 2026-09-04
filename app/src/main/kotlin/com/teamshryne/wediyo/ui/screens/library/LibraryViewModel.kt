package com.teamshryne.wediyo.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.local.HistoryWithVideo
import com.teamshryne.wediyo.data.local.LibraryRepository
import com.teamshryne.wediyo.data.local.LocalPlaylistEntity
import com.teamshryne.wediyo.data.local.SavedVideoRow
import com.teamshryne.wediyo.data.local.SubscriptionRow
import com.teamshryne.wediyo.data.model.UiVideo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    init { LibraryRepository.init(app) }

    val history: StateFlow<List<HistoryWithVideo>> =
        LibraryRepository.history(100).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchLater: StateFlow<List<SavedVideoRow>> =
        LibraryRepository.watchLater().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val liked: StateFlow<List<SavedVideoRow>> =
        LibraryRepository.likedVideos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<LocalPlaylistEntity>> =
        LibraryRepository.playlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistCounts: StateFlow<Map<String, Int>> =
        LibraryRepository.playlistItemCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val playlistCovers: StateFlow<Map<String, com.teamshryne.wediyo.data.local.PlaylistCover>> =
        LibraryRepository.playlistCovers().map { list -> list.associateBy { it.playlistId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val subscriptions: StateFlow<List<SubscriptionRow>> =
        LibraryRepository.subscriptions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val stats = LibraryRepository.historyStats()
    val distinctVideos: StateFlow<Int> = stats.distinctVideos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val totalWatchMs: StateFlow<Long> = stats.totalWatchMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun playlistItems(pid: String): StateFlow<List<SavedVideoRow>> =
        LibraryRepository.playlistItems(pid).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistory() = viewModelScope.launch { LibraryRepository.clearHistory() }
    fun removeHistoryVideo(videoId: String) = viewModelScope.launch { LibraryRepository.removeHistoryVideo(videoId) }
    fun removeWatchLater(videoId: String) = viewModelScope.launch { LibraryRepository.removeWatchLater(videoId) }
    fun clearWatchLater() = viewModelScope.launch { LibraryRepository.clearWatchLater() }
    fun unlike(videoId: String) = viewModelScope.launch { LibraryRepository.setLiked(videoId, false) }
    fun clearLiked() = viewModelScope.launch { LibraryRepository.clearLiked() }

    fun createPlaylist(name: String) = viewModelScope.launch { LibraryRepository.createPlaylist(name) }
    fun renamePlaylist(id: String, name: String) = viewModelScope.launch { LibraryRepository.renamePlaylist(id, name) }
    fun deletePlaylist(id: String) = viewModelScope.launch { LibraryRepository.deletePlaylist(id) }
    fun removeFromPlaylist(pid: String, vid: String) = viewModelScope.launch { LibraryRepository.removeFromPlaylist(pid, vid) }

    fun toUiVideo(row: SavedVideoRow): UiVideo = UiVideo(
        id = row.videoId,
        title = row.title ?: "",
        author = row.author ?: "",
        channelId = row.channelId ?: "",
        thumbnailUrl = row.thumbnailUrl ?: "",
        thumbnailsJson = row.thumbnailsJson ?: "[]",
        avatarUrl = "",
        avatarsJson = "[]",
        viewCountText = row.viewCountText ?: "",
        publishedText = row.publishedText ?: "",
        durationText = row.durationText ?: "",
        isLive = false,
        badges = emptyList(),
        description = ""
    )

    fun historyToUiVideo(h: HistoryWithVideo): UiVideo = UiVideo(
        id = h.videoId,
        title = h.title ?: "",
        author = "",
        channelId = h.channelId ?: "",
        thumbnailUrl = h.thumbnailUrl ?: "",
        thumbnailsJson = h.thumbnailsJson ?: "[]",
        avatarUrl = "",
        avatarsJson = "[]",
        viewCountText = h.viewCountText ?: "",
        publishedText = h.publishedText ?: "",
        durationText = h.durationText ?: "",
        isLive = h.isLive ?: false,
        badges = emptyList(),
        description = ""
    )
}
