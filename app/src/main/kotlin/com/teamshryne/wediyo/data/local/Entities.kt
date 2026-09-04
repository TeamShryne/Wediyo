package com.teamshryne.wediyo.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ── Video cache: exhaustive snapshot so future stats never need re-fetch ──
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val videoId: String,
    val title: String = "",
    val author: String = "",
    val channelId: String = "",
    val channelTitle: String = "",
    val thumbnailUrl: String = "",
    val thumbnailsJson: String = "[]",
    val avatarUrl: String = "",
    val avatarsJson: String = "[]",
    val viewCountText: String = "",
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val likeCountText: String = "",
    val publishedText: String = "",
    val publishDate: String = "",
    val durationText: String = "",
    val durationSecs: Long = 0L,
    val category: String = "",
    val keywordsJson: String = "[]",
    val isLive: Boolean = false,
    val description: String = "",
    // Full UiVideoDetail JSON blob — future-proof for any stat we dream up later
    val fullJson: String = "{}",
    val lastRefreshed: Long = 0L
)

// ── Channel cache ──
@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val channelId: String,
    val title: String = "",
    val handle: String = "",
    val avatarUrl: String = "",
    val avatarsJson: String = "[]",
    val bannerUrl: String = "",
    val bannersJson: String = "[]",
    val subsText: String = "",
    val videoCountText: String = "",
    val description: String = "",
    val verified: Boolean = false,
    val channelUrl: String = "",
    val fullJson: String = "{}",
    val lastRefreshed: Long = 0L
)

// ── History: append-only event log (stats goldmine) ──
@Entity(
    tableName = "history_events",
    indices = [Index("videoId"), Index("watchedAt"), Index("channelId")]
)
data class HistoryEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val videoId: String,
    val channelId: String = "",
    val watchedAt: Long = System.currentTimeMillis(),
    val watchDurationMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val progress: Float = 0f, // 0..1
    val completed: Boolean = false,
    val source: String = "video", // video, search, channel, related, shorts, playlist, show, course, podcast
    val sessionId: String = "",
    val isShorts: Boolean = false,
    val playbackSpeed: Float = 1f,
    val qualityHeight: Int = 0
)

// ── Resume progress: one row per video ──
@Entity(tableName = "watch_progress")
data class WatchProgress(
    @PrimaryKey val videoId: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false
)

// ── Likes (state ready for dislike later) ──
@Entity(tableName = "likes")
data class LikeEntity(
    @PrimaryKey val videoId: String,
    val likedAt: Long = System.currentTimeMillis(),
    val state: Int = 1, // 1 = liked, -1 = disliked (future), 0 = none
    val source: String = "video"
)

// ── Watch Later queue ──
@Entity(tableName = "watch_later", indices = [Index("addedAt")])
data class WatchLaterEntity(
    @PrimaryKey val videoId: String,
    val addedAt: Long = System.currentTimeMillis(),
    val sortOrder: Long = System.currentTimeMillis(),
    val source: String = "video"
)

// ── Subscriptions: local-only follows ──
@Entity(tableName = "subscriptions", indices = [Index("subscribedAt")])
data class SubscriptionEntity(
    @PrimaryKey val channelId: String,
    val title: String = "",
    val handle: String = "",
    val avatarUrl: String = "",
    val avatarsJson: String = "[]",
    val subsText: String = "",
    val verified: Boolean = false,
    val subscribedAt: Long = System.currentTimeMillis(),
    val notifyEnabled: Boolean = false,
    val fullJson: String = "{}"
)

// ── Custom playlists ──
@Entity(tableName = "local_playlists", indices = [Index("updatedAt")])
data class LocalPlaylistEntity(
    @PrimaryKey val playlistId: String, // UUID
    val title: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val coverVideoId: String? = null
)

@Entity(
    tableName = "local_playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = LocalPlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("videoId"), Index("position")]
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val itemId: Long = 0L,
    val playlistId: String,
    val videoId: String,
    val position: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val source: String = "library"
)

// ── Search events: for future taste/interest stats ──
@Entity(tableName = "search_events", indices = [Index("timestamp")])
data class SearchEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
    val resultCount: Int = 0,
    val clickedVideoId: String? = null,
    val clickedChannelId: String? = null
)
