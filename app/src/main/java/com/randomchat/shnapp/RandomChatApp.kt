package com.randomchat.shnapp

import android.app.Application
import android.util.Log
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

        // Pre-warm Firebase Auth anonymous sign-in off the main thread.
        // After this completes, SessionManager.sessionId returns instantly
        // (FirebaseAuth caches the UID locally for offline reuse).
        CoroutineScope(Dispatchers.IO).launch {
            try {
                sessionManager.ensureSignedIn()
            } catch (e: Exception) {
                Log.w("RandomChatApp", "ensureSignedIn failed at startup: ${e.message}")
            }

            // FCM init depends on sessionId — run after auth is ready
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
