package com.suguru.expensetracker.domain.parser

import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.util.MoneyParser
import java.time.LocalDate

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
            if (amountResult.error == "Amount is too large" || amountResult.isInvalid) {
                warnings.add(ParseWarning.INVALID_AMOUNT)
            } else {
                warnings.add(ParseWarning.MISSING_AMOUNT)
            }
            confidence = ParseConfidence.LOW
        } else if (amountResult.isMultipleAmounts) {
            warnings.add(ParseWarning.MULTIPLE_AMOUNTS_DETECTED)
            confidence = ParseConfidence.AMBIGUOUS
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
        val (title, titleIsFallback) = cleanTitle(line, amountResult.rawAmountToken)

        // Extract category/account suggestions based on keywords
        val category = detectCategory(normalized, finalType)
        val accounts = detectAccounts(normalized)

        return TransactionCandidate(
            rawText = line,
            suggestedTitle = title,
            suggestedTitleIsFallback = titleIsFallback,
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
            parseError = amountResult.error
        )
    }

    private data class AmountParseResult(
        val amount: Money?,
        val rawAmountToken: String?,
        val isAmbiguous: Boolean,
        val isMultipleAmounts: Boolean = false,
        val isInvalid: Boolean = false,
        val error: String? = null
    )

    private data class AccountDetectionResult(
        val accountName: String? = null,
        val sourceAccount: String? = null,
        val destAccount: String? = null
    )

    private fun detectDate(normalized: String, today: LocalDate): LocalDate {
        return when {
            normalized.contains("kecha") || normalized.contains("вчера") || normalized.contains("yesterday") -> today.minusDays(1)
            normalized.contains("oldingi kun") || normalized.contains("позавчера") || normalized.contains("before yesterday") -> today.minusDays(2)
            else -> today
        }
    }

    private fun detectType(normalized: String): TransactionType? {
        val expenseKeywords = listOf(
            "sarfladim", "sarf", "xarajat", "to'ladim", "toladim", "toledim", "berdim", "ishlatdim", "xarid", "harid",
            "spent", "paid", "bought", "purchase", "expense",
            "потратил", "потратила", "оплатил", "оплатила", "купил", "купила", "заплатил", "заплатила", "расход", "покупка"
        )
        val incomeKeywords = listOf(
            "oldim", "tushdi", "maosh", "oylik", "daromad", "kirdi", "ish haq",
            "received", "got", "salary", "income", "earned", "deposit",
            "получил", "получила", "пришло", "заработал", "заработала", "зарплата", "доход", "прибыль"
        )
        val transferKeywords = listOf(
            "o'tkazdim", "otkazdim", "o'tkazma", "otkazma", "otkazish",
            "transfer", "transferred",
            "перевод", "перевел", "перевела", "перевести", "kartamdan"
        )

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
        val detectedCurrency = detectCurrency(normalized, defaultCurrency)

        val candidates = findNumberCandidates(line)
        if (candidates.isEmpty()) {
            return AmountParseResult(null, null, isAmbiguous = false, error = "No numeric amount found")
        }

        val amountCandidates = candidates.filter { !isDateToken(it) && !isPhysicalUnitCandidate(it) }
        if (amountCandidates.isEmpty()) {
            return AmountParseResult(null, null, isAmbiguous = false, error = "No numeric amount found")
        }

        val isMultiple = amountCandidates.size > 1
        val primary = amountCandidates.first()

        val parsedMoneyResult = parseMoneyFromCandidate(primary, detectedCurrency)
        if (parsedMoneyResult.isFailure) {
            val errorMsg = parsedMoneyResult.exceptionOrNull()?.message ?: "Invalid amount"
            return AmountParseResult(null, primary.rawMatch, isAmbiguous = true, isInvalid = true, error = errorMsg)
        }

        val money = parsedMoneyResult.getOrThrow()
        if (money.amountInMinorUnits <= 0L) {
            return AmountParseResult(money, primary.rawMatch, isAmbiguous = true, isInvalid = true, error = "Amount must be greater than zero")
        }

        return AmountParseResult(
            amount = money,
            rawAmountToken = primary.rawMatch,
            isAmbiguous = isMultiple,
            isMultipleAmounts = isMultiple,
            error = null
        )
    }

    private data class NumberCandidate(
        val rawMatch: String,
        val numberString: String,
        val suffixString: String?
    )

    private fun findNumberCandidates(line: String): List<NumberCandidate> {
        val list = mutableListOf<NumberCandidate>()
        val regex = Regex("(?:[$€£¥₽]\\s*)?(\\d+(?:[\\s.,]\\d+)*)\\s*([a-zA-Zа-яА-Я'`’.]+)?")
        val matches = regex.findAll(line)

        for (match in matches) {
            val fullMatch = match.value.trim()
            val numGroup = match.groupValues[1].trim()
            val suffixGroup = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.trim()

            list.add(NumberCandidate(fullMatch, numGroup, suffixGroup))
        }
        return list
    }

    private fun isDateToken(candidate: NumberCandidate): Boolean {
        val num = candidate.numberString.toLongOrNull() ?: return false
        val suffix = candidate.suffixString?.lowercase() ?: return false

        val monthKeywords = setOf(
            "январь", "января", "февраль", "февраля", "март", "марта", "апрель", "апреля", "май", "мая", "июнь", "июня",
            "июль", "июля", "август", "августа", "сентябрь", "сентября", "октябрь", "октября", "ноябрь", "ноября", "декабрь", "декабря",
            "january", "february", "march", "april", "june", "july", "august", "september", "october", "november", "december",
            "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "oct", "nov", "dec",
            "yanvar", "fevral", "mart", "aprel", "iyun", "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr",
            "yil", "год", "года"
        )
        return (num in 1..31 || num in 1900..2100) && monthKeywords.contains(suffix)
    }

    private fun detectCurrency(normalized: String, defaultCurrency: String): String {
        return when {
            normalized.contains("so'm") || normalized.contains("som") || normalized.contains("сум") || normalized.contains("сўм") || normalized.contains("uzs") -> "UZS"
            normalized.contains("usd") || normalized.contains("$") || normalized.contains("доллар") -> "USD"
            normalized.contains("eur") || normalized.contains("€") || normalized.contains("евро") -> "EUR"
            normalized.contains("rub") || normalized.contains("руб") || normalized.contains("рублей") || normalized.contains("рубль") || normalized.contains("₽") -> "RUB"
            else -> defaultCurrency
        }
    }

    private fun isPhysicalUnitCandidate(candidate: NumberCandidate): Boolean {
        val suffix = candidate.suffixString?.lowercase()?.trimEnd('.', ',', ';') ?: return false
        val hasExplicitCurrencySymbol = candidate.rawMatch.any { it in "\$€£¥₽" }
        return isPhysicalUnit(suffix) && !hasExplicitCurrencySymbol
    }

    private fun parseMoneyFromCandidate(candidate: NumberCandidate, currencyCode: String): Result<Money> {
        val multiplier = resolveMultiplier(candidate.suffixString)
        val cleanNumberStr = candidate.numberString.replace(" ", "").replace("\u00A0", "")

        if (multiplier == 1L) {
            val sanitized = sanitizeNumericString(cleanNumberStr, currencyCode)
            return MoneyParser.parse(sanitized, currencyCode)
        }

        // Multiplier > 1 (e.g. 1,000 or 1,000,000)
        val normalizedDecimalStr = cleanNumberStr.replace(',', '.')
        val parts = normalizedDecimalStr.split('.')
        if (parts.size > 2) {
            return Result.failure(IllegalArgumentException("Invalid number format: multiple decimal points"))
        }

        val integerPartStr = parts[0]
        val fractionPartStr = if (parts.size == 2) parts[1] else ""

        if (integerPartStr.isNotEmpty() && !integerPartStr.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Invalid characters in number"))
        }
        if (fractionPartStr.isNotEmpty() && !fractionPartStr.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Invalid characters in decimal fraction"))
        }

        val integerPart = if (integerPartStr.isEmpty()) 0L else {
            try {
                integerPartStr.toLong()
            } catch (e: NumberFormatException) {
                return Result.failure(IllegalArgumentException("Amount is too large"))
            }
        }

        val wholeFromInteger = try {
            Math.multiplyExact(integerPart, multiplier)
        } catch (e: ArithmeticException) {
            return Result.failure(IllegalArgumentException("Amount is too large"))
        }

        val wholeFromFraction = if (fractionPartStr.isEmpty()) {
            0L
        } else {
            val fractionLen = fractionPartStr.length
            var scale = 1L
            repeat(fractionLen) {
                scale *= 10L
            }
            if (multiplier % scale != 0L) {
                val rawFrac = fractionPartStr.toLong()
                (rawFrac * multiplier) / scale
            } else {
                val factor = multiplier / scale
                val rawFrac = fractionPartStr.toLong()
                try {
                    Math.multiplyExact(rawFrac, factor)
                } catch (e: ArithmeticException) {
                    return Result.failure(IllegalArgumentException("Amount is too large"))
                }
            }
        }

        val totalBaseUnits = try {
            Math.addExact(wholeFromInteger, wholeFromFraction)
        } catch (e: ArithmeticException) {
            return Result.failure(IllegalArgumentException("Amount is too large"))
        }

        val decimals = MoneyParser.getDecimalPlaces(currencyCode)
        var currencyMultiplier = 1L
        repeat(decimals) {
            currencyMultiplier *= 10L
        }

        val totalMinorUnits = try {
            Math.multiplyExact(totalBaseUnits, currencyMultiplier)
        } catch (e: ArithmeticException) {
            return Result.failure(IllegalArgumentException("Amount is too large"))
        }

        return Result.success(Money(totalMinorUnits, currencyCode))
    }

    private fun resolveMultiplier(suffix: String?): Long {
        if (suffix.isNullOrBlank()) return 1L
        val norm = suffix.lowercase().trimEnd('.', ',', ';')

        if (isPhysicalUnit(norm)) return 1L

        return when {
            // Thousands (1,000)
            norm == "k" || norm == "к" -> 1_000L
            norm.startsWith("ming") || norm.startsWith("тыс") || norm.startsWith("тыщ") || norm.startsWith("thousand") -> 1_000L

            // Millions (1,000,000)
            norm == "m" || norm == "м" -> 1_000_000L
            norm.startsWith("mln") || norm.startsWith("млн") || norm.startsWith("million") || norm.startsWith("миллион") -> 1_000_000L

            // Billions (1,000,000,000)
            norm == "b" || norm == "б" || norm.startsWith("bln") || norm.startsWith("млрд") || norm.startsWith("billion") || norm.startsWith("миллиард") -> 1_000_000_000L

            else -> 1L
        }
    }

    private fun isPhysicalUnit(norm: String): Boolean {
        val physicalUnits = setOf(
            "kg", "кг", "g", "гр", "gram", "грамм",
            "km", "км", "m2", "м2", "m3", "м3", "cm", "см", "mm", "мм",
            "kw", "kwt", "квт", "kv",
            "kb", "mb", "gb", "tb",
            "pcs", "шт", "ta", "dona", "dosa",
            "l", "litr", "литр", "min", "мин", "hour", "chas", "час"
        )
        return physicalUnits.contains(norm)
    }

    private fun sanitizeNumericString(str: String, currencyCode: String): String {
        val s = str.replace(" ", "").replace("\u00A0", "")
        val decimals = MoneyParser.getDecimalPlaces(currencyCode)

        if (decimals == 0) {
            return s.replace(",", "").replace(".", "")
        }

        if (s.contains(',') && s.contains('.')) {
            return s.replace(",", "")
        }

        if (s.contains(',') && !s.contains('.')) {
            val parts = s.split(',')
            if (parts.size == 2 && parts[1].length == 3 && parts[0].length <= 3) {
                return s.replace(",", "")
            }
            return s.replace(',', '.')
        }

        if (s.contains('.') && !s.contains(',')) {
            val parts = s.split('.')
            if (parts.size == 2 && parts[1].length == 3 && parts[0].length <= 3) {
                return s.replace(".", "")
            }
            if (parts.size > 2) {
                return s.replace(".", "")
            }
        }

        return s
    }

    private fun detectCategory(normalized: String, type: TransactionType): String? {
        if (type == TransactionType.TRANSFER) return null
        return when {
            normalized.contains("oziq-ovqat") || normalized.contains("tuxum") || normalized.contains("sut") || normalized.contains("non") || normalized.contains("olma") || normalized.contains("продукты") || normalized.contains("groceries") || normalized.contains("супермаркет") || normalized.contains("bozor") -> "Groceries"
            normalized.contains("taksi") || normalized.contains("taxi") || normalized.contains("транспорт") || normalized.contains("автобус") || normalized.contains("metro") || normalized.contains("метро") || normalized.contains("benzin") -> "Transport"
            normalized.contains("ovqatlanish") || normalized.contains("ovqat") || normalized.contains("tushlik") || normalized.contains("lunch") || normalized.contains("cafe") || normalized.contains("кафе") || normalized.contains("ресторан") || normalized.contains("обед") || normalized.contains("ужин") -> "Food & Dining"
            normalized.contains("oylik") || normalized.contains("maosh") || normalized.contains("salary") || normalized.contains("зарплата") || normalized.contains("доход") -> "Salary"
            else -> null
        }
    }

    private fun detectAccounts(normalized: String): AccountDetectionResult {
        return when {
            normalized.contains("kartamdan") || normalized.contains("card") -> AccountDetectionResult(sourceAccount = "Card", destAccount = "Cash")
            else -> AccountDetectionResult()
        }
    }

    private fun cleanTitle(line: String, amountRawToken: String?): Pair<String, Boolean> {
        var result = line
        if (!amountRawToken.isNullOrBlank()) {
            result = result.replace(amountRawToken, "", ignoreCase = true)
        }
        val cleanRegex = Regex(
            "(?i)\\b(bugun|kecha|oldingi kun|вчера|позавчера|yesterday|today|sarfladim|sarf|to'ladim|toladim|toledim|oldim|o'tkazdim|otkazdim|купил|купила|потратил|потратила|оплатил|оплатила|получил|получила|перевел|перевела|spent|paid|bought|received|so'm|som|сум|сўм|usd|eur|rub|руб|рублей|\\$|€|\\bна\\b|\\bза\\b|\\bfor\\b|\\bon\\b)\\b"
        )
        result = result.replace(cleanRegex, "").trim().replace(Regex("\\s+"), " ")
        return if (result.isBlank()) "Transaction" to true else result to false
    }
}
