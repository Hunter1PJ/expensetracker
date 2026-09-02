package com.example.domain.model

/**
 * Immutable value object representing a monetary amount in the currency's minor units
 * (e.g. cents for USD/EUR, minor unit for UZS/JPY) to avoid floating-point precision loss.
 *
 * Examples:
 * - $10.50 USD is represented as amountInMinorUnits = 1050L with currencyCode = "USD".
 * - 50,000 UZS is represented as amountInMinorUnits = 50000L with currencyCode = "UZS".
 */
data class Money(
    val amountInMinorUnits: Long,
    val currencyCode: String = "USD"
) {
    val isPositive: Boolean get() = amountInMinorUnits > 0L
    val isNegative: Boolean get() = amountInMinorUnits < 0L
    val isZero: Boolean get() = amountInMinorUnits == 0L

    operator fun plus(other: Money): Money {
        require(currencyCode == other.currencyCode) { "Cannot add different currencies: $currencyCode and ${other.currencyCode}" }
        return copy(amountInMinorUnits = amountInMinorUnits + other.amountInMinorUnits)
    }

    operator fun minus(other: Money): Money {
        require(currencyCode == other.currencyCode) { "Cannot subtract different currencies: $currencyCode and ${other.currencyCode}" }
        return copy(amountInMinorUnits = amountInMinorUnits - other.amountInMinorUnits)
    }

    companion object {
        fun zero(currencyCode: String = "USD"): Money = Money(0L, currencyCode)
    }
}
