package com.randomchat.shnapp.theme

import androidx.compose.ui.graphics.Color

/*
 * ── DESIGN TOKEN: color ──
 * Palette intentionally desaturated vs pure web colors — premium apps
 * (Linear, Notion, Telegram) tone down brand colors 10-20%.
 *
 * Names preserved for backwards compatibility with existing call sites.
 * Values refreshed for premium feel.
 */

// ── Deep background palette ───────────────────────────────────────────────
val DeepSpace        = Color(0xFF0A0E18)  // was 0xFF080B14 — slight lift
val SpaceNavy        = Color(0xFF0D1120)
val CardSurface      = Color(0xFF111827)
val ElevatedCard     = Color(0xFF1A2236)
val SubtleBorder     = Color(0x1AFFFFFF)  // was solid #1E2D45 — now hairline 10% alpha

// ── Accent — toned-down cyan ──────────────────────────────────────────────
val AccentCyan       = Color(0xFF00C7E6)  // was 0xFF00D4FF — less neon
val AccentCyanDim    = Color(0xFF0098B8)
val AccentCyanGlow   = Color(0x4000C7E6)

// ── Premium gold — desaturated from eye-burn yellow ───────────────────────
val PremiumGold      = Color(0xFFE6B800)  // was 0xFFFFD700 — pro feel
val PremiumGoldDim   = Color(0xFFB8960C)
val PremiumGoldGlow  = Color(0x40E6B800)

// ── Status colors ─────────────────────────────────────────────────────────
val OnlineGreen      = Color(0xFF00C779)  // was 0xFF00E676 — toned
val WarningAmber     = Color(0xFFFFB938)
val ErrorRed         = Color(0xFFFF5252)

// ── Text — 4 levels ───────────────────────────────────────────────────────
val TextPrimary      = Color(0xFFF0F4FF)
val TextSecondary    = Color(0xFFA0AABF)  // was 0xFF8899BB — slightly warmer
val TextMuted        = Color(0xFF6B7790)  // was 0xFF4A5568 — better contrast on dark

// ── Bubble colors ─────────────────────────────────────────────────────────
val BubbleOutgoing   = Color(0xFF134568)  // was 0xFF1A4A6E — slightly toned
val BubbleOutgoingAlt= Color(0xFF0E3A56)
val BubbleIncoming   = Color(0xFF1A2236)
val SystemChipBg     = Color(0xFF0D1928)
val SystemChipBorder = Color(0xFF1E4060)

// ── Gradient stops ────────────────────────────────────────────────────────
val GradientStart    = Color(0xFF0A0E18)
val GradientMid      = Color(0xFF0A0F1E)
val GradientEnd      = Color(0xFF060912)
