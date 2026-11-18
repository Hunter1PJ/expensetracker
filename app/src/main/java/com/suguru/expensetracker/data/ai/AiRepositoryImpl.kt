package com.suguru.expensetracker.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import com.suguru.expensetracker.domain.parser.DeterministicTransactionParser
import com.suguru.expensetracker.domain.parser.TransactionInputParser
import com.suguru.expensetracker.domain.parser.TransactionCandidate
import com.suguru.expensetracker.domain.util.MoneyParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate

class AiRepositoryImpl(
    private val aiProvider: AiProvider,
    private val secureKeyStorage: SecureKeyStorage,
    private val dataStore: DataStore<Preferences>,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val offlineParser: TransactionInputParser = DeterministicTransactionParser(),
    private val isOnline: () -> Boolean = { true }
) : AiRepository {

    private object PreferencesKeys {
        val AI_FEATURE_ENABLED = booleanPreferencesKey("ai_feature_enabled")
        val VERIFIED_KEY_HASH = stringPreferencesKey("verified_key_hash")
        val INVALID_KEY_HASH = stringPreferencesKey("invalid_key_hash")
    }

    private val _isConfiguredFlow = MutableStateFlow(secureKeyStorage.hasKey())
    override val isAiConfigured: Flow<Boolean> = _isConfiguredFlow.asStateFlow()

    override val isKeyVerified: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val verifiedHash = prefs[PreferencesKeys.VERIFIED_KEY_HASH]
            val invalidHash = prefs[PreferencesKeys.INVALID_KEY_HASH]
            val currentKey = secureKeyStorage.getKey()
            if (verifiedHash != null && currentKey != null && invalidHash != computeKeyFingerprint(currentKey)) {
                computeKeyFingerprint(currentKey) == verifiedHash
            } else {
                false
            }
        }

    override val isKeyInvalid: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            val invalidHash = prefs[PreferencesKeys.INVALID_KEY_HASH]
            val currentKey = secureKeyStorage.getKey()
            invalidHash != null && currentKey != null &&
                computeKeyFingerprint(currentKey.trim()) == invalidHash
        }

    override val isAiFeatureEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            // Default to false for genuinely new user/account
            prefs[PreferencesKeys.AI_FEATURE_ENABLED] ?: false
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
        val currentKey = secureKeyStorage.getKey()
        try {
            secureKeyStorage.saveKey(trimmed)
            // If the key changed, invalidate the previous verification
            if (currentKey != trimmed) {
                dataStore.edit { prefs ->
                    prefs.remove(PreferencesKeys.VERIFIED_KEY_HASH)
                    prefs.remove(PreferencesKeys.INVALID_KEY_HASH)
                }
            }
        } catch (_: Exception) {
            return AiResult.Failure(AiError.ExecutionError("Secure key storage is unavailable."))
        }
        _isConfiguredFlow.value = secureKeyStorage.hasKey()
        return AiResult.Success(Unit)
    }

    override suspend fun validateApiKey(apiKey: String): AiResult<Boolean> {
        val result = aiProvider.validateKey(apiKey)
        if (result is AiResult.Success && result.data) {
            val fingerprint = computeKeyFingerprint(apiKey.trim())
            if (secureKeyStorage.getKey()?.trim() == apiKey.trim()) {
                dataStore.edit { prefs ->
                    prefs[PreferencesKeys.VERIFIED_KEY_HASH] = fingerprint
                    prefs.remove(PreferencesKeys.INVALID_KEY_HASH)
                }
            }
        }
        return result
    }

    override suspend fun validateConfiguredKey(): AiResult<Boolean> {
        val key = secureKeyStorage.getKey()
        if (key.isNullOrBlank()) {
            return AiResult.Failure(AiError.MissingApiKey)
        }
        val result = aiProvider.validateKey(key)
        val fingerprint = computeKeyFingerprint(key.trim())
        if (secureKeyStorage.getKey()?.trim() != key.trim()) {
            return AiResult.Failure(AiError.ExecutionError("The saved API key changed during verification."))
        }
        if (result is AiResult.Success && result.data) {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.VERIFIED_KEY_HASH] = fingerprint
                prefs.remove(PreferencesKeys.INVALID_KEY_HASH)
            }
        } else if (result is AiResult.Failure && result.error is AiError.InvalidApiKey) {
            dataStore.edit { prefs ->
                prefs.remove(PreferencesKeys.VERIFIED_KEY_HASH)
                prefs[PreferencesKeys.INVALID_KEY_HASH] = fingerprint
            }
        }
        return result
    }

    override suspend fun deleteApiKey() {
        secureKeyStorage.deleteKey()
        dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.VERIFIED_KEY_HASH)
            prefs.remove(PreferencesKeys.INVALID_KEY_HASH)
        }
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

        val aiEnabled = try {
            dataStore.data.first()[PreferencesKeys.AI_FEATURE_ENABLED] ?: false
        } catch (_: IOException) {
            false
        }

        if (!aiEnabled || !secureKeyStorage.hasKey() || secureKeyStorage.getKey().isNullOrBlank() || !runCatching(isOnline).getOrDefault(false)) {
            return parseOffline(trimmedInput, defaultCurrency)
        }

        val savedKey = secureKeyStorage.getKey()?.trim()
        if (savedKey.isNullOrBlank()) return parseOffline(trimmedInput, defaultCurrency)
        val invalidHash = dataStore.data.first()[PreferencesKeys.INVALID_KEY_HASH]
        if (invalidHash == computeKeyFingerprint(savedKey)) return parseOffline(trimmedInput, defaultCurrency, ParseWarning.INVALID_API_KEY)

        val config = AiConfiguration(defaultCurrency = defaultCurrency)
        val providerResult = try {
            aiProvider.extractFinancialTransactions(trimmedInput, config)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SocketTimeoutException) {
            AiResult.Failure(AiError.RequestTimeout)
        } catch (_: IOException) {
            AiResult.Failure(AiError.NetworkError("Network request failed."))
        } catch (_: Exception) {
            AiResult.Failure(AiError.ExecutionError("Transaction extraction failed."))
        }

        return when (providerResult) {
            is AiResult.Success -> {
                if (providerResult.data.items.isEmpty()) {
                    parseOffline(trimmedInput, defaultCurrency, ParseWarning.AI_FALLBACK)
                } else {
                    val candidates = providerResult.data.items.map { item ->
                        mapToTransactionCandidate(item, defaultCurrency)
                    }
                    AiResult.Success(candidates)
                }
            }
            is AiResult.Failure -> {
                val warning = if (providerResult.error is AiError.InvalidApiKey) {
                    ParseWarning.INVALID_API_KEY
                } else {
                    ParseWarning.AI_FALLBACK
                }
                when (val offlineResult = parseOffline(trimmedInput, defaultCurrency, warning)) {
                    is AiResult.Success -> offlineResult
                    is AiResult.Failure -> if (providerResult.error is AiError.InvalidApiKey) {
                        providerResult
                    } else {
                        offlineResult
                    }
                }
            }
        }
    }

    private suspend fun parseOffline(
        input: String,
        defaultCurrency: String,
        fallbackWarning: ParseWarning? = null
    ): AiResult<List<TransactionCandidate>> {
        val parsed = try {
            offlineParser.parse(input, defaultCurrency)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return AiResult.Failure(AiError.NoTransactionsFound)
        }
        val meaningfulCandidates = parsed.filter { candidate ->
            candidate.amount != null ||
                candidate.suggestedCategoryName != null ||
                candidate.suggestedAccountName != null ||
                !candidate.warnings.contains(ParseWarning.AMBIGUOUS_TYPE)
        }
        if (meaningfulCandidates.isEmpty()) {
            return AiResult.Failure(AiError.NoTransactionsFound)
        }
        val candidates = if (fallbackWarning == null) {
            meaningfulCandidates
        } else {
            meaningfulCandidates.map { candidate ->
                candidate.copy(warnings = (candidate.warnings + fallbackWarning).distinct())
            }
        }
        return AiResult.Success(candidates)
    }

    /**
     * Maps extracted raw AI items strictly following financial safety rules:
     * - Amounts must be parsed safely using MoneyParser, never Float/Double
     * - Missing amounts remain null
     * - Ambiguous types marked appropriately
     * - Currency ambiguities flagged
     * - Transfers checked for source and destination accounts
     */
    private suspend fun mapToTransactionCandidate(
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
            val parsedResult = parseAiAmount(item.rawAmount, currencyToUse)
            if (parsedResult.isSuccess && parsedResult.getOrThrow().amountInMinorUnits > 0L) {
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
            parseError = null,
            source = com.suguru.expensetracker.domain.parser.CandidateSource.GEMINI_AI
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

    private suspend fun parseAiAmount(rawAmount: String, currencyCode: String): Result<Money> {
        val trimmed = rawAmount.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Empty amount"))

        val cleanAmountStr = trimmed.replace(" ", "").replace("\u00A0", "")
        val directResult = MoneyParser.parse(cleanAmountStr, currencyCode)
        if (directResult.isSuccess) {
            return directResult
        }

        return try {
            val parseResult = offlineParser.parse(trimmed, currencyCode)
            val candidate = parseResult.firstOrNull()
            if (candidate?.amount != null) {
                Result.success(candidate.amount)
            } else {
                Result.failure(IllegalArgumentException("Invalid amount format: $rawAmount"))
            }
        } catch (_: Exception) {
            Result.failure(IllegalArgumentException("Invalid amount format: $rawAmount"))
        }
    }

    private fun computeKeyFingerprint(key: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(key.trim().toByteArray(Charsets.UTF_8))
        val hexChars = CharArray(digest.size * 2)
        val digits = "0123456789abcdef"
        for (index in digest.indices) {
            val value = digest[index].toInt() and 0xFF
            hexChars[index * 2] = digits[value ushr 4]
            hexChars[index * 2 + 1] = digits[value and 0x0F]
        }
        return String(hexChars)
    }
}
