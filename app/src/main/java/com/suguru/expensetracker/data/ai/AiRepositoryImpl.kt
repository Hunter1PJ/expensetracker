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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate

class AiRepositoryImpl(
    private val aiProvider: AiProvider,
    private val secureKeyStorage: SecureKeyStorage,
    private val dataStore: DataStore<Preferences>,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val offlineParser: TransactionInputParser = DeterministicTransactionParser(),
    private val isOnline: () -> Boolean = { true },
    private val currentUidProvider: () -> String? = { null },
    private val authenticatedUidFlow: Flow<String?> = flowOf(currentUidProvider())
) : AiRepository {

    private fun prefKey(uid: String, name: String): androidx.datastore.preferences.core.Preferences.Key<String> =
        stringPreferencesKey("ai_${uidHash(uid)}_$name")

    private fun enabledKey(uid: String): androidx.datastore.preferences.core.Preferences.Key<Boolean> =
        booleanPreferencesKey("ai_${uidHash(uid)}_enabled")

    private fun uidHash(uid: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private val activeUid: () -> String? = {
        currentUidProvider()?.takeIf { it.isNotBlank() && it == it.trim() }
            ?: AiRepository.GUEST_PROFILE_ID
    }
    private val activeProfileFlow: Flow<String?> = authenticatedUidFlow.map { uid ->
        uid?.takeIf { it.isNotBlank() && it == it.trim() } ?: AiRepository.GUEST_PROFILE_ID
    }
    private val legacyKeyAvailable = MutableStateFlow(secureKeyStorage.hasUnassignedLegacyKey())
    override val authenticatedUid: Flow<String?> = authenticatedUidFlow
    override val activeAiProfileId: Flow<String?> = activeProfileFlow
    override val isAiProfileAvailable: Flow<Boolean> = activeProfileFlow.map { !it.isNullOrBlank() }
    override val isAuthenticated: Flow<Boolean> =
        authenticatedUidFlow.map { !it.isNullOrBlank() }

    override val hasUnassignedLegacyKey: Flow<Boolean> =
        combine(authenticatedUidFlow, legacyKeyAvailable) { uid, hasLegacy ->
            !uid.isNullOrBlank() && hasLegacy
        }

    override val isAiConfigured: Flow<Boolean> = combine(activeProfileFlow, dataStore.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }) { uid, _ -> uid?.takeIf { it.isNotBlank() }?.let(secureKeyStorage::hasKey) ?: false }

    override val isKeyVerified: Flow<Boolean> = combine(activeProfileFlow, dataStore.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }) { uid, prefs ->
        if (uid.isNullOrBlank()) false else {
            val currentKey = secureKeyStorage.getKey(uid)
            val verifiedHash = prefs[prefKey(uid, "verified_key_hash")]
            val invalidHash = prefs[prefKey(uid, "invalid_key_hash")]
            currentKey != null && verifiedHash != null &&
                invalidHash != computeKeyFingerprint(currentKey) &&
                computeKeyFingerprint(currentKey) == verifiedHash
        }
    }

    override val isKeyInvalid: Flow<Boolean> = combine(activeProfileFlow, dataStore.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }) { uid, prefs ->
        if (uid.isNullOrBlank()) false else {
            val invalidHash = prefs[prefKey(uid, "invalid_key_hash")]
            val currentKey = secureKeyStorage.getKey(uid)
            invalidHash != null && currentKey != null &&
                computeKeyFingerprint(currentKey.trim()) == invalidHash
        }
    }

    override val isAiFeatureEnabled: Flow<Boolean> = combine(activeProfileFlow, dataStore.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }) { uid, prefs ->
        if (uid.isNullOrBlank()) false else prefs[enabledKey(uid)] ?: false
    }

    override suspend fun migrateLegacyKeyToCurrentUid(confirmed: Boolean): AiResult<Unit> {
        if (!confirmed) return AiResult.Failure(AiError.MissingApiKey)
        if (authenticatedUidFlow.first().isNullOrBlank()) return AiResult.Failure(AiError.MissingApiKey)
        val uid = activeUid() ?: return AiResult.Failure(AiError.MissingApiKey)
        if (uid == AiRepository.GUEST_PROFILE_ID) return AiResult.Failure(AiError.MissingApiKey)
        return try {
            if (!secureKeyStorage.hasUnassignedLegacyKey() ||
                !secureKeyStorage.migrateLegacyKeyToUid(uid)
            ) return AiResult.Failure(AiError.MissingApiKey)
            legacyKeyAvailable.value = secureKeyStorage.hasUnassignedLegacyKey()
            val migratedKey = secureKeyStorage.getKey(uid)
                ?: return AiResult.Failure(AiError.ExecutionError("Saved key migration could not be verified."))
            val fingerprint = computeKeyFingerprint(migratedKey)
            val legacyEnabledKey = booleanPreferencesKey("ai_feature_enabled")
            val legacyVerifiedKey = stringPreferencesKey("verified_key_hash")
            val legacyInvalidKey = stringPreferencesKey("invalid_key_hash")
            dataStore.edit { prefs ->
                prefs[enabledKey(uid)] = prefs[legacyEnabledKey] ?: false
                if (prefs[legacyVerifiedKey] == fingerprint) {
                    prefs[prefKey(uid, "verified_key_hash")] = fingerprint
                }
                if (prefs[legacyInvalidKey] == fingerprint) {
                    prefs[prefKey(uid, "invalid_key_hash")] = fingerprint
                    prefs.remove(prefKey(uid, "verified_key_hash"))
                }
                prefs.remove(legacyEnabledKey)
                prefs.remove(legacyVerifiedKey)
                prefs.remove(legacyInvalidKey)
            }
            AiResult.Success(Unit)
        } catch (_: Exception) {
            AiResult.Failure(AiError.ExecutionError("Saved key migration failed."))
        }
    }

    override suspend fun getMaskedApiKey(): String? {
        val uid = activeUid() ?: return null
        val key = secureKeyStorage.getKey(uid) ?: return null
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
        val uid = activeUid() ?: return AiResult.Failure(AiError.MissingApiKey)
        val currentKey = secureKeyStorage.getKey(uid)
        if (activeUid() != uid) return AiResult.Failure(AiError.MissingApiKey)
        try {
            secureKeyStorage.saveKey(uid, trimmed)
            if (currentKey != trimmed) {
                dataStore.edit { prefs ->
                    prefs.remove(prefKey(uid, "verified_key_hash"))
                    prefs.remove(prefKey(uid, "invalid_key_hash"))
                }
            }
        } catch (_: Exception) {
            return AiResult.Failure(AiError.ExecutionError("Secure key storage is unavailable."))
        }
        return AiResult.Success(Unit)
    }

    override suspend fun validateApiKey(apiKey: String): AiResult<Boolean> {
        val uid = activeUid() ?: return AiResult.Failure(AiError.MissingApiKey)
        val result = aiProvider.validateKey(apiKey)
        if (activeUid() != uid) return AiResult.Failure(AiError.MissingApiKey)
        if (result is AiResult.Success && result.data &&
            secureKeyStorage.getKey(uid)?.trim() == apiKey.trim()
        ) {
            val fingerprint = computeKeyFingerprint(apiKey.trim())
            dataStore.edit { prefs ->
                prefs[prefKey(uid, "verified_key_hash")] = fingerprint
                prefs.remove(prefKey(uid, "invalid_key_hash"))
            }
        }
        return result
    }

    override suspend fun validateConfiguredKey(): AiResult<Boolean> {
        val uid = activeUid() ?: return AiResult.Failure(AiError.MissingApiKey)
        if (dataStore.data.first()[enabledKey(uid)] != true) {
            return AiResult.Failure(AiError.MissingApiKey)
        }
        val key = secureKeyStorage.getKey(uid)
            ?: return AiResult.Failure(AiError.MissingApiKey)
        val result = kotlinx.coroutines.coroutineScope {
            val verificationRequest = async { aiProvider.validateKey(key) }
            val accountOrPreferenceChange = async {
                combine(activeProfileFlow, isAiFeatureEnabled) { observedUid, enabled ->
                    observedUid == uid && enabled
                }.first { stillEnabledForSameUid -> !stillEnabledForSameUid }
            }
            kotlinx.coroutines.selects.select<AiResult<Boolean>?> {
                verificationRequest.onAwait {
                    accountOrPreferenceChange.cancel()
                    it
                }
                accountOrPreferenceChange.onAwait {
                    verificationRequest.cancel()
                    null
                }
            }
        } ?: return AiResult.Failure(AiError.MissingApiKey)
        val fingerprint = computeKeyFingerprint(key.trim())
        if (activeUid() != uid || secureKeyStorage.getKey(uid)?.trim() != key.trim() ||
            dataStore.data.first()[enabledKey(uid)] != true
        ) {
            return AiResult.Failure(AiError.MissingApiKey)
        }
        var persisted = false
        if ((result is AiResult.Success && result.data) ||
            (result is AiResult.Failure && result.error is AiError.InvalidApiKey)
        ) {
            dataStore.edit { prefs ->
                if (activeUid() != uid || prefs[enabledKey(uid)] != true ||
                    secureKeyStorage.getKey(uid)?.trim() != key.trim()
                ) return@edit
                if (result is AiResult.Success) {
                    prefs[prefKey(uid, "verified_key_hash")] = fingerprint
                    prefs.remove(prefKey(uid, "invalid_key_hash"))
                } else {
                    prefs.remove(prefKey(uid, "verified_key_hash"))
                    prefs[prefKey(uid, "invalid_key_hash")] = fingerprint
                }
                persisted = true
            }
            if (!persisted) return AiResult.Failure(AiError.MissingApiKey)
        }
        return result
    }

    override suspend fun deleteApiKey() {
        val uid = activeUid() ?: return
        secureKeyStorage.deleteKey(uid)
        dataStore.edit { prefs ->
            prefs.remove(prefKey(uid, "verified_key_hash"))
            prefs.remove(prefKey(uid, "invalid_key_hash"))
            prefs[enabledKey(uid)] = false
        }
    }

    override suspend fun setAiFeatureEnabled(enabled: Boolean) {
        val uid = activeUid() ?: return
        dataStore.edit { prefs -> prefs[enabledKey(uid)] = enabled }
    }

    override suspend fun extractTransactions(
        input: String,
        defaultCurrency: String
    ): AiResult<List<TransactionCandidate>> {
        val trimmedInput = input.trim()
        if (trimmedInput.isBlank()) {
            return AiResult.Success(emptyList())
        }

        val uid = activeUid() ?: return parseOffline(trimmedInput, defaultCurrency)
        val aiEnabled = try {
            dataStore.data.first()[enabledKey(uid)] ?: false
        } catch (_: IOException) {
            false
        }

        if (!aiEnabled || !secureKeyStorage.hasKey(uid) ||
            secureKeyStorage.getKey(uid).isNullOrBlank() ||
            activeUid() != uid ||
            !runCatching(isOnline).getOrDefault(false)
        ) {
            return parseOffline(trimmedInput, defaultCurrency)
        }

        val savedKey = secureKeyStorage.getKey(uid)?.trim()
        if (savedKey.isNullOrBlank()) return parseOffline(trimmedInput, defaultCurrency)
        val invalidHash = dataStore.data.first()[prefKey(uid, "invalid_key_hash")]
        if (invalidHash == computeKeyFingerprint(savedKey)) return parseOffline(trimmedInput, defaultCurrency, ParseWarning.INVALID_API_KEY)

        val config = AiConfiguration(defaultCurrency = defaultCurrency)
        val providerResult = try {
            kotlinx.coroutines.coroutineScope {
                val request = async { aiProvider.extractFinancialTransactions(trimmedInput, config, savedKey) }
                val authChange = async {
                    activeProfileFlow.first { it != uid }
                }
                kotlinx.coroutines.selects.select<AiResult<com.suguru.expensetracker.domain.ai.ExtractedFinancialPayload>?> {
                    request.onAwait {
                        authChange.cancel()
                        it
                    }
                    authChange.onAwait {
                        request.cancel()
                        null
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SocketTimeoutException) {
            AiResult.Failure(AiError.RequestTimeout)
        } catch (_: IOException) {
            AiResult.Failure(AiError.NetworkError("Network request failed."))
        } catch (_: Exception) {
            AiResult.Failure(AiError.ExecutionError("Transaction extraction failed."))
        }

        if (providerResult == null || activeUid() != uid ||
            secureKeyStorage.getKey(uid)?.trim() != savedKey
        ) {
            return parseOffline(trimmedInput, defaultCurrency)
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

        val hasDescription = !item.description.isNullOrBlank()
        val title = item.description?.takeIf { it.isNotBlank() } ?: "Transaction"

        return TransactionCandidate(
            rawText = listOfNotNull(item.description, item.rawAmount, item.currency).joinToString(" "),
            suggestedTitle = title,
            suggestedTitleIsFallback = !hasDescription,
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
