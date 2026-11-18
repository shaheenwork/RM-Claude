package com.randomchat.shnapp.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.utils.SessionManager
import com.randomchat.shnapp.utils.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Date

/**
 * Manages App Open Ads for maximum impression rate.
 *
 * Strategy:
 *  • Uses [ProcessLifecycleOwner] to reliably detect process-level foreground/background transitions.
 *  • Preloads ad on SDK init, after ad dismissal, or when returning to foreground.
 *  • Enforces 5-second minimum background duration gate to eliminate transient app switch ads.
 *  • Provides an 8-second grace window on foreground return if ad loading is slightly delayed.
 *  • Premium users and private chat sessions ([isChatActive]) are always protected.
 *  • Full Telemetry funnel tracking for requests, fills, displays, suppressions, and impressions.
 */
class AppOpenAdManager private constructor(private val app: Application) :
    Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    // ── State ─────────────────────────────────────────────────────────────────

    private val sessionManager = SessionManager.getInstance(app)
    private val scope          = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var appOpenAd            : AppOpenAd? = null
    private var isLoadingAd           = false
    /** True while an app-open or interstitial full-screen ad is on screen. */
    var isShowingAd                   = false
        private set
    private var isPremium             = false
    private var isOnboardingComplete   = false
    private var isSuppressingAds        = false
    /** True while user is inside a private chat screen. Prevents ads on private communications. */
    var isChatActive                  = false
    private var loadTime              = 0L
    private var lastBackgroundTimeMs  = 0L
    private var appForegroundTimeMs   = 0L
    private var pendingBgDurationMs   = -1L
    private var isWaitingForAdToShow  = false

    /** Always points to the topmost active activity. */
    private var currentActivity: Activity? = null

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG                      = "AppOpenAdManager"
        private const val AD_EXPIRY_MS             = 4 * 60 * 60 * 1_000L  // AdMob 4h TTL
        private const val MIN_BACKGROUND_DURATION  = 5_000L                // 5s min background threshold
        private const val FOREGROUND_SHOW_WINDOW_MS = 8_000L               // 8s window to show ad on foreground return

        @Volatile private var instance: AppOpenAdManager? = null

        fun getInstance(app: Application): AppOpenAdManager =
            instance ?: synchronized(this) {
                instance ?: AppOpenAdManager(app).also { instance = it }
            }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Register lifecycle callbacks and start tracking process lifecycle and premium status.
     * Call once from [Application.onCreate] — before any activity starts.
     */
    fun register() {
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
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
     * Kick off the first ad load. Called by [AdMobManager] once SDK initializes.
     */
    fun preloadAd() {
        loadAd()
    }

    // ── Process Lifecycle Observer ───────────────────────────────────────────

    override fun onStart(owner: LifecycleOwner) {
        appForegroundTimeMs = System.currentTimeMillis()
        val bgDurationMs = if (lastBackgroundTimeMs > 0L) {
            System.currentTimeMillis() - lastBackgroundTimeMs
        } else {
            -1L
        }
        lastBackgroundTimeMs = 0L

        currentActivity?.let { activity ->
            showAdIfAvailable(activity, bgDurationMs)
        } ?: run {
            if (bgDurationMs >= 0L) {
                pendingBgDurationMs = bgDurationMs
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        lastBackgroundTimeMs = System.currentTimeMillis()
        isWaitingForAdToShow = false
        if (!isAdAvailable()) {
            loadAd()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun loadAd() {
        if (!Constants.ADS_ENABLED) return
        if (!AdMobManager.getInstance(app).isInitialized) {
            Log.d(TAG, "Skip load: MobileAds SDK not initialized yet")
            return
        }
        if (isLoadingAd || isAdAvailable()) return
        isLoadingAd = true
        val loadStartTime = System.currentTimeMillis()
        Log.d(TAG, "Loading app open ad…")
        Telemetry.appOpenLoadRequested()

        val adUnitId = if (com.randomchat.shnapp.BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/9257395921" // Google official App Open test ID for debug builds
        } else {
            Constants.ADMOB_APP_OPEN_ID
        }

        AppOpenAd.load(
            app,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    val latencyMs = System.currentTimeMillis() - loadStartTime
                    Log.d(TAG, "Ad loaded ✓ (${latencyMs}ms)")
                    appOpenAd   = ad
                    loadTime    = Date().time
                    isLoadingAd = false
                    Telemetry.appOpenLoadSucceeded(latencyMs)

                    // Foreground return grace window check:
                    // If user came to foreground and is waiting for an ad that was still loading
                    val isAppInForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    if (isWaitingForAdToShow && isAppInForeground && !isShowingAd) {
                        val timeSinceForeground = System.currentTimeMillis() - appForegroundTimeMs
                        if (timeSinceForeground < FOREGROUND_SHOW_WINDOW_MS) {
                            Log.d(TAG, "Ad finished loading within foreground window (${timeSinceForeground}ms) — showing now")
                            isWaitingForAdToShow = false
                            currentActivity?.let { showAdIfAvailable(it) }
                        } else {
                            isWaitingForAdToShow = false
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "Ad failed to load: [${error.code}] ${error.message}")
                    isLoadingAd = false
                    isWaitingForAdToShow = false
                    Telemetry.appOpenLoadFailed(error.code, error.message)
                }
            }
        )
    }

    private fun isAdAvailable(): Boolean =
        appOpenAd != null && Date().time - loadTime < AD_EXPIRY_MS

    private fun showAdIfAvailable(activity: Activity, bgDurationMs: Long = -1L) {
        if (!Constants.ADS_ENABLED) return
        if (isShowingAd) return

        // 5-second minimum background duration gate (only evaluated when returning from background)
        if (bgDurationMs >= 0L && bgDurationMs < MIN_BACKGROUND_DURATION) {
            Log.d(TAG, "Skip: app backgrounded for only ${bgDurationMs}ms (< 5s threshold)")
            Telemetry.appOpenSuppressed("min_background")
            isWaitingForAdToShow = false
            return
        }

        if (isPremium) {
            Log.d(TAG, "Skip: premium user")
            Telemetry.appOpenSuppressed("premium")
            isWaitingForAdToShow = false
            return
        }
        if (!isOnboardingComplete) {
            isOnboardingComplete = runCatching {
                runBlocking { sessionManager.termsAcceptedFlow.first() }
            }.getOrDefault(false)
        }
        if (!isOnboardingComplete) {
            Log.d(TAG, "Skip: onboarding not complete")
            Telemetry.appOpenSuppressed("onboarding_incomplete")
            isWaitingForAdToShow = false
            return
        }
        if (isChatActive) {
            Log.d(TAG, "Skip: user is in private chat screen")
            Telemetry.appOpenSuppressed("chat_active")
            isWaitingForAdToShow = false
            return
        }
        if (isSuppressingAds) {
            Log.d(TAG, "Skip: ads suppressed via flag")
            Telemetry.appOpenSuppressed("billing_suppressed")
            isSuppressingAds = false
            isWaitingForAdToShow = false
            return
        }
        if (AdMobManager.getInstance(app).isShowingFullScreenAd) {
            Log.d(TAG, "Skip: interstitial or full screen ad already on screen")
            Telemetry.appOpenSuppressed("interstitial_showing")
            isWaitingForAdToShow = false
            return
        }

        val isReady = isAdAvailable()
        Telemetry.appOpenShowAttempted(isReady)

        if (!isReady) {
            if (appOpenAd != null && Date().time - loadTime >= AD_EXPIRY_MS) {
                val ageMs = Date().time - loadTime
                Log.d(TAG, "Skip: cached ad expired ($ageMs ms old) — reloading")
                Telemetry.appOpenExpired(ageMs)
                Telemetry.appOpenSuppressed("ad_expired")
                appOpenAd = null
            } else {
                Log.d(TAG, "Skip: no valid ad available — requesting load")
                Telemetry.appOpenSuppressed("no_ad_available")
            }
            // Enable waiting flag so when onAdLoaded completes within window, ad will show
            isWaitingForAdToShow = true
            loadAd()
            return
        }

        val ad = appOpenAd ?: return
        isWaitingForAdToShow = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
                AdMobManager.getInstance(app).isShowingFullScreenAd = true
                Log.d(TAG, "Ad showing")
                Telemetry.appOpenShowSucceeded()
            }

            override fun onAdImpression() {
                Telemetry.appOpenImpressionRecorded()
            }

            override fun onAdDismissedFullScreenContent() {
                appOpenAd   = null
                isShowingAd = false
                AdMobManager.getInstance(app).isShowingFullScreenAd = false
                Log.d(TAG, "Ad dismissed — preloading next ad")
                Telemetry.appOpenDismissed()
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd   = null
                isShowingAd = false
                AdMobManager.getInstance(app).isShowingFullScreenAd = false
                Log.e(TAG, "Ad failed to show: [${error.code}] ${error.message}")
                Telemetry.appOpenShowFailed(error.code, error.message)
                loadAd()
            }
        }

        Log.d(TAG, "Showing app open ad")
        ad.show(activity)
    }

    // ── ActivityLifecycleCallbacks ────────────────────────────────────────────

    override fun onActivityStarted(activity: Activity) {
        if (!isShowingAd) {
            currentActivity = activity
            val pendingBg = pendingBgDurationMs
            if (pendingBg >= 0L) {
                pendingBgDurationMs = -1L
                showAdIfAvailable(activity, pendingBg)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!isShowingAd) currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
