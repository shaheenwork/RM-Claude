package com.randomchat.shnapp.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

object FcmManager {
    private const val TAG = "FcmManager"
    private const val CHANNEL_ID = "activity"
    private const val CHANNEL_NAME = "Activity Notifications"
    private const val TOPIC_ALL = "all_users"

    fun init(context: Context, sessionId: String, enabled: Boolean) {
        createNotificationChannel(context)
        if (enabled) {
            subscribeAll()
            FirebaseMessaging.getInstance().subscribeToTopic("user_$sessionId")
        } else {
            unsubscribeAll(context)
        }
    }

    /**
     * Subscribe to the single broadcast topic. The daily nudge Cloud Function
     * (`dailyNudge`) sends one push per day to this topic at a fixed UTC time —
     * no per-timezone targeting.
     */
    fun subscribeAll() {
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL)
    }

    fun unsubscribeAll(context: Context) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_ALL)
    }

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
