package com.randomchat.shnapp.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.utils.Telemetry

class AdMobManager(private val context: Context) {

    private val app: Application = context.applicationContext as Application

    /**
     * Shared flag — true whenever ANY full-screen ad (interstitial, rewarded, or app-open)
     * is currently on screen. [AppOpenAdManager] reads this to prevent stacking.
     */
    var isShowingFullScreenAd = false

    private var interstitialAd       : InterstitialAd? = null
    private var rewardedAd           : RewardedAd?     = null
    private var rewardedAdLoadedTime : Long            = 0L
    private var isLoadingRewarded    = false
    var isInitialized                 = false
        private set

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Gather UMP privacy consent first, then initialize MobileAds SDK if consent requirements are met.
     */
    fun gatherConsentAndInitialize(activity: Activity, onComplete: () -> Unit = {}) {
        if (!Constants.ADS_ENABLED) { onComplete(); return }
        ConsentManager.getInstance(context).gatherConsent(activity) { canRequest ->
            if (canRequest) {
                initialize()
            }
            onComplete()
        }
    }

    fun initialize() {
        if (!Constants.ADS_ENABLED) return  // master switch off — no SDK init, no preloads
        if (isInitialized) return
        MobileAds.initialize(context) {
            isInitialized = true
            preloadInterstitial()
            // Intent-driven loading: Rewarded ads are loaded when user visits Home or opens Chat media options.
            // App Open Ad preload — runs after SDK is ready.
            AppOpenAdManager.getInstance(app).preloadAd()
        }
    }

    // ── Interstitial ─────────────────────────────────────────────────────────

    fun preloadInterstitial() {
        InterstitialAd.load(
            context,
            Constants.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { interstitialAd = null }
            }
        )
    }

    /**
     * @param onDismissed called exactly once; `shown` is true only when the ad was
     *        actually on screen (drives the "Go ad-free" upsell).
     */
    fun showInterstitialIfReady(activity: Activity, onDismissed: (shown: Boolean) -> Unit) {
        if (!Constants.ADS_ENABLED) { onDismissed(false); return }
        val ad = interstitialAd
        if (ad == null) {
            onDismissed(false)
            preloadInterstitial()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true
            }
            override fun onAdDismissedFullScreenContent() {
                interstitialAd        = null
                isShowingFullScreenAd = false
                preloadInterstitial()
                onDismissed(true)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd        = null
                isShowingFullScreenAd = false
                preloadInterstitial()
                onDismissed(false)
            }
        }
        ad.show(activity)
    }

    fun isInterstitialReady() = Constants.ADS_ENABLED && interstitialAd != null

    // ── Rewarded ──────────────────────────────────────────────────────────────

    /**
     * Intent-driven preload for Rewarded Ads.
     * Drops expired ads (TTL > 4h) and prevents duplicate concurrent load calls.
     */
    fun preloadRewarded(placement: String = "general") {
        if (!Constants.ADS_ENABLED) return

        // Drop cached ad if it has passed AdMob's 4-hour TTL
        if (rewardedAd != null && System.currentTimeMillis() - rewardedAdLoadedTime >= AD_EXPIRY_MS) {
            val ageMs = System.currentTimeMillis() - rewardedAdLoadedTime
            Log.d(TAG, "Discarding expired rewarded ad ($ageMs ms old)")
            Telemetry.rewardedAdExpired(placement, ageMs)
            rewardedAd = null
            rewardedAdLoadedTime = 0L
        }

        if (isLoadingRewarded || rewardedAd != null) return
        isLoadingRewarded = true
        val loadStartTime = System.currentTimeMillis()
        Log.d(TAG, "Loading rewarded ad for placement [$placement]…")
        Telemetry.rewardedLoadRequested(placement)

        RewardedAd.load(
            context,
            Constants.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    val latencyMs = System.currentTimeMillis() - loadStartTime
                    Log.d(TAG, "Rewarded ad loaded ✓ (${latencyMs}ms)")
                    rewardedAd           = ad
                    rewardedAdLoadedTime = System.currentTimeMillis()
                    isLoadingRewarded    = false
                    Telemetry.rewardedLoadSucceeded(placement, latencyMs)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "Rewarded ad failed to load: [${error.code}] ${error.message}")
                    rewardedAd           = null
                    rewardedAdLoadedTime = 0L
                    isLoadingRewarded    = false
                    Telemetry.rewardedLoadFailed(placement, error.code, error.message)
                }
            }
        )
    }

    /**
     * Show the rewarded ad if one is loaded and fresh.
     *
     * @param activity       Current active foreground Activity.
     * @param placement      Semantic identifier of the trigger surface (e.g. "home_credits", "chat_attach").
     * @param onRewarded     Called when user completes the ad and earns the reward.
     * @param onNotAvailable Called when no ad is ready (triggers load + soft failure handling).
     * @param onDismissed    Called when the ad is dismissed WITHOUT earning.
     */
    fun showRewardedIfReady(
        activity      : Activity,
        placement     : String = "general",
        onRewarded    : () -> Unit,
        onNotAvailable: () -> Unit = {},
        onDismissed   : () -> Unit = {}
    ) {
        if (!Constants.ADS_ENABLED) { onNotAvailable(); return }

        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Cannot show rewarded ad: Activity is finishing or destroyed")
            onNotAvailable()
            return
        }

        val ready = isRewardedReady()
        Telemetry.rewardedShowAttempted(placement, ready)

        if (!ready) {
            onNotAvailable()
            preloadRewarded(placement)
            return
        }

        val ad = rewardedAd ?: run {
            onNotAvailable()
            preloadRewarded(placement)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true
                Telemetry.rewardedShowSucceeded(placement)
            }

            override fun onAdImpression() {
                Telemetry.rewardedImpressionRecorded(placement)
            }

            override fun onAdDismissedFullScreenContent() {
                rewardedAd            = null
                rewardedAdLoadedTime  = 0L
                isShowingFullScreenAd = false
                Telemetry.rewardedAdDismissed(placement)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd            = null
                rewardedAdLoadedTime  = 0L
                isShowingFullScreenAd = false
                Log.e(TAG, "Rewarded ad failed to show: [${error.code}] ${error.message}")
                Telemetry.rewardedShowFailed(placement, error.code, error.message)
                onNotAvailable()
                preloadRewarded(placement)
            }
        }

        ad.show(activity) { _ ->
            Telemetry.rewardedAdEarned(placement)
            onRewarded()
        }
    }

    /** True if a non-expired rewarded ad is loaded and ready to show instantly. */
    fun isRewardedReady(): Boolean {
        if (!Constants.ADS_ENABLED) return false
        if (rewardedAd == null) return false
        if (System.currentTimeMillis() - rewardedAdLoadedTime >= AD_EXPIRY_MS) {
            rewardedAd           = null
            rewardedAdLoadedTime = 0L
            return false
        }
        return true
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG          = "AdMobManager"
        private const val AD_EXPIRY_MS = 4 * 60 * 60 * 1_000L  // AdMob 4-hour TTL

        @Volatile private var instance: AdMobManager? = null
        fun getInstance(context: Context): AdMobManager =
            instance ?: synchronized(this) {
                instance ?: AdMobManager(context.applicationContext).also { instance = it }
            }
    }
}
