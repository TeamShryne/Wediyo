package com.teamshryne.wediyo.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.teamshryne.wediyo.MainActivity
import com.teamshryne.wediyo.player.notification.WediyoNotificationProvider

/**
 * Foreground playback service — the state-of-the-art Media3 background playback host.
 *
 * Why this exists (mirrors YouTube):
 * - Owns the [MediaSession] so playback survives navigation, screen-off and other apps.
 * - Automatically publishes the system media notification (shade + lockscreen + wear/auto)
 *   with title/channel/artwork/play-pause. No POST_NOTIFICATIONS permission needed —
 *   media-session notifications are exempt.
 * - The ExoPlayer itself stays single-owned by [PlayerManager] (fast: zero re-buffer when
 *   moving between full player / miniplayer / background). This service only attaches the
 *   session to whatever instance PlayerManager currently holds and re-attaches on
 *   video<->shorts recreation.
 *
 * Lifecycle: started on every PlayerManager.playDetail(); goes background by itself when
 * playback stops; fully stopped on miniplayer X / swipe / notification dismiss while paused.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var sessionActivity: PendingIntent

    override fun onCreate() {
        super.onCreate()
        // Reuse the shared instance — never recreate here (would drop the buffer).
        val player = PlayerManager.get().playerOrNull()
            ?: PlayerManager.get().ensure(this, false)

        sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        attachSession(player)

        setMediaNotificationProvider(WediyoNotificationProvider(this))

        // Keep the session glued to the live player across video<->shorts pool swaps.
        PlayerManager.get().onPlayerReplaced = { fresh ->
            try {
                attachSession(fresh)
            } catch (_: Exception) {
            }
        }
    }

    /** (Re)builds the session on the given player. Needed because MediaSession's player
     *  is fixed at build time while PlayerManager swaps pools on video<->shorts switches. */
    private fun attachSession(player: Player) {
        val prev = session
        val built = MediaSession.Builder(this, player)
            .setId("wediyo-playback")
            .setSessionActivity(sessionActivity)
            .setCallback(WediyoSessionCallback())
            .build()
        session = built
        addSession(built)
        try {
            prev?.release()
        } catch (_: Exception) {
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep audio going if the user swiped the app away mid-play (music-app behavior,
        // stoppable anytime from the notification). Otherwise let the service die.
        val playing = try {
            PlayerManager.get().playerOrNull()?.isPlaying == true
        } catch (_: Exception) {
            false
        }
        if (!playing) {
            try {
                PlayerManager.get().stopAndClear(null)
            } catch (_: Exception) {
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            PlayerManager.get().onPlayerReplaced = null
        } catch (_: Exception) {
        }
        try {
            session?.release()
        } catch (_: Exception) {
        }
        session = null
        super.onDestroy()
    }

    /**
     * Minimal session callback: guard external controllers (Auto/Wear/assistant) so they
     * can play/pause/seek the current video but can never inject their own queue items,
     * which our single-video player could not resolve (no URLs for arbitrary ids).
     */
    private inner class WediyoSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<androidx.media3.common.MediaItem>
        ): com.google.common.util.concurrent.ListenableFuture<List<androidx.media3.common.MediaItem>> {
            // Reject foreign queue items — keep the current video untouched.
            val current = try {
                mediaSession.player.currentMediaItem
            } catch (_: Exception) {
                null
            }
            val kept: List<androidx.media3.common.MediaItem> = if (current != null) listOf(current) else emptyList()
            return com.google.common.util.concurrent.Futures.immediateFuture(kept)
        }
        // Playback resumption intentionally not overridden: the default declines, so the
        // system hides the resumption card (we have no persisted queue with fresh URLs).
    }
}
