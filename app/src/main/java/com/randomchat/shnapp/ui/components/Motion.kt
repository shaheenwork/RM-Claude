package com.randomchat.shnapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.randomchat.shnapp.theme.Motion
import kotlinx.coroutines.delay

/**
 * Staggered fade + rise entrance — perceived smoothness via delay.
 * Reusable across screens; replaces per-screen reimplementations.
 *
 * @param delayMs ms to wait before showing (use index * 80 for staggered lists)
 * @param riseDp how far the content rises from on enter (default ~6dp)
 */
@Composable
fun StaggeredFadeIn(
    delayMs: Int = 0,
    riseDivisor: Int = 4,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.medium()) + slideInVertically(Motion.medium()) { it / riseDivisor },
        modifier = modifier
    ) {
        content()
    }
}
