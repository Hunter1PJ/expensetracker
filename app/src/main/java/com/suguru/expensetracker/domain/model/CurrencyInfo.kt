package com.suguru.expensetracker.domain.model

import com.suguru.expensetracker.domain.util.MoneyParser

/**
 * Currency information model describing supported currencies, their display names, symbols, and minor unit decimals.
 */
data class CurrencyInfo(
    val currencyCode: String,
    val displayName: String,
    val symbol: String,
    val minorUnitDigits: Int
) {
    companion object {
        val SUPPORTED_CURRENCIES: List<CurrencyInfo> = listOf(
            CurrencyInfo(
                currencyCode = "UZS",
                displayName = "Uzbekistani Som",
                symbol = "so'm",
                minorUnitDigits = 0
            ),
            CurrencyInfo(
                currencyCode = "USD",
                displayName = "US Dollar",
                symbol = "$",
                minorUnitDigits = 2
            ),
            CurrencyInfo(
                currencyCode = "EUR",
                displayName = "Euro",
                symbol = "€",
                minorUnitDigits = 2
            ),
            CurrencyInfo(
                currencyCode = "KRW",
                displayName = "South Korean Won",
                symbol = "₩",
                minorUnitDigits = 0
            ),
            CurrencyInfo(
                currencyCode = "JPY",
                displayName = "Japanese Yen",
                symbol = "¥",
                minorUnitDigits = 0
            )
        )

        val DEFAULT: CurrencyInfo = SUPPORTED_CURRENCIES.first { it.currencyCode == "USD" }

        fun findByCode(code: String): CurrencyInfo {
            val upper = code.uppercase().trim()
            return SUPPORTED_CURRENCIES.firstOrNull { it.currencyCode == upper }
                ?: CurrencyInfo(
                    currencyCode = upper,
                    displayName = upper,
                    symbol = MoneyParser.getCurrencySymbol(upper).trim(),
                    minorUnitDigits = MoneyParser.getDecimalPlaces(upper)
                )
        }
    }
}
