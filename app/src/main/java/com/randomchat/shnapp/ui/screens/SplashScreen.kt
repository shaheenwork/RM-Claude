package com.randomchat.shnapp.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.GradientMid
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.ui.components.BrandMark
import com.randomchat.shnapp.ui.components.PulseLoader
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onReady: () -> Unit) {
    var animStarted by remember { mutableStateOf(false) }

    val iconScale by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0.3f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "icon_scale"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0f,
        animationSpec = tween(600, delayMillis = 400),
        label = "text_alpha"
    )

    LaunchedEffect(Unit) {
        delay(100)
        animStarted = true
        delay(1800)
        onReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(DeepSpace, GradientMid, GradientEnd))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .scale(iconScale)
            ) {
                PulseLoader(color = AccentCyan, size = 50f)
                BrandMark(size = 58.dp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Random Malayali",
                color = AccentCyan.copy(textAlpha),
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Talk to Malayalis worldwide, anytime.",
                color = TextSecondary.copy(textAlpha),
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}
