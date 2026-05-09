package com.randomchat.shnapp.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.randomchat.shnapp.theme.AccentCyan

@Composable
fun PulseLoader(
    modifier: Modifier = Modifier,
    color: Color = AccentCyan,
    size: Float = 80f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "s1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "s2"
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "s3"
    )

    Box(modifier = modifier.size((size * 2).dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size((size * 2).dp)) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val maxRadius = size
            drawCircle(color.copy(alpha = (1f - scale1) * 0.5f), radius = maxRadius * scale1, center = center)
            drawCircle(color.copy(alpha = (1f - scale2) * 0.5f), radius = maxRadius * scale2, center = center)
            drawCircle(color.copy(alpha = (1f - scale3) * 0.5f), radius = maxRadius * scale3, center = center)
            // Core dot
            drawCircle(color, radius = 8f, center = center)
        }
    }
}

@Composable
fun DotsLoader(
    modifier: Modifier = Modifier,
    color: Color = AccentCyan
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val offsets = (0..2).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = -12f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = i * 150, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "d$i"
        )
    }

    Canvas(modifier = modifier.size(48.dp, 20.dp)) {
        val dotRadius = 5f
        val spacing = size.width / 4
        offsets.forEachIndexed { i, offset ->
            drawCircle(
                color = color,
                radius = dotRadius,
                center = Offset(spacing * (i + 1), size.height / 2 + offset.value)
            )
        }
    }
}
