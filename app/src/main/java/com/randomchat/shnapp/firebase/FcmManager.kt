package com.randomchat.shnapp.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

object FcmManager {
    private const val CHANNEL_ID = "activity"
    private const val CHANNEL_NAME = "Activity Notifications"
    private const val TOPIC_ALL = "all_users"

    fun init(context: Context, enabled: Boolean) {
        createNotificationChannel(context)
        if (enabled) subscribeAll() else unsubscribeAll(context)
    }

    /**
     * Subscribes to both push topics:
     *  - `all_users`  — the daily nudge Cloud Function (`dailyNudge`).
     *  - `user_<uid>` — per-user pushes such as the paywall reminder (`paywallReminder`).
     * Both follow the in-app notification toggle, so turning it off silences every push.
     */
    fun subscribeAll() {
        val messaging = FirebaseMessaging.getInstance()
        messaging.subscribeToTopic(TOPIC_ALL)
        userTopic()?.let { messaging.subscribeToTopic(it) }
    }

    fun unsubscribeAll(context: Context) {
        val messaging = FirebaseMessaging.getInstance()
        messaging.unsubscribeFromTopic(TOPIC_ALL)
        userTopic()?.let { messaging.unsubscribeFromTopic(it) }
    }

    private fun userTopic(): String? =
        FirebaseAuth.getInstance().currentUser?.uid?.let { "user_$it" }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for chat activity"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
