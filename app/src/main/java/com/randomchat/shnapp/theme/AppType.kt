package com.randomchat.shnapp.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography scale — replaces inline `fontSize = N.sp` literals.
 *
 * - Body / UI: Inter (system fallback). Geometric, modern, readable.
 * - Display (headline-emotion moments): Serif Italic for "premium magazine app" feel.
 *   System serif fallback for now; switch to bundled Instrument Serif later for full effect.
 *
 * Negative letter-spacing on display/title — premium signature (Linear, Notion, Vercel).
 */
object AppType {

    /** Serif italic — use for hero greetings, key emotional accents. */
    val displaySerif = TextStyle(
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        fontSize = 34.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    )

    val display = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Black,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    )
    val title = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    )
    val heading = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp
    )
    val body = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    )
    val bodyEmph = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp
    )
    val callout = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp
    )
    val caption = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    )
    val micro = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 12.sp,
        letterSpacing = 0.4.sp
    )
}
