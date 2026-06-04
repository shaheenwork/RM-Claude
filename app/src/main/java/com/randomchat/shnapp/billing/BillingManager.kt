package com.randomchat.shnapp.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.randomchat.shnapp.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PremiumPlan(
    val productId: String,
    val period: String,
    val formattedPrice: String,
    val offerToken: String,
    val badge: String? = null
)

class BillingManager(
    private val context: Context,
    private val onPremiumGranted: suspend (purchaseToken: String, productId: String, expiryMs: Long) -> Unit,
    private val onPremiumRevoked: suspend () -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _plans = MutableStateFlow<List<PremiumPlan>>(emptyList())
    val plans: StateFlow<List<PremiumPlan>> = _plans

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _activePlanId = MutableStateFlow<String?>(null)
    val activePlanId: StateFlow<String?> = _activePlanId

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // Fire telemetry for genuinely new purchases (this listener only fires for
            // the active billing flow, not restore — restore goes through restorePurchases()).
            purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { p ->
                    p.products.firstOrNull { it in Constants.ALL_PREMIUM_PRODUCTS }?.let { id ->
                        com.randomchat.shnapp.utils.Telemetry.premiumPurchased(id)
                    }
                }
            scope.launch { handlePurchases(purchases) }
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryProductDetails()
                        restorePurchases()
                    }
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private suspend fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                Constants.ALL_PREMIUM_PRODUCTS.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            ).build()

        val detailsList = suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { _, list -> cont.resume(list) }
        }

        productDetailsMap.clear()
        detailsList.forEach { productDetailsMap[it.productId] = it }

        val sortOrder = mapOf(
            Constants.PRODUCT_PREMIUM_WEEKLY  to 0,
            Constants.PRODUCT_PREMIUM_MONTHLY to 1,
            Constants.PRODUCT_PREMIUM_YEARLY  to 2
        )
        val periodLabels = mapOf(
            Constants.PRODUCT_PREMIUM_WEEKLY  to "Weekly",
            Constants.PRODUCT_PREMIUM_MONTHLY to "Monthly",
            Constants.PRODUCT_PREMIUM_YEARLY  to "Yearly"
        )
        val badges = mapOf(
            Constants.PRODUCT_PREMIUM_MONTHLY to "Most Popular",
            Constants.PRODUCT_PREMIUM_YEARLY  to "Best Value"
        )

        _plans.value = detailsList
            .sortedBy { sortOrder[it.productId] ?: 99 }
            .mapNotNull { details ->
                val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return@mapNotNull null
                val price = offer.pricingPhases.pricingPhaseList.firstOrNull()?.formattedPrice
                    ?: return@mapNotNull null
                PremiumPlan(
                    productId  = details.productId,
                    period     = periodLabels[details.productId] ?: details.productId,
                    formattedPrice = price,
                    offerToken = offer.offerToken,
                    badge      = badges[details.productId]
                )
            }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val plan = _plans.value.find { it.productId == productId } ?: return
        val details = productDetailsMap[productId] ?: return

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(plan.offerToken)
                        .build()
                )
            ).build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    suspend fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val purchases = suspendCancellableCoroutine { cont ->
            billingClient.queryPurchasesAsync(params) { _, list -> cont.resume(list) }
        }
        handlePurchases(purchases)
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val activePurchase = purchases.firstOrNull { purchase ->
            purchase.products.any { it in Constants.ALL_PREMIUM_PRODUCTS } &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        if (activePurchase != null) {
            val grantedProductId = activePurchase.products.firstOrNull { it in Constants.ALL_PREMIUM_PRODUCTS } ?: ""
            _isPremium.value = true
            _activePlanId.value = grantedProductId
            if (!activePurchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(activePurchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(ackParams) {}
            }
            onPremiumGranted(activePurchase.purchaseToken, grantedProductId, 0L)
        } else {
            _isPremium.value = false
            _activePlanId.value = null
            onPremiumRevoked()
        }
    }

    fun disconnect() {
        billingClient.endConnection()
    }

    companion object {
        @Volatile private var instance: BillingManager? = null

        fun getInstance(
            context: Context,
            onPremiumGranted: suspend (String, String, Long) -> Unit = { _, _, _ -> },
            onPremiumRevoked: suspend () -> Unit = {}
        ): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext, onPremiumGranted, onPremiumRevoked)
                    .also { instance = it }
            }
        }
    }
}
