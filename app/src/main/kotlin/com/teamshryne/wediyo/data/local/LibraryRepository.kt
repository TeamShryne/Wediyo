package com.teamshryne.wediyo.data.local

import android.content.Context
import com.teamshryne.wediyo.data.model.UiChannelHeader
import com.teamshryne.wediyo.data.model.UiVideo
import com.teamshryne.wediyo.data.model.UiVideoDetail
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for the local-only library.
 * History auto-logs on detail load; progress updates throttled by callers.
 * Everything cached exhaustively so future stats need no migration.
 */
object LibraryRepository {
    @Volatile private var db: WediyoDatabase? = null

    fun init(context: Context) {
        if (db == null) db = WediyoDatabase.get(context)
    }

    private fun db(): WediyoDatabase =
        db ?: throw IllegalStateException("LibraryRepository not init — call init(context) in MainActivity")

    // ── Caching ──────────────────────────────────────────────
    suspend fun cacheVideoDetail(d: UiVideoDetail, source: String = "video") {
        try {
            db().videos().upsert(
                VideoEntity(
                    videoId = d.videoId,
                    title = d.title,
                    author = d.author.ifBlank { d.channelTitle },
                    channelId = d.channelId,
                    channelTitle = d.channelTitle,
                    thumbnailUrl = d.thumbnailUrl,
                    thumbnailsJson = d.thumbnailsJson,
                    avatarUrl = d.channelAvatarUrl,
                    avatarsJson = d.channelAvatarsJson,
                    viewCountText = d.viewCountText,
                    viewCount = d.viewCount,
                    likeCount = d.likeCount,
                    likeCountText = d.likeCountText,
                    publishedText = d.uploadDate.ifBlank { d.publishDate },
                    publishDate = d.publishDate,
                    durationText = d.durationText,
                    durationSecs = d.lengthSeconds,
                    category = d.category,
                    isLive = d.isLive || d.isLiveContent,
                    description = d.description.ifBlank { d.shortDescription },
                    lastRefreshed = System.currentTimeMillis()
                )
            )
            if (d.channelId.isNotBlank()) {
                db().channels().upsert(
                    ChannelEntity(
                        channelId = d.channelId,
                        title = d.channelTitle.ifBlank { d.author },
                        handle = d.channelHandle,
                        avatarUrl = d.channelAvatarUrl,
                        avatarsJson = d.channelAvatarsJson,
                        subsText = d.subscriberCountText,
                        lastRefreshed = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: Exception) {}
    }

    suspend fun cacheVideo(v: UiVideo) {
        try {
            db().videos().upsert(
                VideoEntity(
                    videoId = v.id,
                    title = v.title,
                    author = v.author,
                    channelId = v.channelId,
                    thumbnailUrl = v.thumbnailUrl,
                    thumbnailsJson = v.thumbnailsJson,
                    avatarUrl = v.avatarUrl,
                    avatarsJson = v.avatarsJson,
                    viewCountText = v.viewCountText,
                    publishedText = v.publishedText,
                    durationText = v.durationText,
                    isLive = v.isLive,
                    description = v.description,
                    lastRefreshed = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun cacheChannel(h: UiChannelHeader) {
        try {
            db().channels().upsert(
                ChannelEntity(
                    channelId = h.channelId,
                    title = h.title,
                    handle = h.handle,
                    avatarUrl = h.avatarUrl,
                    avatarsJson = h.avatarsJson,
                    bannerUrl = h.bannerUrl,
                    bannersJson = h.bannersJson,
                    subsText = h.subs,
                    videoCountText = h.videoCount,
                    description = h.description,
                    verified = h.verified,
                    channelUrl = h.channelUrl,
                    lastRefreshed = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}
    }

    // ── History (auto-log after >5s rule handled by caller) ──
    suspend fun logHistory(
        videoId: String,
        channelId: String = "",
        source: String = "video",
        isShorts: Boolean = false,
        watchDurationMs: Long = 0L,
        totalDurationMs: Long = 0L,
        progress: Float = 0f,
        sessionId: String = ""
    ) {
        try {
            db().history().insert(
                HistoryEvent(
                    videoId = videoId,
                    channelId = channelId,
                    source = source,
                    isShorts = isShorts,
                    watchDurationMs = watchDurationMs,
                    totalDurationMs = totalDurationMs,
                    progress = progress.coerceIn(0f, 1f),
                    completed = progress >= 0.9f,
                    sessionId = sessionId
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun saveProgress(videoId: String, positionMs: Long, durationMs: Long) {
        try {
            val p = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
            db().progress().save(
                WatchProgress(
                    videoId = videoId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    completed = p >= 0.9f
                )
            )
        } catch (_: Exception) {}
    }

    fun observeProgress(videoId: String): Flow<WatchProgress?> =
        db().progress().observe(videoId)

    fun history(limit: Int = 100): Flow<List<HistoryWithVideo>> =
        db().history().recentWithVideo(limit)

    suspend fun clearHistory() {
        try {
            db().history().clearAll()
            db().progress().clearAll()
        } catch (_: Exception) {}
    }

    suspend fun removeHistoryVideo(videoId: String) {
        try {
            db().history().deleteForVideo(videoId)
            db().progress().delete(videoId)
        } catch (_: Exception) {}
    }

    fun historyStats(): HistoryStatsFlows = HistoryStatsFlows(
        distinctVideos = db().history().distinctVideoCount(),
        totalEvents = db().history().totalEvents(),
        totalWatchMs = db().history().totalWatchMs()
    )

    data class HistoryStatsFlows(
        val distinctVideos: Flow<Int>,
        val totalEvents: Flow<Int>,
        val totalWatchMs: Flow<Long>
    )

    // ── Watch Later ──
    suspend fun addWatchLater(video: UiVideo, source: String = "video") {
        cacheVideo(video)
        try { db().watchLater().add(WatchLaterEntity(videoId = video.id, source = source)) } catch (_: Exception) {}
    }

    suspend fun addWatchLater(detail: UiVideoDetail, source: String = "video") {
        cacheVideoDetail(detail, source)
        try { db().watchLater().add(WatchLaterEntity(videoId = detail.videoId, source = source)) } catch (_: Exception) {}
    }

    suspend fun removeWatchLater(videoId: String) {
        try { db().watchLater().remove(videoId) } catch (_: Exception) {}
    }

    suspend fun clearWatchLater() {
        try { db().watchLater().clearAll() } catch (_: Exception) {}
    }

    fun watchLater(): Flow<List<SavedVideoRow>> = db().watchLater().queue()
    fun isWatchLater(videoId: String): Flow<Boolean> = db().watchLater().isSaved(videoId)

    // ── Likes ──
    suspend fun toggleLike(video: UiVideo): Boolean {
        cacheVideo(video)
        return toggleLikeById(video.id)
    }

    suspend fun toggleLike(detail: UiVideoDetail): Boolean {
        cacheVideoDetail(detail)
        return toggleLikeById(detail.videoId)
    }

    private suspend fun toggleLikeById(videoId: String): Boolean {
        return try {
            val dao = db().likes()
            if (dao.isLikedOnce(videoId)) {
                dao.unlike(videoId)
                false
            } else {
                dao.like(LikeEntity(videoId = videoId))
                true
            }
        } catch (_: Exception) { false }
    }

    suspend fun setLiked(videoId: String, liked: Boolean, source: String = "video") {
        try {
            if (liked) db().likes().like(LikeEntity(videoId = videoId, source = source))
            else db().likes().unlike(videoId)
        } catch (_: Exception) {}
    }

    fun isLiked(videoId: String): Flow<Boolean> = db().likes().isLiked(videoId)
    suspend fun isLikedOnce(videoId: String): Boolean =
        try { db().likes().isLikedOnce(videoId) } catch (_: Exception) { false }
    fun likedVideos(): Flow<List<SavedVideoRow>> = db().likes().likedVideos()

    // ── Subscriptions ──
    suspend fun subscribe(channelId: String, title: String, handle: String = "", avatarUrl: String = "", avatarsJson: String = "[]", subsText: String = "", verified: Boolean = false) {
        try {
            db().subscriptions().subscribe(
                SubscriptionEntity(
                    channelId = channelId,
                    title = title,
                    handle = handle,
                    avatarUrl = avatarUrl,
                    avatarsJson = avatarsJson,
                    subsText = subsText,
                    verified = verified
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun subscribeHeader(h: UiChannelHeader) {
        subscribe(h.channelId, h.title, h.handle, h.avatarUrl, h.avatarsJson, h.subs, h.verified)
        cacheChannel(h)
    }

    suspend fun unsubscribe(channelId: String) {
        try { db().subscriptions().unsubscribe(channelId) } catch (_: Exception) {}
    }

    fun isSubscribed(channelId: String): Flow<Boolean> =
        if (channelId.isBlank()) kotlinx.coroutines.flow.flowOf(false)
        else db().subscriptions().isSubscribed(channelId)

    fun subscriptions(): Flow<List<SubscriptionRow>> = db().subscriptions().all()
    fun subscriptionCount(): Flow<Int> = db().subscriptions().count()

    // ── Custom playlists ──
    fun playlists(): Flow<List<LocalPlaylistEntity>> = db().playlists().playlists()

    suspend fun createPlaylist(title: String, description: String = ""): String {
        val id = UUID.randomUUID().toString()
        try {
            db().playlists().upsertPlaylist(
                LocalPlaylistEntity(playlistId = id, title = title.trim(), description = description.trim())
            )
        } catch (_: Exception) {}
        return id
    }

    suspend fun renamePlaylist(id: String, title: String, description: String = "") {
        try {
            val cur = db().playlists().getPlaylist(id) ?: return
            db().playlists().upsertPlaylist(cur.copy(title = title.trim(), description = description.trim(), updatedAt = System.currentTimeMillis()))
        } catch (_: Exception) {}
    }

    suspend fun deletePlaylist(id: String) {
        try { db().playlists().deletePlaylist(id) } catch (_: Exception) {}
    }

    suspend fun addToPlaylist(playlistId: String, video: UiVideo, source: String = "library") {
        cacheVideo(video)
        addToPlaylistById(playlistId, video.id, source)
    }

    suspend fun addToPlaylistById(playlistId: String, videoId: String, source: String = "library") {
        try {
            val dao = db().playlists()
            val pos = dao.nextPosition(playlistId)
            dao.addItem(PlaylistItemEntity(playlistId = playlistId, videoId = videoId, position = pos, source = source))
            dao.getPlaylist(playlistId)?.let { pl ->
                dao.upsertPlaylist(pl.copy(updatedAt = System.currentTimeMillis(), coverVideoId = pl.coverVideoId ?: videoId))
            }
        } catch (_: Exception) {}
    }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String) {
        try { db().playlists().removeVideo(playlistId, videoId) } catch (_: Exception) {}
    }

    fun playlistItems(playlistId: String): Flow<List<SavedVideoRow>> =
        db().playlists().items(playlistId)

    // ── Search events (future taste stats) ──
    suspend fun logSearch(query: String, resultCount: Int = 0) {
        if (query.isBlank()) return
        try { db().searches().log(SearchEventEntity(query = query.trim(), resultCount = resultCount)) } catch (_: Exception) {}
    }

    suspend fun logSearchClick(query: String, videoId: String? = null, channelId: String? = null) {
        if (query.isBlank() && videoId.isNullOrBlank()) return
        try { db().searches().log(SearchEventEntity(query = query.trim(), clickedVideoId = videoId, clickedChannelId = channelId)) } catch (_: Exception) {}
    }
}
