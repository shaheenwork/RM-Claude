package com.randomchat.shnapp.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.GradientMid
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.ui.components.PulseLoader
import kotlinx.coroutines.delay

private val STATUS_TEXTS = listOf(
    "Connecting to chat network...",
    "Finding stranger online...",
    "Preparing secure private room...",
    "Encrypting anonymous session...",
    "Establishing connection...",
    "Almost there..."
)

@Composable
fun MatchmakingDialog(
    visible: Boolean,
    onCancel: () -> Unit
) {
    if (!visible) return

    var statusIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            statusIndex = (statusIndex + 1) % STATUS_TEXTS.size
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(DeepSpace, GradientMid, GradientEnd)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                PulseLoader(color = AccentCyan, size = 60f)

                Spacer(Modifier.height(40.dp))

                Text(
                    "Finding your stranger",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 0.3.sp
                )

                Spacer(Modifier.height(12.dp))

                AnimatedContent(
                    targetState = statusIndex,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it }) togetherWith fadeOut()
                    },
                    label = "status"
                ) { idx ->
                    Text(
                        text = STATUS_TEXTS[idx],
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(60.dp))

                TextButton(onClick = onCancel) {
                    Text("Cancel", color = TextMuted, fontSize = 14.sp)
                }
            }
        }
    }
}
