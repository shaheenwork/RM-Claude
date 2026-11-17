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
 *
 * ## Subscription funnel (each event carries `source` = paywall entry point)
 *   premium_viewed → paywall_plan_select → purchase_start →
 *   premium_purchased | purchase_pending | purchase_cancel | purchase_fail.
 *   `via` separates the paywall CTA ("paywall") from the exit offer ("exit_offer").
 *   upsell_shown(placement) counts impressions of upsell surfaces outside the paywall.
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

    fun premiumPurchased(productId: String, source: String?, via: String?) =
        logEvent(EVENT_PREMIUM_PURCHASED) { putPurchaseParams(productId, source, via) }

    fun paywallPlanSelected(productId: String, source: String) =
        logEvent(EVENT_PAYWALL_PLAN_SELECT) {
            putString("product_id", productId)
            putString("source", source)
        }

    fun purchaseStarted(productId: String, source: String, via: String) =
        logEvent(EVENT_PURCHASE_START) { putPurchaseParams(productId, source, via) }

    fun purchaseCancelled(productId: String?, source: String?, via: String?) =
        logEvent(EVENT_PURCHASE_CANCEL) { putPurchaseParams(productId, source, via) }

    /** Payment started but not confirmed yet (UPI etc.) — premium is granted later. */
    fun purchasePending(productId: String?, source: String?, via: String?) =
        logEvent(EVENT_PURCHASE_PENDING) { putPurchaseParams(productId, source, via) }

    fun purchaseFailed(productId: String?, source: String?, via: String?, responseCode: Int) =
        logEvent(EVENT_PURCHASE_FAIL) {
            putPurchaseParams(productId, source, via)
            putInt("code", responseCode)
        }

    /** Impression of an upsell surface outside the paywall (typing teaser, sheets, strips). */
    fun upsellShown(placement: String) =
        logEvent(EVENT_UPSELL_SHOWN) { putString("placement", placement) }

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

    fun rewardedLoadRequested(placement: String) =
        logEvent(EVENT_REWARDED_LOAD_REQ) { putString("placement", placement) }

    fun rewardedLoadSucceeded(placement: String, latencyMs: Long) =
        logEvent(EVENT_REWARDED_LOAD_OK) {
            putString("placement", placement)
            putLong("latency_ms", latencyMs)
        }

    fun rewardedLoadFailed(placement: String, errorCode: Int, errorMessage: String) =
        logEvent(EVENT_REWARDED_LOAD_FAIL) {
            putString("placement", placement)
            putInt("code", errorCode)
            putString("message", errorMessage)
        }

    fun rewardedShowAttempted(placement: String, isReady: Boolean) =
        logEvent(EVENT_REWARDED_SHOW_ATTEMPT) {
            putString("placement", placement)
            putBoolean("is_ready", isReady)
        }

    fun rewardedShowSucceeded(placement: String) =
        logEvent(EVENT_REWARDED_SHOW_OK) { putString("placement", placement) }

    fun rewardedShowFailed(placement: String, errorCode: Int, errorMessage: String) =
        logEvent(EVENT_REWARDED_SHOW_FAIL) {
            putString("placement", placement)
            putInt("code", errorCode)
            putString("message", errorMessage)
        }

    fun rewardedImpressionRecorded(placement: String) =
        logEvent(EVENT_REWARDED_IMPRESSION) { putString("placement", placement) }

    fun rewardedAdExpired(placement: String, ageMs: Long) =
        logEvent(EVENT_REWARDED_EXPIRED) {
            putString("placement", placement)
            putLong("age_ms", ageMs)
        }

    // ── Banner ad funnel ──────────────────────────────────────────────────────
    fun bannerLoadRequested(placement: String) =
        logEvent(EVENT_BANNER_LOAD_REQ) { putString("placement", placement) }

    fun bannerLoadSucceeded(placement: String, latencyMs: Long) =
        logEvent(EVENT_BANNER_LOAD_OK) {
            putString("placement", placement)
            putLong("latency_ms", latencyMs)
        }

    fun bannerLoadFailed(placement: String, errorCode: Int, errorMessage: String) =
        logEvent(EVENT_BANNER_LOAD_FAIL) {
            putString("placement", placement)
            putInt("code", errorCode)
            putString("message", errorMessage)
        }

    fun bannerImpressionRecorded(placement: String) =
        logEvent(EVENT_BANNER_IMPRESSION) { putString("placement", placement) }

    fun bannerClicked(placement: String) =
        logEvent(EVENT_BANNER_CLICKED) { putString("placement", placement) }

    // ── App Open ad funnel ────────────────────────────────────────────────────
    fun appOpenLoadRequested() = logEvent(EVENT_APP_OPEN_LOAD_REQ)

    fun appOpenLoadSucceeded(latencyMs: Long) =
        logEvent(EVENT_APP_OPEN_LOAD_OK) { putLong("latency_ms", latencyMs) }

    fun appOpenLoadFailed(errorCode: Int, errorMessage: String) =
        logEvent(EVENT_APP_OPEN_LOAD_FAIL) {
            putInt("code", errorCode)
            putString("message", errorMessage)
        }

    fun appOpenShowAttempted(isReady: Boolean) =
        logEvent(EVENT_APP_OPEN_SHOW_ATTEMPT) { putBoolean("is_ready", isReady) }

    fun appOpenShowSucceeded() = logEvent(EVENT_APP_OPEN_SHOW_OK)

    fun appOpenShowFailed(errorCode: Int, errorMessage: String) =
        logEvent(EVENT_APP_OPEN_SHOW_FAIL) {
            putInt("code", errorCode)
            putString("message", errorMessage)
        }

    fun appOpenSuppressed(reason: String) =
        logEvent(EVENT_APP_OPEN_SUPPRESSED) { putString("reason", reason) }

    fun appOpenImpressionRecorded() = logEvent(EVENT_APP_OPEN_IMPRESSION)

    fun appOpenDismissed() = logEvent(EVENT_APP_OPEN_DISMISSED)

    fun appOpenExpired(ageMs: Long) =
        logEvent(EVENT_APP_OPEN_EXPIRED) { putLong("age_ms", ageMs) }

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

    private fun Bundle.putPurchaseParams(productId: String?, source: String?, via: String?) {
        putString("product_id", productId ?: UNKNOWN)
        putString("source", source ?: UNKNOWN)
        putString("via", via ?: UNKNOWN)
    }

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
    private const val EVENT_REWARDED_TAP          = "rewarded_tap"
    private const val EVENT_REWARDED_UNAVAILABLE  = "rewarded_unavailable"
    private const val EVENT_REWARDED_DISMISSED    = "rewarded_dismissed"
    private const val EVENT_REWARDED_LOAD_REQ     = "rewarded_load_requested"
    private const val EVENT_REWARDED_LOAD_OK      = "rewarded_load_succeeded"
    private const val EVENT_REWARDED_LOAD_FAIL    = "rewarded_load_failed"
    private const val EVENT_REWARDED_SHOW_ATTEMPT = "rewarded_show_attempted"
    private const val EVENT_REWARDED_SHOW_OK      = "rewarded_show_succeeded"
    private const val EVENT_REWARDED_SHOW_FAIL    = "rewarded_show_failed"
    private const val EVENT_REWARDED_IMPRESSION   = "rewarded_impression_rec"
    private const val EVENT_REWARDED_EXPIRED      = "rewarded_ad_expired"

    // Banner-ad funnel
    private const val EVENT_BANNER_LOAD_REQ       = "banner_load_requested"
    private const val EVENT_BANNER_LOAD_OK        = "banner_load_succeeded"
    private const val EVENT_BANNER_LOAD_FAIL      = "banner_load_failed"
    private const val EVENT_BANNER_IMPRESSION     = "banner_impression_rec"
    private const val EVENT_BANNER_CLICKED        = "banner_clicked"

    // App Open-ad funnel
    private const val EVENT_APP_OPEN_LOAD_REQ     = "app_open_load_requested"
    private const val EVENT_APP_OPEN_LOAD_OK      = "app_open_load_succeeded"
    private const val EVENT_APP_OPEN_LOAD_FAIL    = "app_open_load_failed"
    private const val EVENT_APP_OPEN_SHOW_ATTEMPT = "app_open_show_attempted"
    private const val EVENT_APP_OPEN_SHOW_OK      = "app_open_show_succeeded"
    private const val EVENT_APP_OPEN_SHOW_FAIL    = "app_open_show_failed"
    private const val EVENT_APP_OPEN_SUPPRESSED   = "app_open_suppressed"
    private const val EVENT_APP_OPEN_IMPRESSION   = "app_open_impression_rec"
    private const val EVENT_APP_OPEN_DISMISSED    = "app_open_dismissed"
    private const val EVENT_APP_OPEN_EXPIRED      = "app_open_expired"

    // Gender
    private const val EVENT_GENDER_SELECTED      = "gender_selected"
    private const val PROP_USER_GENDER           = "user_gender"

    // Subscription funnel
    private const val EVENT_PAYWALL_PLAN_SELECT  = "paywall_plan_select"
    private const val EVENT_PURCHASE_START       = "purchase_start"
    private const val EVENT_PURCHASE_CANCEL      = "purchase_cancel"
    private const val EVENT_PURCHASE_PENDING     = "purchase_pending"
    private const val EVENT_PURCHASE_FAIL        = "purchase_fail"
    private const val EVENT_UPSELL_SHOWN         = "upsell_shown"
    private const val UNKNOWN                    = "unknown"
}
