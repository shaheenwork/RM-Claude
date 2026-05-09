package com.randomchat.shnapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.theme.SystemChipBg
import com.randomchat.shnapp.theme.SystemChipBorder
import com.randomchat.shnapp.theme.TextSecondary

@Composable
fun SystemChip(
    text: String,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    var visible by remember { mutableStateOf(!animated) }
    LaunchedEffect(text) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        modifier = modifier.wrapContentWidth(Alignment.CenterHorizontally)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(SystemChipBg, RoundedCornerShape(20.dp))
                .border(1.dp, SystemChipBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = text,
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 0.3.sp
            )
        }
    }
}

@Composable
fun OnlineStatusChip(isOnline: Boolean) {
    val text = if (isOnline) "● Online" else "● Away"
    val color = if (isOnline) com.randomchat.shnapp.theme.OnlineGreen else com.randomchat.shnapp.theme.TextMuted

    Box(
        modifier = Modifier
            .background(
                if (isOnline) com.randomchat.shnapp.theme.OnlineGreen.copy(alpha = 0.12f)
                else com.randomchat.shnapp.theme.TextMuted.copy(alpha = 0.08f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = color, fontSize = 11.sp, letterSpacing = 0.5.sp)
    }
}
