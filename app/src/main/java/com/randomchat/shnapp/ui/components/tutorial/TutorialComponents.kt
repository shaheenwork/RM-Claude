package com.randomchat.shnapp.ui.components.tutorial

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.BrandGradients
import com.randomchat.shnapp.theme.CardSurface
import com.randomchat.shnapp.theme.OnlineGreen
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextPrimary
import kotlinx.coroutines.delay

// ── GlowCTAButton ─────────────────────────────────────────────────────────────

/**
 * Primary onboarding CTA. Upgrades [CyanButton] with:
 *   • Shimmer sweep (2.6 s loop, subtle white highlight)
 *   • Spring press-scale (0.965× — tactile without being dramatic)
 *
 * Shimmer is intentionally slow and low-contrast — premium, not cheap.
 */
@Composable
fun GlowCTAButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium),
        label = "cta_scale"
    )

    val inf = rememberInfiniteTransition(label = "cta_shimmer")
    val shimmerX by inf.animateFloat(
        initialValue = -600f, targetValue = 700f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer_x"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(BrandGradients.primary, RoundedCornerShape(28.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 17.dp)
    ) {
        // Shimmer overlay — clipped to button shape
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.13f),
                            Color.Transparent
                        ),
                        start = Offset(shimmerX, 0f),
                        end   = Offset(shimmerX + 240f, 70f)
                    )
                )
        )
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 0.3.sp
        )
    }
}

// ── GlassCard ─────────────────────────────────────────────────────────────────

/**
 * Frosted-glass surface. Semi-transparent dark bg + gradient hairline border.
 * Use for feature showcases and content grouping.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(CardSurface.copy(alpha = 0.42f), RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.03f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp),
        content = content
    )
}

// ── GlowChip ──────────────────────────────────────────────────────────────────

/**
 * Status/label chip with a pulsing colored dot.
 * [dotColor] drives both the dot and the label text.
 * [chipBg] should be [dotColor].copy(~0.10f).
 */
@Composable
fun GlowChip(
    text: String,
    dotColor: Color,
    chipBg: Color,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "chip_dot")
    val dotAlpha by inf.animateFloat(
        initialValue = 0.40f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot_a"
    )

    Row(
        modifier = modifier
            .background(chipBg, RoundedCornerShape(99.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dotColor.copy(alpha = dotAlpha), CircleShape)
        )
        Text(text, color = dotColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ── TutorialPageIndicator ─────────────────────────────────────────────────────

/**
 * Morphing pill dots. Active dot expands to 24 dp wide with brand gradient.
 * Spring width animation feels physical — not just tween.
 */
@Composable
fun TutorialPageIndicator(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { i ->
            val active = i == currentPage
            val dotWidth by animateFloatAsState(
                targetValue = if (active) 24f else 7f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                label = "dot_w_$i"
            )
            val dotAlpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.28f,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                label = "dot_a_$i"
            )
            Box(
                modifier = Modifier
                    .width(dotWidth.dp)
                    .height(7.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .background(
                        if (active) BrandGradients.primary
                        else Brush.horizontalGradient(listOf(SubtleBorder, SubtleBorder)),
                        RoundedCornerShape(99.dp)
                    )
            )
        }
    }
}

// ── AnimatedCheckRow ──────────────────────────────────────────────────────────

/**
 * Check-list row for the Frictionless page.
 * Appears with a spring-pop scale entrance after [delayMs].
 * The spring overshoot gives it a satisfying "tick" feel.
 */
@Composable
fun AnimatedCheckRow(
    text: String,
    delayMs: Int,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }

    val rowScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "check_scale_$text"
    )
    val rowAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "check_alpha_$text"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = rowScale; scaleY = rowScale; alpha = rowAlpha }
            .background(CardSurface.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .border(1.dp, SubtleBorder.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(OnlineGreen.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = OnlineGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ── VoiceEqualizer ────────────────────────────────────────────────────────────

/**
 * Animated 4-bar equalizer — used inside the Voice feature tile.
 * Each bar animates at a different speed to simulate live audio.
 */
@Composable
fun VoiceEqualizer(
    barColor: Color = AccentCyan,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "eq")
    val h1 by inf.animateFloat(8f, 22f,
        infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "eq_h1")
    val h2 by inf.animateFloat(14f, 30f,
        infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse), "eq_h2")
    val h3 by inf.animateFloat(6f, 20f,
        infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "eq_h3")
    val h4 by inf.animateFloat(10f, 26f,
        infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse), "eq_h4")

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(h1, h2, h3, h4).forEach { h ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(h.dp)
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }
    }
}
