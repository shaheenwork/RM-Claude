package com.randomchat.shnapp.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Manages App Open Ads for maximum impression rate.
 *
 * Strategy:
 *  • Preload starts the moment the AdMob SDK is initialized (via [preloadAd]).
 *  • Every background → foreground transition shows the ad (cold-start included).
 *  • Premium users are always skipped; interstitial conflicts are avoided via
 *    [AdMobManager.isShowingFullScreenAd].
 *  • Ad is reloaded immediately on dismiss / fail-to-show so the next foreground
 *    event always finds a fresh ad.
 *  • Handles AdMob's 4-hour expiry: expired ads are discarded and reloaded.
 *  • Uses Activity-lifecycle counting instead of ProcessLifecycleOwner so that
 *    no extra dependency is required and the logic is explicit.
 */
class AppOpenAdManager private constructor(private val app: Application) :
    Application.ActivityLifecycleCallbacks {

    // ── State ─────────────────────────────────────────────────────────────────

    private val sessionManager = SessionManager.getInstance(app)
    private val scope          = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var appOpenAd          : AppOpenAd? = null
    private var isLoadingAd         = false
    /** True while an app-open or interstitial full-screen ad is on screen. */
    var isShowingAd                 = false
        private set
    private var isPremium           = false
    private var isOnboardingComplete = false
    private var isSuppressingAds      = false
    private var loadTime            = 0L

    /**
     * Tracks how many activities are in the STARTED state (not stopped).
     * Foreground ↔ background transitions are detected by 0 → 1 and 1 → 0 edges.
     * The counter is frozen while an ad is showing to avoid false transitions.
     */
    private var numActivitiesStarted = 0

    /** Always points to the topmost non-ad activity. */
    private var currentActivity: Activity? = null

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG          = "AppOpenAdManager"
        private const val AD_EXPIRY_MS = 4 * 60 * 60 * 1_000L  // AdMob TTL

        @Volatile private var instance: AppOpenAdManager? = null

        fun getInstance(app: Application): AppOpenAdManager =
            instance ?: synchronized(this) {
                instance ?: AppOpenAdManager(app).also { instance = it }
            }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Register lifecycle callbacks and start tracking premium status.
     * Call once from [Application.onCreate] — before any activity starts.
     */
    fun register() {
        app.registerActivityLifecycleCallbacks(this)
        // Keep a cached, always-up-to-date flags so we can check them
        // synchronously on the main thread inside lifecycle callbacks.
        scope.launch {
            sessionManager.isPremiumFlow.collect { isPremium = it }
        }
        scope.launch {
            sessionManager.termsAcceptedFlow.collect { isOnboardingComplete = it }
        }
    }

    /**
     * Temporarily suppress the next app-open ad. Use this when the user is
     * navigating to an external flow (like Play Store billing) where an ad
     * on return would be intrusive.
     */
    fun setSuppressingAds(suppress: Boolean) {
        isSuppressingAds = suppress
    }

    /**
     * Kick off the first ad load. Called by [AdMobManager] once
     * [com.google.android.gms.ads.MobileAds.initialize] completes.
     */
    fun preloadAd() {
        loadAd()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun loadAd() {
        if (isLoadingAd || isAdAvailable()) return
        isLoadingAd = true
        Log.d(TAG, "Loading app open ad…")

        AppOpenAd.load(
            app,
            Constants.ADMOB_APP_OPEN_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "Ad loaded ✓")
                    appOpenAd = ad
                    loadTime  = Date().time
                    isLoadingAd = false
                    // Cold-start path: the SDK finished loading while the app was
                    // already in the foreground (onActivityStarted already fired
                    // but there was no ad to show yet). Try now.
                    if (numActivitiesStarted > 0 && !isShowingAd) {
                        currentActivity?.let { showAdIfAvailable(it) }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "Ad failed to load: ${error.message}")
                    isLoadingAd = false
                    // No retry here — next foreground event will call loadAd() again.
                }
            }
        )
    }

    private fun isAdAvailable(): Boolean =
        appOpenAd != null && Date().time - loadTime < AD_EXPIRY_MS

    private fun showAdIfAvailable(activity: Activity) {
        // Gate checks — order matters for logging clarity
        if (isShowingAd) return
        if (isPremium) {
            Log.d(TAG, "Skip: premium user")
            loadAd()   // keep a fresh ad for if they downgrade / sub lapses
            return
        }
        if (!isOnboardingComplete) {
            Log.d(TAG, "Skip: onboarding not complete")
            return
        }
        if (isSuppressingAds) {
            Log.d(TAG, "Skip: ads suppressed")
            isSuppressingAds = false
            return
        }
        if (AdMobManager.getInstance(app).isShowingFullScreenAd) {
            Log.d(TAG, "Skip: interstitial already on screen")
            return
        }
        if (!isAdAvailable()) {
            Log.d(TAG, "Skip: no valid ad — reloading")
            loadAd()
            return
        }
        val ad = appOpenAd ?: return

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
                AdMobManager.getInstance(app).isShowingFullScreenAd = true
                Log.d(TAG, "Ad showing")
            }

            override fun onAdDismissedFullScreenContent() {
                appOpenAd   = null
                isShowingAd = false
                AdMobManager.getInstance(app).isShowingFullScreenAd = false
                Log.d(TAG, "Ad dismissed — reloading for next foreground")
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd   = null
                isShowingAd = false
                AdMobManager.getInstance(app).isShowingFullScreenAd = false
                Log.d(TAG, "Ad failed to show: ${error.message} — reloading")
                loadAd()
            }
        }

        Log.d(TAG, "Showing app open ad")
        ad.show(activity)
    }

    // ── ActivityLifecycleCallbacks ────────────────────────────────────────────

    override fun onActivityStarted(activity: Activity) {
        // Freeze the counter while an ad occupies the screen to avoid phantom
        // foreground events caused by the ad's own view hierarchy.
        if (!isShowingAd) {
            currentActivity = activity
            numActivitiesStarted++
            if (numActivitiesStarted == 1) {
                // 0 → 1: app came to foreground (cold start or from background)
                showAdIfAvailable(activity)
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (!isShowingAd) {
            numActivitiesStarted = maxOf(0, numActivitiesStarted - 1)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        // Keep currentActivity current when navigating between screens.
        if (!isShowingAd) currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
