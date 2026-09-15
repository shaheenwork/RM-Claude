package com.randomchat.shnapp.billing

/**
 * Where the paywall was opened from. Travels in the premium route so the paywall
 * can match its headline to the trigger, and is attached to every paywall and
 * purchase analytics event so entry points can be compared on conversion.
 */
object PaywallSource {
    const val UNKNOWN          = "unknown"
    const val HOME_ROTATOR     = "home_rotator"
    const val HOME_REWARDS_CAP = "home_rewards_cap"
    const val CHAT_REWARDS_CAP = "chat_rewards_cap"
    const val SETTINGS         = "settings"
    const val MANAGE           = "manage"
    const val APP_LOCK         = "app_lock"
    const val VOICE            = "voice"
    const val PHOTO            = "photo"
    const val GIF              = "gif"
    const val REACTIONS        = "reactions"
    const val LIVE_TYPING      = "live_typing"
    const val SAVE_CHAT        = "save_chat"
    const val BADGE            = "badge"
    const val ADS              = "ads"
    const val PUSH_REMINDER    = "push_reminder"
}
