package com.randomchat.shnapp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.randomchat.shnapp.ads.AdMobManager
import com.randomchat.shnapp.billing.BillingManager
import com.randomchat.shnapp.firebase.FcmManager
import com.randomchat.shnapp.firebase.FirestoreManager
import com.randomchat.shnapp.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RandomChatApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase
        FirebaseApp.initializeApp(this)

        // Init AdMob
        AdMobManager.getInstance(this).initialize()

        val sessionManager = SessionManager.getInstance(this)

        // FCM — token, TZ topic, notification channels.
        // Read enabled flag synchronously on background thread (DataStore is async by default).
        CoroutineScope(Dispatchers.IO).launch {
            val enabled = sessionManager.notifsEnabledFlow.first()
            withContext(Dispatchers.Main) {
                FcmManager.init(this@RandomChatApp, sessionManager.sessionId, enabled)
            }
        }

        // Init billing
        BillingManager.getInstance(
            context = this,
            onPremiumGranted = { token, expiry ->
                sessionManager.setPremium(true, expiry)
                FirestoreManager.getInstance().savePremiumStatus(
                    sessionId = sessionManager.sessionId,
                    isPremium = true,
                    expiryMs = expiry,
                    purchaseToken = token
                )
            },
            onPremiumRevoked = {
                sessionManager.setPremium(false)
            }
        ).connect()
    }
}
