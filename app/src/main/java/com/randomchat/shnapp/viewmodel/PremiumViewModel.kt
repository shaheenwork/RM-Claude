package com.randomchat.shnapp.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.randomchat.shnapp.BuildConfig
import com.randomchat.shnapp.billing.BillingManager
import com.randomchat.shnapp.billing.PremiumPlan
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        PremiumPlan(Constants.PRODUCT_PREMIUM_WEEKLY,  "Weekly",  "$2.99",  "fake_offer", null),
        PremiumPlan(Constants.PRODUCT_PREMIUM_MONTHLY, "Monthly", "$6.99",  "fake_offer", "Most Popular"),
        PremiumPlan(Constants.PRODUCT_PREMIUM_YEARLY,  "Yearly",  "$39.99", "fake_offer", "Best Value"),
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

    private val _selectedPlanId = MutableStateFlow(Constants.PRODUCT_PREMIUM_MONTHLY)
    val selectedPlanId: StateFlow<String> = _selectedPlanId

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage

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
        }
    }

    fun selectPlan(productId: String) {
        _selectedPlanId.value = productId
    }

    fun purchasePremium(activity: Activity) {
        if (useFakeBilling) {
            viewModelScope.launch {
                sessionManager.setPremium(true, 0L)
                _fakeActivePlanId.value = _selectedPlanId.value
                showMessage("✓ Test purchase successful (debug build)")
            }
            return
        }
        if (plans.value.isEmpty()) {
            billingManager.connect()
            showMessage("Loading pricing… please try again in a moment.")
            return
        }
        billingManager.launchPurchaseFlow(activity, _selectedPlanId.value)
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
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    fun restorePurchases() {
        if (useFakeBilling) {
            showMessage("Nothing to restore (debug build)")
            return
        }
        viewModelScope.launch {
            billingManager.restorePurchases()
        }
    }

    private fun showMessage(msg: String) {
        viewModelScope.launch {
            _uiMessage.value = msg
            delay(3500)
            _uiMessage.value = null
        }
    }
}
