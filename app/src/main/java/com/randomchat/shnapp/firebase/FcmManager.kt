package com.randomchat.shnapp.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import java.util.TimeZone

object FcmManager {
    private const val TAG = "FcmManager"
    private const val CHANNEL_ID = "activity"
    private const val CHANNEL_NAME = "Activity Notifications"

    fun init(context: Context, sessionId: String, enabled: Boolean) {
        createNotificationChannel(context)
        if (enabled) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d(TAG, "FCM Token: $token")
                }
            }
            syncTzTopic(context)
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
            FirebaseMessaging.getInstance().subscribeToTopic("user_$sessionId")
        } else {
            unsubscribeAll(context)
        }
    }

    fun syncTzTopic(context: Context) {
        val tz = TimeZone.getDefault().id.replace("/", "-")
        FirebaseMessaging.getInstance().subscribeToTopic("tz_$tz")
    }

    fun unsubscribeAll(context: Context) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic("all_users")
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
