package com.randomchat.shnapp

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.functions.ktx.functions
import com.randomchat.shnapp.ads.AdMobManager
import com.randomchat.shnapp.ads.AppOpenAdManager
import com.randomchat.shnapp.billing.BillingManager
import com.randomchat.shnapp.firebase.FcmManager
import com.randomchat.shnapp.utils.SessionManager
import com.randomchat.shnapp.utils.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RandomChatApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase
        FirebaseApp.initializeApp(this)

        // Crashlytics — off in debug so local runs don't pollute crash dashboard
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        // Telemetry — central wrapper for Crashlytics keys + Analytics events
        Telemetry.init(this)

        // App Check — DISABLED for now (will implement before Play Store launch).
        // TODO: re-enable + enforce in Firebase Console before release.
        // FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        //     appCheckProviderFactory()
        // )

        // Init AdMob (interstitial + banner)
        AdMobManager.getInstance(this).initialize()
        // App Open Ad — register lifecycle callbacks before any activity starts so the
        // very first foreground event is captured. The actual ad preload is triggered
        // inside AdMobManager.initialize() once the SDK is ready.
        AppOpenAdManager.getInstance(this).register()

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

            // Crashlytics/Analytics — attach session + premium to every report/event
            Telemetry.setSession(sessionManager.sessionId)
            Telemetry.setPremium(sessionManager.isPremiumFlow.first())

            // FCM init depends on sessionId — run after auth is ready
            val enabled = sessionManager.notifsEnabledFlow.first()
            withContext(Dispatchers.Main) {
                FcmManager.init(this@RandomChatApp, sessionManager.sessionId, enabled)
            }
        }

        // Init billing
        BillingManager.getInstance(
            context = this,
            onPremiumGranted = { token, productId, expiry ->
                // Grant locally for instant UX — Play SDK already verified the purchase state.
                sessionManager.setPremium(true, expiry)
                Telemetry.setPremium(true)
                Telemetry.premiumPurchased(productId)
                // Server-side verification: CF calls Play Developer API and writes Firestore.
                // Firestore rules block direct client writes to subscriptions/.
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Firebase.functions("us-central1")
                            .getHttpsCallable("verifyPurchase")
                            .call(hashMapOf("purchaseToken" to token, "productId" to productId))
                            .await()
                    } catch (e: Exception) {
                        Log.w("RandomChatApp", "verifyPurchase CF failed: ${e.message}")
                    }
                }
            },
            onPremiumRevoked = {
                sessionManager.setPremium(false)
                Telemetry.setPremium(false)
            }
        ).connect()
    }
}
