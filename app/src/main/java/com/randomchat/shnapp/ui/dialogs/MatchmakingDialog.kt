package com.randomchat.shnapp.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.AuroraBlue
import com.randomchat.shnapp.theme.BrandGradients
import com.randomchat.shnapp.theme.BrandViolet
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.GradientMid
import com.randomchat.shnapp.theme.PinkSoft
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.utils.LocalHaptics
import kotlinx.coroutines.delay

private val ORBIT_EMOJIS = listOf("🌊", "🔥", "🌙", "✨", "🎮", "🎵", "🌸", "🦋", "☕", "🎬")

private val STATUS_TEXTS = listOf(
    "Scanning the night",
    "Looking for someone interesting",
    "Preparing a private room",
    "Almost there"
)

@Composable
fun MatchmakingDialog(
    visible: Boolean,
    onCancel: () -> Unit
) {
    if (!visible) return

    val haptics = LocalHaptics.current
    var statusIndex by remember { mutableIntStateOf(0) }

    // 3 orbit emojis that swap occasionally
    var orbitA by remember { mutableIntStateOf(0) }
    var orbitB by remember { mutableIntStateOf(3) }
    var orbitC by remember { mutableIntStateOf(6) }

    val transition = rememberInfiniteTransition(label = "match")

    // Core breathe — scale + glow
    val coreScale by transition.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "core_scale"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.30f, targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_a"
    )
    // Expanding ring — single value, drawn 3× with phase offsets
    val ringProgress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "ring"
    )
    // Slow orbit rotation
    val orbitAngle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "orbit"
    )

    // Status cycle
    LaunchedEffect(Unit) {
        while (true) {
            delay(1900)
            statusIndex = (statusIndex + 1) % STATUS_TEXTS.size
        }
    }
    // Orbit emoji shuffle + soft haptic
    LaunchedEffect(Unit) {
        while (true) {
            delay(1100)
            when ((0..2).random()) {
                0 -> orbitA = (orbitA + 1) % ORBIT_EMOJIS.size
                1 -> orbitB = (orbitB + 1) % ORBIT_EMOJIS.size
                else -> orbitC = (orbitC + 1) % ORBIT_EMOJIS.size
            }
            haptics.tick()
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DeepSpace, GradientMid, GradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // ── Pulsing core + rings + orbit avatars ──────────────────────
                Box(
                    modifier = Modifier.size(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 3 expanding rings, phase-offset
                    listOf(0f, 0.33f, 0.66f).forEach { phase ->
                        val p = (ringProgress + phase) % 1f
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .graphicsLayer {
                                    val s = 1f + p * 1.2f
                                    scaleX = s; scaleY = s
                                    alpha = (1f - p) * 0.5f
                                }
                                .border(1.dp, AccentCyan.copy(alpha = 0.4f), CircleShape)
                        )
                    }

                    // Soft glow halo
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .graphicsLayer { alpha = glowAlpha }
                            .background(
                                Brush.radialGradient(
                                    listOf(AccentCyan.copy(0.4f), Color.Transparent)
                                ),
                                CircleShape
                            )
                            .blur(30.dp)
                    )

                    // Core
                    Box(
                        modifier = Modifier
                            .size(108.dp)
                            .graphicsLayer { scaleX = coreScale; scaleY = coreScale }
                            .background(BrandGradients.primary, CircleShape)
                            .border(2.dp, Color.White.copy(0.10f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✨", fontSize = 40.sp)
                    }

                    // 3 orbit avatars — placed at fixed positions, gently rotating wrapper
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .graphicsLayer { rotationZ = orbitAngle },
                        contentAlignment = Alignment.Center
                    ) {
                        OrbitAvatar(
                            emoji = ORBIT_EMOJIS[orbitA],
                            tint = AuroraBlue,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        OrbitAvatar(
                            emoji = ORBIT_EMOJIS[orbitB],
                            tint = PremiumGold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-12).dp, y = (-12).dp)
                        )
                        OrbitAvatar(
                            emoji = ORBIT_EMOJIS[orbitC],
                            tint = BrandViolet,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = 12.dp, y = (-12).dp)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Headline — Inter + serif italic accent
                Text(
                    "Searching for someone",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    "interesting…",
                    color = PinkSoft,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif,
                    fontSize = 24.sp,
                    letterSpacing = (-0.3).sp
                )

                Spacer(Modifier.height(10.dp))

                AnimatedContent(
                    targetState = statusIndex,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                            fadeOut(tween(160))
                    },
                    label = "status"
                ) { idx ->
                    Text(
                        text = STATUS_TEXTS[idx],
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(52.dp))

                TextButton(onClick = onCancel) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, SubtleBorder, CircleShape)
                            .padding(horizontal = 22.dp, vertical = 8.dp)
                    ) {
                        Text("Cancel", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbitAvatar(
    emoji: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = emoji,
        transitionSpec = {
            (fadeIn(tween(200)) + androidx.compose.animation.scaleIn(
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.6f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                ),
                initialScale = 0.5f
            )) togetherWith (fadeOut(tween(120)) + androidx.compose.animation.scaleOut(tween(120)))
        },
        modifier = modifier,
        label = "orbit_av"
    ) { e ->
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(tint.copy(alpha = 0.16f), CircleShape)
                .border(1.5.dp, tint.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Counter-rotate emoji so it stays upright while wrapper rotates
            Text(e, fontSize = 18.sp)
        }
    }
}
