package com.randomchat.shnapp.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.utils.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

data class PremiumPlan(
    val productId: String,
    /** Display label: "Weekly" / "Monthly" / "Yearly". */
    val period: String,
    /** Play-formatted recurring price, e.g. "₹149.00". */
    val formattedPrice: String,
    val offerToken: String,
    val priceMicros: Long,
    val currencyCode: String,
    /** ISO-8601 billing period, e.g. "P1W", "P1M", "P1Y". */
    val billingPeriod: String
)

/** Outcome of a purchase flow, surfaced once by the paywall. */
sealed class PurchaseEvent {
    data object Success : PurchaseEvent()
    /** Paid but not confirmed yet — typical for UPI and other delayed payment methods. */
    data object Pending : PurchaseEvent()
    data object Cancelled : PurchaseEvent()
    data object AlreadyOwned : PurchaseEvent()
    data class Failed(val responseCode: Int) : PurchaseEvent()
}

enum class RestoreResult { RESTORED, NOTHING_FOUND, FAILED }

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

    /** True while Play holds a premium purchase that is started but not yet confirmed. */
    private val _hasPendingPurchase = MutableStateFlow(false)
    val hasPendingPurchase: StateFlow<Boolean> = _hasPendingPurchase

    private val _purchaseEvents = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 4)
    val purchaseEvents: SharedFlow<PurchaseEvent> = _purchaseEvents

    @Volatile private var productDetailsMap: Map<String, ProductDetails> = emptyMap()

    // Connection bookkeeping — Play can drop the service connection at any time.
    private val ready = MutableStateFlow(false)
    private val connecting = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)

    // Attribution of the purchase flow currently on screen (analytics only).
    @Volatile private var flowProductId: String? = null
    @Volatile private var flowSource: String? = null
    @Volatile private var flowVia: String? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        val productId = flowProductId
        val source = flowSource
        val via = flowVia
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                val purchased = list.filter {
                    it.isPremium() && it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                // Telemetry for genuinely new purchases (this listener only fires for
                // the active billing flow, not restore — restore goes through restorePurchases()).
                purchased.forEach { p ->
                    p.products.firstOrNull { it in Constants.ALL_PREMIUM_PRODUCTS }?.let { id ->
                        Telemetry.premiumPurchased(id, source, via)
                    }
                }
                if (purchased.isNotEmpty()) {
                    _purchaseEvents.tryEmit(PurchaseEvent.Success)
                } else if (list.any { it.isPremium() && it.purchaseState == Purchase.PurchaseState.PENDING }) {
                    Telemetry.purchasePending(productId, source, via)
                    _purchaseEvents.tryEmit(PurchaseEvent.Pending)
                }
                // Partial update (only this flow's purchases) — must never revoke.
                scope.launch { handlePurchases(list, isFullSnapshot = false) }
            }
            BillingResponseCode.USER_CANCELED -> {
                Telemetry.purchaseCancelled(productId, source, via)
                _purchaseEvents.tryEmit(PurchaseEvent.Cancelled)
            }
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Bought earlier (reinstall / other device) — re-sync instead of failing.
                scope.launch {
                    restorePurchases()
                    _purchaseEvents.tryEmit(PurchaseEvent.AlreadyOwned)
                }
            }
            else -> {
                Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
                Telemetry.purchaseFailed(productId, source, via, result.responseCode)
                _purchaseEvents.tryEmit(PurchaseEvent.Failed(result.responseCode))
                if (result.responseCode.isConnectionError()) refresh()
            }
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    fun connect() {
        if (billingClient.isReady) {
            ready.value = true
            return
        }
        if (!connecting.compareAndSet(false, true)) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting.set(false)
                if (result.responseCode == BillingResponseCode.OK) {
                    reconnectAttempts.set(0)
                    ready.value = true
                    scope.launch {
                        queryProductDetails()
                        restorePurchases()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                    ready.value = false
                    scheduleReconnect()
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting.set(false)
                ready.value = false
                scheduleReconnect()
            }
        })
    }

    /**
     * Re-fetches plans, reconnecting first when needed. Called when the paywall
     * opens and after connection errors, so a dropped Play connection never leaves
     * the paywall stuck on "Loading pricing…".
     */
    fun refresh() {
        if (billingClient.isReady) {
            scope.launch { queryProductDetails() }
        } else {
            reconnectAttempts.set(0)
            connect()
        }
    }

    /** Exponential backoff — 1 s, 2 s, 4 s, 8 s, 16 s — then waits for [refresh]. */
    private fun scheduleReconnect() {
        val attempt = reconnectAttempts.getAndIncrement()
        if (attempt >= MAX_RECONNECT_ATTEMPTS) return
        val delayMs = (1_000L shl attempt).coerceAtMost(30_000L)
        scope.launch {
            delay(delayMs)
            connect()
        }
    }

    private suspend fun awaitReady(timeoutMs: Long = 5_000L): Boolean {
        if (billingClient.isReady) return true
        connect()
        return withTimeoutOrNull(timeoutMs) { ready.first { it } } ?: false
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

        val (result, detailsList) = suspendCancellableCoroutine<Pair<BillingResult, List<ProductDetails>>> { cont ->
            billingClient.queryProductDetailsAsync(params) { r, list -> cont.resume(r to list) }
        }
        if (result.responseCode != BillingResponseCode.OK) {
            // Keep the last good plans; the paywall calls refresh() when it opens.
            Log.w(TAG, "queryProductDetails failed: ${result.responseCode} ${result.debugMessage}")
            return
        }

        productDetailsMap = detailsList.associateBy { it.productId }

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

        _plans.value = detailsList
            .sortedBy { sortOrder[it.productId] ?: 99 }
            .mapNotNull { it.toPlan(periodLabels[it.productId] ?: it.productId) }
    }

    /**
     * Uses the base plan (offerId == null) and its recurring price. Promotional
     * offers are ignored on purpose: their first pricing phase (e.g. "Free") would
     * otherwise be shown as the plan price.
     */
    private fun ProductDetails.toPlan(periodLabel: String): PremiumPlan? {
        val offers = subscriptionOfferDetails.orEmpty()
        val offer = offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() ?: return null
        val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return null
        return PremiumPlan(
            productId      = productId,
            period         = periodLabel,
            formattedPrice = phase.formattedPrice,
            offerToken     = offer.offerToken,
            priceMicros    = phase.priceAmountMicros,
            currencyCode   = phase.priceCurrencyCode,
            billingPeriod  = phase.billingPeriod
        )
    }

    /**
     * @param source paywall entry point ([PaywallSource]) and [via] the surface that
     *        started the flow ("paywall" / "exit_offer") — analytics attribution only.
     * @return false when the plan isn't loaded or Play refused to open the flow.
     */
    fun launchPurchaseFlow(activity: Activity, productId: String, source: String, via: String): Boolean {
        val plan = _plans.value.find { it.productId == productId } ?: return false
        val details = productDetailsMap[productId] ?: return false

        flowProductId = productId
        flowSource = source
        flowVia = via
        Telemetry.purchaseStarted(productId, source, via)

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(plan.offerToken)
                        .build()
                )
            ).build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingResponseCode.OK) {
            Log.w(TAG, "launchBillingFlow failed: ${result.responseCode} ${result.debugMessage}")
            // Play normally reports this through purchasesUpdatedListener as well; emit
            // here too so the paywall is never silent. A repeated message is harmless.
            if (result.responseCode != BillingResponseCode.USER_CANCELED &&
                result.responseCode != BillingResponseCode.ITEM_ALREADY_OWNED
            ) {
                _purchaseEvents.tryEmit(PurchaseEvent.Failed(result.responseCode))
            }
            return false
        }
        return true
    }

    /**
     * Queries Play for owned subscriptions and re-applies premium state.
     * Lets the UI tell "restored ✓" from "nothing to restore" from "couldn't check".
     */
    suspend fun restorePurchases(): RestoreResult {
        if (!awaitReady()) return RestoreResult.FAILED

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val (result, purchases) = suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { cont ->
            billingClient.queryPurchasesAsync(params) { r, list -> cont.resume(r to list) }
        }
        if (result.responseCode != BillingResponseCode.OK) {
            // A failed query returns an empty list that says nothing about ownership —
            // revoking on it would strip premium from paying users on a bad connection.
            Log.w(TAG, "queryPurchases failed: ${result.responseCode} ${result.debugMessage}")
            return RestoreResult.FAILED
        }

        handlePurchases(purchases, isFullSnapshot = true)
        val hasActive = purchases.any {
            it.isPremium() && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        return if (hasActive) RestoreResult.RESTORED else RestoreResult.NOTHING_FOUND
    }

    /**
     * @param isFullSnapshot true for a successful queryPurchases (everything the user
     *        owns). Listener updates only carry the current flow's purchases, so they
     *        may grant but must never revoke.
     */
    private suspend fun handlePurchases(purchases: List<Purchase>, isFullSnapshot: Boolean) {
        val premiumPurchases = purchases.filter { it.isPremium() }
        val activePurchase = premiumPurchases.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val hasPending = premiumPurchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }

        if (activePurchase != null) {
            _hasPendingPurchase.value = false
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
            if (hasPending || isFullSnapshot) _hasPendingPurchase.value = hasPending
            if (isFullSnapshot) {
                _isPremium.value = false
                _activePlanId.value = null
                onPremiumRevoked()
            }
        }
    }

    private fun Purchase.isPremium(): Boolean =
        products.any { it in Constants.ALL_PREMIUM_PRODUCTS }

    private fun Int.isConnectionError(): Boolean =
        this == BillingResponseCode.SERVICE_DISCONNECTED ||
            this == BillingResponseCode.SERVICE_UNAVAILABLE ||
            this == BillingResponseCode.NETWORK_ERROR

    fun disconnect() {
        billingClient.endConnection()
    }

    companion object {
        private const val TAG = "BillingManager"
        private const val MAX_RECONNECT_ATTEMPTS = 5

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
