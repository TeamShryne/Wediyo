package com.teamshryne.wediyo.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.home.FeedSource
import com.teamshryne.wediyo.data.home.HomeFeedEngine
import com.teamshryne.wediyo.data.local.LibraryRepository
import com.teamshryne.wediyo.data.local.ResumeWithVideo
import com.teamshryne.wediyo.data.local.SubscriptionRow
import com.teamshryne.wediyo.data.model.UiShort
import com.teamshryne.wediyo.data.model.UiVideo
import com.teamshryne.wediyo.data.repository.ChannelRepository
import com.teamshryne.wediyo.data.repository.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

enum class HomeFilter { ALL, SUBS, QUEUE, FRESH }

data class ResumeCard(
    val videoId: String,
    val title: String,
    val author: String,
    val channelId: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val positionMs: Long,
    val durationMs: Long,
    val progress: Float,
    val viewCountText: String = "",
    val publishedText: String = "",
    val durationText: String = ""
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val resume: ResumeCard? = null,
    val resumeDismissed: Boolean = false,
    val videos: List<UiVideo> = emptyList(),
    val shorts: List<UiShort> = emptyList(),
    val error: String? = null,
    val offline: Boolean = false,
    val canLoadMore: Boolean = true,
    val filter: HomeFilter = HomeFilter.ALL,
    val seed: Long = 0L
)

/**
 * YouTube-style home feed, local-first.
 *
 * Sources (no YouTube home/trending endpoint):
 * 1. Resume hero — latest unfinished [ResumeWithVideo] from Room.
 * 2. SUBS — first page of Videos per subscribed channel, interleaved.
 * 3. QUEUE — `next` graph: last-watched video's related + continuation (branching frontier).
 * 4. SEARCH — rotating recent query for freshness (different query each refresh).
 *
 * Reliability: supervisorScope (one source never kills the feed), per-source
 * timeouts, full try/catch, offline fallback to cached history.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    init { LibraryRepository.init(app) }

    private val channelRepo = ChannelRepository()
    private val searchRepo = SearchRepository()

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    // Full unfiltered feed + metadata for local chip filtering + pagination.
    private var fullVideos: List<UiVideo> = emptyList()
    private var sourceOf: Map<String, FeedSource> = emptyMap()
    private var subIds: Set<String> = emptySet()
    private var watchedIds: Set<String> = emptySet()
    private val seenIds = LinkedHashSet<String>()
    private val frontier = ArrayDeque<String>()       // videoIds whose related to branch into
    private val continuations = ArrayDeque<String>()  // related continuations (cheapest next page)
    private var branchesUsed = 0
    private var refreshCount = 0
    // Pagination reservoirs — when one source runs dry the next takes over,
    // so the feed keeps going instead of ending after 1–2 pages.
    private var allSeeds: List<String> = emptyList()
    private var seedCursor = 1 // seeds[0] feeds the initial queue
    private val pendingSubs = ArrayDeque<SubscriptionRow>()
    private var searchQuery: String? = null
    private var searchCont: String? = null
    // Load gate: a Job flag, NOT state flags — initial state starts with
    // isLoading=true (skeletons), so gating on state would no-op the first
    // refresh forever (the stuck-loading bug).
    private var loadJob: Job? = null

    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = it.videos.isEmpty(),
                    isRefreshing = it.videos.isNotEmpty(),
                    error = null, offline = false, canLoadMore = true
                )
            }
            try {
                loadFeed()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false, isRefreshing = false,
                        error = if (it.videos.isEmpty()) (e.message ?: "Couldn't load home") else null
                    )
                }
            }
        }
    }

    fun retry() = refresh()

    fun loadMore() {
        val cur = _state.value
        if (cur.isLoading || cur.isRefreshing || cur.isLoadingMore || !cur.canLoadMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val added = expandOnce()
                if (added.isEmpty()) {
                    _state.update { it.copy(isLoadingMore = false, canLoadMore = false) }
                } else {
                    appendVideos(added, FeedSource.QUEUE)
                    _state.update { it.copy(isLoadingMore = false) }
                }
            } catch (_: Exception) {
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun setFilter(f: HomeFilter) {
        _state.update { it.copy(filter = f, videos = applyFilter(fullVideos, f)) }
    }

    fun dismissResume() {
        val r = _state.value.resume ?: return
        _state.update { it.copy(resume = null, resumeDismissed = true) }
        viewModelScope.launch { LibraryRepository.dismissResume(r.videoId) }
    }

    // ── Core load ────────────────────────────────────────────────

    private suspend fun loadFeed() {
        refreshCount++
        val seed = System.currentTimeMillis() + refreshCount * 7919L

        // 1. Local signals (fast, one-shot).
        val resumeList = LibraryRepository.resumeCandidates(3)
        val seedIds = LibraryRepository.recentSeeds(6)
        val watched = LibraryRepository.watchedSet()
        val subs = LibraryRepository.subscriptionsOnce()
        val topChannels = LibraryRepository.topChannels()
        val queries = LibraryRepository.recentQueries(6)
        subIds = subs.map { it.channelId }.toSet()
        watchedIds = watched
        val topIds = topChannels.map { it.channelId }.toSet()

        // Reset pagination reservoirs for this generation.
        allSeeds = seedIds
        seedCursor = 1
        pendingSubs.clear()
        searchQuery = null
        searchCont = null

        // 2. Network fan-out — each source failure-safe, bounded.
        var subsVideos: List<UiVideo>
        var queueVideos: List<UiVideo>
        var searchVideos: List<UiVideo>
        var shorts: List<UiShort>
        supervisorScope {
            val subsD = async { fetchSubsVideos(firstSubsBatch(subs)) }
            val queueD = async { fetchQueueSeed(seedIds) }
            val searchD = async { fetchSearchFresh(queries, refreshCount) }
            val shortsD = async { fetchShorts(subs.map { it.channelId }) }
            subsVideos = withTimeoutOrNull(20_000) { subsD.await() } ?: emptyList()
            val queueRes = withTimeoutOrNull(20_000) { queueD.await() }
            queueVideos = queueRes?.videos ?: emptyList()
            queueRes?.let {
                continuations.clear()
                if (it.continuation.isNotBlank()) continuations.add(it.continuation)
                frontier.clear()
                branchesUsed = 0
                it.frontierIds.filter { id -> id !in watched && id !in seedIds }.take(8).forEach { id ->
                    if (!seenIds.contains(id)) frontier.add(id)
                }
            }
            val searchPage = withTimeoutOrNull(20_000) { searchD.await() }
            searchVideos = searchPage?.videos ?: emptyList()
            searchQuery = searchPage?.query
            searchCont = searchPage?.continuation?.takeIf { it.isNotBlank() }
            shorts = withTimeoutOrNull(20_000) { shortsD.await() } ?: emptyList()
            // Merge search shorts into shelf when subs have none.
            if (shorts.isEmpty()) {
                shorts = withTimeoutOrNull(15_000) { fetchSearchShorts(queries) } ?: emptyList()
            }
        }

        // 3. Offline fallback: cached history as feed.
        var offline = false
        var historyFallback: List<UiVideo> = emptyList()
        if (subsVideos.isEmpty() && queueVideos.isEmpty() && searchVideos.isEmpty()) {
            historyFallback = offlineFallback()
            offline = historyFallback.isNotEmpty()
        }

        // 4. Rank / mix / vary per launch, then backfill missing avatars.
        val (mixed, sources) = HomeFeedEngine.rankAndMix(
            subs = subsVideos, queue = queueVideos, search = searchVideos,
            historyFallback = historyFallback,
            subIds = subIds, topChannelIds = topIds, watchedIds = watched,
            seed = seed
        )
        val enriched = enrichAvatars(mixed)
        fullVideos = enriched
        sourceOf = sources
        seenIds.clear()
        seenIds.addAll(enriched.map { it.id })
        seedIds.forEach { seenIds.add(it) }

        // Cache for offline next launch (fire-and-forget).
        if (enriched.isNotEmpty()) {
            val toCache = enriched.take(30)
            viewModelScope.launch {
                try {
                    toCache.forEach { LibraryRepository.cacheVideo(it) }
                } catch (_: Exception) {}
            }
        }

        val resume = resumeList.firstOrNull()?.toCard()?.takeIf { it.progress in 0.02f..0.92f }
        val filter = _state.value.filter
        _state.update {
            it.copy(
                isLoading = false, isRefreshing = false, isLoadingMore = false,
                // Dismissed row was deleted from DB, so it can't reappear —
                // safe to re-arm for future genuine resumes.
                resume = resume,
                resumeDismissed = false,
                videos = applyFilter(enriched, filter),
                shorts = shorts.take(12),
                error = if (enriched.isEmpty() && !offline) "Nothing yet — search or subscribe to fill your home" else null,
                offline = offline,
                canLoadMore = enriched.isNotEmpty() && hasMore(),
                seed = seed
            )
        }
    }

    /** Any pagination reservoir left? Drives the infinite footer. */
    private fun hasMore(): Boolean =
        continuations.isNotEmpty() || frontier.isNotEmpty() ||
            seedCursor < allSeeds.size || searchCont != null || pendingSubs.isNotEmpty()

    private fun applyFilter(list: List<UiVideo>, f: HomeFilter): List<UiVideo> = when (f) {
        HomeFilter.ALL -> list
        HomeFilter.SUBS -> list.filter { it.channelId in subIds }
        HomeFilter.QUEUE -> list.filter { sourceOf[it.id] == FeedSource.QUEUE }
        HomeFilter.FRESH -> list.filter { it.id !in watchedIds }
    }

    private suspend fun appendVideos(added: List<UiVideo>, defaultSource: FeedSource) {
        if (added.isEmpty()) return
        val fresh = added.filter { it.id.isNotBlank() && !seenIds.contains(it.id) }
        if (fresh.isEmpty()) return
        val enriched = enrichAvatars(fresh)
        enriched.forEach { seenIds.add(it.id) }
        val mutableSources = sourceOf.toMutableMap()
        enriched.forEach { mutableSources[it.id] = defaultSource }
        sourceOf = mutableSources
        fullVideos = fullVideos + enriched
        // Cache paged items too.
        viewModelScope.launch {
            try { enriched.forEach { LibraryRepository.cacheVideo(it) } } catch (_: Exception) {}
        }
        val f = _state.value.filter
        _state.update {
            it.copy(
                videos = applyFilter(fullVideos, f),
                canLoadMore = hasMore()
            )
        }
    }

    /** Fill blank avatars: subscriptions → per-video cache → channel cache. */
    private suspend fun enrichAvatars(videos: List<UiVideo>): List<UiVideo> {
        val need = videos.filter { it.avatarUrl.isBlank() && it.channelId.isNotBlank() }
        if (need.isEmpty()) return videos
        val subAvatars: Map<String, Pair<String, String>> = try {
            LibraryRepository.subscriptionsOnce()
                .filter { it.avatarUrl.isNotBlank() }
                .associate { it.channelId to (it.avatarUrl to it.avatarsJson) }
        } catch (_: Exception) { emptyMap() }
        val cachedVideo = LibraryRepository.cachedVideoAvatars(need.map { it.id })
        val cachedChannel = LibraryRepository.channelAvatars(need.map { it.channelId })
        if (subAvatars.isEmpty() && cachedVideo.isEmpty() && cachedChannel.isEmpty()) return videos
        return videos.map { v ->
            if (v.avatarUrl.isNotBlank()) return@map v
            val hit = subAvatars[v.channelId] ?: cachedVideo[v.id] ?: cachedChannel[v.channelId]
            if (hit == null) v else HomeFeedEngine.withAvatar(v, hit.first, hit.second)
        }
    }

    // ── Pagination: cheapest first ───────────────────────────────

    private data class QueueResult(
        val videos: List<UiVideo>,
        val continuation: String,
        val frontierIds: List<String>
    )

    /** One expansion step, cheapest source first; reservoirs cascade so pages keep coming. */
    private suspend fun expandOnce(): List<UiVideo> {
        // 1. Exhaust related continuations first.
        while (continuations.isNotEmpty()) {
            val cont = continuations.removeFirst()
            val page = safeRelated(cont)
            if (page != null && page.videos.isNotEmpty()) {
                if (page.continuation.isNotBlank()) continuations.add(page.continuation)
                page.frontierIds.filter { !seenIds.contains(it) }.take(4).forEach { frontier.add(it) }
                val fresh = page.videos.filter { !seenIds.contains(it.id) }
                if (fresh.isNotEmpty()) return fresh.take(8)
                // else keep draining continuations
            }
        }
        // 2. Branch: best unseen video's related (bounded).
        var guard = 0
        while (frontier.isNotEmpty() && branchesUsed < MAX_BRANCHES && guard++ < 4) {
            val id = frontier.removeFirst()
            if (seenIds.contains(id)) continue
            seenIds.add(id)
            branchesUsed++
            val res = safeDetail(id) ?: continue
            if (res.continuation.isNotBlank()) continuations.add(res.continuation)
            res.frontierIds.filter { !seenIds.contains(it) }.take(4).forEach { frontier.add(it) }
            val fresh = res.videos.filter { !seenIds.contains(it.id) }
            if (fresh.isNotEmpty()) return fresh.take(8)
        }
        // 3. Next history seed's own queue (each watched video is a new branch root).
        while (seedCursor < allSeeds.size) {
            val id = allSeeds[seedCursor++]
            if (id.isBlank() || seenIds.contains(id)) continue
            seenIds.add(id)
            val res = safeDetail(id) ?: continue
            if (res.continuation.isNotBlank()) continuations.add(res.continuation)
            res.frontierIds.filter { !seenIds.contains(it) }.take(6).forEach { frontier.add(it) }
            val fresh = res.videos.filter { !seenIds.contains(it.id) }
            if (fresh.isNotEmpty()) return fresh.take(8)
        }
        // 4. Fresh-search pagination (rotating query's next page).
        val q = searchQuery
        val sc = searchCont
        if (q != null && sc != null) {
            searchCont = null // consume; re-armed below if another page exists
            val page = safeSearchPage(q, sc)
            if (page != null) {
                searchCont = page.continuation.takeIf { it.isNotBlank() }
                val fresh = page.videos.filter { !seenIds.contains(it.id) }
                if (fresh.isNotEmpty()) return fresh.take(8)
            }
        }
        // 5. Remaining subscribed channels (beyond the first window).
        if (pendingSubs.isNotEmpty()) {
            val batch = ArrayList<SubscriptionRow>()
            repeat(3) { pendingSubs.removeFirstOrNull()?.let { batch.add(it) } }
            val vids = fetchSubsVideos(batch)
            val fresh = vids.filter { !seenIds.contains(it.id) }
            if (fresh.isNotEmpty()) return fresh
        }
        return emptyList()
    }

    // ── Source fetchers (all failure-safe) ───────────────────────

    /**
     * First subs window for the initial feed; leftovers wait in [pendingSubs]
     * for step 5 of pagination. Rotated per refresh so leads differ.
     */
    private fun firstSubsBatch(subs: List<SubscriptionRow>): List<SubscriptionRow> {
        pendingSubs.clear()
        if (subs.isEmpty()) return emptyList()
        val start = (refreshCount * 3) % subs.size
        val rotated = subs.drop(start) + subs.take(start)
        val batch = rotated.take(MAX_SUB_CHANNELS)
        rotated.drop(MAX_SUB_CHANNELS).forEach { pendingSubs.add(it) }
        return batch
    }

    private suspend fun fetchSubsVideos(rows: List<SubscriptionRow>): List<UiVideo> {
        if (rows.isEmpty()) return emptyList()
        return supervisorScope {
            rows.map { row ->
                async {
                    try {
                        withTimeoutOrNull(12_000) {
                            channelRepo.fetchVideos(row.channelId).videos.take(PER_CHANNEL).map { v ->
                                // Backfill identity AND avatar from the local sub —
                                // channel video rows often carry neither.
                                v.copy(
                                    channelId = v.channelId.ifBlank { row.channelId },
                                    author = v.author.ifBlank { row.title },
                                    avatarUrl = v.avatarUrl.ifBlank { row.avatarUrl },
                                    avatarsJson = if (v.avatarsJson.isBlank() || v.avatarsJson == "[]") row.avatarsJson else v.avatarsJson
                                )
                            }
                        } ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }
            }.awaitAll().let { HomeFeedEngine.interleave(it, PER_CHANNEL) }
        }
    }

    private suspend fun fetchQueueSeed(seedIds: List<String>): QueueResult? {
        val seed = seedIds.firstOrNull() ?: return null
        return try {
            withTimeoutOrNull(15_000) {
                val d = com.teamshryne.wediyo.data.engine.WediyoEngine.fetchVideoDetail(seed)
                val vids = d.relatedVideos.filter { it.id.isNotBlank() && it.id != seed }
                QueueResult(vids, d.relatedContinuation, vids.take(10).map { it.id })
            }
        } catch (_: Exception) { null }
    }

    private data class SearchPage(
        val videos: List<UiVideo>,
        val query: String,
        val continuation: String
    )

    private suspend fun fetchSearchFresh(queries: List<String>, round: Int): SearchPage? {
        // Rotate query each refresh → different results, no boredom.
        val q = queries.getOrNull(if (queries.isNotEmpty()) round % queries.size else -1)
            ?: return null
        return try {
            withTimeoutOrNull(12_000) {
                val r = searchRepo.search(q)
                SearchPage(
                    r.videos.filter { it.id.isNotBlank() }.take(8),
                    q, r.continuation
                )
            }
        } catch (_: Exception) { null }
    }

    private suspend fun safeSearchPage(query: String, continuation: String): SearchPage? {
        if (query.isBlank() || continuation.isBlank()) return null
        return try {
            withTimeoutOrNull(12_000) {
                val r = searchRepo.search(query, "", continuation)
                SearchPage(
                    r.videos.filter { it.id.isNotBlank() }.take(10),
                    query, r.continuation
                )
            }
        } catch (_: Exception) { null }
    }

    private suspend fun fetchSearchShorts(queries: List<String>): List<UiShort> {
        val q = queries.firstOrNull() ?: return emptyList()
        return try {
            withTimeoutOrNull(12_000) {
                searchRepo.search(q).shorts.take(8)
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun fetchShorts(subIds: List<String>): List<UiShort> {
        if (subIds.isEmpty()) return emptyList()
        return supervisorScope {
            subIds.take(MAX_SHORTS_CHANNELS).map { id ->
                async {
                    try {
                        withTimeoutOrNull(10_000) {
                            channelRepo.fetchShorts(id).shorts.take(PER_CHANNEL_SHORTS)
                        } ?: emptyList()
                    } catch (_: Exception) { emptyList<UiShort>() }
                }
            }.awaitAll().let { HomeFeedEngine.interleave(it, PER_CHANNEL_SHORTS) }
        }
    }

    private suspend fun safeRelated(cont: String): QueueResult? {
        if (cont.isBlank()) return null
        return try {
            withTimeoutOrNull(12_000) {
                val m = com.teamshryne.wediyo.data.engine.WediyoEngine.fetchRelated(cont)
                QueueResult(
                    m.relatedVideos.filter { it.id.isNotBlank() },
                    m.relatedContinuation, m.relatedVideos.take(8).map { it.id }
                )
            }
        } catch (_: Exception) { null }
    }

    private suspend fun safeDetail(id: String): QueueResult? {
        if (id.isBlank()) return null
        return try {
            withTimeoutOrNull(15_000) {
                val d = com.teamshryne.wediyo.data.engine.WediyoEngine.fetchVideoDetail(id)
                QueueResult(
                    d.relatedVideos.filter { it.id.isNotBlank() && it.id != id },
                    d.relatedContinuation, d.relatedVideos.take(8).map { it.id }
                )
            }
        } catch (_: Exception) { null }
    }

    private suspend fun offlineFallback(): List<UiVideo> {
        return try {
            LibraryRepository.history(20).first().mapNotNull { h ->
                if (h.videoId.isBlank() || h.title.isNullOrBlank()) null else UiVideo(
                    id = h.videoId, title = h.title ?: "", author = h.author ?: "",
                    channelId = h.channelId ?: "", thumbnailUrl = h.thumbnailUrl ?: "",
                    thumbnailsJson = h.thumbnailsJson ?: "[]", avatarUrl = "", avatarsJson = "[]",
                    viewCountText = h.viewCountText ?: "", publishedText = h.publishedText ?: "",
                    durationText = h.durationText ?: "", isLive = h.isLive ?: false,
                    badges = emptyList(), description = ""
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun ResumeWithVideo.toCard(): ResumeCard {
        val p = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        return ResumeCard(
            videoId = videoId, title = title ?: "", author = author ?: "",
            channelId = channelId ?: "", thumbUrl = thumbnailUrl ?: "",
            thumbsJson = thumbnailsJson ?: "[]", positionMs = positionMs,
            durationMs = durationMs, progress = p,
            durationText = durationText ?: ""
        )
    }

    companion object {
        private const val MAX_SUB_CHANNELS = 10
        private const val PER_CHANNEL = 2
        private const val MAX_SHORTS_CHANNELS = 4
        private const val PER_CHANNEL_SHORTS = 3
        private const val MAX_BRANCHES = 6
    }
}
