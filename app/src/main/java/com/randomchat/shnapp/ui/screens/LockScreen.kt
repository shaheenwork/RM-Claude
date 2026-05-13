package com.randomchat.shnapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.ui.components.CyanButton

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val haptics = com.randomchat.shnapp.utils.LocalHaptics.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepSpace, GradientEnd)))
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lock icon
        androidx.compose.foundation.layout.Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .background(PremiumGold.copy(alpha = 0.12f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = PremiumGold,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "StrangerChat Locked",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Verify your identity to continue",
            color = TextMuted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(40.dp))

        CyanButton(
            text = "🔓  Unlock",
            onClick = { haptics.click(); onUnlock() }
        )
    }
}
