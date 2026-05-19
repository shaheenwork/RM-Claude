package com.randomchat.shnapp.theme

import androidx.compose.ui.graphics.Color

/*
 * ── MIDNIGHT SUBDUED palette ──
 * Pink-violet brand desaturated ~20% from neon (premium 20-40 audience).
 * Aurora blue + Ember amber as functional accents.
 *
 * Names preserved for back-compat. Values now Midnight Subdued.
 */

// ── Deep background palette ───────────────────────────────────────────────
val DeepSpace        = Color(0xFF0C0814)  // purple-tinted black (deepest)
val SpaceNavy        = Color(0xFF120E1C)  // gradient mid
val CardSurface      = Color(0xFF1A1426)  // cards
val ElevatedCard     = Color(0xFF221A30)  // sheets, modals
val SubtleBorder     = Color(0x14F0E8F5)  // ~8% warm white — hairline

// ── Brand: Midnight Subdued (pink → violet) ───────────────────────────────
// AccentCyan name kept for back-compat; semantics now = brand pink.
val AccentCyan       = Color(0xFFE04A8C)  // pink — primary brand (desaturated from neon)
val AccentCyanDim    = Color(0xFFC03A7C)  // pink pressed/dim
val AccentCyanGlow   = Color(0x40E04A8C)  // 25% pink glow

// ── Violet (brand secondary) ──────────────────────────────────────────────
val BrandViolet      = Color(0xFF6E4FE0)
val BrandVioletDim   = Color(0xFF5A3FC2)
val BrandVioletGlow  = Color(0x406E4FE0)

// ── Aurora (accent blue) — replaces tech-cyan for live/typing/online ─────
val AuroraBlue       = Color(0xFF66D7FF)
val AuroraBlueDim    = Color(0xFF4AB8E6)

// ── Ember (amber) — premium tier, streaks, badges ────────────────────────
val PremiumGold      = Color(0xFFFFD062)
val PremiumGoldDim   = Color(0xFFFFA040)
val PremiumGoldGlow  = Color(0x40FFD062)

// ── Status colors ─────────────────────────────────────────────────────────
val OnlineGreen      = Color(0xFF00C779)
val WarningAmber     = Color(0xFFFFB938)
val ErrorRed         = Color(0xFFFF5252)

// ── Text — warm cream hierarchy (4 levels) ────────────────────────────────
val TextPrimary      = Color(0xFFF0E8F5)  // warm cream, not pure white
val TextSecondary    = Color(0xFFB4A6C2)  // muted lavender-gray
val TextMuted        = Color(0xFF7A6F87)  // tertiary
val TextDisabled     = Color(0xFF4A4254)
val PinkSoft         = Color(0xFFF08AB5)  // italic display accent

// ── Bubble colors ─────────────────────────────────────────────────────────
// Outgoing uses brand gradient (not a solid) — these are fallbacks.
val BubbleOutgoing   = Color(0xFFB23A7A)  // mid-tone pink/violet for fallback
val BubbleOutgoingAlt= Color(0xFF7B4FB0)
val BubbleIncoming   = Color(0x12F0E8F5)  // ~7% warm white — glassmorphism feel
val BubbleIncomingBorder = Color(0x14F0E8F5)

// ── System chip ───────────────────────────────────────────────────────────
val SystemChipBg     = Color(0x1466D7FF)  // ~8% aurora blue
val SystemChipBorder = Color(0x3366D7FF)

// ── Gradient stops ────────────────────────────────────────────────────────
val GradientStart    = Color(0xFF0C0814)
val GradientMid      = Color(0xFF120E1C)
val GradientEnd      = Color(0xFF080610)
