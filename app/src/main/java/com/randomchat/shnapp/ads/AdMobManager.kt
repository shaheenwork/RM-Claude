package com.randomchat.shnapp.ads

import android.app.Activity
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

    private var interstitialAd: InterstitialAd? = null
    private var isInitialized = false

    fun initialize() {
        if (isInitialized) return
        MobileAds.initialize(context) {
            isInitialized = true
            preloadInterstitial()
        }
    }

    fun preloadInterstitial() {
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            Constants.ADMOB_INTERSTITIAL_ID,
            request,
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
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preloadInterstitial()
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                preloadInterstitial()
                onDismissed()
            }
        }
        ad.show(activity)
    }

    fun isInterstitialReady() = interstitialAd != null

    companion object {
        @Volatile private var instance: AdMobManager? = null
        fun getInstance(context: Context): AdMobManager = instance ?: synchronized(this) {
            instance ?: AdMobManager(context.applicationContext).also { instance = it }
        }
    }
}
