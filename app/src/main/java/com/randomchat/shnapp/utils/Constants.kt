package com.randomchat.shnapp.utils

object Constants {

    // Firebase RTDB paths
    const val PATH_PRESENCE = "presence"
    const val PATH_WAITING_QUEUE = "waitingQueue"
    const val PATH_SESSION_ASSIGNMENTS = "sessionAssignments"
    const val PATH_ROOMS = "rooms"
    const val PATH_MESSAGES = "messages"
    const val PATH_TYPING = "typing"
    const val PATH_REACTIONS = "reactions"
    const val PATH_DRAFT_TEXT = "draftText"
    const val PATH_REPORTED_ROOMS = "reportedRooms"
    const val PATH_ARCHIVED_ROOMS = "archivedRooms"

    // Firestore collections
    const val COL_SUBSCRIPTIONS = "subscriptions"
    const val COL_REPORTS = "reports"
    const val COL_BANNED_SESSIONS = "banned_sessions"
    const val COL_SAVED_CHATS = "saved_chats"
    const val COL_ANALYTICS = "analytics"

    // AdMob IDs (test IDs - replace with production)
    const val ADMOB_BANNER_ID        = "ca-app-pub-3940256099942544/6300978111"
    const val ADMOB_INTERSTITIAL_ID  = "ca-app-pub-3940256099942544/1033173712"
    const val ADMOB_NATIVE_ID        = "ca-app-pub-3940256099942544/2247696110"
    const val ADMOB_APP_OPEN_ID      = "ca-app-pub-3940256099942544/9257395921"
    const val ADMOB_REWARDED_ID      = "ca-app-pub-3940256099942544/5224354917"

    // Billing — product IDs must match Google Play Console exactly
    const val PRODUCT_PREMIUM_WEEKLY  = "premium_weekly"
    const val PRODUCT_PREMIUM_MONTHLY = "premium_monthly"
    const val PRODUCT_PREMIUM_YEARLY  = "premium_yearly"
    val ALL_PREMIUM_PRODUCTS = listOf(PRODUCT_PREMIUM_WEEKLY, PRODUCT_PREMIUM_MONTHLY, PRODUCT_PREMIUM_YEARLY)

    // Chat limits
    const val MAX_MESSAGE_LENGTH = 500
    const val MESSAGES_PAGE_SIZE = 50
    const val TYPING_INDICATOR_TIMEOUT_MS = 3000L
    const val DRAFT_TEXT_EXPIRE_MS = 6000L  // ghost disappears if no update for 6s
    const val DRAFT_TEXT_DEBOUNCE_MS = 120L  // max write rate to RTDB

    const val HEARTBEAT_INTERVAL_MS = 20_000L
    const val MAX_AUDIO_DURATION_MS = 60_000L
    const val QUEUE_ENTRY_TTL_MS = 60_000L

    // Matchmaking
    const val MIN_MATCH_DELAY_MS = 2_000L
    const val MAX_MATCH_DELAY_MS = 4_000L
    const val STRANGER_JOIN_CHIP_DELAY_MS = 2_500L

    // Flood protection
    const val MAX_MESSAGES_PER_MINUTE = 30
    const val SPAM_BAN_DURATION_MINUTES = 10L

    // Profanity filter
    val PROFANITY_LIST = setOf(
        "spam", "scam", "hack"
        // Add actual profanity words for your target language
    )

    // Interstitial frequency
    const val INTERSTITIAL_AFTER_N_CHATS = 3

    // Legal URLs — replace with your actual hosted pages
    const val URL_PRIVACY_POLICY = "https://www.privacypolicygenerator.info/"
    const val URL_TERMS_OF_SERVICE = "https://strcht.app/terms"

    // Session
    const val PREF_TERMS_ACCEPTED = "terms_accepted"
    const val PREF_SESSION_ID = "session_id"
    const val PREF_IS_PREMIUM = "is_premium"
    const val PREF_PREMIUM_EXPIRY = "premium_expiry"
    const val PREF_CHATS_SINCE_AD = "chats_since_ad"
}
