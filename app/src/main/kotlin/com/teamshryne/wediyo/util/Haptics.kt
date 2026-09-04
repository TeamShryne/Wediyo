package com.teamshryne.wediyo.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Simple, human haptics. Use Compose haptics for taps/toggles,
 * View-level constants for long-press/context, vibrator for success.
 */
object Haptics {

    fun tap(view: View) {
        try { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Exception) {}
    }

    fun toggle(view: View, enabled: Boolean) {
        try {
            if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            else view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } catch (_: Exception) {
            tap(view)
        }
    }

    fun longPress(view: View) {
        try { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } catch (_: Exception) {}
    }

    fun success(context: Context) {
        try {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vib?.let {
                if (!it.hasVibrator()) return
                if (Build.VERSION.SDK_INT >= 26) {
                    it.vibrate(VibrationEffect.createOneShot(24, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") it.vibrate(24)
                }
            }
        } catch (_: Exception) {}
    }
}

@Composable
fun rememberHaptics(): HapticPair {
    val fb: HapticFeedback = LocalHapticFeedback.current
    val view: View = LocalView.current
    return remember(fb, view) {
        HapticPair(
            tap = { try { fb.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {} },
            toggle = { on -> Haptics.toggle(view, on) },
            longPress = { Haptics.longPress(view) },
            confirm = { Haptics.success(view.context); try { fb.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Exception) {} }
        )
    }
}

data class HapticPair(
    val tap: () -> Unit,
    val toggle: (Boolean) -> Unit,
    val longPress: () -> Unit,
    val confirm: () -> Unit
)
