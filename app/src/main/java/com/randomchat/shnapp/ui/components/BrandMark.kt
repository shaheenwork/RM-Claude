package com.randomchat.shnapp.ui.components

import android.graphics.drawable.PaintDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.randomchat.shnapp.R
import com.randomchat.shnapp.theme.BrandGradients
import com.randomchat.shnapp.theme.TextPrimary

/**
 * Brand logo mark — a chat bubble carrying the Malayalam letter "മ" (ma).
 * Green brand gradient, chat-bubble corner (tail at bottom-start).
 * Purely visual; takes a [size] and renders nothing interactive.
 */
@Composable
fun BrandMark(
    size: Dp,
    modifier: Modifier = Modifier,
    iconScale: Float = 0.85f
) {
    val corner = size * 0.30f
    val tail = size * 0.12f

    val shape = RoundedCornerShape(
        topStart = corner,
        topEnd = corner,
        bottomEnd = corner,
        bottomStart = tail
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                clip = false
            )
            .clip(shape)
            .background(BrandGradients.primary)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.icon_no_bg),
            contentDescription = "Random Malayali",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(iconScale)
        )
    }
}
