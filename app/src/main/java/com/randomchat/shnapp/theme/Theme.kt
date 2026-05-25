package com.randomchat.shnapp.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = DeepSpace,
    primaryContainer = Color(0xFF0F3D2E),
    onPrimaryContainer = AccentCyan,
    secondary = PremiumGold,
    onSecondary = DeepSpace,
    secondaryContainer = Color(0xFF3A2E12),
    onSecondaryContainer = PremiumGold,
    tertiary = OnlineGreen,
    onTertiary = DeepSpace,
    background = DeepSpace,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = TextSecondary,
    outline = SubtleBorder,
    error = ErrorRed,
    onError = TextPrimary
)

@Composable
fun StrangerChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
