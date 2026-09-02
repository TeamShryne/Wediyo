package com.teamshryne.wediyo.player

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reliable sleep timer for Wediyo.
 *
 * Guarantees:
 *  - Survives rotation / navigation (singleton + app scope)
 *  - Survives background / Doze via AlarmManager backup
 *  - Survives process death via persisted end-time (SharedPrefs)
 *  - Smooth fade-out (volume ramp) in last [FADE_DURATION_MS]
 *  - Modes: timed duration, custom duration, end-of-video
 *  - Extend / cancel / quick presets
 */
@UnstableApi
object SleepTimerManager {

    enum class Mode { TIMER, END_OF_VIDEO }

    data class State(
        val isActive: Boolean = false,
        val mode: Mode = Mode.TIMER,
        val totalMs: Long = 0L,
        val remainingMs: Long = 0L,
        val endTimeElapsed: Long = 0L, // elapsedRealtime target
        val endTimeWall: Long = 0L,    // wall clock for alarm
        val fadeEnabled: Boolean = true,
        val isFading: Boolean = false,
    )

    private const val PREFS = "wediyo_sleep_timer"
    private const val KEY_ACTIVE = "active"
    private const val KEY_MODE = "mode"
    private const val KEY_TOTAL = "total"
    private const val KEY_END_ELAPSED = "end_elapsed"
    private const val KEY_END_WALL = "end_wall"
    private const val KEY_FADE = "fade"
    private const val RC_ALARM = 0x5EE

    const val FADE_DURATION_MS = 30_000L
    private const val TICK_MS = 500L

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var videoEndListener: Player.Listener? = null
    private var appContext: Context? = null
    private var originalVolume: Float = 1f
    private var hasFaded = false

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        restore(context)
    }

    // ── Public API ──────────────────────────────────────────────────────────

    fun startMinutes(context: Context, minutes: Int, fade: Boolean = true) {
        startDuration(context, minutes * 60_000L, fade)
    }

    fun startDuration(context: Context, durationMs: Long, fade: Boolean = true) {
        require(durationMs > 0) { "duration must be > 0" }
        val ctx = context.applicationContext
        appContext = ctx
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val endElapsed = nowElapsed + durationMs
        val endWall = nowWall + durationMs
        persist(ctx, true, Mode.TIMER, durationMs, endElapsed, endWall, fade)
        _state.value = State(
            isActive = true,
            mode = Mode.TIMER,
            totalMs = durationMs,
            remainingMs = durationMs,
            endTimeElapsed = endElapsed,
            endTimeWall = endWall,
            fadeEnabled = fade,
            isFading = false
        )
        scheduleAlarm(ctx, endWall)
        startTicker(ctx)
        hasFaded = false
        originalVolume = PlayerManager.get().playerOrNull()?.volume ?: 1f
        attachEndOfVideoListener(null)
    }

    fun startEndOfVideo(context: Context, fade: Boolean = true) {
        val ctx = context.applicationContext
        appContext = ctx
        // For end-of-video we still need a ticker for fading/indicator, but we don't have fixed end time
        // We'll observe player state and fire on STATE_ENDED.
        // Persist as TIMER with 0 to indicate indefinite.
        persist(ctx, true, Mode.END_OF_VIDEO, 0L, 0L, 0L, fade)
        _state.value = State(
            isActive = true,
            mode = Mode.END_OF_VIDEO,
            totalMs = 0L,
            remainingMs = -1L,
            endTimeElapsed = 0L,
            endTimeWall = 0L,
            fadeEnabled = fade,
            isFading = false
        )
        cancelAlarm(ctx)
        startEndOfVideoWatcher(ctx)
        hasFaded = false
        originalVolume = PlayerManager.get().playerOrNull()?.volume ?: 1f
    }

    fun extend(context: Context, extraMs: Long) {
        val ctx = context.applicationContext
        val cur = _state.value
        if (!cur.isActive) return
        if (cur.mode == Mode.END_OF_VIDEO) {
            // Convert to timer: remaining = position left + extra? Simpler: start 15 min timer
            startDuration(ctx, extraMs, cur.fadeEnabled)
            return
        }
        val newTotal = cur.totalMs + extraMs
        val newEndElapsed = cur.endTimeElapsed + extraMs
        val newEndWall = cur.endTimeWall + extraMs
        persist(ctx, true, Mode.TIMER, newTotal, newEndElapsed, newEndWall, cur.fadeEnabled)
        _state.value = cur.copy(
            totalMs = newTotal,
            endTimeElapsed = newEndElapsed,
            endTimeWall = newEndWall,
            remainingMs = (newEndElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        )
        scheduleAlarm(ctx, newEndWall)
    }

    fun cancel(context: Context? = null) {
        val ctx = (context ?: appContext)?.applicationContext ?: return
        tickerJob?.cancel()
        tickerJob = null
        attachEndOfVideoListener(null)
        cancelAlarm(ctx)
        clearPersist(ctx)
        // restore volume if we faded
        if (hasFaded) {
            try { PlayerManager.get().playerOrNull()?.volume = originalVolume.coerceIn(0f, 1f) } catch (_: Exception) {}
            hasFaded = false
        }
        _state.value = State(isActive = false)
    }

    /** Called by AlarmManager or ticker when time is up */
    fun onTimerFinished(context: Context? = null) {
        val ctx = (context ?: appContext)?.applicationContext
        tickerJob?.cancel()
        tickerJob = null
        attachEndOfVideoListener(null)
        ctx?.let { cancelAlarm(it); clearPersist(it) }
        // fade completion already at 0 volume, ensure pause
        doPause()
        // reset volume after short delay so next play is normal
        scope.launch {
            delay(400)
            try { PlayerManager.get().playerOrNull()?.volume = originalVolume.coerceIn(0f, 1f) } catch (_: Exception) {}
            hasFaded = false
        }
        _state.value = State(isActive = false)
    }

    fun onAlarmFired(context: Context) {
        appContext = context.applicationContext
        // Ensure timer is considered finished even if process was dead and state not restored
        onTimerFinished(context)
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun startTicker(context: Context) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val cur = _state.value
                if (!cur.isActive || cur.mode != Mode.TIMER) break
                val nowElapsed = SystemClock.elapsedRealtime()
                val remaining = (cur.endTimeElapsed - nowElapsed).coerceAtLeast(0L)
                val isFading = cur.fadeEnabled && remaining <= FADE_DURATION_MS && remaining > 0
                _state.value = cur.copy(remainingMs = remaining, isFading = isFading)
                if (cur.fadeEnabled && remaining in 1..FADE_DURATION_MS) {
                    applyFadeVolume(remaining)
                }
                if (remaining <= 0L) {
                    onTimerFinished(context)
                    break
                }
            }
        }
    }

    private fun startEndOfVideoWatcher(context: Context) {
        tickerJob?.cancel()
        attachEndOfVideoListener(context)
        // Also tick to provide fade when < 30s before end: need duration awareness
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val cur = _state.value
                if (!cur.isActive || cur.mode != Mode.END_OF_VIDEO) break
                if (cur.fadeEnabled) {
                    val p = PlayerManager.get().playerOrNull()
                    if (p != null && p.duration > 0 && p.currentPosition > 0) {
                        val remaining = (p.duration - p.currentPosition).coerceAtLeast(0L)
                        _state.value = cur.copy(remainingMs = remaining, isFading = remaining in 1..FADE_DURATION_MS)
                        if (remaining in 1..FADE_DURATION_MS) applyFadeVolume(remaining)
                    }
                }
            }
        }
    }

    private fun attachEndOfVideoListener(context: Context?) {
        videoEndListener?.let { l -> try { PlayerManager.get().playerOrNull()?.removeListener(l) } catch (_: Exception) {} }
        videoEndListener = null
        if (context == null) return
        val p = PlayerManager.get().playerOrNull() ?: return
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val cur = _state.value
                    if (cur.isActive && cur.mode == Mode.END_OF_VIDEO) {
                        onTimerFinished(context)
                    }
                }
            }
        }
        p.addListener(listener)
        videoEndListener = listener
    }

    private fun applyFadeVolume(remainingMs: Long) {
        val p = PlayerManager.get().playerOrNull() ?: return
        val ratio = (remainingMs.toFloat() / FADE_DURATION_MS).coerceIn(0f, 1f)
        // ease-out: keep a bit louder early, then drop fast
        val vol = ratio // linear for predictability
        try {
            p.volume = vol.coerceIn(0f, 1f)
            hasFaded = true
        } catch (_: Exception) {}
    }

    private fun doPause() {
        try {
            val p = PlayerManager.get().playerOrNull()
            if (p != null) {
                p.pause()
                p.volume = 0f
            } else {
                PlayerManager.get().pause()
            }
        } catch (_: Exception) {}
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persist(ctx: Context, active: Boolean, mode: Mode, total: Long, endElapsed: Long, endWall: Long, fade: Boolean) {
        prefs(ctx).edit()
            .putBoolean(KEY_ACTIVE, active)
            .putString(KEY_MODE, mode.name)
            .putLong(KEY_TOTAL, total)
            .putLong(KEY_END_ELAPSED, endElapsed)
            .putLong(KEY_END_WALL, endWall)
            .putBoolean(KEY_FADE, fade)
            .apply()
    }

    private fun clearPersist(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    private fun restore(ctx: Context) {
        val sp = prefs(ctx)
        val active = sp.getBoolean(KEY_ACTIVE, false)
        if (!active) {
            _state.value = State(isActive = false)
            return
        }
        val mode = try { Mode.valueOf(sp.getString(KEY_MODE, Mode.TIMER.name) ?: Mode.TIMER.name) } catch (_: Exception) { Mode.TIMER }
        val total = sp.getLong(KEY_TOTAL, 0L)
        val endElapsed = sp.getLong(KEY_END_ELAPSED, 0L)
        val endWall = sp.getLong(KEY_END_WALL, 0L)
        val fade = sp.getBoolean(KEY_FADE, true)

        if (mode == Mode.END_OF_VIDEO) {
            _state.value = State(isActive = true, mode = mode, totalMs = 0L, remainingMs = -1L, endTimeElapsed = 0L, endTimeWall = 0L, fadeEnabled = fade)
            startEndOfVideoWatcher(ctx)
            return
        }
        val remaining = (endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        if (remaining <= 0L || endElapsed == 0L) {
            clearPersist(ctx)
            _state.value = State(isActive = false)
            return
        }
        _state.value = State(
            isActive = true,
            mode = mode,
            totalMs = total,
            remainingMs = remaining,
            endTimeElapsed = endElapsed,
            endTimeWall = endWall,
            fadeEnabled = fade,
            isFading = fade && remaining <= FADE_DURATION_MS
        )
        scheduleAlarm(ctx, endWall)
        startTicker(ctx)
    }

    // ── AlarmManager ────────────────────────────────────────────────────────

    private fun scheduleAlarm(ctx: Context, endWall: Long) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = alarmPendingIntent(ctx)
            // Use ELAPSED_REALTIME_WAKEUP where possible for Doze correctness; but we also have wall time
            // Prefer RTC_WAKEUP with exact+idle for precise wall-clock end.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endWall, pi)
                } catch (se: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endWall, pi)
                }
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.RTC_WAKEUP, endWall, pi)
            }
        } catch (_: Exception) {}
    }

    private fun cancelAlarm(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(alarmPendingIntent(ctx))
        } catch (_: Exception) {}
    }

    private fun alarmPendingIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, SleepTimerReceiver::class.java).apply { action = SleepTimerReceiver.ACTION_FIRE }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(ctx, RC_ALARM, i, flags)
    }
}
