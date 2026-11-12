package com.suguru.expensetracker.domain.parser

import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.util.MoneyParser
import java.time.LocalDate
import java.util.regex.Pattern

/**
 * Deterministic, rule-based natural language transaction parser supporting Uzbek, Russian, and English.
 * Operates purely offline with exact integer minor-unit arithmetic via [MoneyParser].
 */
class DeterministicTransactionParser(
    private val clockToday: () -> LocalDate = { LocalDate.now() }
) : TransactionInputParser {

    override suspend fun parse(input: String, defaultCurrency: String): List<TransactionCandidate> {
        if (input.isBlank()) {
            return emptyList()
        }

        val lines = input.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return lines.map { line -> parseSingleLine(line, defaultCurrency) }
    }

    private fun parseSingleLine(line: String, defaultCurrency: String): TransactionCandidate {
        val today = clockToday()
        val normalized = line.lowercase()

        // 1. Date detection
        val date = detectDate(normalized, today)

        // 2. Transaction Type detection
        val type = detectType(normalized)

        // 3. Currency and Amount extraction
        val amountResult = extractAmountAndCurrency(line, defaultCurrency)

        // 4. Unit price vs total ambiguity detection (e.g. "3 kg olma 10000 so'mdan" vs "jami")
        val isUnitPrice = normalized.contains("so'mdan") || normalized.contains("сум с") || normalized.contains("each") || (normalized.contains("dan") && !normalized.contains("jami"))
        val isExplicitTotal = normalized.contains("jami") || normalized.contains("всего") || normalized.contains("total")

        val warnings = mutableListOf<ParseWarning>()
        var confidence = ParseConfidence.HIGH

        if (amountResult.amount == null) {
            warnings.add(ParseWarning.MISSING_AMOUNT)
            confidence = ParseConfidence.LOW
        } else if (amountResult.isAmbiguous) {
            warnings.add(ParseWarning.AMBIGUOUS_AMOUNT)
            confidence = ParseConfidence.AMBIGUOUS
        }

        if (type == null) {
            warnings.add(ParseWarning.AMBIGUOUS_TYPE)
            confidence = minOf(confidence, ParseConfidence.MEDIUM)
        }

        if (isUnitPrice && !isExplicitTotal) {
            warnings.add(ParseWarning.UNIT_PRICE_AMBIGUITY)
            confidence = ParseConfidence.AMBIGUOUS
        }

        val finalType = type ?: TransactionType.EXPENSE // Default fallback to expense if ambiguous
        val title = cleanTitle(line)

        // Extract category/account suggestions based on keywords
        val category = detectCategory(normalized, finalType)
        val accounts = detectAccounts(normalized)

        return TransactionCandidate(
            rawText = line,
            suggestedTitle = title,
            transactionType = finalType,
            amount = amountResult.amount,
            date = date,
            suggestedCategoryName = category,
            suggestedAccountName = accounts.accountName,
            transferSourceAccountName = accounts.sourceAccount,
            transferDestinationAccountName = accounts.destAccount,
            confidence = confidence,
            warnings = warnings,
            isUnitPriceInterpretation = isUnitPrice && !isExplicitTotal,
            parseError = if (amountResult.error != null) amountResult.error else null
        )
    }

    private data class AmountParseResult(
        val amount: Money?,
        val isAmbiguous: Boolean,
        val error: String?
    )

    private data class AccountDetectionResult(
        val accountName: String? = null,
        val sourceAccount: String? = null,
        val destAccount: String? = null
    )

    private fun detectDate(normalized: String, today: LocalDate): LocalDate {
        return when {
            normalized.contains("kecha") || normalized.contains("вчера") || normalized.contains("yesterday") -> today.minusDays(1)
            else -> today
        }
    }

    private fun detectType(normalized: String): TransactionType? {
        val expenseKeywords = listOf("sarfladim", "xarajat", "to'ladim", "toledim", "spent", "paid", "потратил", "оплатил", "купил", "олдим" /* context dependent */)
        val incomeKeywords = listOf("oldim", "maosh", "oylik", "daromad", "received", "salary", "получил", "зарплата", "прибыль")
        val transferKeywords = listOf("o'tkazdim", "o'tkazma", "transfer", "перевод", "перевел", "kartamdan")

        val hasTransfer = transferKeywords.any { normalized.contains(it) }
        val hasIncome = incomeKeywords.any { normalized.contains(it) && !normalized.contains("non") && !normalized.contains("tuxum") }
        val hasExpense = expenseKeywords.any { normalized.contains(it) }

        return when {
            hasTransfer -> TransactionType.TRANSFER
            hasIncome && !hasExpense -> TransactionType.INCOME
            hasExpense || hasIncome -> TransactionType.EXPENSE
            else -> null
        }
    }

    private fun extractAmountAndCurrency(line: String, defaultCurrency: String): AmountParseResult {
        val normalized = line.lowercase()

        // Match patterns like "5000 so'm", "$25", "2 million", "500 ming", "25000 сум", "500 рублей"
        // Let's search for numbers and magnitude modifiers
        
        // Handle magnitudes like "2 million", "2 mln", "500 ming"
        var multiplier = 1L
        var rawNumberStr: String? = null
        var detectedCurrency = defaultCurrency

        // Regex for numbers with optional decimals
        val numberRegex = Regex("(\\d+[\\d\\s.,]*)")
        val matchResult = numberRegex.find(line)

        if (matchResult == null) {
            return AmountParseResult(null, false, "No numeric amount found")
        }

        val numStrMatch = matchResult.value.replace(" ", "").replace(",", ".")
        
        // Check magnitudes following the number
        val afterNumber = line.substring(matchResult.range.last + 1).trim().lowercase()
        when {
            afterNumber.startsWith("million") || afterNumber.startsWith("mln") -> multiplier = 1_000_000L
            afterNumber.startsWith("ming") || afterNumber.startsWith("тыс") -> multiplier = 1_000L
        }

        // Detect currency in string
        detectedCurrency = when {
            normalized.contains("so'm") || normalized.contains("som") || normalized.contains("сум") -> "UZS"
            normalized.contains("usd") || normalized.contains("$") -> "USD"
            normalized.contains("eur") || normalized.contains("€") -> "EUR"
            normalized.contains("rub") || normalized.contains("руб") -> "RUB"
            else -> defaultCurrency
        }

        // Parse base number using MoneyParser
        val baseParse = MoneyParser.parse(numStrMatch, detectedCurrency)
        if (baseParse.isFailure) {
            return AmountParseResult(null, false, baseParse.exceptionOrNull()?.message ?: "Invalid amount")
        }

        val baseMoney = baseParse.getOrThrow()
        val finalMinorUnits = try {
            Math.multiplyExact(baseMoney.amountInMinorUnits, multiplier)
        } catch (e: ArithmeticException) {
            return AmountParseResult(null, false, "Amount is too large")
        }

        return AmountParseResult(Money(finalMinorUnits, detectedCurrency), false, null)
    }

    private fun detectCategory(normalized: String, type: TransactionType): String? {
        if (type == TransactionType.TRANSFER) return null
        return when {
            normalized.contains("tuxum") || normalized.contains("sut") || normalized.contains("non") || normalized.contains("olma") || normalized.contains("продукты") || normalized.contains("groceries") -> "Groceries"
            normalized.contains("taksi") || normalized.contains("taxi") || normalized.contains("транспорт") -> "Transport"
            normalized.contains("tushlik") || normalized.contains("lunch") || normalized.contains("cafe") || normalized.contains("кафе") -> "Food & Dining"
            normalized.contains("oylik") || normalized.contains("maosh") || normalized.contains("salary") || normalized.contains("зарплата") -> "Salary"
            else -> null
        }
    }

    private fun detectAccounts(normalized: String): AccountDetectionResult {
        return when {
            normalized.contains("kartamdan") || normalized.contains("card") -> AccountDetectionResult(sourceAccount = "Card", destAccount = "Cash")
            else -> AccountDetectionResult()
        }
    }

    private fun cleanTitle(line: String): String {
        return line.replace(Regex("(?i)\\b(bugun|kecha|sarfladim|oldim|o'tkazdim|so'm|som|сум|usd|eur|rub|\\$|€)\\b"), "").trim().replace(Regex("\\s+"), " ")
    }
}
