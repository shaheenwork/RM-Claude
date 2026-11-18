package com.randomchat.shnapp.ui.components

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.utils.Telemetry

/**
 * Reusable, lifecycle-aware Anchored Adaptive Banner Ad component.
 *
 * Key features:
 *  • Calculates exact screen width for Anchored Adaptive Banners (high eCPM demand).
 *  • Preserves single [AdView] instance across recompositions via [remember].
 *  • Cleans up WebView resources via [DisposableEffect] -> [AdView.destroy].
 *  • Tracks complete AdMob banner funnel (loads, fails with error code, impressions, clicks).
 */
@Composable
fun AdBanner(
    placement: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    val adView = remember(placement) {
        AdView(context).apply {
            val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp)
            setAdSize(adaptiveSize)
            adUnitId = Constants.ADMOB_BANNER_ID

            val loadStartTime = System.currentTimeMillis()
            Telemetry.bannerLoadRequested(placement)

            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    val latencyMs = System.currentTimeMillis() - loadStartTime
                    Log.d("AdBanner", "Banner loaded for [$placement] ✓ (${latencyMs}ms)")
                    Telemetry.bannerLoadSucceeded(placement, latencyMs)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d("AdBanner", "Banner failed to load [$placement]: [${error.code}] ${error.message}")
                    Telemetry.bannerLoadFailed(placement, error.code, error.message)
                }

                override fun onAdImpression() {
                    Telemetry.bannerImpressionRecorded(placement)
                }

                override fun onAdClicked() {
                    Telemetry.bannerClicked(placement)
                }
            }

            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
        }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth()
    )
}
