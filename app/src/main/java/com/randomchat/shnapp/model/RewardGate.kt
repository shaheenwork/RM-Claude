package com.randomchat.shnapp.model

/**
 * Daily rewarded-ad earn state. Single source of truth driving both the Home
 * `RewardsCard` and the Chat `AttachSheet` "Watch Ad" tile.
 *
 *  - [Ready]      → user can earn now. [watchesLeft] = remaining ads today.
 *  - [Cooldown]   → just earned; must wait. [secondsLeft] counts down to 0.
 *  - [CapReached] → daily cap hit. Show "Upgrade for unlimited" upsell.
 */
sealed class RewardGate {
    data class Ready(val watchesLeft: Int) : RewardGate()
    data class Cooldown(val secondsLeft: Long) : RewardGate()
    data object CapReached : RewardGate()
}
