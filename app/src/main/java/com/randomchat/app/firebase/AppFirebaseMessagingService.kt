package com.randomchat.app.firebase

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.randomchat.shnapp.MainActivity
import com.randomchat.shnapp.R
import com.randomchat.shnapp.utils.SessionManager
import kotlin.jvm.java


/**
 * Receives FCM pushes and renders them as a notification.
 *
 * The dispatcher Cloud Function sends a `notification` block (handled automatically when
 * the app is in background) plus a `data` block (we read it here when the app is in
 * foreground, since FCM does not auto-render in that case).
 */
class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val sessionId = SessionManager.getInstance(applicationContext).sessionId
        FcmManager.onNewToken(applicationContext, sessionId, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Strcht"
        val body  = message.notification?.body  ?: message.data["body"]  ?: return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("from", "push")
            putExtra("type", message.data["type"] ?: "daily_nudge")
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, "activity")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}
