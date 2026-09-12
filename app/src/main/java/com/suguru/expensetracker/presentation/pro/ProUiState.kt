package com.suguru.expensetracker.presentation.pro

import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.ProProductDetails

data class ProUiState(
    val entitlement: ProEntitlement = ProEntitlement.Checking,
    val product: ProProductDetails? = null,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val userMessage: String? = null
)
