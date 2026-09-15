package com.randomchat.shnapp.theme

import androidx.compose.ui.graphics.Color

/*
 * ── KERALA TRUST palette ──
 * Trust-coded green (safety / live / your-messages) + warm brass (premium / accent)
 * on deep backwater green-black, with warm cream text.
 *
 * Variable NAMES are preserved for back-compat across the app:
 *   AccentCyan   → primary brand (now green)
 *   BrandViolet  → brand secondary (now deep green) — BrandGradients.primary = green→deep-green
 *   PremiumGold  → brass
 *   AuroraBlue   → soft teal (info / system / live accents)
 */

// ── Deep background palette ───────────────────────────────────────────────
val DeepSpace        = Color(0xFF0A1410)  // backwater green-black (deepest)
val SpaceNavy        = Color(0xFF0C1A14)  // gradient mid
val CardSurface      = Color(0xFF11201A)  // cards
val ElevatedCard     = Color(0xFF18281F)  // sheets, modals
val SubtleBorder     = Color(0x16EEECDE)  // ~9% warm cream — hairline

// ── Brand: primary green (trust) ──────────────────────────────────────────
// AccentCyan name kept for back-compat; semantics now = brand green.
val AccentCyan       = Color(0xFF2FB68C)  // green — primary brand
val AccentCyanDim    = Color(0xFF268E6E)  // green pressed/dim
val AccentCyanGlow   = Color(0x402FB68C)  // 25% green glow

// ── Deep green (brand secondary) ──────────────────────────────────────────
val BrandViolet      = Color(0xFF1C6B4F)
val BrandVioletDim   = Color(0xFF155A42)
val BrandVioletGlow  = Color(0x401C6B4F)

// ── Aurora (info / system accent) — soft teal ─────────────────────────────
val AuroraBlue       = Color(0xFF4FC8AD)
val AuroraBlueDim    = Color(0xFF3FA890)

// ── Brass (amber) — premium tier, streaks, badges ─────────────────────────
val PremiumGold      = Color(0xFFE3B964)
val PremiumGoldDim   = Color(0xFFC99C48)
val PremiumGoldGlow  = Color(0x40E3B964)

// ── Status colors ─────────────────────────────────────────────────────────
val OnlineGreen      = Color(0xFF2FB68C)
val WarningAmber     = Color(0xFFFFB938)
val ErrorRed         = Color(0xFFFF5252)

// ── Text — warm cream hierarchy (4 levels) ────────────────────────────────
val TextPrimary      = Color(0xFFF1EDDD)  // warm cream, not pure white
val TextSecondary    = Color(0xFF9FAE9E)  // sage gray-green
val TextMuted        = Color(0xFF66766A)  // tertiary
val TextDisabled     = Color(0xFF3E4A40)
val PinkSoft         = Color(0xFFEBC987)  // italic display accent (brass-light)

// ── Bubble colors ─────────────────────────────────────────────────────────
// Outgoing uses brand gradient (green) — these are fallbacks.
val BubbleOutgoing   = Color(0xFF259E78)  // mid-green fallback
val BubbleOutgoingAlt= Color(0xFF1C6B4F)
val BubbleIncoming   = Color(0x12F1EDDD)  // ~7% warm cream — glassmorphism feel
val BubbleIncomingBorder = Color(0x16F1EDDD)

// ── System chip ───────────────────────────────────────────────────────────
val SystemChipBg     = Color(0x144FC8AD)  // ~8% soft teal
val SystemChipBorder = Color(0x334FC8AD)

// ── Gradient stops ────────────────────────────────────────────────────────
val GradientStart    = Color(0xFF0A1410)
val GradientMid      = Color(0xFF0C1A14)
val GradientEnd      = Color(0xFF06100B)
