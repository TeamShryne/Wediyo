package com.teamshryne.wediyo.ui.screens.subscriptions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.local.LibraryRepository
import com.teamshryne.wediyo.data.local.SubscriptionRow
import com.teamshryne.wediyo.data.model.UiShort
import com.teamshryne.wediyo.data.model.UiVideo
import com.teamshryne.wediyo.data.repository.ChannelRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * YouTube-style Subscriptions feed.
 *
 * YouTube Android (stable 2025-26): channel avatar bar on top (tap = filter feed
 * to that channel), filter chips below (All / Today / Videos / Shorts / Live /
 * Posts / ...), then a reverse-chron feed of uploads from subscribed channels
 * with Shorts in a separate shelf and LIVE badges on streams.
 * (The May-2026 experiment only moves the tab to a top swipeable bar — same
 * content — so we keep the bottom-nav entry.)
 *
 * Local adaptation: subs are device-local; the feed is built by fetching the
 * first page of Videos/Shorts/Live per channel concurrently. Each channel list
 * is newest-first, so we interleave round-robin to approximate a mixed
 * reverse-chron feed. Chips + channel bar filter locally — no refetch.
 * Caps keep refresh bounded; per-channel failures are tolerated.
 */
enum class SubFilter { ALL, VIDEOS, SHORTS, LIVE }

data class SubFeedState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val videos: List<UiVideo> = emptyList(),
    val shorts: List<SubShort> = emptyList(),
    val lives: List<UiVideo> = emptyList(),
    val failedChannels: Int = 0,
    val refreshedAt: Long = 0L,
    /** Subs count this feed was built for — drives auto-refresh on sub changes. */
    val channelCount: Int = -1
)

data class SubShort(
    val short: UiShort,
    val channelId: String,
    val channelTitle: String
)

class SubscriptionsViewModel(app: Application) : AndroidViewModel(app) {
    init { LibraryRepository.init(app) }

    private val repo = ChannelRepository()

    val subscriptions: StateFlow<List<SubscriptionRow>> =
        LibraryRepository.subscriptions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filter = MutableStateFlow(SubFilter.ALL)

    /** null = All channels (YouTube's "All" avatar). */
    val selectedChannelId = MutableStateFlow<String?>(null)

    val feed = MutableStateFlow(SubFeedState())

    fun selectFilter(f: SubFilter) { filter.value = f }

    fun selectChannel(channelId: String?) { selectedChannelId.value = channelId }

    fun unsubscribe(channelId: String) = viewModelScope.launch { LibraryRepository.unsubscribe(channelId) }

    fun refresh() {
        if (feed.value.isLoading) return
        viewModelScope.launch {
            val subs = try { subscriptions.first() } catch (_: Exception) { emptyList() }
            if (subs.isEmpty()) {
                feed.update { it.copy(isLoading = false, error = null, videos = emptyList(), shorts = emptyList(), lives = emptyList(), failedChannels = 0) }
                return@launch
            }
            feed.update { it.copy(isLoading = true, error = null) }
            try {
                val videos = fetchAllVideos(subs)
                val shorts = fetchAllShorts(subs)
                val lives = fetchAllLives(subs)
                val failed = (videos.second + shorts.second + lives.second)
                feed.update {
                    it.copy(
                        isLoading = false, error = null,
                        videos = videos.first, shorts = shorts.first, lives = lives.first,
                        failedChannels = failed, refreshedAt = System.currentTimeMillis(),
                        channelCount = subs.size
                    )
                }
            } catch (e: Exception) {
                feed.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load subscriptions") }
            }
        }
    }

    fun retry() = refresh()

    // ── Bounded concurrent fetchers ──────────────────────────────

    private suspend fun fetchAllVideos(subs: List<SubscriptionRow>): Pair<List<UiVideo>, Int> =
        supervisorScope {
            subs.take(MAX_VIDEO_CHANNELS).map { s ->
                async {
                    try {
                        repo.fetchVideos(s.channelId).videos.take(PER_CHANNEL_VIDEOS).map { v ->
                            // Backfill channel identity for cards (Videos-tab items often lack it)
                            if (v.channelId.isBlank() || v.author.isBlank()) v.copy(channelId = s.channelId, author = s.title) else v
                        }
                    } catch (_: Exception) { null }
                }
            }.awaitAll().let { results ->
                val failed = results.count { it == null }
                interleave(results.filterNotNull()) to failed
            }
        }

    private suspend fun fetchAllShorts(subs: List<SubscriptionRow>): Pair<List<SubShort>, Int> =
        supervisorScope {
            subs.take(MAX_SHORTS_CHANNELS).map { s ->
                async {
                    try {
                        repo.fetchShorts(s.channelId).shorts.take(PER_CHANNEL_SHORTS)
                            .map { SubShort(it, s.channelId, s.title) }
                    } catch (_: Exception) { null }
                }
            }.awaitAll().let { results ->
                val failed = results.count { it == null }
                interleave(results.filterNotNull()) to failed
            }
        }

    private suspend fun fetchAllLives(subs: List<SubscriptionRow>): Pair<List<UiVideo>, Int> =
        supervisorScope {
            subs.take(MAX_LIVE_CHANNELS).map { s ->
                async {
                    try {
                        repo.fetchLive(s.channelId).lives.map { v ->
                            if (v.channelId.isBlank() || v.author.isBlank()) v.copy(channelId = s.channelId, author = s.title) else v
                        }
                    } catch (_: Exception) { null }
                }
            }.awaitAll().let { results ->
                val failed = results.count { it == null }
                interleave(results.filterNotNull()) to failed
            }
        }

    /** Round-robin interleave: each channel list is newest-first, so taking
     * turns approximates a mixed recency feed without reliable timestamps. */
    private fun <T> interleave(lists: List<List<T>>): List<T> {
        if (lists.isEmpty()) return emptyList()
        val out = ArrayList<T>(lists.sumOf { it.size })
        var idx = 0
        var added: Boolean
        do {
            added = false
            for (l in lists) {
                if (idx < l.size) { out.add(l[idx]); added = true }
            }
            idx++
        } while (added)
        return out
    }

    // ── Local filtering (chips + channel bar, no refetch) ─────────

    fun filteredVideos(state: SubFeedState, channelId: String?, f: SubFilter): List<UiVideo> {
        val base = when (f) {
            SubFilter.ALL, SubFilter.VIDEOS -> state.videos
            SubFilter.SHORTS -> return emptyList()
            SubFilter.LIVE -> state.lives
        }
        // YouTube Live chip also surfaces live videos inside All
        val merged = if (f == SubFilter.ALL && state.lives.isNotEmpty()) {
            val liveIds = state.lives.map { it.id }.toSet()
            state.lives + base.filter { it.id !in liveIds }
        } else base
        return if (channelId == null) merged else merged.filter { it.channelId == channelId }
    }

    fun filteredShorts(state: SubFeedState, channelId: String?): List<SubShort> {
        val list = if (channelId == null) state.shorts else state.shorts.filter { it.channelId == channelId }
        return list
    }

    companion object {
        private const val MAX_VIDEO_CHANNELS = 15
        private const val PER_CHANNEL_VIDEOS = 4
        private const val MAX_SHORTS_CHANNELS = 6
        private const val PER_CHANNEL_SHORTS = 4
        private const val MAX_LIVE_CHANNELS = 8
    }
}
