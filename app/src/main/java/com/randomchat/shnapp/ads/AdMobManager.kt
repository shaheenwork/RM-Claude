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

class AdMobManager(private val context: Context) {

    private val app: Application = context.applicationContext as Application

    /**
     * Shared flag — true whenever ANY full-screen ad (interstitial, rewarded, or app-open)
     * is currently on screen. [AppOpenAdManager] reads this to prevent stacking.
     */
    var isShowingFullScreenAd = false

    private var interstitialAd   : InterstitialAd? = null
    private var rewardedAd       : RewardedAd?     = null
    private var isLoadingRewarded = false
    private var isInitialized     = false

    // ── Initialisation ────────────────────────────────────────────────────────

    fun initialize() {
        if (!Constants.ADS_ENABLED) return  // master switch off — no SDK init, no preloads
        if (isInitialized) return
        MobileAds.initialize(context) {
            isInitialized = true
            preloadInterstitial()
            preloadRewarded()
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

    fun showInterstitialIfReady(activity: Activity, onDismissed: () -> Unit) {
        if (!Constants.ADS_ENABLED) { onDismissed(); return }
        val ad = interstitialAd
        if (ad == null) {
            onDismissed()
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
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd        = null
                isShowingFullScreenAd = false
                preloadInterstitial()
                onDismissed()
            }
        }
        ad.show(activity)
    }

    fun isInterstitialReady() = Constants.ADS_ENABLED && interstitialAd != null

    // ── Rewarded ──────────────────────────────────────────────────────────────

    fun preloadRewarded() {
        if (isLoadingRewarded || rewardedAd != null) return
        isLoadingRewarded = true
        Log.d("AdMobManager", "Loading rewarded ad…")
        RewardedAd.load(
            context,
            Constants.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdMobManager", "Rewarded ad loaded ✓")
                    rewardedAd        = ad
                    isLoadingRewarded = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d("AdMobManager", "Rewarded ad failed: ${error.message}")
                    rewardedAd        = null
                    isLoadingRewarded = false
                }
            }
        )
    }

    /**
     * Show the rewarded ad if one is loaded.
     *
     * @param onRewarded    Called when the user completes the ad and earns the reward.
     * @param onNotAvailable Called when no ad is ready (treat as soft failure — let user try again).
     * @param onDismissed   Called when the ad is dismissed WITHOUT earning (user skipped early).
     */
    fun showRewardedIfReady(
        activity      : Activity,
        onRewarded    : () -> Unit,
        onNotAvailable: () -> Unit = {},
        onDismissed   : () -> Unit = {}
    ) {
        if (!Constants.ADS_ENABLED) { onNotAvailable(); return }
        val ad = rewardedAd
        if (ad == null) {
            onNotAvailable()
            preloadRewarded()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true
            }
            override fun onAdDismissedFullScreenContent() {
                rewardedAd            = null
                isShowingFullScreenAd = false
                preloadRewarded()
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd            = null
                isShowingFullScreenAd = false
                preloadRewarded()
                onNotAvailable()
            }
        }
        // The reward callback fires before onAdDismissed
        ad.show(activity) { _ -> onRewarded() }
    }

    /** True if a rewarded ad is loaded and ready to show instantly. */
    fun isRewardedReady() = Constants.ADS_ENABLED && rewardedAd != null

    // ── Singleton ─────────────────────────────────────────────────────────────

    companion object {
        @Volatile private var instance: AdMobManager? = null
        fun getInstance(context: Context): AdMobManager =
            instance ?: synchronized(this) {
                instance ?: AdMobManager(context.applicationContext).also { instance = it }
            }
    }
}
