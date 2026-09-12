package com.suguru.expensetracker.presentation.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.PurchaseLaunchResult
import com.suguru.expensetracker.domain.model.RestorePurchasesResult
import com.suguru.expensetracker.domain.usecase.billing.LaunchProPurchaseUseCase
import com.suguru.expensetracker.domain.usecase.billing.ObserveProEntitlementUseCase
import com.suguru.expensetracker.domain.usecase.billing.ObserveProProductDetailsUseCase
import com.suguru.expensetracker.domain.usecase.billing.RefreshProEntitlementUseCase
import com.suguru.expensetracker.domain.usecase.billing.RestorePurchasesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProViewModel(
    private val observeProEntitlementUseCase: ObserveProEntitlementUseCase,
    private val observeProProductDetailsUseCase: ObserveProProductDetailsUseCase,
    private val refreshProEntitlementUseCase: RefreshProEntitlementUseCase,
    private val restorePurchasesUseCase: RestorePurchasesUseCase,
    private val launchProPurchaseUseCase: LaunchProPurchaseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProUiState())
    val uiState: StateFlow<ProUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeProEntitlementUseCase().collect { entitlement ->
                _uiState.update { it.copy(entitlement = entitlement) }
            }
        }
        viewModelScope.launch {
            observeProProductDetailsUseCase().collect { product ->
                _uiState.update { it.copy(product = product) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshProEntitlementUseCase()
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, userMessage = null) }
            when (val result = restorePurchasesUseCase()) {
                is RestorePurchasesResult.Restored -> {
                    _uiState.update { it.copy(isRestoring = false, userMessage = "ExpenseTracker Pro restored successfully!") }
                }
                is RestorePurchasesResult.NothingToRestore -> {
                    _uiState.update { it.copy(isRestoring = false, userMessage = "No existing Pro purchase found for this account.") }
                }
                is RestorePurchasesResult.BillingUnavailable -> {
                    _uiState.update { it.copy(isRestoring = false, userMessage = "Google Play billing is currently unavailable.") }
                }
                is RestorePurchasesResult.Error -> {
                    _uiState.update { it.copy(isRestoring = false, userMessage = result.message ?: "Failed to restore purchase.") }
                }
            }
        }
    }

    fun launchPurchase(activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPurchasing = true, userMessage = null) }
            when (val result = launchProPurchaseUseCase(activity)) {
                is PurchaseLaunchResult.Launched -> {
                    _uiState.update { it.copy(isPurchasing = false) }
                }
                is PurchaseLaunchResult.AlreadyOwned -> {
                    _uiState.update { it.copy(isPurchasing = false, userMessage = "Pro is already unlocked on this account!") }
                }
                is PurchaseLaunchResult.ProductUnavailable -> {
                    _uiState.update { it.copy(isPurchasing = false, userMessage = "Pro product details could not be retrieved from Google Play.") }
                }
                is PurchaseLaunchResult.BillingUnavailable -> {
                    _uiState.update { it.copy(isPurchasing = false, userMessage = "Google Play billing service is unavailable.") }
                }
                is PurchaseLaunchResult.Error -> {
                    _uiState.update { it.copy(isPurchasing = false, userMessage = result.message ?: "Purchase cancelled or failed.") }
                }
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
