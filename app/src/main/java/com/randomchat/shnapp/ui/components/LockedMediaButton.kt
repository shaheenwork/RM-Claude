package com.randomchat.shnapp.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.ErrorRed
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.PremiumGoldGlow
import com.randomchat.shnapp.theme.ElevatedCard

@Composable
fun LockedMediaButton(
    icon: ImageVector,
    isPremium: Boolean,
    size: Dp = 40.dp,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow_alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (isPremium) Modifier.background(ElevatedCard)
                else Modifier.background(
                    Brush.radialGradient(
                        colors = listOf(
                            PremiumGold.copy(alpha = glowAlpha * 0.15f),
                            Color.Transparent
                        )
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = if (isPremium)
                    Brush.sweepGradient(listOf(AccentCyan.copy(0.4f), AccentCyan.copy(0.1f)))
                else
                    Brush.sweepGradient(listOf(PremiumGold.copy(glowAlpha * 0.8f), PremiumGoldGlow)),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPremium) AccentCyan else PremiumGold,
            modifier = Modifier.size(20.dp)
        )
        if (!isPremium) {
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.size(size)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(PremiumGold)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(9.dp).align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun ImageButton(isPremium: Boolean, onClick: () -> Unit) {
    LockedMediaButton(icon = Icons.Default.PhotoCamera, isPremium = isPremium, onClick = onClick)
}

@Composable
fun AudioButton(isPremium: Boolean, isRecording: Boolean = false, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "rec_alpha"
    )

    if (isRecording) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ErrorRed.copy(alpha = recAlpha * 0.2f))
                .border(1.dp, Brush.sweepGradient(listOf(ErrorRed.copy(recAlpha), ErrorRed.copy(0.4f))), CircleShape)
                .clickable(onClick = onClick)
        ) {
            Icon(Icons.Default.Stop, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
        }
    } else {
        LockedMediaButton(icon = Icons.Default.Mic, isPremium = isPremium, onClick = onClick)
    }
}
