@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.randomchat.shnapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.AccentCyanGlow
import com.randomchat.shnapp.theme.AuroraBlue
import com.randomchat.shnapp.theme.BrandGradients
import com.randomchat.shnapp.theme.BrandViolet
import com.randomchat.shnapp.theme.BrandVioletGlow
import com.randomchat.shnapp.theme.CardSurface
import com.randomchat.shnapp.theme.OnlineGreen
import com.randomchat.shnapp.theme.PinkSoft
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.ui.components.tutorial.AnimatedCheckRow
import com.randomchat.shnapp.ui.components.tutorial.AuroraBackground
import com.randomchat.shnapp.ui.components.tutorial.GlassCard
import com.randomchat.shnapp.ui.components.tutorial.GlowCTAButton
import com.randomchat.shnapp.ui.components.tutorial.GlowChip
import com.randomchat.shnapp.ui.components.tutorial.TutorialPageIndicator
import com.randomchat.shnapp.ui.components.tutorial.VoiceEqualizer
import com.randomchat.shnapp.utils.LocalHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Orchestrator ──────────────────────────────────────────────────────────────

@Composable
fun TutorialScreen(onComplete: () -> Unit) {
    val haptics    = LocalHaptics.current
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope      = rememberCoroutineScope()

    LaunchedEffect(pagerState.settledPage) { haptics.tick() }

    // Accent color follows page — drives AuroraBackground mood glow
    val pageAccent = when (pagerState.currentPage) {
        0    -> AccentCyan
        1    -> OnlineGreen
        2    -> PinkSoft
        else -> BrandViolet
    }

    AuroraBackground(accentColor = pageAccent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar — Skip fades out on last page
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                AnimatedVisibility(
                    visible = pagerState.currentPage < 3,
                    enter = fadeIn(tween(200)),
                    exit  = fadeOut(tween(200))
                ) {
                    TextButton(onClick = { haptics.tick(); onComplete() }) {
                        Text("Skip", color = TextMuted, fontSize = 13.sp, letterSpacing = 0.3.sp)
                    }
                }
            }

            // Pager — parallax fade between pages
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 24.dp),
                pageSpacing = 0.dp,
                modifier = Modifier.weight(1f)
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) +
                        pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
                val alpha by animateFloatAsState(
                    targetValue = 1f - pageOffset * pageOffset * 0.65f,
                    animationSpec = tween(0),
                    label = "page_alpha"
                )
                val translateY by animateFloatAsState(
                    targetValue = pageOffset * 18f,
                    animationSpec = tween(0),
                    label = "page_ty"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.alpha      = alpha
                            translationY    = translateY.dp.toPx()
                        }
                ) {
                    when (page) {
                        0    -> HookPage()
                        1    -> FrictionlessPage()
                        2    -> ExpressPage()
                        else -> ControlPage()
                    }
                }
            }

            // Page indicator
            TutorialPageIndicator(
                currentPage = pagerState.currentPage,
                pageCount   = 4,
                modifier    = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 14.dp)
            )

            // CTA
            Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
                GlowCTAButton(
                    text = if (pagerState.currentPage == 3) "⚡  Start Chatting" else "Next  →",
                    onClick = {
                        if (pagerState.currentPage == 3) {
                            haptics.heavy()
                            com.randomchat.shnapp.utils.Telemetry.tutorialCompleted()
                            onComplete()
                        } else {
                            haptics.click()
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage + 1,
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

// ── Page 1: Hook — Identity ───────────────────────────────────────────────────
//
// Goal: instant emotional hook. Mysterious, exciting, modern.
// Hero: floating message orb with two orbiting particles.
// Supporting: "online now" chip with pulsing dot.

@Composable
private fun HookPage() {
    val inf = rememberInfiniteTransition(label = "hook")

    // Hero float — slow vertical oscillation
    val floatY by inf.animateFloat(-7f, 7f,
        infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), "float_y")

    // Glow pulse behind orb
    val glowAlpha by inf.animateFloat(0.25f, 0.60f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), "glow_a")
    val glowScale by inf.animateFloat(0.92f, 1.08f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), "glow_s")

    // Orbit angles — two particles at different speeds + radii
    val orbit1 by inf.animateFloat(0f, 360f,
        infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), "orb1")
    val orbit2 by inf.animateFloat(180f, 540f,
        infiniteRepeatable(tween(6400, easing = LinearEasing), RepeatMode.Restart), "orb2")

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Hero ──────────────────────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(180.dp)
                .graphicsLayer { translationY = floatY.dp.toPx() }
        ) {
            // Pulse glow ring
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .graphicsLayer { scaleX = glowScale; scaleY = glowScale; alpha = glowAlpha }
                    .background(AccentCyanGlow, CircleShape)
                    .run { this } // no-op — blur via outer
            )
            // Orb core
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(AccentCyan.copy(0.10f), CircleShape)
                    .border(1.dp, AccentCyan.copy(0.32f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ChatBubble, null, tint = AccentCyan, modifier = Modifier.size(46.dp))
            }
            // Orbiting particle 1 — aurora blue
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        translationX = cos(orbit1 * PI / 180.0).toFloat() * 76.dp.toPx()
                        translationY = sin(orbit1 * PI / 180.0).toFloat() * 76.dp.toPx()
                    }
                    .background(AuroraBlue, CircleShape)
            )
            // Orbiting particle 2 — gold, inner orbit, opposite direction feel
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        translationX = cos(orbit2 * PI / 180.0).toFloat() * 58.dp.toPx()
                        translationY = sin(orbit2 * PI / 180.0).toFloat() * 58.dp.toPx()
                    }
                    .background(PremiumGold, CircleShape)
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Headlines ─────────────────────────────────────────────────────────
        Reveal(delayMs = 80) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Talk to anyone,",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "anonymously",
                    color = PinkSoft,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Reveal(delayMs = 180) {
            Text(
                "Real strangers worldwide. No name, no trace.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }

        Spacer(Modifier.height(28.dp))

        Reveal(delayMs = 280) {
            TutorialBulletRow(Icons.Default.Lock, "100% anonymous — no phone, no email", AccentCyan)
        }
        Spacer(Modifier.height(9.dp))
        Reveal(delayMs = 380) {
            TutorialBulletRow(Icons.Default.Bolt, "Instant matching with a tap", PremiumGold)
        }
        Spacer(Modifier.height(14.dp))

        Reveal(delayMs = 500) {
            GlowChip(
                text      = "1,240+ strangers online right now",
                dotColor  = OnlineGreen,
                chipBg    = OnlineGreen.copy(0.10f)
            )
        }
    }
}

// ── Page 2: Frictionless ──────────────────────────────────────────────────────
//
// Goal: remove signup friction psychologically before it's even mentioned.
// Hero: sequential spring-pop checklist — each row hits like a satisfying tick.

@Composable
private fun FrictionlessPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 36.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Overline
        Reveal(delayMs = 40) {
            Text(
                "ZERO SETUP",
                color = TextMuted,
                fontSize = 11.sp,
                letterSpacing = 2.8.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(14.dp))

        // Headlines (left-aligned — intentional editorial break from centered pages)
        Reveal(delayMs = 100) {
            Column {
                Text(
                    "Start chatting in",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "seconds.",
                    color = PinkSoft,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    letterSpacing = (-0.4).sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Reveal(delayMs = 180) {
            Text(
                "No account. No waiting. Just open and go.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }

        Spacer(Modifier.height(30.dp))

        // Staggered spring-pop check rows
        AnimatedCheckRow(text = "No email",         delayMs = 320)
        Spacer(Modifier.height(10.dp))
        AnimatedCheckRow(text = "No phone number",  delayMs = 480)
        Spacer(Modifier.height(10.dp))
        AnimatedCheckRow(text = "No signup, ever",  delayMs = 640)
    }
}

// ── Page 3: Express — Features ────────────────────────────────────────────────
//
// Goal: reframe expectations — this isn't just text chat.
// Hero: 2×2 glass feature grid. Voice tile has live equalizer. Tiles have tap feedback.

@Composable
private fun ExpressPage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Reveal(delayMs = 60) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Say it",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "your way.",
                    color = PinkSoft,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif,
                    fontSize = 34.sp,
                    letterSpacing = (-0.4).sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Reveal(delayMs = 140) {
            Text(
                "Photos, voice, GIFs, reactions —\ngo beyond plain text.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }

        Spacer(Modifier.height(28.dp))

        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Reveal(delayMs = 220, modifier = Modifier.weight(1f)) {
                ExpressTile(
                    icon    = Icons.Default.PhotoCamera,
                    label   = "Photos",
                    accent  = AccentCyan
                )
            }
            Reveal(delayMs = 310, modifier = Modifier.weight(1f)) {
                // Voice tile — equalizer instead of static icon
                ExpressTile(
                    icon   = null,
                    label  = "Voice",
                    accent = BrandViolet
                ) {
                    VoiceEqualizer(barColor = BrandViolet, modifier = Modifier.height(32.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Reveal(delayMs = 400, modifier = Modifier.weight(1f)) {
                ExpressTile(
                    icon   = Icons.Default.Gif,
                    label  = "GIFs",
                    accent = PinkSoft
                )
            }
            Reveal(delayMs = 490, modifier = Modifier.weight(1f)) {
                ExpressTile(
                    icon   = Icons.Default.AddReaction,
                    label  = "React",
                    accent = OnlineGreen
                )
            }
        }
    }
}

// ── Page 4: Control + Final CTA ───────────────────────────────────────────────
//
// Goal: trust + conversion. User feels safe. Ending is decisive.
// Hero: lock icon surrounded by two concentric pulsing rings (different speeds = phase offset).

@Composable
private fun ControlPage() {
    val inf = rememberInfiniteTransition(label = "control")

    // Ring 1 — inner, faster
    val r1Scale by inf.animateFloat(0.92f, 1.08f,
        infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), "r1s")
    val r1Alpha by inf.animateFloat(0.55f, 0.18f,
        infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), "r1a")

    // Ring 2 — outer, slower (different duration = natural phase drift)
    val r2Scale by inf.animateFloat(0.88f, 1.12f,
        infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "r2s")
    val r2Alpha by inf.animateFloat(0.35f, 0.10f,
        infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "r2a")

    // Lock float
    val floatY by inf.animateFloat(-5f, 5f,
        infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), "lock_float")

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Hero ──────────────────────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(170.dp)
                .graphicsLayer { translationY = floatY.dp.toPx() }
        ) {
            // Outer ring
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer { scaleX = r2Scale; scaleY = r2Scale; alpha = r2Alpha }
                    .border(1.dp, BrandViolet.copy(r2Alpha), CircleShape)
            )
            // Inner ring
            Box(
                modifier = Modifier
                    .size(122.dp)
                    .graphicsLayer { scaleX = r1Scale; scaleY = r1Scale; alpha = r1Alpha }
                    .border(1.5.dp, BrandViolet.copy(r1Alpha), CircleShape)
            )
            // Violet glow
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .background(BrandVioletGlow.copy(0.25f), CircleShape)
            )
            // Core
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(BrandViolet.copy(0.12f), CircleShape)
                    .border(2.dp, BrandViolet.copy(0.52f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, null, tint = BrandViolet, modifier = Modifier.size(44.dp))
            }
        }

        Spacer(Modifier.height(26.dp))

        // ── Headlines ─────────────────────────────────────────────────────────
        Reveal(delayMs = 60) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "You stay",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "in control.",
                    color = PinkSoft,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif,
                    fontSize = 34.sp,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Reveal(delayMs = 160) {
            Text(
                "Lock the app, save chats worth keeping,\nreport anyone in one tap.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }

        Spacer(Modifier.height(26.dp))

        Reveal(delayMs = 280) {
            TutorialBulletRow(Icons.Default.Lock, "App Lock with device PIN", BrandViolet)
        }
        Spacer(Modifier.height(9.dp))
        Reveal(delayMs = 380) {
            TutorialBulletRow(Icons.Default.Save, "Save chats worth keeping", AccentCyan)
        }
        Spacer(Modifier.height(9.dp))
        Reveal(delayMs = 480) {
            TutorialBulletRow(Icons.Default.Flag, "Report anyone in one tap", Color(0xFFFF6B6B))
        }
    }
}

// ── Private building blocks ───────────────────────────────────────────────────

/**
 * Feature tile for the Express page.
 * Tap → spring-scale press feedback.
 * [heroContent] overrides the default icon box (used by Voice tile for equalizer).
 */
@Composable
private fun ExpressTile(
    icon: ImageVector?,
    label: String,
    accent: Color,
    heroContent: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val tileScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium),
        label = "tile_scale_$label"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = tileScale; scaleY = tileScale }
            .background(CardSurface.copy(0.48f), RoundedCornerShape(18.dp))
            .border(1.dp, SubtleBorder, RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null) { }
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (heroContent != null) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                heroContent()
            }
        } else if (icon != null) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Icon + label bullet row — shared across Hook and Control pages.
 */
@Composable
private fun TutorialBulletRow(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CardSurface.copy(0.55f), RoundedCornerShape(13.dp))
            .border(1.dp, SubtleBorder.copy(0.45f), RoundedCornerShape(13.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(tint.copy(0.13f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(text, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Staggered fade + rise entrance with a slightly longer duration than the
 * global StaggeredFadeIn — chosen for the editorial pacing of onboarding.
 */
@Composable
private fun Reveal(
    delayMs: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                  slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 5 },
        modifier = modifier
    ) { content() }
}
