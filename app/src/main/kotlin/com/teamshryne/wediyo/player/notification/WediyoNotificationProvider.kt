package com.teamshryne.wediyo.player.notification

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.DefaultMediaNotificationProvider
import com.google.common.collect.ImmutableList
import com.teamshryne.wediyo.R

/**
 * System playback notification (shade + lockscreen).
 *
 * State of the art via Media3 defaults, tuned for a single-video app like YouTube:
 * - play/pause only — no prev/next queue buttons (nothing to skip to)
 * - app channel "Playback", Wediyo small icon
 * - title = video title, text = channel (from MediaMetadata set in PlayerManager)
 * - artwork loads async from the video thumbnail URL via Media3's bitmap loader
 * - tap opens the app; swipe-away while paused stops the service (Media3 default)
 */
@UnstableApi
class WediyoNotificationProvider(context: Context) :
    DefaultMediaNotificationProvider(
        context,
        /* notificationIdProvider = */ { NOTIFICATION_ID },
        CHANNEL_ID,
        R.string.playback_channel_name
    ) {

    init {
        setSmallIcon(R.drawable.ic_stat_wediyo)
    }

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        // Single queue item: only play/pause makes sense. Anything else would be a dead button.
        if (!playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
            return ImmutableList.of()
        }
        val button = if (showPauseButton) {
            CommandButton.Builder(CommandButton.ICON_PAUSE)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setDisplayName("Pause")
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build()
        } else {
            CommandButton.Builder(CommandButton.ICON_PLAY)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setDisplayName("Play")
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build()
        }
        return ImmutableList.of(button)
    }

    companion object {
        const val CHANNEL_ID = "wediyo_playback"
        const val NOTIFICATION_ID = 1001
    }
}
