package com.suguru.expensetracker.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.suguru.expensetracker.data.security.SecureKeyStorage
import com.suguru.expensetracker.domain.ai.AiConfiguration
import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiProvider
import com.suguru.expensetracker.domain.ai.AiRepository
import com.suguru.expensetracker.domain.ai.AiResult
import com.suguru.expensetracker.domain.ai.ExtractedFinancialItem
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.parser.ParseConfidence
import com.suguru.expensetracker.domain.parser.ParseWarning
import com.suguru.expensetracker.domain.parser.TransactionCandidate
import com.suguru.expensetracker.domain.util.MoneyParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

class AiRepositoryImpl(
    private val aiProvider: AiProvider,
    private val secureKeyStorage: SecureKeyStorage,
    private val dataStore: DataStore<Preferences>,
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) : AiRepository {

    private object PreferencesKeys {
        val AI_FEATURE_ENABLED = booleanPreferencesKey("ai_feature_enabled")
    }

    private val _isConfiguredFlow = MutableStateFlow(secureKeyStorage.hasKey())
    override val isAiConfigured: Flow<Boolean> = _isConfiguredFlow.asStateFlow()

    override val isAiFeatureEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            // Default to true so user sees AI entry point when desired, but disabled when no key is entered
            prefs[PreferencesKeys.AI_FEATURE_ENABLED] ?: true
        }

    override suspend fun getMaskedApiKey(): String? {
        val key = secureKeyStorage.getKey() ?: return null
        val trimmed = key.trim()
        if (trimmed.length <= 8) {
            return "••••••••"
        }
        val last4 = trimmed.takeLast(4)
        return "••••••••$last4"
    }

    override suspend fun saveApiKey(apiKey: String): AiResult<Unit> {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            return AiResult.Failure(AiError.MissingApiKey)
        }
        secureKeyStorage.saveKey(trimmed)
        _isConfiguredFlow.value = secureKeyStorage.hasKey()
        return AiResult.Success(Unit)
    }

    override suspend fun validateApiKey(apiKey: String): AiResult<Boolean> {
        return aiProvider.validateKey(apiKey)
    }

    override suspend fun validateConfiguredKey(): AiResult<Boolean> {
        val key = secureKeyStorage.getKey()
        if (key.isNullOrBlank()) {
            return AiResult.Failure(AiError.MissingApiKey)
        }
        return aiProvider.validateKey(key)
    }

    override suspend fun deleteApiKey() {
        secureKeyStorage.deleteKey()
        _isConfiguredFlow.value = false
    }

    override suspend fun setAiFeatureEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.AI_FEATURE_ENABLED] = enabled
        }
    }

    override suspend fun extractTransactions(
        input: String,
        defaultCurrency: String
    ): AiResult<List<TransactionCandidate>> {
        val trimmedInput = input.trim()
        if (trimmedInput.isBlank()) {
            return AiResult.Success(emptyList())
        }

        if (!secureKeyStorage.hasKey() || secureKeyStorage.getKey().isNullOrBlank()) {
            return AiResult.Failure(AiError.MissingApiKey)
        }

        val config = AiConfiguration(defaultCurrency = defaultCurrency)
        val providerResult = aiProvider.extractFinancialTransactions(trimmedInput, config)

        return when (providerResult) {
            is AiResult.Success -> {
                val candidates = providerResult.data.items.map { item ->
                    mapToTransactionCandidate(item, defaultCurrency)
                }
                AiResult.Success(candidates)
            }
            is AiResult.Failure -> providerResult
        }
    }

    /**
     * Maps extracted raw AI items strictly following financial safety rules:
     * - Amounts must be parsed safely using MoneyParser, never Float/Double
     * - Missing amounts remain null
     * - Ambiguous types marked appropriately
     * - Currency ambiguities flagged
     * - Transfers checked for source and destination accounts
     */
    private fun mapToTransactionCandidate(
        item: ExtractedFinancialItem,
        defaultCurrency: String
    ): TransactionCandidate {
        val warnings = mutableListOf<ParseWarning>()
        var confidence = ParseConfidence.HIGH

        val finalType = when (item.transactionType.uppercase()) {
            "EXPENSE" -> TransactionType.EXPENSE
            "INCOME" -> TransactionType.INCOME
            "TRANSFER" -> TransactionType.TRANSFER
            "AMBIGUOUS" -> {
                warnings.add(ParseWarning.AMBIGUOUS_TYPE)
                confidence = ParseConfidence.AMBIGUOUS
                TransactionType.EXPENSE // Safe fallback type
            }
            else -> {
                warnings.add(ParseWarning.AMBIGUOUS_TYPE)
                confidence = ParseConfidence.LOW
                TransactionType.EXPENSE
            }
        }

        val currencyToUse = item.currency?.takeIf { it.isNotBlank() } ?: defaultCurrency
        if (item.currencyAmbiguous) {
            warnings.add(ParseWarning.UNSUPPORTED_CURRENCY)
            confidence = ParseConfidence.AMBIGUOUS
        }

        var money: Money? = null
        if (item.isAmountMissing || item.rawAmount.isNullOrBlank()) {
            warnings.add(ParseWarning.MISSING_AMOUNT)
            if (confidence == ParseConfidence.HIGH || confidence == ParseConfidence.MEDIUM) {
                confidence = ParseConfidence.LOW
            }
        } else {
            // Strict deterministic money parsing: never Float/Double
            val cleanAmountStr = item.rawAmount.replace(" ", "").replace(",", ".")
            val parsedResult = MoneyParser.parse(cleanAmountStr, currencyToUse)
            if (parsedResult.isSuccess) {
                money = parsedResult.getOrThrow()
            } else {
                warnings.add(ParseWarning.INVALID_AMOUNT)
                confidence = ParseConfidence.AMBIGUOUS
            }
        }

        if (item.isAmbiguous) {
            confidence = ParseConfidence.AMBIGUOUS
        }

        // Transfer safety validation: transfers require both source and destination accounts
        if (finalType == TransactionType.TRANSFER) {
            if (item.sourceAccountSuggestion.isNullOrBlank() || item.destinationAccountSuggestion.isNullOrBlank()) {
                warnings.add(ParseWarning.AMBIGUOUS_TYPE)
                confidence = ParseConfidence.AMBIGUOUS
            }
        }

        val isUnitPrice = item.unitPrice != null || item.quantity != null
        if (isUnitPrice) {
            warnings.add(ParseWarning.UNIT_PRICE_AMBIGUITY)
        }

        // Date resolution from dateExpression
        val resolvedDate = resolveDate(item.dateExpression, todayProvider())

        val title = item.description?.takeIf { it.isNotBlank() } ?: "Transaction"

        return TransactionCandidate(
            rawText = listOfNotNull(item.description, item.rawAmount, item.currency).joinToString(" "),
            suggestedTitle = title,
            transactionType = finalType,
            amount = money,
            date = resolvedDate,
            suggestedCategoryName = item.categorySuggestion,
            suggestedAccountName = item.sourceAccountSuggestion,
            transferSourceAccountName = item.sourceAccountSuggestion,
            transferDestinationAccountName = item.destinationAccountSuggestion,
            confidence = confidence,
            warnings = warnings,
            isUnitPriceInterpretation = isUnitPrice,
            parseError = null
        )
    }

    private fun resolveDate(expression: String?, today: LocalDate): LocalDate {
        if (expression.isNullOrBlank()) return today
        val norm = expression.lowercase().trim()
        return when {
            norm.contains("kecha") || norm.contains("вчера") || norm.contains("yesterday") -> today.minusDays(1)
            norm.contains("oldingi kun") || norm.contains("позавчера") || norm.contains("before yesterday") -> today.minusDays(2)
            else -> today
        }
    }
}
