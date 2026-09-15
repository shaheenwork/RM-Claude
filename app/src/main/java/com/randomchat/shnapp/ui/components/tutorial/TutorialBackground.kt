package com.randomchat.shnapp.ui.components.tutorial

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.BrandGradients
import com.randomchat.shnapp.theme.BrandViolet

/**
 * Layered animated background for the tutorial / onboarding flow.
 *
 * Layers (back → front):
 *   0. Deep brand gradient
 *   1. Center radial accent glow (mood light — changes per page via [accentColor])
 *   2. Top-right pink blob — drifts slowly (7.2 s cycle)
 *   3. Bottom-left violet blob — drifts on a different rhythm (6.3 s cycle)
 *   4. Edge vignette
 *   [content]
 *
 * The blobs animate alpha and position independently so the background
 * feels alive but never distracts from content.
 */
@Composable
fun AuroraBackground(
    accentColor: Color = AccentCyan,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "aurora_bg")

    // Blob 1 — pink, top-right area
    val b1x by inf.animateFloat(-18f, 18f,
        infiniteRepeatable(tween(7200, easing = FastOutSlowInEasing), RepeatMode.Reverse), "b1x")
    val b1y by inf.animateFloat(-12f, 14f,
        infiniteRepeatable(tween(5400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "b1y")
    val b1a by inf.animateFloat(0.24f, 0.44f,
        infiniteRepeatable(tween(4100, easing = FastOutSlowInEasing), RepeatMode.Reverse), "b1a")

    // Blob 2 — violet, bottom-left area (different rhythm = natural phase offset)
    val b2x by inf.animateFloat(14f, -22f,
        infiniteRepeatable(tween(6300, easing = FastOutSlowInEasing), RepeatMode.Reverse), "b2x")
    val b2y by inf.animateFloat(18f, -10f,
        infiniteRepeatable(tween(7800, easing = FastOutSlowInEasing), RepeatMode.Reverse), "b2y")
    val b2a by inf.animateFloat(0.17f, 0.34f,
        infiniteRepeatable(tween(5300, easing = FastOutSlowInEasing), RepeatMode.Reverse), "b2a")

    Box(modifier = modifier.fillMaxSize()) {
        // 0 — base gradient
        Box(Modifier.fillMaxSize().background(BrandGradients.backgroundDeep))

        // 1 — center mood glow (accentColor drives tint; transitions with page)
        Box(
            Modifier
                .size(440.dp)
                .align(Alignment.Center)
                .offset((-30).dp, 80.dp)
                .blur(130.dp)
                .background(
                    Brush.radialGradient(listOf(accentColor.copy(0.07f), Color.Transparent)),
                    CircleShape
                )
        )

        // 2 — pink blob (top-right, drifting)
        Box(
            Modifier
                .size(310.dp)
                .align(Alignment.TopEnd)
                .offset(b1x.dp + (-50).dp, b1y.dp + (-60).dp)
                .blur(105.dp)
                .background(AccentCyan.copy(b1a), CircleShape)
        )

        // 3 — violet blob (bottom-left, drifting)
        Box(
            Modifier
                .size(260.dp)
                .align(Alignment.BottomStart)
                .offset(b2x.dp + (-10).dp, b2y.dp)
                .blur(95.dp)
                .background(BrandViolet.copy(b2a), CircleShape)
        )

        // 4 — edge vignette (depth + focus)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0x55000000)),
                        radius = 1500f
                    )
                )
        )

        content()
    }
}
