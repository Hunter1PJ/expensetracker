package com.suguru.expensetracker.domain.parser

import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.TransactionType
import java.time.LocalDate

/**
 * Represents confidence or parsing certainty level for a parsed transaction candidate.
 */
enum class ParseConfidence {
    HIGH,
    MEDIUM,
    LOW,
    AMBIGUOUS
}

/**
 * Reasons why a candidate might be marked ambiguous or incomplete.
 */
enum class ParseWarning {
    MISSING_AMOUNT,
    AMBIGUOUS_TYPE,
    AMBIGUOUS_AMOUNT,
    UNIT_PRICE_AMBIGUITY,
    UNSUPPORTED_CURRENCY,
    INVALID_AMOUNT,
    MULTIPLE_AMOUNTS_DETECTED,
    UNPARSED_LINE
}

/**
 * Structured model representing a parsed transaction candidate from natural language text.
 * Completely independent of Room, database IDs, and Android UI.
 */
data class TransactionCandidate(
    val rawText: String,
    val suggestedTitle: String,
    val transactionType: TransactionType,
    val amount: Money?,
    val date: LocalDate,
    val suggestedCategoryName: String? = null,
    val suggestedAccountName: String? = null,
    val transferSourceAccountName: String? = null,
    val transferDestinationAccountName: String? = null,
    val confidence: ParseConfidence,
    val warnings: List<ParseWarning> = emptyList(),
    val isUnitPriceInterpretation: Boolean = false,
    val parseError: String? = null
)

/**
 * Stable abstraction interface for natural-language transaction parsing engines.
 * Designed to be implemented by deterministic rule-based parsers as well as future on-device AI parsers.
 */
interface TransactionInputParser {
    /**
     * Parses raw natural language text (which may contain single or multiple lines)
     * into a list of structured transaction candidates.
     */
    suspend fun parse(input: String, defaultCurrency: String = "UZS"): List<TransactionCandidate>
}
