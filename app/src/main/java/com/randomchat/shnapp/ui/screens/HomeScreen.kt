package com.randomchat.shnapp.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.randomchat.shnapp.ads.AdMobManager
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.AccentCyanGlow
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.ui.components.CyanButton
import com.randomchat.shnapp.ui.components.HomePremiumCard
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel    : HomeViewModel,
    onStartChat  : () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context  = LocalContext.current
    val activity = context as Activity
    val haptics  = com.randomchat.shnapp.utils.LocalHaptics.current

    val isPremium        by viewModel.isPremium.collectAsState()
    val isBanned         by viewModel.isBanned.collectAsState()
    val photoCredits     by viewModel.rewardedPhotoCredits.collectAsState()
    val audioCredits     by viewModel.rewardedAudioCredits.collectAsState()
    val onlineCount      by viewModel.onlineCount.collectAsState()
    // Stable random offset per session — doesn't re-roll on recompose
    val onlineOffset     = remember { (11..23).random() }
    val displayedOnline  = onlineCount + onlineOffset

    // Ambient background animation
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val bgShift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bg_shift"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated dark background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            DeepSpace,
                            Color(0xFF080E1C).copy(0.8f + bgShift * 0.2f),
                            GradientEnd
                        )
                    )
                )
        )

        // Glowing accent circles (decorative)
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .blur(120.dp)
                .background(AccentCyan.copy(0.04f + bgShift * 0.02f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.BottomStart)
                .blur(100.dp)
                .background(Color(0xFF0050AA).copy(0.06f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPremium) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(PremiumGold.copy(0.15f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = PremiumGold, modifier = Modifier.size(14.dp))
                        Text(" Premium", color = PremiumGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Box(modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, null, tint = TextMuted)
                }
            }

            // Hero content — vertically centred in remaining space
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(AccentCyanGlow, CircleShape)
                            .blur(14.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    "StrangerChat",
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Anonymous · Private · Free",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                // Online count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .background(Color(0xFF0A1A10), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(com.randomchat.shnapp.theme.OnlineGreen, CircleShape)
                    )
                    Text(
                        "$displayedOnline strangers online",
                        color = com.randomchat.shnapp.theme.OnlineGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Main CTA
                if (isBanned) {
                    Text(
                        "Your session has been temporarily suspended.",
                        color = com.randomchat.shnapp.theme.ErrorRed,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                } else {
                    CyanButton(
                        text = "⚡  Start Anonymous Chat",
                        onClick = { haptics.heavy(); onStartChat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Premium upsell card — shown first, higher conversion priority
                if (!isPremium) {
                    HomePremiumCard(onUpgradeClick = onOpenPremium)
                    Spacer(Modifier.height(10.dp))
                }

                // Free-sends rewards card — compact one-row layout
                if (!isPremium) {
                    RewardsCard(
                        photoCredits = photoCredits,
                        audioCredits = audioCredits,
                        onEarnCredits = {
                            haptics.tick()
                            if (AdMobManager.getInstance(context).isRewardedReady()) {
                                AdMobManager.getInstance(context).showRewardedIfReady(
                                    activity     = activity,
                                    onRewarded   = {
                                        haptics.success()
                                        viewModel.addRewardedCredits()
                                        com.randomchat.shnapp.utils.Telemetry.rewardedAdEarned("home_credits")
                                    },
                                    onNotAvailable = {
                                        Toast.makeText(
                                            context,
                                            "Ad not ready yet — try again in a moment.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    "Ad not ready yet — try again in a moment.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }

            // Ad banner — pinned at bottom, non-premium only
            if (!isPremium) {
                AndroidView(
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            adUnitId = Constants.ADMOB_BANNER_ID
                            loadAd(AdRequest.Builder().build())
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Rewards card ──────────────────────────────────────────────────────────────

@Composable
private fun RewardsCard(
    photoCredits : Int,
    audioCredits : Int,
    onEarnCredits: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF060D1A), RoundedCornerShape(12.dp))
            .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left: title + inline credit counts
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Free Media Credits",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CreditInline(Icons.Default.PhotoCamera, photoCredits)
                CreditInline(Icons.Default.Mic, audioCredits)
            }
        }

        // Right: watch-ad button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AccentCyan.copy(alpha = 0.12f))
                .border(1.dp, AccentCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .clickable(onClick = onEarnCredits)
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(14.dp)
            )
            Text(
                "Watch Ad",
                color = AccentCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        }
    }
}

@Composable
private fun CreditInline(
    icon : androidx.compose.ui.graphics.vector.ImageVector,
    count: Int
) {
    val hasCredits = count > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (hasCredits) AccentCyan else TextMuted,
            modifier = Modifier.size(13.dp)
        )
        Text(
            "×$count",
            color = if (hasCredits) AccentCyan else TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
