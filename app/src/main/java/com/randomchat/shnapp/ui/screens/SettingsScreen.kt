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
import androidx.compose.material.icons.filled.DeleteForever
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
import androidx.compose.runtime.setValue
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
    val deleteState     by viewModel.deleteAccountState.collectAsState()
    val uriHandler      = LocalUriHandler.current
    val haptics         = com.randomchat.shnapp.utils.LocalHaptics.current
    val context         = androidx.compose.ui.platform.LocalContext.current

    var showDeleteDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // On successful deletion: restart app to onboarding (fresh first-launch flow)
    androidx.compose.runtime.LaunchedEffect(deleteState) {
        if (deleteState is com.randomchat.shnapp.viewmodel.DeleteAccountState.Done) {
            haptics.success()
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.apply { flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                  android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
            (context as? android.app.Activity)?.finishAffinity()
        }
    }

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
                    onChange = { haptics.tick(); viewModel.setAppLockEnabled(it) }
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
                onChange = { haptics.tick(); viewModel.setNotifsEnabled(it) }
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

            Spacer(Modifier.height(12.dp))

            // ── Danger Zone ──────────────────────────────────────────────────
            SectionHeader("Danger Zone")
            DangerRow(
                icon     = Icons.Default.DeleteForever,
                title    = "Delete Account",
                subtitle = "Permanently erase all your data",
                onClick  = { haptics.warning(); showDeleteDialog = true }
            )
            Spacer(Modifier.height(12.dp))
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

    // ── Delete-account confirmation dialog ────────────────────────────────────
    if (showDeleteDialog) {
        DeleteAccountDialog(
            inProgress = deleteState is com.randomchat.shnapp.viewmodel.DeleteAccountState.InProgress,
            errorMsg = (deleteState as? com.randomchat.shnapp.viewmodel.DeleteAccountState.Error)?.message,
            onConfirm = { viewModel.deleteAccount() },
            onDismiss = {
                if (deleteState !is com.randomchat.shnapp.viewmodel.DeleteAccountState.InProgress) {
                    showDeleteDialog = false
                }
            }
        )
    }
}

@Composable
private fun DeleteAccountDialog(
    inProgress: Boolean,
    errorMsg: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DeleteForever, null, tint = com.randomchat.shnapp.theme.ErrorRed, modifier = Modifier.size(22.dp))
                Text("Delete Account?", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This will permanently delete:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text("• Your session and anonymous identity\n• Saved conversations\n• Subscription record\n• All locally stored data",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Text(
                    "This action cannot be undone. The app will restart.",
                    color = com.randomchat.shnapp.theme.ErrorRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                if (errorMsg != null) {
                    Text(
                        "Error: $errorMsg",
                        color = com.randomchat.shnapp.theme.ErrorRed,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = onConfirm,
                enabled = !inProgress
            ) {
                if (inProgress) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = com.randomchat.shnapp.theme.ErrorRed,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Deleting...", color = com.randomchat.shnapp.theme.ErrorRed)
                    }
                } else {
                    Text("Delete Forever", color = com.randomchat.shnapp.theme.ErrorRed, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun DangerRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(com.randomchat.shnapp.theme.ErrorRed.copy(0.06f), RoundedCornerShape(14.dp))
            .border(1.dp, com.randomchat.shnapp.theme.ErrorRed.copy(0.30f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = com.randomchat.shnapp.theme.ErrorRed, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = com.randomchat.shnapp.theme.ErrorRed, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = com.randomchat.shnapp.theme.ErrorRed.copy(0.5f), modifier = Modifier.size(14.dp))
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
