package com.randomchat.shnapp.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.randomchat.shnapp.BuildConfig
import com.randomchat.shnapp.ads.AppOpenAdManager
import com.randomchat.shnapp.billing.BillingManager
import com.randomchat.shnapp.billing.PaywallSource
import com.randomchat.shnapp.billing.PremiumPlan
import com.randomchat.shnapp.billing.PurchaseEvent
import com.randomchat.shnapp.billing.RestoreResult
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.utils.SessionManager
import com.randomchat.shnapp.utils.Telemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PremiumViewModel(app: Application) : AndroidViewModel(app) {

    private val sessionManager = SessionManager.getInstance(app)
    private val billingManager = BillingManager.getInstance(app)

    /**
     * Debug builds use a fully-local fake billing flow so the subscription/cancel UI
     * can be exercised without Play Store install or product setup.
     * Release builds always use real Google Play Billing.
     */
    private val useFakeBilling = BuildConfig.DEBUG

    private val fakePlans = listOf(
        PremiumPlan(Constants.PRODUCT_PREMIUM_WEEKLY,  "Weekly",  "$2.99",  "fake_offer", 2_990_000L,  "USD", "P1W"),
        PremiumPlan(Constants.PRODUCT_PREMIUM_MONTHLY, "Monthly", "$6.99",  "fake_offer", 6_990_000L,  "USD", "P1M"),
        PremiumPlan(Constants.PRODUCT_PREMIUM_YEARLY,  "Yearly",  "$39.99", "fake_offer", 39_990_000L, "USD", "P1Y"),
    )

    val isPremium: StateFlow<Boolean> = sessionManager.isPremiumFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _fakePlans = MutableStateFlow(fakePlans)
    val plans: StateFlow<List<PremiumPlan>> = if (useFakeBilling) {
        _fakePlans
    } else {
        billingManager.plans
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    private val _fakeActivePlanId = MutableStateFlow<String?>(null)
    val activePlanId: StateFlow<String?> = if (useFakeBilling) {
        _fakeActivePlanId
    } else {
        billingManager.activePlanId
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    /** True while Google Play holds an unconfirmed premium payment (common with UPI). */
    val purchasePending: StateFlow<Boolean> =
        if (useFakeBilling) MutableStateFlow(false) else billingManager.hasPendingPurchase

    private val _selectedPlanId = MutableStateFlow(Constants.PRODUCT_PREMIUM_MONTHLY)
    val selectedPlanId: StateFlow<String> = _selectedPlanId

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage
    private var messageJob: Job? = null

    /** Entry point of the current paywall visit — attached to paywall and purchase analytics. */
    private var source: String = PaywallSource.UNKNOWN
    private var exitOfferShownThisVisit = false
    /** null until DataStore has answered — the exit offer never shows before that. */
    private val lastExitOfferShownMs: StateFlow<Long?> = sessionManager.lastExitOfferShownMsFlow
        .map<Long, Long?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            plans.collect { planList ->
                if (planList.isNotEmpty() && planList.none { it.productId == _selectedPlanId.value }) {
                    _selectedPlanId.value = planList
                        .find { it.productId == Constants.PRODUCT_PREMIUM_MONTHLY }?.productId
                        ?: planList.first().productId
                }
            }
        }
        if (useFakeBilling) {
            viewModelScope.launch {
                isPremium.collect { premium ->
                    if (!premium) _fakeActivePlanId.value = null
                }
            }
        } else {
            viewModelScope.launch {
                billingManager.purchaseEvents.collect { onPurchaseEvent(it) }
            }
        }
    }

    /**
     * Called once per paywall visit: funnel event tagged with the entry point,
     * plan refresh if pricing is missing, and the server-side view log that feeds
     * the paywall reminder push.
     */
    fun onPaywallOpened(source: String) {
        this.source = source
        exitOfferShownThisVisit = false
        viewModelScope.launch {
            if (sessionManager.isPremiumFlow.first()) return@launch
            Telemetry.premiumViewed(source)
            if (useFakeBilling) return@launch
            if (plans.value.isEmpty()) billingManager.refresh()
            logPaywallView(source)
        }
    }

    private suspend fun logPaywallView(source: String) {
        try {
            Firebase.functions("us-central1")
                .getHttpsCallable("logPaywallView")
                .call(hashMapOf("source" to source))
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "logPaywallView failed: ${e.message}")
        }
    }

    fun selectPlan(productId: String) {
        if (_selectedPlanId.value == productId) return
        _selectedPlanId.value = productId
        Telemetry.paywallPlanSelected(productId, source)
    }

    fun purchasePremium(activity: Activity, via: String = VIA_PAYWALL) {
        if (useFakeBilling) {
            viewModelScope.launch {
                sessionManager.setPremium(true, 0L)
                _fakeActivePlanId.value = _selectedPlanId.value
                showMessage("✓ Test purchase successful (debug build)")
            }
            return
        }
        if (plans.value.isEmpty()) {
            billingManager.refresh()
            showMessage("Loading pricing… please try again in a moment.")
            return
        }
        AppOpenAdManager.getInstance(getApplication()).setSuppressingAds(true)
        billingManager.launchPurchaseFlow(activity, _selectedPlanId.value, source, via)
    }

    private fun onPurchaseEvent(event: PurchaseEvent) {
        when (event) {
            PurchaseEvent.Success -> showMessage("✓ Welcome to Premium!")
            // The paywall shows a persistent pending banner instead of a timed message.
            PurchaseEvent.Pending -> Unit
            PurchaseEvent.Cancelled -> showMessage("Purchase cancelled. You haven't been charged.")
            PurchaseEvent.AlreadyOwned -> showMessage("✓ You already have Premium — restored.")
            is PurchaseEvent.Failed -> showMessage(
                when (event.responseCode) {
                    BillingResponseCode.BILLING_UNAVAILABLE ->
                        "Google Play billing isn't available. Check that you're signed in to the Play Store."
                    BillingResponseCode.SERVICE_DISCONNECTED,
                    BillingResponseCode.SERVICE_UNAVAILABLE,
                    BillingResponseCode.NETWORK_ERROR ->
                        "Couldn't reach Google Play. Check your connection and try again."
                    else -> "Something went wrong. Please try again."
                }
            )
        }
    }

    fun manageSubscription(context: Context) {
        if (useFakeBilling) {
            viewModelScope.launch {
                sessionManager.setPremium(false)
                _fakeActivePlanId.value = null
                showMessage("✓ Subscription cancelled (debug build)")
            }
            return
        }
        val productId = activePlanId.value ?: Constants.PRODUCT_PREMIUM_MONTHLY
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                    "?sku=$productId&package=${context.packageName}"
        )
        AppOpenAdManager.getInstance(getApplication()).setSuppressingAds(true)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    fun restorePurchases() {
        if (useFakeBilling) {
            showMessage("Nothing to restore (debug build)")
            return
        }
        viewModelScope.launch {
            messageJob?.cancel()
            _uiMessage.value = "Checking your purchases…"
            showMessage(
                when (billingManager.restorePurchases()) {
                    RestoreResult.RESTORED -> "✓ Premium restored"
                    RestoreResult.NOTHING_FOUND -> "No active subscription found for this account"
                    RestoreResult.FAILED -> "Couldn't reach Google Play. Check your connection and try again."
                }
            )
        }
    }

    // ── Exit offer ────────────────────────────────────────────────────────────

    /**
     * Whether leaving the paywall should first offer the weekly plan: free users
     * who were looking at a longer plan, at most once per [EXIT_OFFER_COOLDOWN_MS].
     */
    fun shouldShowExitOffer(): Boolean {
        if (exitOfferShownThisVisit) return false
        if (isPremium.value || purchasePending.value) return false
        if (_selectedPlanId.value == Constants.PRODUCT_PREMIUM_WEEKLY) return false
        if (plans.value.none { it.productId == Constants.PRODUCT_PREMIUM_WEEKLY }) return false
        val lastShown = lastExitOfferShownMs.value ?: return false
        return System.currentTimeMillis() - lastShown >= EXIT_OFFER_COOLDOWN_MS
    }

    fun onExitOfferShown() {
        exitOfferShownThisVisit = true
        Telemetry.upsellShown("exit_offer")
        viewModelScope.launch { sessionManager.markExitOfferShown() }
    }

    fun acceptExitOffer(activity: Activity) {
        selectPlan(Constants.PRODUCT_PREMIUM_WEEKLY)
        purchasePremium(activity, via = VIA_EXIT_OFFER)
    }

    private fun showMessage(msg: String) {
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            _uiMessage.value = msg
            delay(3500)
            _uiMessage.value = null
        }
    }

    companion object {
        private const val TAG = "PremiumViewModel"
        const val VIA_PAYWALL = "paywall"
        const val VIA_EXIT_OFFER = "exit_offer"
        private const val EXIT_OFFER_COOLDOWN_MS = 3L * 24 * 60 * 60 * 1000
    }
}
