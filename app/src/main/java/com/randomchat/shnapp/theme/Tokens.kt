package com.randomchat.shnapp.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/**
 * Design tokens — single source of truth for spacing, radius, elevation, motion.
 * Replace inline literals across the app with these.
 */

// ── Spacing — 4dp grid ────────────────────────────────────────────────────
object Spacing {
    val xxs = 2.dp
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp
    val lg  = 16.dp
    val xl  = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val xxxxl = 40.dp
}

// ── Corner radius — 4 sizes max, no inline literals ───────────────────────
object Radius {
    val sm   = 8.dp
    val md   = 12.dp
    val lg   = 16.dp
    val xl   = 24.dp
    val pill = 999.dp
}

// ── Elevation — used for surface tint, not shadow ─────────────────────────
object Elevation {
    val level0 = 0.dp
    val level1 = 1.dp
    val level2 = 3.dp
    val level3 = 6.dp
    val level4 = 8.dp
    val level5 = 12.dp
}

// ── Motion — durations + easings + reusable specs ─────────────────────────
object Motion {

    // Durations (ms)
    const val durMicro      = 100
    const val durFast       = 150
    const val durMedium     = 220
    const val durSlow       = 320
    const val durEmphasized = 420
    const val durHero       = 600

    // Easings
    val standard    = FastOutSlowInEasing
    val decelerate  = LinearOutSlowInEasing
    val accelerate  = FastOutLinearInEasing
    val emphasized  = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // Spring specs
    fun <T> springDefault() = spring<T>(
        dampingRatio = 0.78f,
        stiffness    = Spring.StiffnessMediumLow
    )
    fun <T> springBouncy() = spring<T>(
        dampingRatio = 0.6f,
        stiffness    = Spring.StiffnessMedium
    )
    fun <T> springSnappy() = spring<T>(
        dampingRatio = 0.9f,
        stiffness    = Spring.StiffnessHigh
    )

    // Tween specs
    fun <T> fast()       = tween<T>(durFast, easing = standard)
    fun <T> medium()     = tween<T>(durMedium, easing = standard)
    fun <T> slow()       = tween<T>(durSlow, easing = standard)
    fun <T> emphasized() = tween<T>(durEmphasized, easing = emphasized)
    fun <T> hero()       = tween<T>(durHero, easing = emphasized)
}
