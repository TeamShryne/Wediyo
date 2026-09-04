package com.teamshryne.wediyo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ── Rich joined rows for UI ──
data class HistoryWithVideo(
    val id: Long,
    val videoId: String,
    val watchedAt: Long,
    val progress: Float,
    val completed: Boolean,
    val source: String,
    val isShorts: Boolean,
    // video snapshot (nullable if pruned)
    val title: String?,
    val author: String?,
    val channelId: String?,
    val thumbnailUrl: String?,
    val thumbnailsJson: String?,
    val durationText: String?,
    val viewCountText: String?,
    val publishedText: String?,
    val isLive: Boolean?
)

data class SavedVideoRow(
    val videoId: String,
    val addedAt: Long,
    val sortOrder: Long = 0L,
    val position: Int = 0,
    val title: String?,
    val author: String?,
    val channelId: String?,
    val thumbnailUrl: String?,
    val thumbnailsJson: String?,
    val durationText: String?,
    val viewCountText: String?,
    val publishedText: String?
)

data class SubscriptionRow(
    val channelId: String,
    val title: String,
    val handle: String,
    val avatarUrl: String,
    val avatarsJson: String,
    val subsText: String,
    val verified: Boolean,
    val subscribedAt: Long
)

@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(video: VideoEntity)

    @Query("SELECT * FROM videos WHERE videoId = :id")
    suspend fun get(id: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE videoId IN (:ids)")
    suspend fun getMany(ids: List<String>): List<VideoEntity>
}

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(channel: ChannelEntity)

    @Query("SELECT * FROM channels WHERE channelId = :id")
    suspend fun get(id: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE channelId IN (:ids)")
    suspend fun getMany(ids: List<String>): List<ChannelEntity>
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(event: HistoryEvent): Long

    @Query("DELETE FROM history_events")
    suspend fun clearAll()

    @Query("DELETE FROM history_events WHERE videoId = :videoId")
    suspend fun deleteForVideo(videoId: String)

    // Latest event per video, newest first — powers History list + stats later
    @Query(
        """SELECT h.id as id, h.videoId as videoId, h.watchedAt as watchedAt,
           h.progress as progress, h.completed as completed, h.source as source, h.isShorts as isShorts,
           v.title as title, v.author as author, v.channelId as channelId,
           v.thumbnailUrl as thumbnailUrl, v.thumbnailsJson as thumbnailsJson,
           v.durationText as durationText, v.viewCountText as viewCountText,
           v.publishedText as publishedText, v.isLive as isLive
           FROM history_events h LEFT JOIN videos v ON v.videoId = h.videoId
           WHERE h.id IN (SELECT MAX(id) FROM history_events GROUP BY videoId)
           ORDER BY h.watchedAt DESC LIMIT :limit"""
    )
    fun recentWithVideo(limit: Int = 100): Flow<List<HistoryWithVideo>>

    @Query("SELECT COUNT(DISTINCT videoId) FROM history_events")
    fun distinctVideoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM history_events")
    fun totalEvents(): Flow<Int>

    @Query("SELECT COALESCE(SUM(watchDurationMs),0) FROM history_events")
    fun totalWatchMs(): Flow<Long>

    @Query("SELECT COALESCE(SUM(watchDurationMs),0) FROM history_events WHERE watchedAt >= :since")
    fun watchMsSince(since: Long): Flow<Long>
}

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(progress: WatchProgress)

    @Query("SELECT * FROM watch_progress WHERE videoId = :id")
    suspend fun get(id: String): WatchProgress?

    @Query("SELECT * FROM watch_progress WHERE videoId = :id")
    fun observe(id: String): Flow<WatchProgress?>

    @Query("DELETE FROM watch_progress WHERE videoId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM watch_progress")
    suspend fun clearAll()
}

@Dao
interface LikeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun like(like: LikeEntity)

    @Query("DELETE FROM likes WHERE videoId = :id")
    suspend fun unlike(id: String)

    @Query("DELETE FROM likes")
    suspend fun clearAll()

    @Query("SELECT EXISTS(SELECT 1 FROM likes WHERE videoId = :id AND state = 1)")
    fun isLiked(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM likes WHERE videoId = :id AND state = 1)")
    suspend fun isLikedOnce(id: String): Boolean

    @Query(
        """SELECT l.videoId as videoId, l.likedAt as addedAt, 0 as sortOrder, 0 as position,
           v.title as title, v.author as author, v.channelId as channelId,
           v.thumbnailUrl as thumbnailUrl, v.thumbnailsJson as thumbnailsJson,
           v.durationText as durationText, v.viewCountText as viewCountText, v.publishedText as publishedText
           FROM likes l LEFT JOIN videos v ON v.videoId = l.videoId
           WHERE l.state = 1 ORDER BY l.likedAt DESC"""
    )
    fun likedVideos(): Flow<List<SavedVideoRow>>

    @Query("SELECT COUNT(*) FROM likes WHERE state = 1")
    fun likedCount(): Flow<Int>
}

@Dao
interface WatchLaterDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE videoId = :id")
    suspend fun remove(id: String)

    @Query("DELETE FROM watch_later")
    suspend fun clearAll()

    @Query("SELECT EXISTS(SELECT 1 FROM watch_later WHERE videoId = :id)")
    fun isSaved(id: String): Flow<Boolean>

    @Query(
        """SELECT w.videoId as videoId, w.addedAt as addedAt, w.sortOrder as sortOrder, 0 as position,
           v.title as title, v.author as author, v.channelId as channelId,
           v.thumbnailUrl as thumbnailUrl, v.thumbnailsJson as thumbnailsJson,
           v.durationText as durationText, v.viewCountText as viewCountText, v.publishedText as publishedText
           FROM watch_later w LEFT JOIN videos v ON v.videoId = w.videoId
           ORDER BY w.sortOrder DESC"""
    )
    fun queue(): Flow<List<SavedVideoRow>>

    @Query("SELECT COUNT(*) FROM watch_later")
    fun count(): Flow<Int>
}

@Dao
interface SubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun subscribe(sub: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE channelId = :id")
    suspend fun unsubscribe(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE channelId = :id)")
    fun isSubscribed(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE channelId = :id)")
    suspend fun isSubscribedOnce(id: String): Boolean

    @Query(
        """SELECT channelId, title, handle, avatarUrl, avatarsJson, subsText, verified, subscribedAt
           FROM subscriptions ORDER BY subscribedAt DESC"""
    )
    fun all(): Flow<List<SubscriptionRow>>

    @Query("SELECT COUNT(*) FROM subscriptions")
    fun count(): Flow<Int>
}

@Dao
interface LocalPlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(pl: LocalPlaylistEntity)

    @Query("DELETE FROM local_playlists WHERE playlistId = :id")
    suspend fun deletePlaylist(id: String)

    @Query("SELECT * FROM local_playlists ORDER BY updatedAt DESC")
    fun playlists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM local_playlists WHERE playlistId = :id")
    suspend fun getPlaylist(id: String): LocalPlaylistEntity?

    @Query("SELECT COUNT(*) FROM local_playlists")
    fun playlistCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItem(item: PlaylistItemEntity): Long

    @Query("DELETE FROM local_playlist_items WHERE itemId = :itemId")
    suspend fun removeItem(itemId: Long)

    @Query("DELETE FROM local_playlist_items WHERE playlistId = :pid AND videoId = :vid")
    suspend fun removeVideo(pid: String, vid: String)

    @Query("DELETE FROM local_playlist_items WHERE playlistId = :pid")
    suspend fun clearPlaylist(pid: String)

    @Query("SELECT COALESCE(MAX(position),-1)+1 FROM local_playlist_items WHERE playlistId = :pid")
    suspend fun nextPosition(pid: String): Int

    @Query(
        """SELECT i.videoId as videoId, i.addedAt as addedAt, 0 as sortOrder, i.position as position,
           v.title as title, v.author as author, v.channelId as channelId,
           v.thumbnailUrl as thumbnailUrl, v.thumbnailsJson as thumbnailsJson,
           v.durationText as durationText, v.viewCountText as viewCountText, v.publishedText as publishedText
           FROM local_playlist_items i LEFT JOIN videos v ON v.videoId = i.videoId
           WHERE i.playlistId = :pid ORDER BY i.position ASC"""
    )
    fun items(pid: String): Flow<List<SavedVideoRow>>

    @Query("SELECT COUNT(*) FROM local_playlist_items WHERE playlistId = :pid")
    fun itemCount(pid: String): Flow<Int>

    @Query("SELECT playlistId AS playlistId, COUNT(*) AS c FROM local_playlist_items GROUP BY playlistId")
    fun itemCounts(): Flow<List<PlaylistCount>>

    @Query("SELECT p.playlistId AS playlistId, v.thumbnailUrl AS thumb, v.thumbnailsJson AS thumbs FROM local_playlists p LEFT JOIN videos v ON v.videoId = p.coverVideoId")
    fun covers(): Flow<List<PlaylistCover>>
}

data class PlaylistCount(val playlistId: String, val c: Int)
data class PlaylistCover(val playlistId: String, val thumb: String?, val thumbs: String?)

@Dao
interface SearchEventDao {
    @Insert
    suspend fun log(event: SearchEventEntity)

    @Query("SELECT query, MAX(timestamp) as ts, COUNT(*) as c FROM search_events GROUP BY query ORDER BY ts DESC LIMIT :limit")
    suspend fun recentQueries(limit: Int = 20): List<RecentQuery>

    @Query("DELETE FROM search_events")
    suspend fun clearAll()
}

data class RecentQuery(val query: String, val ts: Long, val c: Int)
