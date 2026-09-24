package com.randomchat.shnapp.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.billing.PaywallSource
import com.randomchat.shnapp.billing.PlanPricing
import com.randomchat.shnapp.billing.PremiumPlan
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.AuroraBlue
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
import com.randomchat.shnapp.utils.SessionManager
import com.randomchat.shnapp.viewmodel.PremiumViewModel

/** One Premium perk — drives the paywall list, the contextual highlight and the premium view. */
private enum class PaywallPerk(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val color: Color
) {
    PHOTOS(Icons.Default.PhotoCamera, "Send Photos", "Share images directly in chat", AccentCyan),
    VOICE(Icons.Default.Mic, "Send Voice Notes", "Record and send audio clips", AuroraBlue),
    LIVE_TYPING(Icons.Default.Visibility, "Live Typing Preview", "See what they're typing in real time", AuroraBlue),
    NO_ADS(Icons.Default.Block, "No Ads", "Completely ad-free experience", AccentCyan),
    SAVE_CHATS(Icons.Default.Save, "Save Conversations", "Keep your best chats forever", PremiumGold),
    GIFS(Icons.Default.Gif, "Send GIFs", "React with animated GIFs from Klipy", PremiumGold),
    REACTIONS(Icons.Default.AddReaction, "Message Reactions", "React to messages with emojis", Color(0xFFE8765A)),
    APP_LOCK(Icons.Default.Lock, "App Lock", "Protect your chats with your screen lock", PremiumGold),
    BADGE(Icons.Default.AutoAwesome, "Premium Badge", "Show a gold badge to everyone you chat with", PremiumGold),
}

/** Perks that exist in this build — "No Ads" means nothing when ads are switched off. */
private fun availablePerks(): List<PaywallPerk> =
    PaywallPerk.entries.filter { it != PaywallPerk.NO_ADS || Constants.ADS_ENABLED }

private const val PAYWALL_PERK_COUNT = 5

/** Short perk list for the paywall, led by the perk that brought the user here. */
private fun paywallPerks(hero: PaywallPerk?): List<PaywallPerk> {
    val all = availablePerks()
    val ordered = if (hero != null && hero in all) listOf(hero) + (all - hero) else all
    return ordered.take(PAYWALL_PERK_COUNT)
}

/** Headline matched to the paywall entry point ([PaywallSource]). */
private data class PaywallCopy(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val hero: PaywallPerk? = null
)

private fun paywallCopyFor(source: String): PaywallCopy = when (source) {
    PaywallSource.VOICE -> PaywallCopy(
        Icons.Default.Mic, "Send voice notes",
        "Say it out loud — unlimited voice notes in every chat.", PaywallPerk.VOICE
    )
    PaywallSource.PHOTO -> PaywallCopy(
        Icons.Default.PhotoCamera, "Share photos",
        "Send unlimited photos to anyone you meet.", PaywallPerk.PHOTOS
    )
    PaywallSource.GIF -> PaywallCopy(
        Icons.Default.Gif, "Send GIFs",
        "Unlimited GIFs to make every chat more fun.", PaywallPerk.GIFS
    )
    PaywallSource.REACTIONS -> PaywallCopy(
        Icons.Default.AddReaction, "React to messages",
        "Long-press any message and react with an emoji.", PaywallPerk.REACTIONS
    )
    PaywallSource.LIVE_TYPING -> PaywallCopy(
        Icons.Default.Visibility, "See them typing, live",
        "Read their message as they type it — before they hit send.", PaywallPerk.LIVE_TYPING
    )
    PaywallSource.SAVE_CHAT -> PaywallCopy(
        Icons.Default.Bookmark, "Keep this chat forever",
        "Save your best conversations, photos and voice notes included.", PaywallPerk.SAVE_CHATS
    )
    PaywallSource.ADS -> PaywallCopy(
        Icons.Default.Block, "Chat without ads",
        "No ads between chats, no banners — just conversations.", PaywallPerk.NO_ADS
    )
    PaywallSource.APP_LOCK -> PaywallCopy(
        Icons.Default.Lock, "Lock your chats",
        "Require your screen lock whenever you come back to the app.", PaywallPerk.APP_LOCK
    )
    PaywallSource.BADGE -> PaywallCopy(
        Icons.Default.AutoAwesome, "Get the Premium badge",
        "Show a gold Premium badge to everyone you chat with.", PaywallPerk.BADGE
    )
    PaywallSource.HOME_REWARDS_CAP, PaywallSource.CHAT_REWARDS_CAP -> PaywallCopy(
        Icons.Default.AutoAwesome, "Out of free unlocks today",
        "Get unlimited photos, voice notes and GIFs — no ads to watch.", PaywallPerk.PHOTOS
    )
    else -> PaywallCopy(
        Icons.Default.AutoAwesome, "Upgrade to Premium",
        "Unlock the full power of Random Malayali"
    )
}

/** Plan with the lowest per-week price — labelled only when it is strictly the cheapest. */
private fun bestValuePlanId(plans: List<PremiumPlan>): String? {
    val priced = plans.mapNotNull { plan -> PlanPricing.perWeekMicros(plan)?.let { plan to it } }
    if (priced.size < 2 || priced.map { it.first.currencyCode }.distinct().size != 1) return null
    val sorted = priced.sortedBy { it.second }
    return sorted[0].first.productId.takeIf { sorted[0].second < sorted[1].second }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    viewModel: PremiumViewModel,
    source: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptics = com.randomchat.shnapp.utils.LocalHaptics.current
    val isPremium by viewModel.isPremium.collectAsState()
    val plans by viewModel.plans.collectAsState()
    val selectedPlanId by viewModel.selectedPlanId.collectAsState()
    val activePlanId by viewModel.activePlanId.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val purchasePending by viewModel.purchasePending.collectAsState()

    var showCancelDialog by remember { mutableStateOf(false) }
    var showExitOffer by remember { mutableStateOf(false) }

    // Celebratory haptic when premium becomes active
    var wasPremium by remember { mutableStateOf(isPremium) }
    LaunchedEffect(isPremium) {
        if (isPremium && !wasPremium) haptics.match()
        wasPremium = isPremium
    }

    // Once per visit: funnel event tagged with the entry point, plan refresh when
    // pricing is missing, and the server-side view log for the reminder push.
    LaunchedEffect(source) {
        viewModel.onPaywallOpened(source)
    }

    // Leaving the paywall offers the weekly plan first (rate-limited in the ViewModel).
    val leave: () -> Unit = {
        if (viewModel.shouldShowExitOffer()) {
            viewModel.onExitOfferShown()
            showExitOffer = true
        } else {
            onNavigateBack()
        }
    }
    BackHandler(onBack = leave)

    val copy = remember(source) { paywallCopyFor(source) }

    // Static glow alpha — infinite pulse was casino-aesthetic, not premium.
    // Real premium products (Linear, Notion, Stripe, Vercel) use static gold borders.
    val glowAlpha = 0.55f

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
                IconButton(onClick = leave) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                }
                Spacer(Modifier.weight(1f))
                if (!isPremium) {
                    TextButton(onClick = { viewModel.restorePurchases() }) {
                        Text("Restore", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(PremiumGold.copy(glowAlpha * 0.2f), CircleShape)
                        .blur(16.dp)
                )
                Icon(
                    imageVector = if (isPremium) Icons.Default.AutoAwesome else copy.icon,
                    contentDescription = null,
                    tint = PremiumGold,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                if (isPremium) "You're Premium ✨" else copy.title,
                color = PremiumGold,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                letterSpacing = 0.3.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isPremium) "Enjoy all premium features" else copy.subtitle,
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(28.dp))

            if (!isPremium) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    paywallPerks(copy.hero).forEach { perk ->
                        CompactPerkRow(perk, highlighted = perk == copy.hero)
                    }
                }

                Spacer(Modifier.height(24.dp))

                if (plans.isNotEmpty()) {
                    val weekly = plans.find { it.productId == Constants.PRODUCT_PREMIUM_WEEKLY }
                    val bestValueId = remember(plans) { bestValuePlanId(plans) }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        plans.forEach { plan ->
                            PlanCard(
                                plan = plan,
                                isSelected = plan.productId == selectedPlanId,
                                isBestValue = plan.productId == bestValueId,
                                savingsPercent = weekly
                                    ?.takeIf { it.productId != plan.productId }
                                    ?.let { PlanPricing.savingsPercent(plan, it) },
                                glowAlpha = glowAlpha,
                                onClick = {
                                    haptics.tick()
                                    viewModel.selectPlan(plan.productId)
                                }
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

                if (purchasePending) {
                    PendingPaymentBanner(Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(12.dp))
                }

                val plansLoaded = plans.isNotEmpty()
                Text(
                    if (plansLoaded) "✨  Unlock Premium" else "Loading pricing…",
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

                // Price + renewal disclosure right under the CTA, for the selected plan.
                plans.find { it.productId == selectedPlanId }?.let { selected ->
                    val unit = PlanPricing.unitLabel(selected.billingPeriod) ?: selected.period.lowercase()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Cancel anytime · No commitment",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Secure payment via Google Play", color = TextMuted, fontSize = 11.sp)

                Spacer(Modifier.height(28.dp))

                FreeVsPremiumTable(Modifier.padding(horizontal = 24.dp))

                Spacer(Modifier.height(20.dp))
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
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    availablePerks().forEach { PremiumPerkRow(it) }
                }

                Spacer(Modifier.height(32.dp))

                val activePeriod = plans.find { it.productId == activePlanId }?.period ?: "Premium"

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

    val weeklyPlan = plans.find { it.productId == Constants.PRODUCT_PREMIUM_WEEKLY }
    if (showExitOffer && weeklyPlan != null) {
        ExitOfferSheet(
            weekly = weeklyPlan,
            onAccept = {
                showExitOffer = false
                viewModel.acceptExitOffer(context as Activity)
            },
            onDecline = {
                showExitOffer = false
                onNavigateBack()
            },
            onDismiss = { showExitOffer = false }
        )
    }
}

@Composable
private fun PlanCard(
    plan: PremiumPlan,
    isSelected: Boolean,
    isBestValue: Boolean,
    savingsPercent: Int?,
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

    val unit = PlanPricing.unitLabel(plan.billingPeriod) ?: plan.period.lowercase()
    // Per-week equivalent for plans longer than a week, computed from the real price.
    val perWeek = PlanPricing.weeksIn(plan.billingPeriod)
        ?.takeIf { it > 1.0 }
        ?.let { PlanPricing.perWeekMicros(plan) }
        ?.let { PlanPricing.formatMicros(it, plan.currencyCode) }

    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgBrush, shape)
            .border(if (isSelected) 1.5.dp else 1.dp, borderBrush, shape)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        plan.period,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    if (isBestValue) {
                        Text(
                            "BEST VALUE",
                            color = Color(0xFF1A1100),
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier
                                .background(PremiumGold, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    perWeek?.let { "$it / week" } ?: "Most flexible",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                savingsPercent?.let { percent ->
                    Box(
                        modifier = Modifier
                            .background(OnlineGreen.copy(0.18f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "Save $percent%",
                            color = OnlineGreen,
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
                Text("/ $unit", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CompactPerkRow(perk: PaywallPerk, highlighted: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) PremiumGold.copy(0.08f) else CardSurface, shape)
            .border(1.dp, if (highlighted) PremiumGold.copy(0.45f) else SubtleBorder, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .background(perk.color.copy(0.12f), RoundedCornerShape(10.dp))
        ) {
            Icon(perk.icon, null, tint = perk.color, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(perk.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                perk.subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.Check,
            null,
            tint = if (highlighted) PremiumGold else OnlineGreen,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun PremiumPerkRow(perk: PaywallPerk) {
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
                .background(perk.color.copy(0.12f), RoundedCornerShape(12.dp))
        ) {
            Icon(perk.icon, null, tint = perk.color, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(perk.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(perk.subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.Check, null, tint = OnlineGreen, modifier = Modifier.size(18.dp))
    }
}

/** Shown while Play holds an unconfirmed payment — UPI mandates can take minutes. */
@Composable
private fun PendingPaymentBanner(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PremiumGold.copy(0.10f), shape)
            .border(1.dp, PremiumGold.copy(0.35f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Schedule, null, tint = PremiumGold, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Payment pending", color = PremiumGold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                "Premium unlocks automatically once Google Play confirms your payment. " +
                    "This can take a few minutes.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

private sealed class CompareCell {
    data object Yes : CompareCell()
    data object No : CompareCell()
    data class Label(val text: String) : CompareCell()
}

/** Honest Free vs Premium comparison — free-tier limits come from the real constants. */
@Composable
private fun FreeVsPremiumTable(modifier: Modifier = Modifier) {
    val adsEnabled = Constants.ADS_ENABLED
    val rows = buildList<Triple<String, CompareCell, CompareCell>> {
        add(Triple("Text chat", CompareCell.Yes, CompareCell.Yes))
        add(
            Triple(
                "Photos, voice & GIFs",
                if (adsEnabled) CompareCell.Label("${SessionManager.DAILY_REWARD_CAP}/day with ads") else CompareCell.No,
                CompareCell.Label("Unlimited")
            )
        )
        add(Triple("Live typing preview", CompareCell.No, CompareCell.Yes))
        add(Triple("Message reactions", CompareCell.No, CompareCell.Yes))
        add(
            Triple(
                "Save chats",
                if (adsEnabled) CompareCell.Label("Watch an ad") else CompareCell.No,
                CompareCell.Yes
            )
        )
        add(Triple("App Lock", CompareCell.No, CompareCell.Yes))
        add(Triple("Premium badge", CompareCell.No, CompareCell.Yes))
        if (adsEnabled) add(Triple("Ads", CompareCell.Label("Yes"), CompareCell.Label("None")))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(16.dp))
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Free vs Premium",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1.6f)
            )
            Text(
                "Free",
                color = TextMuted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Premium",
                color = PremiumGold,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        rows.forEach { (label, free, premium) ->
            HorizontalDivider(thickness = 0.5.dp, color = SubtleBorder.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = TextSecondary, fontSize = 12.5.sp, modifier = Modifier.weight(1.6f))
                CompareCellView(free, premiumColumn = false, modifier = Modifier.weight(1f))
                CompareCellView(premium, premiumColumn = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompareCellView(cell: CompareCell, premiumColumn: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (cell) {
            CompareCell.Yes -> Icon(
                Icons.Default.Check,
                null,
                tint = if (premiumColumn) PremiumGold else OnlineGreen,
                modifier = Modifier.size(16.dp)
            )
            CompareCell.No -> Icon(
                Icons.Default.Close,
                null,
                tint = TextMuted.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            is CompareCell.Label -> Text(
                cell.text,
                color = if (premiumColumn) PremiumGold else TextMuted,
                fontSize = 11.sp,
                fontWeight = if (premiumColumn) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Offered once when a free user leaves the paywall while looking at a longer plan:
 * the lowest-commitment plan, with the renewal terms spelled out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExitOfferSheet(
    weekly: PremiumPlan,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val unit = PlanPricing.unitLabel(weekly.billingPeriod) ?: "week"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = ElevatedCard,
        tonalElevation   = 0.dp,
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(SubtleBorder, CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFF4D89B), PremiumGold, PremiumGoldDim)),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    tint = Color(0xFF3A2A00),
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "Start with just a $unit?",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "Every Premium feature for ${weekly.formattedPrice} a $unit. Cancel anytime.",
                color = TextSecondary,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(20.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.horizontalGradient(listOf(PremiumGold, PremiumGoldDim)))
                    .clickable(onClick = onAccept)
                    .padding(vertical = 15.dp)
            ) {
                Text(
                    "Get Premium · ${weekly.formattedPrice}/$unit",
                    color = Color(0xFF3A2A00),
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 0.3.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDecline)
                    .padding(vertical = 10.dp)
            ) {
                Text("No thanks", color = TextMuted, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
