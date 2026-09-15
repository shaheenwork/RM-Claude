package com.randomchat.shnapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.ElevatedCard
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.PremiumGoldDim
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary

/**
 * Bottom-sheet upsell shown when a user taps "Watch Ad" right as the daily
 * reward cap flips (race condition). Premium-feel: brass medal, perk list,
 * brass CTA → PremiumScreen.
 *
 * Not the primary upsell surface — the static RewardsCard/AttachSheet tile
 * already morph to "Upgrade" at cap. This is the defensive catch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardCapSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            // ── Brass medal ───────────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(92.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFF4D89B), PremiumGold, PremiumGoldDim)),
                        RoundedCornerShape(24.dp)
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            ) {
                Text("✦", fontSize = 44.sp, color = Color(0xFF3A2A00), fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "You've used today's ",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    "free credits",
                    color = PremiumGold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = (-0.3).sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Want unlimited photos, voice notes & GIFs? Skip the daily limit with Premium.",
                color = TextSecondary,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── Perks list ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F1610))
                    .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PerkRow("Unlimited photos, voice notes & GIFs")
                PerkRow("React to messages & live typing preview")
                PerkRow("Save chats · Zero ads")
            }

            Spacer(Modifier.height(20.dp))

            // ── Brass Upgrade button ──────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.horizontalGradient(listOf(PremiumGold, PremiumGoldDim))
                    )
                    .clickable(onClick = onUpgrade)
                    .padding(vertical = 15.dp)
            ) {
                Text(
                    "Upgrade",
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
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    "Maybe later",
                    color = TextMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                "Free credits reset at midnight.",
                color = TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PerkRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.18f))
        ) {
            Text("✓", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
