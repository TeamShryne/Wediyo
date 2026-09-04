package com.teamshryne.wediyo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VideoEntity::class,
        ChannelEntity::class,
        HistoryEvent::class,
        WatchProgress::class,
        LikeEntity::class,
        WatchLaterEntity::class,
        SubscriptionEntity::class,
        LocalPlaylistEntity::class,
        PlaylistItemEntity::class,
        SearchEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WediyoDatabase : RoomDatabase() {
    abstract fun videos(): VideoDao
    abstract fun channels(): ChannelDao
    abstract fun history(): HistoryDao
    abstract fun progress(): ProgressDao
    abstract fun likes(): LikeDao
    abstract fun watchLater(): WatchLaterDao
    abstract fun subscriptions(): SubscriptionDao
    abstract fun playlists(): LocalPlaylistDao
    abstract fun searches(): SearchEventDao

    companion object {
        @Volatile private var inst: WediyoDatabase? = null

        fun get(context: Context): WediyoDatabase =
            inst ?: synchronized(this) {
                inst ?: Room.databaseBuilder(
                    context.applicationContext,
                    WediyoDatabase::class.java,
                    "wediyo-library.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { inst = it }
            }
    }
}
