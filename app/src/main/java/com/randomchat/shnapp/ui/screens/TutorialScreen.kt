@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.randomchat.shnapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.AccentCyanGlow
import com.randomchat.shnapp.theme.CardSurface
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.OnlineGreen
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.PremiumGoldDim
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.ui.components.CyanButton
import com.randomchat.shnapp.utils.LocalHaptics
import kotlinx.coroutines.launch

@Composable
fun TutorialScreen(
    onComplete: () -> Unit
) {
    val haptics    = LocalHaptics.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope      = rememberCoroutineScope()

    // Settled-page haptic
    LaunchedEffect(pagerState.settledPage) { haptics.tick() }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Ambient background ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DeepSpace, GradientEnd)))
        )
        // Decorative blur blobs — color shifts per page
        val blobColor = when (pagerState.currentPage) {
            0    -> AccentCyan
            1    -> PremiumGold
            else -> AccentCyan
        }
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .blur(130.dp)
                .background(blobColor.copy(0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomStart)
                .blur(110.dp)
                .background(blobColor.copy(0.06f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar: Skip (hidden on last page)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                AnimatedVisibility(visible = pagerState.currentPage < 2) {
                    TextButton(onClick = { haptics.tick(); onComplete() }) {
                        Text("Skip", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 24.dp),
                pageSpacing = 0.dp,
                modifier = Modifier.weight(1f)
            ) { page ->
                // Parallax fade based on distance from current
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
                val alpha by animateFloatAsState(
                    targetValue = 1f - pageOffset * pageOffset * 0.6f,
                    animationSpec = tween(0),
                    label = "page_alpha"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.alpha = alpha }
                ) {
                    when (page) {
                        0    -> WelcomePage()
                        1    -> PremiumPage()
                        else -> SafetyPage()
                    }
                }
            }

            // ── Page indicators ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { i ->
                    val active = i == pagerState.currentPage
                    val width by animateFloatAsState(
                        targetValue = if (active) 24f else 8f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "dot_$i"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(width.dp)
                            .height(8.dp)
                            .background(
                                if (active) AccentCyan else SubtleBorder,
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            // ── Primary CTA ──────────────────────────────────────────────────
            Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                CyanButton(
                    text = if (pagerState.currentPage == 2) "⚡  Start Chatting" else "Next  →",
                    onClick = {
                        if (pagerState.currentPage == 2) {
                            haptics.heavy()
                            com.randomchat.shnapp.utils.Telemetry.tutorialCompleted()
                            onComplete()
                        } else {
                            haptics.click()
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage + 1,
                                    animationSpec = tween(420, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── Page 1: Welcome ─────────────────────────────────────────────────────────
@Composable
private fun WelcomePage() {
    // Pulsing logo glow
    val infiniteTransition = rememberInfiniteTransition(label = "welcome_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logo_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_a"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale)
                    .background(AccentCyanGlow.copy(alpha = glowAlpha), CircleShape)
                    .blur(40.dp)
            )
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(AccentCyan.copy(0.10f), CircleShape)
                    .border(1.dp, AccentCyan.copy(0.30f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ChatBubble, null, tint = AccentCyan, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(Modifier.height(28.dp))

        StaggeredFadeIn(delayMs = 80) {
            Text(
                "Talk to anyone,\nanonymously",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(10.dp))

        StaggeredFadeIn(delayMs = 200) {
            Text(
                "Match with random strangers worldwide.\nNo signup. No trace left.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }

        Spacer(Modifier.height(28.dp))

        StaggeredFadeIn(delayMs = 340) { BulletRow(Icons.Default.Lock, "100% anonymous — no phone, no email", AccentCyan) }
        Spacer(Modifier.height(10.dp))
        StaggeredFadeIn(delayMs = 460) { BulletRow(Icons.Default.Bolt, "Instant matching", PremiumGold) }
        Spacer(Modifier.height(10.dp))
        StaggeredFadeIn(delayMs = 580) {
            // Online chip with pulsing green dot
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.5f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "dot_a"
            )
            Row(
                modifier = Modifier
                    .background(OnlineGreen.copy(0.10f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(OnlineGreen.copy(dotAlpha), CircleShape)
                )
                Text(
                    "1,240+ strangers online right now",
                    color = OnlineGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─── Page 2: Premium showcase ────────────────────────────────────────────────
@Composable
private fun PremiumPage() {
    val infiniteTransition = rememberInfiniteTransition(label = "gold_sweep")
    val sparkleRot by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing)),
        label = "sparkle_rot"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated gold sparkle
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(PremiumGold.copy(0.15f), CircleShape)
                    .blur(20.dp)
            )
            Icon(
                Icons.Default.AutoAwesome,
                null,
                tint = PremiumGold,
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer { rotationZ = sparkleRot }
            )
        }

        Spacer(Modifier.height(16.dp))

        StaggeredFadeIn(delayMs = 60) {
            Text(
                "Go further with Premium",
                color = PremiumGold,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                letterSpacing = 0.3.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        // 2x3 feature grid
        val features = listOf(
            Triple(Icons.Default.PhotoCamera, "Photos", "Send images"),
            Triple(Icons.Default.Mic,         "Voice", "Record audio notes"),
            Triple(Icons.Default.AddReaction, "Reactions", "React with emojis"),
            Triple(Icons.Default.Visibility,  "Live Preview", "See typing in real-time"),
            Triple(Icons.Default.Save,        "Save Chats", "Keep best moments"),
            Triple(Icons.Default.Block,       "No Ads", "Ad-free experience"),
        )
        features.chunked(2).forEachIndexed { rowIdx, pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEachIndexed { colIdx, (icon, title, sub) ->
                    val totalIdx = rowIdx * 2 + colIdx
                    StaggeredFadeIn(
                        delayMs = 180 + totalIdx * 90,
                        modifier = Modifier.weight(1f)
                    ) {
                        FeatureTile(icon, title, sub)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        StaggeredFadeIn(delayMs = 800) {
            Text(
                "Or earn free credits by watching short ads",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FeatureTile(icon: ImageVector, title: String, sub: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(CardSurface, Color(0xFF1A1505))),
                RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(PremiumGold.copy(0.35f), PremiumGoldDim.copy(0.15f))),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = PremiumGold, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, color = TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
    }
}

// ─── Page 3: Safety ──────────────────────────────────────────────────────────
@Composable
private fun SafetyPage() {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_pulse")
    val shieldScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shield_s"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shield_g"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(shieldScale)
                    .background(AccentCyan.copy(glowAlpha), CircleShape)
                    .blur(36.dp)
            )
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(AccentCyan.copy(0.12f), CircleShape)
                    .border(1.dp, AccentCyan.copy(0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Shield, null, tint = AccentCyan, modifier = Modifier.size(44.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        StaggeredFadeIn(delayMs = 60) {
            Text(
                "Built for your safety",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(8.dp))

        StaggeredFadeIn(delayMs = 180) {
            Text(
                "Stay private. Stay in control.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(28.dp))

        StaggeredFadeIn(delayMs = 320) { BulletRow(Icons.Default.Flag, "Report anyone in one tap", Color(0xFFFF6B6B)) }
        Spacer(Modifier.height(10.dp))
        StaggeredFadeIn(delayMs = 440) { BulletRow(Icons.Default.Lock, "App Lock with device PIN (Premium)", PremiumGold) }
        Spacer(Modifier.height(10.dp))
        StaggeredFadeIn(delayMs = 560) { BulletRow(Icons.Default.Timer, "Chats auto-expire — nothing stored", AccentCyan) }
        Spacer(Modifier.height(10.dp))
        StaggeredFadeIn(delayMs = 680) { BulletRow(Icons.Default.VerifiedUser, "AI-moderated content", OnlineGreen) }
    }
}

// ─── Shared building blocks ──────────────────────────────────────────────────
@Composable
private fun BulletRow(icon: ImageVector, text: String, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface.copy(0.6f), RoundedCornerShape(12.dp))
            .border(1.dp, SubtleBorder.copy(0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(tint.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(text, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Staggered slide+fade entrance — perceived smoothness via delay. */
@Composable
private fun StaggeredFadeIn(
    delayMs: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 4 },
        modifier = modifier
    ) {
        content()
    }
}
