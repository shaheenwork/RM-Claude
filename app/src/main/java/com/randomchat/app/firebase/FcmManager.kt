package com.randomchat.app.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import java.util.TimeZone

/**
 * Handles FCM token capture + timezone-bucket topic subscription.
 *
 * Strategy: each device subscribes to a topic named `tz_p330` (UTC+5:30, e.g. IST) or
 * `tz_n300` (UTC-5:00, e.g. EST). Cloud Function `dispatchDailyNudge` iterates known
 * offset buckets and sends to whichever bucket's local time currently matches today's
 * randomly-picked send minute. Keeps the dispatch O(1) regardless of user count.
 *
 * Token is also stored at `fcmTokens/{sessionId}` for direct (non-topic) sends later.
 */
object FcmManager {

    private const val TAG       = "FcmManager"
    private const val PREFS     = "fcm_prefs"
    private const val KEY_TOPIC = "subscribed_tz_topic"

    /** Call once from RandomChatApp.onCreate(). Idempotent. */
    fun init(ctx: Context, sessionId: String, notifsEnabled: Boolean = true) {
        registerNotificationChannels(ctx)
        captureToken(ctx, sessionId)
        if (notifsEnabled) syncTzTopic(ctx) else unsubscribeAll(ctx)
    }

    // ── Notification channels ──────────────────────────────────────────────────

    private fun registerNotificationChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Activity = "people online" daily nudge — mutable, default importance
        nm.createNotificationChannel(
            NotificationChannel(
                "activity",
                "Activity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily ping when strangers are around to chat"
            }
        )
    }

    // ── Token ──────────────────────────────────────────────────────────────────

    private fun captureToken(ctx: Context, sessionId: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "getToken failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result ?: return@addOnCompleteListener
            FirebaseDatabase.getInstance().reference
                .child("fcmTokens").child(sessionId)
                .setValue(mapOf(
                    "token"     to token,
                    "updatedAt" to System.currentTimeMillis()
                ))
        }
    }

    /** Called by AppFirebaseMessagingService.onNewToken */
    fun onNewToken(ctx: Context, sessionId: String, token: String) {
        FirebaseDatabase.getInstance().reference
            .child("fcmTokens").child(sessionId)
            .setValue(mapOf(
                "token"     to token,
                "updatedAt" to System.currentTimeMillis()
            ))
    }

    // ── Timezone topic ─────────────────────────────────────────────────────────

    /** Computes current TZ topic name (e.g. `tz_p330`, `tz_n300`) and subscribes,
     *  unsubscribing from any previously-stored topic if it differs. */
    fun syncTzTopic(ctx: Context) {
        val offsetMin = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        val topic = tzTopic(offsetMin)

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prev = prefs.getString(KEY_TOPIC, null)
        if (prev == topic) return  // already subscribed, no-op

        val fm = FirebaseMessaging.getInstance()
        if (prev != null) fm.unsubscribeFromTopic(prev)
        fm.subscribeToTopic(topic).addOnCompleteListener {
            if (it.isSuccessful) {
                prefs.edit().putString(KEY_TOPIC, topic).apply()
                Log.d(TAG, "Subscribed to $topic")
            } else {
                Log.w(TAG, "Subscribe failed", it.exception)
            }
        }
    }

    /** `tz_p330` for +330min (IST), `tz_n300` for -300min (EST). */
    private fun tzTopic(offsetMin: Int): String {
        val sign = if (offsetMin >= 0) "p" else "n"
        return "tz_$sign${kotlin.math.abs(offsetMin)}"
    }

    /** Settings toggle: unsubscribe from current TZ topic. Idempotent. */
    fun unsubscribeAll(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prev  = prefs.getString(KEY_TOPIC, null) ?: return
        FirebaseMessaging.getInstance().unsubscribeFromTopic(prev).addOnCompleteListener {
            prefs.edit().remove(KEY_TOPIC).apply()
            Log.d(TAG, "Unsubscribed from $prev (notifs disabled)")
        }
    }
}
