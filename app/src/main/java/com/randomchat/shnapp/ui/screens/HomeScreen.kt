package com.randomchat.shnapp.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.AccentCyanGlow
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.ui.components.CyanButton
import com.randomchat.shnapp.ui.components.HomePremiumCard
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartChat: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isPremium by viewModel.isPremium.collectAsState()
    val isBanned by viewModel.isBanned.collectAsState()

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
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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

            Spacer(Modifier.height(40.dp))

            // Logo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(AccentCyanGlow, CircleShape)
                        .blur(20.dp)
                )
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "StrangerChat",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Talk to a random stranger.\nAnonymous. Private. Free.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(40.dp))

            // Online count illusion
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(Color(0xFF0A1A10), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(com.randomchat.shnapp.theme.OnlineGreen, CircleShape)
                )
                Text(
                    "1,240+ strangers online",
                    color = com.randomchat.shnapp.theme.OnlineGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(32.dp))

            // Main CTA button
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
                    onClick = onStartChat,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(32.dp))

            // Premium card (hidden for premium users)
            if (!isPremium) {
                HomePremiumCard(onUpgradeClick = onOpenPremium)
                Spacer(Modifier.height(24.dp))
            }

            // Feature hint chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
                listOf("🔒 Anonymous", "⚡ Instant", "🌐 Global").forEach { label ->
                    Text(
                        label,
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color(0xFF111827), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Banner ad (hidden for premium)
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
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
