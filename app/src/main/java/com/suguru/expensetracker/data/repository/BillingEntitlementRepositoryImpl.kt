package com.suguru.expensetracker.data.repository

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.suguru.expensetracker.data.billing.BillingQueryResult
import com.suguru.expensetracker.data.billing.GooglePlayBillingManager
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.ProProductDetails
import com.suguru.expensetracker.domain.model.PurchaseLaunchResult
import com.suguru.expensetracker.domain.model.RestorePurchasesResult
import com.suguru.expensetracker.domain.repository.EntitlementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BillingEntitlementRepositoryImpl(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val billingManager: GooglePlayBillingManager
) : EntitlementRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entitlement = MutableStateFlow<ProEntitlement>(ProEntitlement.Checking)
    override val entitlement: StateFlow<ProEntitlement> = _entitlement.asStateFlow()

    override val productDetails: StateFlow<ProProductDetails?> = billingManager.productDetails

    init {
        scope.launch {
            val cachedOwned = getCachedProOwnership()
            if (cachedOwned) {
                _entitlement.value = ProEntitlement.Pro
            } else {
                _entitlement.value = ProEntitlement.Free
            }
            refreshPurchases()
        }
    }

    fun onBillingEntitlementUpdated(newEntitlement: ProEntitlement) {
        scope.launch {
            if (newEntitlement is ProEntitlement.Pro) {
                saveCachedProOwnership(true)
                _entitlement.value = ProEntitlement.Pro
            } else if (newEntitlement is ProEntitlement.Free) {
                saveCachedProOwnership(false)
                _entitlement.value = ProEntitlement.Free
            } else {
                _entitlement.value = newEntitlement
            }
        }
    }

    override suspend fun refreshPurchases() {
        billingManager.startConnection {
            scope.launch {
                when (val result = billingManager.queryPurchases()) {
                    is BillingQueryResult.Success -> {
                        if (result.entitlement is ProEntitlement.Pro) {
                            saveCachedProOwnership(true)
                            _entitlement.value = ProEntitlement.Pro
                        } else if (result.entitlement is ProEntitlement.Free) {
                            saveCachedProOwnership(false)
                            _entitlement.value = ProEntitlement.Free
                        } else {
                            _entitlement.value = result.entitlement
                        }
                    }
                    is BillingQueryResult.Error -> {
                        // Offline or Billing Unavailable continuity:
                        val cachedOwned = getCachedProOwnership()
                        if (cachedOwned) {
                            _entitlement.value = ProEntitlement.Pro
                        } else {
                            _entitlement.value = ProEntitlement.Free
                        }
                    }
                }
            }
        }
    }

    override suspend fun restorePurchases(): RestorePurchasesResult {
        val result = billingManager.restorePurchases()
        if (result is RestorePurchasesResult.Restored) {
            saveCachedProOwnership(true)
            _entitlement.value = ProEntitlement.Pro
        }
        return result
    }

    override suspend fun launchPurchase(activity: Activity): PurchaseLaunchResult {
        val result = billingManager.launchPurchaseFlow(activity)
        if (result is PurchaseLaunchResult.AlreadyOwned) {
            saveCachedProOwnership(true)
            _entitlement.value = ProEntitlement.Pro
        }
        return result
    }

    private suspend fun getCachedProOwnership(): Boolean {
        return try {
            dataStore.data.map { prefs ->
                prefs[PRO_LAST_VERIFIED_OWNED] ?: false
            }.first()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun saveCachedProOwnership(owned: Boolean) {
        try {
            dataStore.edit { prefs ->
                prefs[PRO_LAST_VERIFIED_OWNED] = owned
                prefs[PRO_LAST_VERIFIED_AT] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            // Ignore storage failure gracefully
        }
    }

    companion object {
        private val PRO_LAST_VERIFIED_OWNED = booleanPreferencesKey("pro_last_verified_owned")
        private val PRO_LAST_VERIFIED_AT = longPreferencesKey("pro_last_verified_at_epoch_ms")
    }
}
