package com.suguru.expensetracker.presentation.startup

import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.domain.model.CurrencyInfo

sealed interface StartupProvisioningState {
    data object Loading : StartupProvisioningState
    data object SettingsRecoveryPending : StartupProvisioningState
    data object Ready : StartupProvisioningState
    data object RequiresCurrencySelection : StartupProvisioningState
    data class RecoverableError(val message: String) : StartupProvisioningState
    data class UnrecoverableError(val message: String) : StartupProvisioningState

    companion object {
        fun fromResult(result: InternalDefaultAccountProvisioningResult): StartupProvisioningState {
            return when (result) {
                is InternalDefaultAccountProvisioningResult.Ready -> Ready
                is InternalDefaultAccountProvisioningResult.RequiresCurrencySelection -> RequiresCurrencySelection
                is InternalDefaultAccountProvisioningResult.Failure -> {
                    val message = result.error.localizedMessage ?: "Unable to initialize the app."
                    if (message.contains("currency", ignoreCase = true) ||
                        message.contains("selection", ignoreCase = true)
                    ) {
                        RecoverableError(message)
                    } else {
                        UnrecoverableError(message)
                    }
                }
            }
        }
    }
}

val StartupProvisioningState.allowsNavigation: Boolean
    get() = this == StartupProvisioningState.Ready

val SupportedStartupCurrencies: List<CurrencyInfo> = CurrencyInfo.SUPPORTED_CURRENCIES
