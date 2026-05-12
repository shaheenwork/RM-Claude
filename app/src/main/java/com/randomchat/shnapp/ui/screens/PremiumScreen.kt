package com.randomchat.shnapp.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.billing.PremiumPlan
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.CardSurface
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.ElevatedCard
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.OnlineGreen
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.PremiumGoldDim
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.viewmodel.PremiumViewModel

private data class PremiumFeature(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val color: Color
)

private val FEATURES = listOf(
    PremiumFeature(Icons.Default.PhotoCamera, "Send Photos", "Share images directly in chat", AccentCyan),
    PremiumFeature(Icons.Default.Mic, "Send Voice Notes", "Record and send audio clips", Color(0xFF9C6FFF)),
    PremiumFeature(Icons.Default.AddReaction, "Message Reactions", "React to messages with emojis", Color(0xFFFF9800)),
    PremiumFeature(Icons.Default.Visibility, "Live Typing Preview", "See what stranger types in real time", Color(0xFF00E5FF)),
    PremiumFeature(Icons.Default.Save, "Save Conversations", "Keep your best chats forever", PremiumGold),
    PremiumFeature(Icons.Default.Block, "No Ads", "Completely ad-free experience", Color(0xFFFF6B6B)),
)

private val PERIOD_LABELS = mapOf(
    Constants.PRODUCT_PREMIUM_WEEKLY  to "Weekly",
    Constants.PRODUCT_PREMIUM_MONTHLY to "Monthly",
    Constants.PRODUCT_PREMIUM_YEARLY  to "Yearly"
)

@Composable
fun PremiumScreen(
    viewModel: PremiumViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptics = com.randomchat.shnapp.utils.LocalHaptics.current
    val isPremium by viewModel.isPremium.collectAsState()
    val plans by viewModel.plans.collectAsState()
    val selectedPlanId by viewModel.selectedPlanId.collectAsState()
    val activePlanId by viewModel.activePlanId.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()

    var showCancelDialog by remember { mutableStateOf(false) }

    // Celebratory haptic when premium becomes active
    var wasPremium by remember { mutableStateOf(isPremium) }
    LaunchedEffect(isPremium) {
        if (isPremium && !wasPremium) haptics.match()
        wasPremium = isPremium
    }

    val infiniteTransition = rememberInfiniteTransition(label = "prem_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "prem_glow_a"
    )

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            containerColor = ElevatedCard,
            title = { Text("Cancel Subscription", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Your premium access will remain active until the end of your current billing period. " +
                            "You can manage your subscription on Google Play.",
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    viewModel.manageSubscription(context)
                }) {
                    Text("Confirm Cancel", color = PremiumGold, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep Premium", color = TextMuted)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0C0A00), DeepSpace, GradientEnd)))
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopCenter)
                .blur(120.dp)
                .background(PremiumGold.copy(0.08f + glowAlpha * 0.04f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                }
                Spacer(Modifier.weight(1f))
                if (!isPremium) {
                    TextButton(onClick = { viewModel.restorePurchases() }) {
                        Text("Restore", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(PremiumGold.copy(glowAlpha * 0.2f), CircleShape)
                        .blur(16.dp)
                )
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = PremiumGold,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                if (isPremium) "You're Premium ✨" else "Upgrade to Premium",
                color = PremiumGold,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                letterSpacing = 0.3.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isPremium) "Enjoy all premium features"
                else "Unlock the full power of StrangerChat",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FEATURES.forEach { PremiumFeatureRow(it, isPremium) }
            }

            Spacer(Modifier.height(32.dp))

            if (!isPremium) {
                if (plans.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        plans.forEach { plan ->
                            PlanCard(
                                plan = plan,
                                isSelected = plan.productId == selectedPlanId,
                                glowAlpha = glowAlpha,
                                onClick = { viewModel.selectPlan(plan.productId) }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .background(CardSurface, RoundedCornerShape(16.dp))
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                val plansLoaded = plans.isNotEmpty()
                Text(
                    if (plansLoaded) "✨  Start Premium" else "Loading pricing…",
                    color = if (plansLoaded) Color.Black else Color.Black.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .background(
                            if (plansLoaded)
                                Brush.horizontalGradient(listOf(PremiumGold, PremiumGoldDim, PremiumGold))
                            else
                                Brush.horizontalGradient(listOf(PremiumGold.copy(0.4f), PremiumGoldDim.copy(0.4f))),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { viewModel.purchasePremium(context as Activity) }
                        .padding(vertical = 16.dp)
                )

                uiMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        msg,
                        color = PremiumGold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Secure payment via Google Play", color = TextMuted, fontSize = 11.sp)

                Spacer(Modifier.height(16.dp))
                Text(
                    "By subscribing you agree to our Terms of Service.\n" +
                            "Subscription auto-renews unless cancelled 24h before renewal.\n" +
                            "Prices vary by region and are shown in your local currency.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            } else {
                val activePeriod = PERIOD_LABELS[activePlanId] ?: "Premium"

                Row(
                    modifier = Modifier
                        .background(OnlineGreen.copy(0.12f), RoundedCornerShape(20.dp))
                        .border(1.dp, OnlineGreen.copy(0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Check, null, tint = OnlineGreen, modifier = Modifier.size(18.dp))
                    Text("Premium Active", color = OnlineGreen, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .background(CardSurface, RoundedCornerShape(16.dp))
                        .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Plan", color = TextMuted, fontSize = 13.sp)
                        Text("$activePeriod Subscription", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Billing", color = TextMuted, fontSize = 13.sp)
                        Text("Auto-renews via Google Play", color = TextSecondary, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .background(
                            Brush.linearGradient(listOf(ElevatedCard, Color(0xFF1A1500), ElevatedCard)),
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(PremiumGold.copy(0.5f), PremiumGoldDim.copy(0.3f))),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { viewModel.manageSubscription(context) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Settings, null, tint = PremiumGold, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Manage Subscription", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Change plan, update payment method", color = TextMuted, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))

                TextButton(onClick = { showCancelDialog = true }) {
                    Text("Cancel Subscription", color = TextMuted, fontSize = 13.sp)
                }

                uiMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        msg,
                        color = PremiumGold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Cancellation takes effect at end of billing period.",
                    color = TextMuted.copy(0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PlanCard(
    plan: PremiumPlan,
    isSelected: Boolean,
    glowAlpha: Float,
    onClick: () -> Unit
) {
    val borderBrush = if (isSelected)
        Brush.linearGradient(listOf(PremiumGold.copy(glowAlpha), PremiumGoldDim.copy(glowAlpha * 0.6f)))
    else
        Brush.linearGradient(listOf(SubtleBorder, SubtleBorder))

    val bgBrush = if (isSelected)
        Brush.linearGradient(listOf(ElevatedCard, Color(0xFF1A1500), ElevatedCard))
    else
        Brush.linearGradient(listOf(CardSurface, CardSurface))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgBrush, RoundedCornerShape(16.dp))
            .border(if (isSelected) 1.5.dp else 1.dp, borderBrush, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) PremiumGold else TextMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    plan.period,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                if (plan.productId == Constants.PRODUCT_PREMIUM_YEARLY) {
                    Text("Save up to 70% vs weekly", color = OnlineGreen, fontSize = 11.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                plan.badge?.let { badge ->
                    Box(
                        modifier = Modifier
                            .background(
                                if (badge == "Best Value") OnlineGreen.copy(0.18f) else PremiumGold.copy(0.18f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            badge,
                            color = if (badge == "Best Value") OnlineGreen else PremiumGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    plan.formattedPrice,
                    color = if (isSelected) PremiumGold else TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
                Text("/ ${plan.period.lowercase()}", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PremiumFeatureRow(feature: PremiumFeature, isPremium: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(16.dp))
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .background(feature.color.copy(0.12f), RoundedCornerShape(12.dp))
        ) {
            Icon(feature.icon, null, tint = feature.color, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(feature.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(feature.subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        if (isPremium) {
            Icon(Icons.Default.Check, null, tint = OnlineGreen, modifier = Modifier.size(18.dp))
        }
    }
}
