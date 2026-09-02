package com.example.presentation.transactions.add

import com.example.domain.error.DomainException

/**
 * Presentation-level error model for the Add Transaction screen.
 * Keeps UI completely decoupled from domain exceptions.
 */
sealed interface AddTransactionError {
    data object InvalidAmount : AddTransactionError
    data object AmountMustBePositive : AddTransactionError
    data object AccountRequired : AddTransactionError
    data object CategoryRequired : AddTransactionError
    data object DestinationRequired : AddTransactionError
    data object SameSourceAndDestination : AddTransactionError
    data object CurrencyMismatch : AddTransactionError
    data object AccountArchived : AddTransactionError
    data object CategoryArchived : AddTransactionError
    data object IncompatibleCategory : AddTransactionError
    data class Unknown(val message: String? = null) : AddTransactionError

    companion object {
        fun fromDomainException(exception: Throwable): AddTransactionError {
            return when (exception) {
                is DomainException.InvalidAmount -> AmountMustBePositive
                is DomainException.InvalidCurrency -> InvalidAmount
                is DomainException.CurrencyMismatch -> CurrencyMismatch
                is DomainException.AccountNotFound -> AccountRequired
                is DomainException.AccountArchived -> AccountArchived
                is DomainException.CategoryNotFound -> CategoryRequired
                is DomainException.CategoryArchived -> CategoryArchived
                is DomainException.IncompatibleCategoryType -> IncompatibleCategory
                is DomainException.InvalidTransfer -> {
                    val msg = exception.message ?: ""
                    when {
                        msg.contains("destination", ignoreCase = true) && msg.contains("specify", ignoreCase = true) -> DestinationRequired
                        msg.contains("different", ignoreCase = true) -> SameSourceAndDestination
                        msg.contains("currencies", ignoreCase = true) -> CurrencyMismatch
                        else -> Unknown(msg)
                    }
                }
                is DomainException.InvalidAccount -> AccountRequired
                is DomainException.InvalidCategory -> CategoryRequired
                else -> Unknown(exception.message)
            }
        }
    }
}
