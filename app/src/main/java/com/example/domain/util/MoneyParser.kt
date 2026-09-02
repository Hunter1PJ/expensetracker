package com.example.domain.util

import com.example.domain.model.Money

/**
 * Safe, precision-loss-free parser for monetary string inputs.
 * Converts user decimal string input (e.g. "12", "12.5", "12.50") into integer minor units (Long)
 * without using any floating-point arithmetic (no Double or Float).
 */
object MoneyParser {

    /**
     * Determines standard minor unit decimal places for a given currency code.
     * Default is 2 decimal places (1 major unit = 100 minor units).
     * Zero-decimal currencies (JPY, KRW, UZS, VND, etc.) use 0 decimal places when treated as whole units,
     * but standard ISO-4217 standardizes 2 for most fiat currencies.
     */
    fun getDecimalPlaces(currencyCode: String): Int {
        return when (currencyCode.uppercase().trim()) {
            "JPY", "KRW", "VND", "CLP", "PYG", "RWF", "UGX", "BIF", "DJF", "GNF", "KMF" -> 0
            "BHD", "JOD", "KWD", "OMR", "TND" -> 3
            else -> 2
        }
    }

    /**
     * Parses a raw string input into a [Money] object for the specified [currencyCode].
     *
     * @param input Raw user string, e.g. "12.50" or "12,50"
     * @param currencyCode Currency code, e.g. "USD"
     * @return [Result] containing [Money] on success, or an [IllegalArgumentException] on parse failure.
     */
    fun parse(input: String, currencyCode: String = "USD"): Result<Money> {
        val trimmed = input.trim().replace(',', '.')
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Amount cannot be empty"))
        }

        // Reject negative signs explicitly
        if (trimmed.startsWith('-') || trimmed.startsWith('+')) {
            return Result.failure(IllegalArgumentException("Amount must be a positive number without signs"))
        }

        val decimals = getDecimalPlaces(currencyCode)
        val parts = trimmed.split('.')

        if (parts.size > 2) {
            return Result.failure(IllegalArgumentException("Invalid number format: multiple decimal points"))
        }

        val integerPartStr = parts[0]
        val fractionalPartStr = if (parts.size == 2) parts[1] else ""

        // Validate integer part digits
        if (integerPartStr.isNotEmpty() && !integerPartStr.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Invalid characters in whole amount"))
        }

        // Validate fractional part digits
        if (fractionalPartStr.isNotEmpty() && !fractionalPartStr.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Invalid characters in decimal fraction"))
        }

        if (decimals == 0 && fractionalPartStr.isNotEmpty() && fractionalPartStr.any { it != '0' }) {
            return Result.failure(IllegalArgumentException("Currency $currencyCode does not support decimal fractions"))
        }

        if (fractionalPartStr.length > decimals) {
            return Result.failure(IllegalArgumentException("Too many decimal places (maximum $decimals allowed)"))
        }

        // Parse whole amount safely with overflow protection
        val wholePart: Long = if (integerPartStr.isEmpty()) {
            0L
        } else {
            try {
                integerPartStr.toLong()
            } catch (e: NumberFormatException) {
                return Result.failure(IllegalArgumentException("Amount is too large"))
            }
        }

        // Scale whole part to minor units using integer multiplication
        var multiplier = 1L
        for (i in 0 until decimals) {
            multiplier *= 10L
        }

        val wholeInMinorUnits = try {
            Math.multiplyExact(wholePart, multiplier)
        } catch (e: ArithmeticException) {
            return Result.failure(IllegalArgumentException("Amount is too large"))
        }

        // Parse fraction part into minor units
        val normalizedFractionStr = fractionalPartStr.padEnd(decimals, '0')
        val fractionInMinorUnits: Long = if (normalizedFractionStr.isEmpty()) {
            0L
        } else {
            normalizedFractionStr.toLong()
        }

        val totalMinorUnits = try {
            Math.addExact(wholeInMinorUnits, fractionInMinorUnits)
        } catch (e: ArithmeticException) {
            return Result.failure(IllegalArgumentException("Amount is too large"))
        }

        return Result.success(Money(amountInMinorUnits = totalMinorUnits, currencyCode = currencyCode))
    }

    /**
     * Formats a [Money] object into a display string without floating-point arithmetic.
     * E.g. Money(1250L, "USD") -> "$12.50" or "12.50 USD".
     */
    fun format(money: Money, includeSymbol: Boolean = true): String {
        val decimals = getDecimalPlaces(money.currencyCode)
        val isNegative = money.amountInMinorUnits < 0
        val absoluteUnits = if (isNegative) -money.amountInMinorUnits else money.amountInMinorUnits

        val formattedAmount = if (decimals == 0) {
            absoluteUnits.toString()
        } else {
            var divisor = 1L
            for (i in 0 until decimals) {
                divisor *= 10L
            }
            val whole = absoluteUnits / divisor
            val fraction = absoluteUnits % divisor
            val fractionStr = fraction.toString().padStart(decimals, '0')
            "$whole.$fractionStr"
        }

        val sign = if (isNegative) "-" else ""

        if (!includeSymbol) {
            return "$sign$formattedAmount"
        }

        val symbol = getCurrencySymbol(money.currencyCode)
        return if (symbol.isNotEmpty()) {
            "$sign$symbol$formattedAmount"
        } else {
            "$sign$formattedAmount ${money.currencyCode}"
        }
    }

    fun getCurrencySymbol(currencyCode: String): String {
        return when (currencyCode.uppercase().trim()) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "CNY" -> "¥"
            "RUB" -> "₽"
            "UZS" -> "UZS "
            "INR" -> "₹"
            else -> "$currencyCode "
        }
    }
}
