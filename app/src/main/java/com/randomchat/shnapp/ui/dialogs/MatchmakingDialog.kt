package com.randomchat.shnapp.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.ElevatedCard
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.GradientMid
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.utils.LocalHaptics
import kotlinx.coroutines.delay

private val EMOJI_POOL = listOf(
    "😎", "🎨", "🌍", "🎮", "🎵", "📚", "🏔️", "☕",
    "🎯", "🌟", "🍕", "✨", "🌸", "🦋", "🎬", "🚀",
    "🎸", "🍵", "🌊", "🔥", "💡", "🎤", "📷", "🌙"
)

private val STATUS_TEXTS = listOf(
    "Connecting to network",
    "Looking for someone interesting",
    "Preparing secure room",
    "Encrypting session",
    "Almost there"
)

@Composable
fun MatchmakingDialog(
    visible: Boolean,
    onCancel: () -> Unit
) {
    if (!visible) return

    val haptics = LocalHaptics.current

    // 6 avatar emojis cycling — gives the sense of scanning through users
    val emojis = remember {
        mutableStateListOf<String>().apply {
            repeat(6) { add(EMOJI_POOL.random()) }
        }
    }
    var statusIndex by remember { mutableIntStateOf(0) }

    // Cycle: each tick, swap ONE avatar at a time. Pseudo-physics — feels alive
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            val idx = (0 until emojis.size).random()
            val newEmoji = EMOJI_POOL.filterNot { it == emojis[idx] }.random()
            emojis[idx] = newEmoji
            haptics.tick()
        }
    }

    // Cycle status text every 1.8s
    LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            statusIndex = (statusIndex + 1) % STATUS_TEXTS.size
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
                .background(
                    Brush.verticalGradient(listOf(DeepSpace, GradientMid, GradientEnd))
                ),
            contentAlignment = Alignment.Center
        ) {
            // Subtle ambient halo behind avatar grid
            Box(
                modifier = Modifier
                    .size(360.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AccentCyan.copy(alpha = 0.08f), androidx.compose.ui.graphics.Color.Transparent),
                            radius = 500f
                        )
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // 3×2 avatar grid
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AvatarTile(emojis[0], accent = AccentCyan)
                        AvatarTile(emojis[1], accent = PremiumGold)
                        AvatarTile(emojis[2], accent = AccentCyan)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AvatarTile(emojis[3], accent = AccentCyan)
                        AvatarTile(emojis[4], accent = AccentCyan)
                        AvatarTile(emojis[5], accent = PremiumGold)
                    }
                }

                Spacer(Modifier.height(36.dp))

                Text(
                    "Finding a stranger",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.3).sp
                )

                Spacer(Modifier.height(8.dp))

                AnimatedContent(
                    targetState = statusIndex,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                            fadeOut(tween(180))
                    },
                    label = "status"
                ) { idx ->
                    Text(
                        text = STATUS_TEXTS[idx],
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(56.dp))

                TextButton(onClick = onCancel) {
                    Text("Cancel", color = TextMuted, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun AvatarTile(emoji: String, accent: androidx.compose.ui.graphics.Color) {
    AnimatedContent(
        targetState = emoji,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = Spring.StiffnessMedium
                ),
                initialScale = 0.5f
            )) togetherWith (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.15f))
        },
        label = "avatar"
    ) { current ->
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(accent.copy(alpha = 0.10f), CircleShape)
                .border(1.dp, accent.copy(alpha = 0.20f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(current, fontSize = 30.sp)
        }
    }
}
