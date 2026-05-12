package com.randomchat.shnapp.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.randomchat.shnapp.utils.Constants

class AdMobManager(private val context: Context) {

    private val app: Application = context.applicationContext as Application

    /**
     * Shared flag — true whenever ANY full-screen ad (interstitial or app-open)
     * is currently occupying the screen. [AppOpenAdManager] reads this to avoid
     * stacking two full-screen ads simultaneously.
     */
    var isShowingFullScreenAd = false

    private var interstitialAd: InterstitialAd? = null
    private var isInitialized  = false

    fun initialize() {
        if (isInitialized) return
        MobileAds.initialize(context) {
            isInitialized = true
            preloadInterstitial()
            // Trigger the app-open ad preload now that the AdMob SDK is ready.
            // AppOpenAdManager.register() must have been called before this point
            // (done in RandomChatApp.onCreate) so lifecycle tracking is already active.
            AppOpenAdManager.getInstance(app).preloadAd()
        }
    }

    fun preloadInterstitial() {
        InterstitialAd.load(
            context,
            Constants.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    fun showInterstitialIfReady(activity: Activity, onDismissed: () -> Unit) {
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

    fun isInterstitialReady() = interstitialAd != null

    companion object {
        @Volatile private var instance: AdMobManager? = null
        fun getInstance(context: Context): AdMobManager =
            instance ?: synchronized(this) {
                instance ?: AdMobManager(context.applicationContext).also { instance = it }
            }
    }
}
