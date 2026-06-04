package com.randomchat.shnapp.utils

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Central telemetry — wraps Crashlytics custom keys + Firebase Analytics events.
 *
 * Single point of contact so call sites stay clean and event names stay consistent.
 * All calls are safe from any thread and degrade silently if Firebase is unavailable.
 *
 * ## Crashlytics custom keys (always-fresh metadata attached to crashes)
 *   • session_id   — current anonymous user
 *   • is_premium   — paid tier?
 *   • last_action  — last semantic action (set via [logAction])
 *
 * ## Analytics events (funnel tracking)
 *   chat_started, message_sent, chat_ended, premium_viewed, premium_purchased,
 *   pii_blocked, image_pii_blocked, report_submitted, app_lock_enabled,
 *   tutorial_completed, account_deleted
 */
object Telemetry {

    private val crash by lazy { runCatching { FirebaseCrashlytics.getInstance() }.getOrNull() }
    @Volatile private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (analytics == null) {
            analytics = runCatching { FirebaseAnalytics.getInstance(context.applicationContext) }.getOrNull()
        }
    }

    // ── Crashlytics custom keys ───────────────────────────────────────────────
    fun setSession(sessionId: String) {
        crash?.setCustomKey(KEY_SESSION_ID, sessionId)
        analytics?.setUserId(sessionId)
    }

    fun setPremium(isPremium: Boolean) {
        crash?.setCustomKey(KEY_IS_PREMIUM, isPremium)
        analytics?.setUserProperty(KEY_IS_PREMIUM, isPremium.toString())
    }

    /** Breadcrumb — semantic action label (e.g., "send_text", "start_match"). */
    fun logAction(action: String) {
        crash?.setCustomKey(KEY_LAST_ACTION, action)
        crash?.log(action)
    }

    /** Manual non-fatal exception report. */
    fun recordError(throwable: Throwable, context: String? = null) {
        if (context != null) crash?.log(context)
        crash?.recordException(throwable)
    }

    // ── Analytics events ──────────────────────────────────────────────────────
    fun chatStarted() = logEvent(EVENT_CHAT_STARTED)

    fun messageSent(type: String) = logEvent(EVENT_MESSAGE_SENT) { putString("type", type) }

    fun chatEnded(messageCount: Int) = logEvent(EVENT_CHAT_ENDED) { putInt("msg_count", messageCount) }

    fun premiumViewed(source: String) = logEvent(EVENT_PREMIUM_VIEWED) { putString("source", source) }

    fun premiumPurchased(productId: String) =
        logEvent(EVENT_PREMIUM_PURCHASED) { putString("product_id", productId) }

    fun piiBlocked(kind: String) = logEvent(EVENT_PII_BLOCKED) { putString("kind", kind) }

    fun imagePiiBlocked(kind: String) = logEvent(EVENT_IMAGE_PII_BLOCKED) { putString("kind", kind) }

    fun reportSubmitted(reason: String) =
        logEvent(EVENT_REPORT_SUBMITTED) { putString("reason", reason) }

    fun appLockEnabled(enabled: Boolean) =
        logEvent(EVENT_APP_LOCK_TOGGLED) { putString("state", if (enabled) "on" else "off") }

    fun tutorialCompleted() = logEvent(EVENT_TUTORIAL_COMPLETED)

    fun accountDeleted() = logEvent(EVENT_ACCOUNT_DELETED)

    fun rewardedAdEarned(reason: String) =
        logEvent(EVENT_REWARDED_EARNED) { putString("reason", reason) }

    /** User tapped a "Watch Ad" CTA (intent — before fill check). */
    fun rewardedAdTap(source: String) =
        logEvent(EVENT_REWARDED_TAP) { putString("source", source) }

    /** Ad inventory not ready — AdMob waterfall / fill miss. */
    fun rewardedAdUnavailable(source: String) =
        logEvent(EVENT_REWARDED_UNAVAILABLE) { putString("source", source) }

    /** Ad shown but user dismissed before earning reward (abandoned mid-ad). */
    fun rewardedAdDismissed(source: String) =
        logEvent(EVENT_REWARDED_DISMISSED) { putString("source", source) }

    // ── Gender — user property (slice-by) + per-selection event ───────────────
    /** Persistent user property — re-apply each session so EVERY event can be sliced by gender. */
    fun setUserGender(gender: String) {
        analytics?.setUserProperty(PROP_USER_GENDER, gender)
    }

    /** Fired each time the user picks/changes gender. Counts selections. */
    fun genderSelected(gender: String) =
        logEvent(EVENT_GENDER_SELECTED) { putString("gender", gender) }

    // ── Launch-funnel priority events (one-shot or per-chat) ─────────────────
    /** Fired ONCE per install — user finished the consent gate. */
    fun onboardingComplete() = logEvent(EVENT_ONBOARDING_COMPLETE)

    /** Fired ONCE per install — user tapped Start chatting for the first time. */
    fun firstMatchStarted() = logEvent(EVENT_FIRST_MATCH_STARTED)

    /** Fired ONCE per install — user sent their first outgoing text. */
    fun firstChatMsgSent() = logEvent(EVENT_FIRST_CHAT_MSG_SENT)

    /** Fired ONCE per chat — total messages (both sides) reached 5+. Engagement signal. */
    fun chatFiveMessages() = logEvent(EVENT_CHAT_5PLUS_MSGS)

    private inline fun logEvent(name: String, builder: Bundle.() -> Unit = {}) {
        val bundle = Bundle().apply(builder)
        analytics?.logEvent(name, bundle)
        logAction(name)
    }

    // ── Constants ─────────────────────────────────────────────────────────────
    // Crashlytics keys
    private const val KEY_SESSION_ID  = "session_id"
    private const val KEY_IS_PREMIUM  = "is_premium"
    private const val KEY_LAST_ACTION = "last_action"

    // Analytics event names (snake_case per Firebase convention, ≤40 chars)
    private const val EVENT_CHAT_STARTED       = "chat_started"
    private const val EVENT_MESSAGE_SENT       = "message_sent"
    private const val EVENT_CHAT_ENDED         = "chat_ended"
    private const val EVENT_PREMIUM_VIEWED     = "premium_viewed"
    private const val EVENT_PREMIUM_PURCHASED  = "premium_purchased"
    private const val EVENT_PII_BLOCKED        = "pii_blocked"
    private const val EVENT_IMAGE_PII_BLOCKED  = "image_pii_blocked"
    private const val EVENT_REPORT_SUBMITTED   = "report_submitted"
    private const val EVENT_APP_LOCK_TOGGLED   = "app_lock_toggled"
    private const val EVENT_TUTORIAL_COMPLETED = "tutorial_completed"
    private const val EVENT_ACCOUNT_DELETED    = "account_deleted"
    private const val EVENT_REWARDED_EARNED    = "rewarded_earned"

    // Funnel events
    private const val EVENT_ONBOARDING_COMPLETE  = "onboarding_complete"
    private const val EVENT_FIRST_MATCH_STARTED  = "first_match_started"
    private const val EVENT_FIRST_CHAT_MSG_SENT  = "first_chat_msg_sent"
    private const val EVENT_CHAT_5PLUS_MSGS      = "chat_5plus_msgs"

    // Rewarded-ad funnel
    private const val EVENT_REWARDED_TAP         = "rewarded_tap"
    private const val EVENT_REWARDED_UNAVAILABLE = "rewarded_unavailable"
    private const val EVENT_REWARDED_DISMISSED   = "rewarded_dismissed"

    // Gender
    private const val EVENT_GENDER_SELECTED      = "gender_selected"
    private const val PROP_USER_GENDER           = "user_gender"
}
