package com.randomchat.shnapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.CardSurface
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.ElevatedCard
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.BuildConfig
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.viewmodel.HomeViewModel

@Composable
fun SettingsScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenSavedChats: () -> Unit = {}
) {
    val isPremium       by viewModel.isPremium.collectAsState()
    val hasSavedChat    by viewModel.hasSavedFirstChat.collectAsState()
    val notifsEnabled   by viewModel.notifsEnabled.collectAsState()
    val appLockEnabled  by viewModel.appLockEnabled.collectAsState()
    val uriHandler      = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepSpace, GradientEnd)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
            }
            Text(
                "Settings",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Session info
            SectionHeader("Account")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Anonymous Session", color = TextSecondary, fontSize = 12.sp)
                Text(
                    viewModel.sessionId.take(16) + "...",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                if (isPremium) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(PremiumGold.copy(0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = PremiumGold, modifier = Modifier.size(12.dp))
                        Text("Premium Active", color = PremiumGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Premium section
            SectionHeader("Subscription")
            // Show "Saved Conversations" for premium users AND non-premium who
            // have saved at least one chat via rewarded ad.
            if (isPremium || hasSavedChat) {
                SettingsRow(
                    icon = Icons.Default.Bookmark,
                    title = "Saved Conversations",
                    subtitle = "View your saved chats",
                    iconTint = AccentCyan,
                    onClick = onOpenSavedChats
                )
            }
            if (isPremium) {
                SettingsToggleRow(
                    icon     = Icons.Default.Lock,
                    title    = "App Lock",
                    subtitle = "Require device lock when app resumes",
                    iconTint = PremiumGold,
                    checked  = appLockEnabled,
                    onChange = { viewModel.setAppLockEnabled(it) }
                )
            } else {
                SettingsRow(
                    icon     = Icons.Default.Lock,
                    title    = "App Lock",
                    subtitle = "Upgrade to Premium to enable",
                    iconTint = PremiumGold,
                    onClick  = onOpenPremium
                )
            }
            if (isPremium) {
                SettingsRow(
                    icon = Icons.Default.CardMembership,
                    title = "Manage Subscription",
                    subtitle = "Change plan or cancel",
                    iconTint = PremiumGold,
                    onClick = onOpenPremium
                )
            } else {
                SettingsRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Upgrade to Premium",
                    subtitle = "Unlock photos, audio & more",
                    iconTint = PremiumGold,
                    onClick = onOpenPremium
                )
            }
            Spacer(Modifier.height(4.dp))

            SectionHeader("Notifications")
            SettingsToggleRow(
                icon     = Icons.Default.Notifications,
                title    = "Daily activity ping",
                subtitle = "One reminder per day when strangers are around",
                checked  = notifsEnabled,
                onChange = { viewModel.setNotifsEnabled(it) }
            )
            Spacer(Modifier.height(4.dp))

            SectionHeader("About")
            SettingsRow(
                icon = Icons.Default.Shield,
                title = "Privacy Policy",
                subtitle = "How we protect your data",
                onClick = { uriHandler.openUri(Constants.URL_PRIVACY_POLICY) }
            )
            SettingsRow(
                icon = Icons.Default.Policy,
                title = "Terms of Service",
                subtitle = "Usage rules and guidelines",
                onClick = { uriHandler.openUri(Constants.URL_TERMS_OF_SERVICE) }
            )
            SettingsRow(
                icon = Icons.Default.Info,
                title = "App Version",
                subtitle = BuildConfig.VERSION_NAME,
                showArrow = false,
                onClick = {}
            )
        }

        // Banner ad pinned at bottom — non-premium only
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    iconTint: Color = AccentCyan,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(14.dp))
            .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
            .clickable { onChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = AccentCyan,
                checkedTrackColor   = AccentCyan.copy(alpha = 0.35f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = ElevatedCard
            )
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = AccentCyan,
    showArrow: Boolean = true,
    badge: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(14.dp))
            .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
        if (badge) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .background(PremiumGold.copy(0.15f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = PremiumGold, modifier = Modifier.size(10.dp))
                Text("Premium", color = PremiumGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        } else if (showArrow) {
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = TextMuted, modifier = Modifier.size(14.dp))
        }
    }
}
