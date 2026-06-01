package com.randomchat.shnapp.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.randomchat.shnapp.model.Gender
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.AccentCyanGlow
import com.randomchat.shnapp.theme.BrandGradients
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.ui.components.BrandMark
import com.randomchat.shnapp.ui.components.CyanButton
import com.randomchat.shnapp.ui.components.HomePremiumCard
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel    : HomeViewModel,
    onStartChat  : (Gender) -> Unit,
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
    val gifCredits       by viewModel.rewardedGifCredits.collectAsState()
    val onlineCount      by viewModel.onlineCount.collectAsState()
    val selectedGender   by viewModel.selectedGender.collectAsState()

    // ── Shake-on-empty: CTA tapped w/o gender → pills shake + warning haptic ──
    var needsAttention by remember { mutableStateOf(false) }
    val shakeOffsetX   = remember { Animatable(0f) }
    LaunchedEffect(needsAttention) {
        if (!needsAttention) return@LaunchedEffect
        listOf(-14f, 14f, -12f, 10f, -6f, 0f).forEach { off ->
            shakeOffsetX.animateTo(off, animationSpec = tween(60, easing = LinearEasing))
        }
        delay(1000)
        needsAttention = false
    }
    // Auto-clear the attention state the moment user picks a gender.
    LaunchedEffect(selectedGender) {
        if (selectedGender != null) needsAttention = false
    }

    // Believable presence: time-seeded so it's stable within a minute and drifts
    // gently across minutes (slow tide ±60 + gentle wobble ±12) — no drastic jumps.
    val displayedOnline  = remember(onlineCount) {
        val minute = System.currentTimeMillis() / 60_000L
        val tide   = (kotlin.math.sin(minute / 60.0) * 60).toInt()
        val wobble = (kotlin.math.sin(minute / 7.0)  * 12).toInt()
        onlineCount + 150 + tide + wobble
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Static background — radial gradients replace animated blur blobs
        // (blur was ~3-5ms/frame GPU cost on mid-range devices, infinite anim drained battery)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(DeepSpace, Color(0xFF0C1A14), GradientEnd)
                    )
                )
        )
        // Pink ambient — top-right
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentCyan.copy(0.08f), Color.Transparent),
                        radius = 600f,
                        center = androidx.compose.ui.geometry.Offset(800f, 200f)
                    )
                )
        )
        // Violet ambient — bottom-left
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(com.randomchat.shnapp.theme.BrandViolet.copy(0.10f), Color.Transparent),
                        radius = 500f,
                        center = androidx.compose.ui.geometry.Offset(100f, 1600f)
                    )
                )
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
                            .size(44.dp)
                            .background(AccentCyanGlow, CircleShape)
                            .blur(22.dp)
                    )
                    BrandMark(size = 52.dp)
                }

                Spacer(Modifier.height(16.dp))

                // ── Headline — 2-line editorial, no sub line (presence speaks below) ──
                Text(
                    "Talk to a",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "Random Malayali",
                    color = com.randomchat.shnapp.theme.PinkSoft,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontSize = 34.sp,
                    letterSpacing = (-0.5).sp
                )

                Spacer(Modifier.height(14.dp))

                // ── Online presence — pulsing LIVE badge + count (Twitch-style) ──
                LiveOnlineChip(count = displayedOnline)

                Spacer(Modifier.height(32.dp))

                // ── Gender selection — required, persisted; soft F-F bias only ──
                Row(
                    modifier = Modifier.graphicsLayer { translationX = shakeOffsetX.value },
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GenderPill(
                        label    = "I'm Male",
                        icon     = Icons.Default.Male,
                        selected = selectedGender == Gender.MALE,
                        onClick  = { haptics.tick(); viewModel.setGender(Gender.MALE) }
                    )
                    GenderPill(
                        label    = "I'm Female",
                        icon     = Icons.Default.Female,
                        selected = selectedGender == Gender.FEMALE,
                        onClick  = { haptics.tick(); viewModel.setGender(Gender.FEMALE) }
                    )
                }
                if (selectedGender == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pick one to start",
                        color = if (needsAttention) com.randomchat.shnapp.theme.ErrorRed else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = if (needsAttention) FontWeight.SemiBold else FontWeight.Normal,
                        letterSpacing = 0.4.sp
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ── CTA ──────────────────────────────────────────────────────
                if (isBanned) {
                    Text(
                        "Your session has been temporarily suspended.",
                        color = com.randomchat.shnapp.theme.ErrorRed,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                } else {
                    val ctaEnabled = selectedGender != null
                    CyanButton(
                        text = "Start chatting",
                        leadingIcon = Icons.Default.ChatBubble,
                        onClick = {
                            val g = selectedGender
                            if (g != null) {
                                haptics.heavy()
                                onStartChat(g)
                            } else {
                                // No gender → warning haptic + trigger shake on pills.
                                haptics.warning()
                                needsAttention = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (ctaEnabled) 1f else 0.55f)
                    )
                }

                Spacer(Modifier.height(18.dp))

                // ── Premium feature rotator — auto-cycles every 3s, taps into PremiumScreen ──
                if (!isPremium) {
                    PremiumRotator(onClick = onOpenPremium)
                }

                // ── Free media credits — watch ad to earn ──
                if (!isPremium && Constants.ADS_ENABLED) {
                    Spacer(Modifier.height(10.dp))
                    RewardsCard(
                        photoCredits = photoCredits,
                        audioCredits = audioCredits,
                        gifCredits   = gifCredits,
                        onEarnCredits = {
                            haptics.tick()
                            if (AdMobManager.getInstance(context).isRewardedReady()) {
                                AdMobManager.getInstance(context).showRewardedIfReady(
                                    activity       = activity,
                                    onRewarded     = {
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

            // Ad banner — pinned at bottom, non-premium + ADS_ENABLED only
            if (!isPremium && Constants.ADS_ENABLED) {
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
    gifCredits   : Int,
    onEarnCredits: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF07140E), RoundedCornerShape(12.dp))
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
            if (photoCredits + audioCredits + gifCredits > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CreditInline(Icons.Default.PhotoCamera, photoCredits)
                    CreditInline(Icons.Default.Mic, audioCredits)
                    CreditInline(Icons.Default.Gif, gifCredits)
                }
            } else {
                Text(
                    "Unlock photos, voice notes & GIFs",
                    color = TextMuted,
                    fontSize = 11.sp
                )
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

// ── Gender pill (Home inline selector) ────────────────────────────────────────
// Selected → brand-green gradient fill, cream icon + label.
// Unselected → subtle border, sage icon + label.
@Composable
private fun GenderPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected)
                    Modifier.background(BrandGradients.primary, shape)
                else
                    Modifier.border(1.dp, SubtleBorder, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else TextSecondary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            label,
            color = if (selected) Color.White else TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp
        )
    }
}

// ── Premium strip (Home upsell — minimal single row, taps into PremiumScreen) ──
@Composable
private fun PremiumStrip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PremiumGold.copy(alpha = 0.05f), shape)
            .border(1.dp, PremiumGold.copy(alpha = 0.40f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(PremiumGold.copy(alpha = 0.14f), CircleShape)
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = PremiumGold,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Go Premium",
                color = PremiumGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            )
            Text(
                "No ads, photos, voice notes & more",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = PremiumGold,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ── Premium feature rotator (Home upsell — cycles 1 feature at a time) ───────
private data class PremiumFeat(val emoji: String, val name: String, val desc: String)

private val PREMIUM_FEATS = listOf(
    PremiumFeat("🎙", "Voice notes",  "Record & send up to 60s"),
    PremiumFeat("📷", "Send photos",  "Share images directly in chat"),
    PremiumFeat("❤️", "Reactions",    "React to any message"),
    PremiumFeat("👀", "Live typing",  "See them type in real time"),
    PremiumFeat("💾", "Save chats",   "Keep your best conversations")
)

@Composable
private fun PremiumRotator(onClick: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }

    // Auto-cycle every 3s
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000L)
            index = (index + 1) % PREMIUM_FEATS.size
        }
    }

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.22f), shape)
            .border(1.dp, SubtleBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Header: ✦ INSIDE PREMIUM + Try button ─────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = PremiumGold,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    "INSIDE PREMIUM",
                    color = PremiumGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .border(1.dp, PremiumGold.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    "Try",
                    color = PremiumGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── Body: rotating feature spotlight ──────────────────────────────
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 }) togetherWith
                    fadeOut(tween(160))
            },
            label = "premium_feat"
        ) { i ->
            val feat = PREMIUM_FEATS[i]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(feat.emoji, fontSize = 26.sp)
                Column {
                    Text(
                        feat.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        feat.desc,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // ── Dot navigator ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PREMIUM_FEATS.forEachIndexed { i, _ ->
                val isOn = i == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(width = if (isOn) 14.dp else 5.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isOn) PremiumGold else TextMuted.copy(alpha = 0.6f))
                )
            }
        }
    }
}

// ── Live online chip (Twitch-style — pulsing red LIVE badge + count) ─────────
@Composable
private fun LiveOnlineChip(count: Int) {
    val transition = rememberInfiniteTransition(label = "live_chip")

    // Ripple ring scale + fade — expands outward from the LIVE pill
    val ringScale by transition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.65f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing)),
        label = "ring_scale"
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing)),
        label = "ring_alpha"
    )
    // Blinking white dot inside the LIVE pill
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue  = 0.35f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "dot_alpha"
    )

    val liveRed     = Color(0xFFE8443C)
    val onlineGreen = com.randomchat.shnapp.theme.OnlineGreen
    val chipShape   = RoundedCornerShape(20.dp)
    val pillShape   = RoundedCornerShape(2.dp)

    Row(
        modifier = Modifier
            .background(onlineGreen.copy(alpha = 0.10f), chipShape)
            .border(1.dp, onlineGreen.copy(alpha = 0.30f), chipShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // LIVE pill with pulse ring behind it
        Box(contentAlignment = Alignment.Center) {
            // Ripple ring (drawn first → behind the pill)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = ringScale
                        scaleY = ringScale
                        alpha  = ringAlpha
                    }
                    .background(liveRed, pillShape)
            )
            // Actual LIVE pill — fixed tiny height so it doesn't grow with text metrics
            Row(
                modifier = Modifier
                    .height(11.dp)
                    .background(liveRed, pillShape)
                    .padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(2.5.dp)
                        .alpha(dotAlpha)
                        .background(Color.White, CircleShape)
                )
                Text(
                    "LIVE",
                    color = Color.White,
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    lineHeight = 6.sp
                )
            }
        }
        Text(
            "%,d".format(count),
            color = TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp
        )
        Text(
            "online",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
