package com.teamshryne.wediyo.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi

@UnstableApi
class SleepTimerReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_FIRE = "com.teamshryne.wediyo.SLEEP_TIMER_FIRE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        try {
            // Ensure volume restored and playback paused even if process was dead
            SleepTimerManager.init(context)
            SleepTimerManager.onAlarmFired(context)
        } catch (_: Exception) {
            try { PlayerManager.get().pause() } catch (_: Exception) {}
        }
        // Bring up a quick cleanup: goAsync if we needed longer work, but pause is instantaneous
    }
}
