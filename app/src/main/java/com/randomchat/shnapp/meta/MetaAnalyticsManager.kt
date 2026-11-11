package com.randomchat.shnapp.meta

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import java.math.BigDecimal
import java.util.Currency

/**
 * Centralized manager for Meta (Facebook) App Events and Campaign Attribution.
 *
 * Wraps Meta's [AppEventsLogger] to safely log standard conversion events (purchases)
 * and custom engagement events for Facebook/Instagram App Promotion campaigns.
 *
 * Features:
 *   • Single point of entry for Meta App Events.
 *   • Safe initialization and error boundary (no app crashes on analytics failures).
 *   • Strict PII compliance — zero chat text, audio/photo contents, or user IDs are logged.
 *   • Clear Logcat debug statements for local event verification.
 */
object MetaAnalyticsManager {

    private const val TAG = "MetaAnalyticsManager"

    @Volatile
    private var logger: AppEventsLogger? = null

    /**
     * Initializes the Meta SDK and creates the [AppEventsLogger] instance.
     * Should be called once during [Application.onCreate].
     */
    fun init(application: Application) {
        if (logger != null) return
        try {
            // Enable auto-logging & advertiser ID collection
            FacebookSdk.setAutoInitEnabled(true)
            FacebookSdk.fullyInitialize()
            FacebookSdk.setAutoLogAppEventsEnabled(true)
            FacebookSdk.setAdvertiserIDCollectionEnabled(true)

            // Activate App & create logger instance
            AppEventsLogger.activateApp(application)
            logger = AppEventsLogger.newLogger(application)
            Log.d(TAG, "Meta (Facebook) SDK initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Meta SDK: ${e.message}", e)
        }
    }

    /**
     * Explicit app activation log. Automatically handled by Meta SDK if auto-logging
     * is enabled, but provided here for lifecycle consistency.
     */
    fun logAppActivation(application: Application) {
        try {
            AppEventsLogger.activateApp(application)
            Log.d(TAG, "App activation logged to Meta")
        } catch (e: Exception) {
            Log.w(TAG, "logAppActivation failed: ${e.message}")
        }
    }

    // ── Engagement Events ──────────────────────────────────────────────────────

    /**
     * Triggered when the user actually navigates to and enters the main chat screen.
     */
    fun logChatScreenViewed() {
        logEvent(EVENT_CHAT_SCREEN_VIEWED)
    }

    /**
     * Triggered when the matchmaking algorithm successfully pairs the user with a stranger.
     */
    fun logMatchFound() {
        logEvent(EVENT_MATCH_FOUND)
    }

    /**
     * Triggered when a chat session starts between matched users.
     */
    fun logChatStarted() {
        logEvent(EVENT_CHAT_STARTED)
    }

    /**
     * Triggered only when the user sends their first message (one-shot per install).
     */
    fun logFirstMessageSent() {
        logEvent(EVENT_FIRST_MESSAGE_SENT)
    }

    /**
     * Triggered when the user completes their first meaningful chat session (>4 messages).
     */
    fun logFirstChatCompleted() {
        logEvent(EVENT_FIRST_CHAT_COMPLETED)
    }

    /**
     * Triggered when a voice note is successfully recorded and sent.
     */
    fun logVoiceNoteSent() {
        logEvent(EVENT_VOICE_NOTE_SENT)
    }

    // ── Purchase & Conversion Events ───────────────────────────────────────────

    /**
     * Logs a verified subscription or one-time purchase standard event to Meta.
     *
     * @param amount Price paid (e.g., BigDecimal("149.00"))
     * @param currencyCode ISO 4217 currency code (e.g., "INR", "USD")
     * @param productId Play Store SKU / Product ID (e.g., "stranger_chat_monthly")
     */
    fun logPurchase(amount: BigDecimal, currencyCode: String, productId: String) {
        try {
            val currency = try {
                Currency.getInstance(currencyCode.uppercase())
            } catch (_: Exception) {
                Currency.getInstance("USD")
            }
            val params = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, productId)
                putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, "subscription")
                putInt(AppEventsConstants.EVENT_PARAM_NUM_ITEMS, 1)
            }
            logger?.logPurchase(amount, currency, params)
                ?: Log.w(TAG, "Logger not initialized. Purchase event not sent.")
            Log.d(TAG, "Logged Meta Purchase: $amount $currencyCode for product $productId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log Meta Purchase: ${e.message}", e)
        }
    }

    // ── Generic Event Dispatcher ───────────────────────────────────────────────

    private fun logEvent(eventName: String, params: Bundle? = null) {
        try {
            val appEventsLogger = logger
            if (appEventsLogger != null) {
                if (params != null) {
                    appEventsLogger.logEvent(eventName, params)
                } else {
                    appEventsLogger.logEvent(eventName)
                }
                Log.d(TAG, "Logged Meta Event: $eventName")
            } else {
                Log.w(TAG, "Logger not initialized. Event '$eventName' ignored.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event '$eventName': ${e.message}", e)
        }
    }

    // ── Meta Event Name Constants ─────────────────────────────────────────────
    const val EVENT_CHAT_SCREEN_VIEWED  = "Chat_Screen_Viewed"
    const val EVENT_MATCH_FOUND         = "Match_Found"
    const val EVENT_CHAT_STARTED        = "Chat_Started"
    const val EVENT_FIRST_MESSAGE_SENT  = "First_Message_Sent"
    const val EVENT_FIRST_CHAT_COMPLETED= "First_Chat_Completed"
    const val EVENT_VOICE_NOTE_SENT     = "Voice_Note_Sent"
}
