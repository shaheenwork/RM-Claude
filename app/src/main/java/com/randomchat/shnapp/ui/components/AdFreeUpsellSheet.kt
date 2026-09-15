package com.randomchat.shnapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.randomchat.shnapp.theme.ElevatedCard
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.PremiumGoldDim
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary

/**
 * "Tired of ads?" upsell shown right after a full-screen ad closes. Rate-limited by
 * [com.randomchat.shnapp.utils.SessionManager.recordInterstitialShown]: from the
 * 2nd interstitial of the day, at most once per day. Same brass style as
 * [RewardCapSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdFreeUpsellSheet(
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
                    .size(84.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFF4D89B), PremiumGold, PremiumGoldDim)),
                        RoundedCornerShape(24.dp)
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            ) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    tint = Color(0xFF3A2A00),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tired of ",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    "ads?",
                    color = PremiumGold,
                    fontSize = 22.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = (-0.3).sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Premium removes every ad — plus unlimited photos, voice notes and live typing preview.",
                color = TextSecondary,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(22.dp))

            // ── Brass CTA ─────────────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.horizontalGradient(listOf(PremiumGold, PremiumGoldDim)))
                    .clickable(onClick = onUpgrade)
                    .padding(vertical = 15.dp)
            ) {
                Text(
                    "Go ad-free",
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
                    "Not now",
                    color = TextMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
