package com.randomchat.shnapp.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Named haptic vocabulary for the app.
 *
 * Use semantically (tick = light, click = standard, heavy = strong,
 * success = two crisp ticks, match = celebratory pattern, warning = sharp pair).
 * Falls back to amplitude-based vibration on API < 29.
 * No-ops on devices without a vibrator.
 */
class Haptics private constructor(private val vibrator: Vibrator?) {

    /** Subtle hint — toggle, typing, reaction picker open. ~10ms */
    fun tick() = predefined(VibrationEffect.EFFECT_TICK, fallbackMs = 10L)

    /** Standard tap — send, primary button, select. ~25ms */
    fun click() = predefined(VibrationEffect.EFFECT_CLICK, fallbackMs = 25L)

    /** Strong tap — major action / commitment. ~50ms */
    fun heavy() = predefined(VibrationEffect.EFFECT_HEAVY_CLICK, fallbackMs = 50L)

    /** Two crisp ticks — confirmation of a completed action (save, unlock). */
    fun success() = waveform(longArrayOf(0, 25, 55, 30))

    /** Celebratory pattern — match found, premium activated. */
    fun match() = waveform(longArrayOf(0, 35, 70, 50, 70, 40))

    /** Sharp double — error / disconnect / forced action. */
    fun warning() = waveform(longArrayOf(0, 45, 70, 45))

    // ── Internals ──────────────────────────────────────────────────────────────
    private fun predefined(effectId: Int, fallbackMs: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(effectId))
        } else {
            v.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun waveform(pattern: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    companion object {
        fun create(context: Context): Haptics {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return Haptics(v)
        }

        /** No-op fallback when not provided. */
        val NoOp = Haptics(null)
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics.NoOp }

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics.create(context) }
}
