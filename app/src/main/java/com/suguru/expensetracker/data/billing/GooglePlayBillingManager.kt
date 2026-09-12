package com.suguru.expensetracker.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.suguru.expensetracker.domain.model.BillingProducts
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.ProProductDetails
import com.suguru.expensetracker.domain.model.PurchaseLaunchResult
import com.suguru.expensetracker.domain.model.RestorePurchasesResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed interface BillingQueryResult {
    data class Success(val entitlement: ProEntitlement) : BillingQueryResult
    data class Error(val reason: String) : BillingQueryResult
}

/**
 * App-scoped manager isolating Google Play BillingClient connection, purchase queries,
 * acknowledgement, and product details query.
 */
class GooglePlayBillingManager(
    private val context: Context,
    private val onEntitlementUpdated: (ProEntitlement) -> Unit
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isConnecting = AtomicBoolean(false)

    private val _productDetails = MutableStateFlow<ProProductDetails?>(null)
    val productDetails: StateFlow<ProProductDetails?> = _productDetails.asStateFlow()

    private var cachedNativeProductDetails: ProductDetails? = null

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
    }

    fun startConnection(onConnected: (() -> Unit)? = null) {
        if (billingClient.isReady) {
            onConnected?.invoke()
            return
        }
        if (isConnecting.getAndSet(true)) return

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isConnecting.set(false)
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "BillingClient connected successfully.")
                    scope.launch {
                        queryProductDetails()
                        queryPurchases()
                    }
                    onConnected?.invoke()
                } else {
                    Log.w(TAG, "BillingClient setup failed: ${billingResult.debugMessage} (code ${billingResult.responseCode})")
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting.set(false)
                Log.w(TAG, "BillingClient disconnected. Will reconnect on demand.")
            }
        })
    }

    suspend fun queryProductDetails(): ProProductDetails? = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext null

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(BillingProducts.PRO_LIFETIME)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        return@withContext suspendCoroutine<ProProductDetails?> { continuation ->
            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !productDetailsList.isNullOrEmpty()) {
                    val nativeDetails = productDetailsList.first()
                    cachedNativeProductDetails = nativeDetails
                    val offerDetails = nativeDetails.oneTimePurchaseOfferDetails
                    val priceFormatted = offerDetails?.formattedPrice ?: "N/A"
                    val domainDetails = ProProductDetails(
                        productId = nativeDetails.productId,
                        title = nativeDetails.title,
                        description = nativeDetails.description,
                        formattedPrice = priceFormatted
                    )
                    _productDetails.value = domainDetails
                    continuation.resume(domainDetails)
                } else {
                    Log.w(TAG, "Failed to query ProductDetails: ${billingResult.debugMessage}")
                    continuation.resume(null)
                }
            }
        }
    }

    suspend fun queryPurchases(): BillingQueryResult = withContext(Dispatchers.IO) {
        if (!ensureConnected()) {
            return@withContext BillingQueryResult.Error("Billing service unavailable")
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        return@withContext suspendCoroutine<BillingQueryResult> { continuation ->
            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val proPurchase = purchases.firstOrNull { purchase ->
                        purchase.products.contains(BillingProducts.PRO_LIFETIME)
                    }

                    if (proPurchase != null) {
                        when (proPurchase.purchaseState) {
                            Purchase.PurchaseState.PURCHASED -> {
                                scope.launch {
                                    acknowledgeIfNeeded(proPurchase)
                                }
                                onEntitlementUpdated(ProEntitlement.Pro)
                                continuation.resume(BillingQueryResult.Success(ProEntitlement.Pro))
                            }
                            Purchase.PurchaseState.PENDING -> {
                                onEntitlementUpdated(ProEntitlement.Pending)
                                continuation.resume(BillingQueryResult.Success(ProEntitlement.Pending))
                            }
                            else -> {
                                onEntitlementUpdated(ProEntitlement.Free)
                                continuation.resume(BillingQueryResult.Success(ProEntitlement.Free))
                            }
                        }
                    } else {
                        onEntitlementUpdated(ProEntitlement.Free)
                        continuation.resume(BillingQueryResult.Success(ProEntitlement.Free))
                    }
                } else {
                    continuation.resume(BillingQueryResult.Error(billingResult.debugMessage))
                }
            }
        }
    }

    suspend fun launchPurchaseFlow(activity: Activity): PurchaseLaunchResult = withContext(Dispatchers.Main) {
        if (!ensureConnected()) {
            return@withContext PurchaseLaunchResult.BillingUnavailable
        }

        val nativeDetails = cachedNativeProductDetails ?: run {
            val fetched = queryProductDetails()
            if (fetched == null) return@withContext PurchaseLaunchResult.ProductUnavailable
            cachedNativeProductDetails
        } ?: return@withContext PurchaseLaunchResult.ProductUnavailable

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(nativeDetails)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        return@withContext when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> PurchaseLaunchResult.Launched
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                scope.launch { queryPurchases() }
                PurchaseLaunchResult.AlreadyOwned
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseLaunchResult.Error("Purchase cancelled.")
            else -> PurchaseLaunchResult.Error(result.debugMessage)
        }
    }

    suspend fun restorePurchases(): RestorePurchasesResult = withContext(Dispatchers.IO) {
        val result = queryPurchases()
        return@withContext when (result) {
            is BillingQueryResult.Success -> {
                if (result.entitlement == ProEntitlement.Pro) {
                    RestorePurchasesResult.Restored
                } else {
                    RestorePurchasesResult.NothingToRestore
                }
            }
            is BillingQueryResult.Error -> RestorePurchasesResult.BillingUnavailable
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    val proPurchase = purchases.firstOrNull { it.products.contains(BillingProducts.PRO_LIFETIME) }
                    if (proPurchase != null) {
                        when (proPurchase.purchaseState) {
                            Purchase.PurchaseState.PURCHASED -> {
                                scope.launch {
                                    acknowledgeIfNeeded(proPurchase)
                                }
                                onEntitlementUpdated(ProEntitlement.Pro)
                            }
                            Purchase.PurchaseState.PENDING -> {
                                onEntitlementUpdated(ProEntitlement.Pending)
                            }
                            else -> {
                                onEntitlementUpdated(ProEntitlement.Free)
                            }
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                scope.launch { queryPurchases() }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "User cancelled purchase flow.")
            }
            else -> {
                Log.w(TAG, "PurchasesUpdated failed: ${billingResult.debugMessage}")
            }
        }
    }

    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (!purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            suspendCoroutine<Unit> { continuation ->
                billingClient.acknowledgePurchase(params) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.i(TAG, "Purchase acknowledged successfully.")
                    } else {
                        Log.w(TAG, "Failed to acknowledge purchase: ${billingResult.debugMessage}")
                    }
                    continuation.resume(Unit)
                }
            }
        }
    }

    private suspend fun ensureConnected(): Boolean = suspendCoroutine { continuation ->
        if (billingClient.isReady) {
            continuation.resume(true)
        } else {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    val ok = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                    continuation.resume(ok)
                }

                override fun onBillingServiceDisconnected() {
                    Log.w(TAG, "Disconnected during connection check.")
                }
            })
        }
    }

    companion object {
        private const val TAG = "ExpenseTrackerBilling"
    }
}
