package com.randomchat.shnapp.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.AccentCyanDim
import com.randomchat.shnapp.theme.AccentCyanGlow
import com.randomchat.shnapp.theme.CardSurface
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.ElevatedCard
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.ui.components.BrandMark
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * First-launch consent gate. Shown once — after acceptance the pref is persisted
 * and this screen never appears again. All three confirmations required (age + PP + ToS)
 * before the Continue button unlocks (clickwrap consent).
 */
@Composable
fun OnboardingScreen(onAccepted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ageChecked     by remember { mutableStateOf(false) }
    var privacyChecked by remember { mutableStateOf(false) }
    var termsChecked   by remember { mutableStateOf(false) }
    val canContinue = ageChecked && privacyChecked && termsChecked

    // Outer Box fills screen with gradient background; inner Column is scroll-driven height.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepSpace, GradientEnd)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(52.dp))

            // ── Brand header ──────────────────────────────────────────────────
            BrandMark(size = 72.dp)

            Spacer(Modifier.height(20.dp))

            Text(
                "Random Malayali",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                letterSpacing = 0.3.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "No email · No phone · No signup — just talk",
                color = TextSecondary,
                fontSize = 12.sp,
                letterSpacing = 0.4.sp
            )

            Spacer(Modifier.height(52.dp))

            // ── Section label ─────────────────────────────────────────────────
            Text(
                "TO CONTINUE, PLEASE CONFIRM",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            // ── Consent rows ──────────────────────────────────────────────────
            ConsentRow(
                checked = ageChecked,
                onToggle = { ageChecked = it },
                title = "I am 18 years of age or older"
            )

            Spacer(Modifier.height(8.dp))

            ConsentRow(
                checked = privacyChecked,
                onToggle = { privacyChecked = it },
                title = "Privacy Policy",
                subtitle = "I have read and agree to this policy",
                linkUrl = Constants.URL_PRIVACY_POLICY
            )

            Spacer(Modifier.height(8.dp))

            ConsentRow(
                checked = termsChecked,
                onToggle = { termsChecked = it },
                title = "Terms of Service",
                subtitle = "I accept the terms of use",
                linkUrl = Constants.URL_TERMS_OF_SERVICE
            )

            Spacer(Modifier.height(36.dp))

            // ── Continue button ───────────────────────────────────────────────
            val btnGradient = if (canContinue)
                Brush.horizontalGradient(listOf(AccentCyanDim, AccentCyan))
            else
                Brush.horizontalGradient(listOf(ElevatedCard, ElevatedCard))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(btnGradient)
                    .clickable(enabled = canContinue) {
                        scope.launch {
                            SessionManager.getInstance(context).markTermsAccepted()
                            onAccepted()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Continue",
                    color = if (canContinue) DeepSpace else TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "All three confirmations required to proceed.",
                color = TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

// ── Consent row ───────────────────────────────────────────────────────────────

/**
 * @param title   Primary label. Rendered in AccentCyan when [linkUrl] non-empty.
 * @param subtitle Optional secondary line (shown for policy rows).
 * @param linkUrl  When non-empty: tapping the ↗ icon opens the URL without toggling the checkbox.
 */
@Composable
private fun ConsentRow(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    title: String,
    subtitle: String = "",
    linkUrl: String = ""
) {
    val uriHandler = LocalUriHandler.current

    val borderColor by animateColorAsState(
        targetValue = if (checked) AccentCyan.copy(alpha = 0.55f) else SubtleBorder,
        animationSpec = tween(200),
        label = "border"
    )
    val checkboxBg by animateColorAsState(
        targetValue = if (checked) AccentCyan else ElevatedCard,
        animationSpec = tween(150),
        label = "checkbox_bg"
    )
    val checkboxBorderColor by animateColorAsState(
        targetValue = if (checked) AccentCyan else TextMuted.copy(alpha = 0.4f),
        animationSpec = tween(150),
        label = "checkbox_border"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Animated custom checkbox
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(checkboxBg)
                .border(1.5.dp, checkboxBorderColor, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = DeepSpace,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Title + subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (linkUrl.isNotEmpty()) AccentCyan else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }

        // Tapping ↗ opens the policy URL; its clickable intercepts the event
        // so the row's checkbox toggle does NOT fire simultaneously.
        if (linkUrl.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { uriHandler.openUri(linkUrl) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open",
                    tint = TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
