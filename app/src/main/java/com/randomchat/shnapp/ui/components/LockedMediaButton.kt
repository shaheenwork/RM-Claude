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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.ErrorRed
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.PremiumGoldGlow
import com.randomchat.shnapp.theme.ElevatedCard

/**
 * Circular media button with three visual states:
 *  • **Premium**    — cyan border + cyan icon (full access)
 *  • **Has credit** — cyan border + cyan "×N" badge (one-time rewarded access)
 *  • **Locked**     — pulsing gold border + gold lock badge (upgrade prompt)
 *
 * The badge lives OUTSIDE the circle clip in an oversized wrapper Box so it
 * is never cut off at the circle edge.
 */
@Composable
fun LockedMediaButton(
    icon       : ImageVector,
    isPremium  : Boolean,
    creditCount: Int  = 0,
    size       : Dp   = 40.dp,
    onClick    : () -> Unit
) {
    val hasCredit  = !isPremium && creditCount > 0
    val badgeSize  = if (hasCredit) 16.dp else 14.dp
    // Wrapper is slightly larger than the circle so the bottom-right badge
    // overhangs cleanly without being clipped.
    val wrapperSize = size + 8.dp

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 0.9f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Outer wrapper — NOT clipped, holds the circle + the badge overlay
    Box(modifier = Modifier.size(wrapperSize)) {

        // ── Clipped circle button ────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .align(Alignment.Center)
                .clip(CircleShape)
                .then(
                    when {
                        isPremium -> Modifier.background(ElevatedCard)
                        hasCredit -> Modifier.background(AccentCyan.copy(alpha = 0.12f))
                        else      -> Modifier.background(
                            Brush.radialGradient(
                                listOf(
                                    PremiumGold.copy(alpha = glowAlpha * 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    brush = when {
                        isPremium -> Brush.sweepGradient(
                            listOf(AccentCyan.copy(0.4f), AccentCyan.copy(0.1f))
                        )
                        hasCredit -> Brush.sweepGradient(
                            listOf(AccentCyan.copy(0.7f), AccentCyan.copy(0.25f))
                        )
                        else      -> Brush.sweepGradient(
                            listOf(PremiumGold.copy(glowAlpha * 0.8f), PremiumGoldGlow)
                        )
                    },
                    shape = CircleShape
                )
                .clickable(onClick = onClick)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPremium || hasCredit) AccentCyan else PremiumGold,
                modifier = Modifier.size(20.dp)
            )
        }

        // ── Badge — bottom-right of wrapper, fully outside the circle clip ──
        if (!isPremium) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(badgeSize)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (hasCredit) AccentCyan else PremiumGold)
            ) {
                if (hasCredit) {
                    Text(
                        text       = "×$creditCount",
                        color      = Color(0xFF001A22),
                        fontSize   = 7.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ImageButton(isPremium: Boolean, creditCount: Int = 0, onClick: () -> Unit) {
    LockedMediaButton(
        icon        = Icons.Default.PhotoCamera,
        isPremium   = isPremium,
        creditCount = creditCount,
        onClick     = onClick
    )
}

@Composable
fun AudioButton(
    isPremium  : Boolean,
    creditCount: Int     = 0,
    isRecording: Boolean = false,
    onClick    : () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )

    if (isRecording) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ErrorRed.copy(alpha = recAlpha * 0.2f))
                .border(
                    1.dp,
                    Brush.sweepGradient(listOf(ErrorRed.copy(recAlpha), ErrorRed.copy(0.4f))),
                    CircleShape
                )
                .clickable(onClick = onClick)
        ) {
            Icon(Icons.Default.Stop, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
        }
    } else {
        LockedMediaButton(
            icon        = Icons.Default.Mic,
            isPremium   = isPremium,
            creditCount = creditCount,
            onClick     = onClick
        )
    }
}
