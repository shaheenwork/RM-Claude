package com.randomchat.shnapp.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Manages Google User Messaging Platform (UMP) privacy consent flows for GDPR, CCPA & global privacy compliance.
 *
 * Gather consent BEFORE initializing MobileAds / requesting ad inventory to maximize match rate & eCPM.
 */
class ConsentManager private constructor(context: Context) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context.applicationContext)

    /**
     * Checks whether privacy consent has been satisfied and ads can be requested.
     */
    fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    /**
     * Requests consent info updates and presents the Google UMP consent form if required (e.g. EEA/UK/CPRA users).
     *
     * @param activity Foreground activity to host the UMP consent dialog.
     * @param onConsentGathered Callback invoked when consent gathering completes (regardless of user choice).
     */
    fun gatherConsent(
        activity: Activity,
        onConsentGathered: (canRequestAds: Boolean) -> Unit
    ) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: [${formError.errorCode}] ${formError.message}")
                    }
                    onConsentGathered(consentInformation.canRequestAds())
                }
            },
            { requestConsentError ->
                Log.w(TAG, "Consent info update failed: [${requestConsentError.errorCode}] ${requestConsentError.message}")
                onConsentGathered(consentInformation.canRequestAds())
            }
        )
    }

    /**
     * Shows privacy options form if available (e.g. settings screen privacy reset).
     */
    fun showPrivacyOptionsForm(activity: Activity, onComplete: () -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options form error: [${formError.errorCode}] ${formError.message}")
            }
            onComplete()
        }
    }

    fun isPrivacyOptionsRequired(): Boolean =
        consentInformation.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    companion object {
        private const val TAG = "ConsentManager"

        @Volatile private var instance: ConsentManager? = null

        fun getInstance(context: Context): ConsentManager =
            instance ?: synchronized(this) {
                instance ?: ConsentManager(context.applicationContext).also { instance = it }
            }
    }
}
