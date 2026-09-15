package com.randomchat.shnapp.theme

import androidx.compose.ui.graphics.Brush

/**
 * Reusable brand gradients. Use across CTAs, outgoing bubbles, match-core,
 * any moment that needs the Midnight Subdued signature.
 */
object BrandGradients {

    /** Primary brand — pink → violet. Used for hero CTAs, outgoing bubbles, send button. */
    val primary: Brush
        get() = Brush.linearGradient(
            colors = listOf(AccentCyan, BrandViolet)  // pink → violet
        )

    /** Primary 135° — diagonal, slightly more dramatic. */
    val primary135: Brush
        get() = Brush.linearGradient(
            colors = listOf(AccentCyan, BrandViolet),
            start  = androidx.compose.ui.geometry.Offset(0f, 0f),
            end    = androidx.compose.ui.geometry.Offset(800f, 800f)
        )

    /** Aurora — blue → violet. Avatars, secondary actions. */
    val aurora: Brush
        get() = Brush.linearGradient(
            colors = listOf(AuroraBlue, BrandViolet)
        )

    /** Ember — amber → orange. Premium plans, streaks. */
    val ember: Brush
        get() = Brush.linearGradient(
            colors = listOf(PremiumGold, PremiumGoldDim)
        )

    /** Soft purple-tinged background gradient. */
    val backgroundDeep: Brush
        get() = Brush.verticalGradient(
            colors = listOf(GradientStart, GradientMid, GradientEnd)
        )
}
