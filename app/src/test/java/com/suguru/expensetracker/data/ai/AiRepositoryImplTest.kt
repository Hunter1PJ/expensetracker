package com.suguru.expensetracker.data.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.suguru.expensetracker.data.security.SecureKeyStorage
import com.suguru.expensetracker.domain.ai.AiConfiguration
import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiProvider
import com.suguru.expensetracker.domain.ai.AiRepository
import com.suguru.expensetracker.domain.ai.AiResult
import com.suguru.expensetracker.domain.ai.ExtractedFinancialItem
import com.suguru.expensetracker.domain.ai.ExtractedFinancialPayload
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.parser.CandidateSource
import com.suguru.expensetracker.domain.parser.ParseConfidence
import com.suguru.expensetracker.domain.parser.ParseWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.io.IOException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class AiRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var testDataStore: DataStore<Preferences>
    private val authUid = MutableStateFlow<String?>("test_uid")

    private lateinit var fakeStorage: FakeSecureKeyStorage
    private lateinit var fakeProvider: FakeAiProvider
    private lateinit var repository: AiRepositoryImpl
    private var isOnline = true

    private class FakeSecureKeyStorage : SecureKeyStorage {
        private val uidKeys = mutableMapOf<String, String>()
        var legacyKey: String? = null
        override fun saveKey(key: String) { saveKey("test_uid", key) }
        override fun getKey(): String? = getKey("test_uid")
        override fun deleteKey() { deleteKey("test_uid") }
        override fun hasKey(): Boolean = hasKey("test_uid")
        override fun saveKey(uid: String, key: String) { uidKeys[uid] = key.trim() }
        override fun getKey(uid: String): String? = uidKeys[uid]
        override fun deleteKey(uid: String) { uidKeys.remove(uid) }
        override fun hasKey(uid: String): Boolean = uidKeys.containsKey(uid)
        override fun hasUnassignedLegacyKey(): Boolean = legacyKey != null
        override fun migrateLegacyKeyToUid(uid: String): Boolean {
            val legacy = legacyKey ?: return false
            saveKey(uid, legacy)
            if (getKey(uid) != legacy) return false
            legacyKey = null
            return true
        }
    }

    private class FakeAiProvider : AiProvider {
        override val providerId: String = "test_gemini"
        override val displayName: String = "Test Gemini"
        var mockResult: AiResult<ExtractedFinancialPayload>? = null
        var mockValidationResult: AiResult<Boolean> = AiResult.Success(true)
        var validationResponse: kotlinx.coroutines.CompletableDeferred<AiResult<Boolean>>? = null
        var validationStarted: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        var callCount = 0
        var thrownException: Exception? = null

        override suspend fun isConfigured(): Boolean = true
        override suspend fun validateKey(apiKey: String): AiResult<Boolean> {
            validationStarted?.complete(Unit)
            return validationResponse?.await() ?: mockValidationResult
        }
        override suspend fun extractFinancialTransactions(
            prompt: String,
            config: AiConfiguration
        ): AiResult<ExtractedFinancialPayload> {
            callCount++
            thrownException?.let { throw it }
            return mockResult ?: AiResult.Failure(AiError.MalformedResponse("No mock set"))
        }
    }

    @Before
    fun setup() {
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        val testDataStoreFile = tempFolder.root.resolve("test_settings.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { testDataStoreFile }
        )
        authUid.value = "test_uid"
        fakeStorage = FakeSecureKeyStorage()
        fakeProvider = FakeAiProvider()
        isOnline = true
        repository = createRepository()
        runBlocking {
            assertFalse(repository.isAiFeatureEnabled.first())
        }
    }

    @After
    fun tearDown() {
        runBlocking { dataStoreScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    private fun createRepository() = AiRepositoryImpl(
        aiProvider = fakeProvider,
        secureKeyStorage = fakeStorage,
        dataStore = testDataStore,
        todayProvider = { LocalDate.of(2026, 11, 12) },
        isOnline = { isOnline },
        currentUidProvider = { authUid.value },
        authenticatedUidFlow = authUid
    )

    @Test
    fun isAiConfigured_reflectsKeyPresence() = runTest {
        assertFalse(repository.isAiConfigured.first())
        assertNull(repository.getMaskedApiKey())

        repository.saveApiKey("AIzaSyTestKey1234567890")
        assertTrue(repository.isAiConfigured.first())
        assertEquals("••••••••7890", repository.getMaskedApiKey())

        repository.deleteApiKey()
        assertFalse(repository.isAiConfigured.first())
        assertNull(repository.getMaskedApiKey())
    }

    @Test
    fun uidSwitchRestoresOnlyThatAccountsKeyAndPreferenceAndDeletionIsIsolated() = runTest {
        authUid.value = "uid_a"
        repository.saveApiKey("AIzaSyKeyForAccountA1234")
        repository.setAiFeatureEnabled(true)
        assertEquals("••••••••1234", repository.getMaskedApiKey())
        assertTrue(repository.isAiFeatureEnabled.first())

        authUid.value = "uid_b"
        assertNull(repository.getMaskedApiKey())
        assertFalse(repository.isAiFeatureEnabled.first())
        repository.saveApiKey("AIzaSyKeyForAccountB9876")
        repository.setAiFeatureEnabled(true)

        authUid.value = "uid_a"
        assertEquals("••••••••1234", repository.getMaskedApiKey())
        assertTrue(repository.isAiFeatureEnabled.first())
        repository.deleteApiKey()

        authUid.value = "uid_b"
        assertEquals("••••••••9876", repository.getMaskedApiKey())
        assertTrue(repository.isAiFeatureEnabled.first())
    }

    @Test
    fun legacyKeyRequiresConfirmedMigrationAndIsRemovedOnlyAfterUidScopedWrite() = runTest {
        fakeStorage.legacyKey = "AIzaSyLegacySecret"
        repository = createRepository()
        assertTrue(repository.hasUnassignedLegacyKey.first())
        assertFalse(repository.migrateLegacyKeyToCurrentUid(confirmed = false) is AiResult.Success)
        assertEquals("AIzaSyLegacySecret", fakeStorage.legacyKey)

        assertTrue(repository.migrateLegacyKeyToCurrentUid(confirmed = true) is AiResult.Success)
        assertNull(fakeStorage.legacyKey)
        assertEquals("••••••••cret", repository.getMaskedApiKey())
        assertFalse(repository.isAiFeatureEnabled.first())
    }

    @Test
    fun guestProfileIsolatedFromUidAndLegacyKeysAndCanBeDeletedIndependently() = runTest {
        fakeStorage.saveKey("uid_a", "account_a_key")
        fakeStorage.legacyKey = "legacy_unassigned_key"
        authUid.value = null
        repository = createRepository()

        assertFalse(repository.isAuthenticated.first())
        assertFalse(repository.isAiConfigured.first())
        assertFalse(repository.isAiFeatureEnabled.first())
        assertNull(repository.getMaskedApiKey())
        assertFalse(repository.migrateLegacyKeyToCurrentUid(confirmed = true) is AiResult.Success)
        assertEquals(AiResult.Success(Unit), repository.saveApiKey("guest_key_1234"))
        repository.setAiFeatureEnabled(true)
        assertEquals("••••••••1234", repository.getMaskedApiKey())
        assertTrue(repository.isAiFeatureEnabled.first())

        repository.deleteApiKey()
        assertNull(fakeStorage.getKey(AiRepository.GUEST_PROFILE_ID))
        assertEquals("account_a_key", fakeStorage.getKey("uid_a"))
        assertEquals("legacy_unassigned_key", fakeStorage.legacyKey)
    }

    @Test
    fun guestCanUseOnlyItsOwnGeminiKeyWithoutGoogleSignIn() = runTest {
        authUid.value = null
        repository = createRepository()
        repository.setAiFeatureEnabled(true)
        repository.saveApiKey("guest_key_1234")
        fakeProvider.mockResult = AiResult.Success(
            ExtractedFinancialPayload(
                items = listOf(
                    ExtractedFinancialItem(
                        transactionType = "EXPENSE",
                        rawAmount = "500",
                        currency = "UZS",
                        description = "Guest lunch"
                    )
                )
            )
        )

        val guestResult = repository.extractTransactions("paid 500 som for lunch")
        assertTrue(guestResult is AiResult.Success)
        assertEquals(1, fakeProvider.callCount)

        authUid.value = "uid_a"
        assertNull(repository.getMaskedApiKey())
        repository.setAiFeatureEnabled(true)
        repository.extractTransactions("paid 500 som for lunch")
        assertEquals("guest_key_1234", fakeStorage.getKey(AiRepository.GUEST_PROFILE_ID))
        assertEquals(1, fakeProvider.callCount)
    }

    @Test
    fun staleVerificationCannotValidateAnotherUidOrReplacementKey() = runTest {
        repository.setAiFeatureEnabled(true)
        repository.saveApiKey("old_key")
        val responseForAccountA = kotlinx.coroutines.CompletableDeferred<AiResult<Boolean>>()
        fakeProvider.validationResponse = responseForAccountA
        fakeProvider.validationStarted = kotlinx.coroutines.CompletableDeferred()
        val accountARequest = async { repository.validateConfiguredKey() }
        runCurrent()
        fakeProvider.validationStarted?.await()
        authUid.value = "uid_b"
        responseForAccountA.complete(AiResult.Success(true))
        advanceUntilIdle()
        assertFalse(repository.isKeyVerified.first())
        assertFalse(accountARequest.await() is AiResult.Success)

        authUid.value = "test_uid"
        repository.setAiFeatureEnabled(true)
        fakeProvider.validationResponse = null
        repository.saveApiKey("old_key_again")
        val responseForOldKey = kotlinx.coroutines.CompletableDeferred<AiResult<Boolean>>()
        fakeProvider.validationResponse = responseForOldKey
        fakeProvider.validationStarted = kotlinx.coroutines.CompletableDeferred()
        val oldKeyRequest = async { repository.validateConfiguredKey() }
        runCurrent()
        fakeProvider.validationStarted?.await()
        repository.saveApiKey("replacement_key")
        responseForOldKey.complete(AiResult.Success(true))
        advanceUntilIdle()
        assertFalse(repository.isKeyVerified.first())
        assertFalse(oldKeyRequest.await() is AiResult.Success)
    }

    @Test
    fun temporaryVerificationFailurePreservesPreviouslyVerifiedKey() = runTest {
        repository.saveApiKey("saved_key")
        repository.setAiFeatureEnabled(true)
        fakeProvider.mockValidationResult = AiResult.Success(true)
        assertEquals(AiResult.Success(true), repository.validateConfiguredKey())
        assertTrue(repository.isKeyVerified.first())

        fakeProvider.mockValidationResult = AiResult.Failure(AiError.NetworkError("offline"))
        assertEquals(AiResult.Failure(AiError.NetworkError("offline")), repository.validateConfiguredKey())
        assertTrue(repository.isKeyVerified.first())
    }

    @Test
    fun invalidVerificationClearsVerifiedStateAndBlocksFurtherGeminiCalls() = runTest {
        repository.setAiFeatureEnabled(true)
        repository.saveApiKey("saved_key")
        fakeProvider.mockValidationResult = AiResult.Success(true)
        repository.validateConfiguredKey()
        assertTrue(repository.isKeyVerified.first())

        fakeProvider.mockValidationResult = AiResult.Failure(AiError.InvalidApiKey)
        assertEquals(AiResult.Failure(AiError.InvalidApiKey), repository.validateConfiguredKey())
        assertFalse(repository.isKeyVerified.first())

        fakeProvider.mockResult = AiResult.Success(
            ExtractedFinancialPayload(items = listOf(ExtractedFinancialItem(
                transactionType = "EXPENSE", rawAmount = "50", description = "Lunch"
            )))
        )
        val result = repository.extractTransactions("paid 50 dollars")
        assertTrue(result is AiResult.Success)
        assertEquals(0, fakeProvider.callCount)
    }

    @Test
    fun extractTransactions_withoutKey_usesOfflineParser() = runTest {
        val result = repository.extractTransactions("paid 50 dollars")
        assertTrue(result is AiResult.Success)
        val candidate = (result as AiResult.Success).data.single()
        assertEquals(CandidateSource.OFFLINE_PARSER, candidate.source)
        assertNotNull(candidate.amount)
        assertEquals(0, fakeProvider.callCount)
    }

    @Test
    fun extractTransactions_whenAiDisabled_usesOfflineParser() = runTest {
        fakeStorage.saveKey("valid_key")
        repository.setAiFeatureEnabled(false)

        val result = repository.extractTransactions("paid 50 dollars")

        assertTrue(result is AiResult.Success)
        assertEquals(CandidateSource.OFFLINE_PARSER, (result as AiResult.Success).data.single().source)
        assertEquals(0, fakeProvider.callCount)
    }

    @Test
    fun extractTransactions_whenDeviceOffline_usesOfflineParser() = runTest {
        repository.setAiFeatureEnabled(true)
        fakeStorage.saveKey("valid_key")
        isOnline = false

        val result = repository.extractTransactions("paid 50 dollars")

        assertTrue(result is AiResult.Success)
        assertEquals(CandidateSource.OFFLINE_PARSER, (result as AiResult.Success).data.single().source)
        assertEquals(0, fakeProvider.callCount)
    }

    @Test
    fun extractTransactions_whenConfiguredAndOnline_usesGemini() = runTest {
        repository.setAiFeatureEnabled(true)
        fakeStorage.saveKey("valid_key")
        fakeProvider.mockResult = AiResult.Success(
            ExtractedFinancialPayload(
                items = listOf(ExtractedFinancialItem(transactionType = "EXPENSE", rawAmount = "50", description = "Lunch"))
            )
        )

        val result = repository.extractTransactions("paid 50 dollars")

        assertTrue(result is AiResult.Success)
        assertEquals(CandidateSource.GEMINI_AI, (result as AiResult.Success).data.single().source)
        assertEquals(1, fakeProvider.callCount)
    }

    @Test
    fun extractTransactions_networkTimeoutRateLimitServerAndMalformed_failuresFallbackOffline() = runTest {
        repository.setAiFeatureEnabled(true)
        fakeStorage.saveKey("valid_key")
        val failures = listOf(
            AiError.NetworkError("ignored backend detail"),
            AiError.RequestTimeout,
            AiError.RateLimitExceeded(),
            AiError.ServerError(503, "ignored backend detail"),
            AiError.MalformedResponse("ignored backend detail")
        )

        failures.forEach { failure ->
            fakeProvider.mockResult = AiResult.Failure(failure)
            val result = repository.extractTransactions("paid 50 dollars")
            assertTrue("$failure should fall back", result is AiResult.Success)
            val candidate = (result as AiResult.Success).data.single()
            assertEquals(CandidateSource.OFFLINE_PARSER, candidate.source)
            assertTrue(candidate.warnings.contains(ParseWarning.AI_FALLBACK))
        }
    }

    @Test
    fun extractTransactions_invalidKey_fallsBackWithActionableWarning() = runTest {
        repository.setAiFeatureEnabled(true)
        fakeStorage.saveKey("invalid_key")
        fakeProvider.mockResult = AiResult.Failure(AiError.InvalidApiKey)

        val result = repository.extractTransactions("paid 50 dollars")

        assertTrue(result is AiResult.Success)
        val candidate = (result as AiResult.Success).data.single()
        assertTrue(candidate.warnings.contains(ParseWarning.INVALID_API_KEY))
        assertFalse(candidate.warnings.contains(ParseWarning.AI_FALLBACK))
    }

    @Test
    fun extractTransactions_timeoutAndUnexpectedNetworkExceptions_fallbackOffline() = runTest {
        repository.setAiFeatureEnabled(true)
        fakeStorage.saveKey("valid_key")
        listOf(SocketTimeoutException("private detail"), IOException("private detail")).forEach { exception ->
            fakeProvider.thrownException = exception
            val result = repository.extractTransactions("paid 50 dollars")
            assertTrue(result is AiResult.Success)
            assertTrue((result as AiResult.Success).data.single().warnings.contains(ParseWarning.AI_FALLBACK))
        }
    }

    @Test
    fun extractTransactions_emptyGeminiPayload_fallsBackOffline() = runTest {
        repository.setAiFeatureEnabled(true)
        fakeStorage.saveKey("valid_key")
        fakeProvider.mockResult = AiResult.Success(ExtractedFinancialPayload(emptyList()))

        val result = repository.extractTransactions("paid 50 dollars")

        assertTrue(result is AiResult.Success)
        assertTrue((result as AiResult.Success).data.single().warnings.contains(ParseWarning.AI_FALLBACK))
    }

    @Test
    fun extractTransactions_invalidOfflineInput_returnsNoTransactions() = runTest {
        val result = repository.extractTransactions("hello there")

        assertTrue(result is AiResult.Failure)
        assertEquals(AiError.NoTransactionsFound, (result as AiResult.Failure).error)
    }

    @Test
    fun extractTransactions_offlineMissingAmountPreservesNullAndConfidence() = runTest {
        val result = repository.extractTransactions("spent on lunch")

        assertTrue(result is AiResult.Success)
        val candidate = (result as AiResult.Success).data.single()
        assertNull(candidate.amount)
        assertEquals(ParseConfidence.LOW, candidate.confidence)
        assertTrue(candidate.warnings.contains(ParseWarning.MISSING_AMOUNT))
        assertEquals(CandidateSource.OFFLINE_PARSER, candidate.source)
    }

    @Test
    fun extractTransactions_validExpense_convertsSafelyToCandidate() = runTest {
        repository.setAiFeatureEnabled(true)
        fakeStorage.saveKey("valid_key")
        fakeProvider.mockResult = AiResult.Success(
            ExtractedFinancialPayload(
                items = listOf(
                    ExtractedFinancialItem(
                        transactionType = "EXPENSE",
                        rawAmount = "45000",
                        currency = "UZS",
                        description = "Tushlik",
                        categorySuggestion = "Food & Dining",
                        sourceAccountSuggestion = "Cash",
                        dateExpression = "bugun"
                    )
                )
            )
        )

        val result = repository.extractTransactions("Bugun tushlikka 45000 som berdim")
        assertTrue(result is AiResult.Success)

        val candidates = (result as AiResult.Success).data
        assertEquals(1, candidates.size)
        val candidate = candidates.first()
        assertEquals(TransactionType.EXPENSE, candidate.transactionType)
        assertNotNull(candidate.amount)
        assertEquals(45000L, candidate.amount!!.amountInMinorUnits)
        assertEquals("UZS", candidate.amount!!.currencyCode)
        assertEquals("Tushlik", candidate.suggestedTitle)
        assertEquals(ParseConfidence.HIGH, candidate.confidence)
        assertTrue(candidate.warnings.isEmpty())
        assertEquals(CandidateSource.GEMINI_AI, candidate.source)
    }

    @Test
    fun extractTransactions_missingAmount_keepsAmountNullAndFlagsWarning() = runTest {
        repository.setAiFeatureEnabled(true)
        fakeStorage.saveKey("valid_key")
        fakeProvider.mockResult = AiResult.Success(
            ExtractedFinancialPayload(
                items = listOf(
                    ExtractedFinancialItem(
                        transactionType = "EXPENSE",
                        rawAmount = null,
                        currency = "UZS",
                        description = "Benzin quydim",
                        isAmountMissing = true
                    )
                )
            )
        )

        val result = repository.extractTransactions("Benzin quydim")
        assertTrue(result is AiResult.Success)

        val candidate = (result as AiResult.Success).data.first()
        assertNull(candidate.amount)
        assertTrue(candidate.warnings.contains(ParseWarning.MISSING_AMOUNT))
    }

    @Test
    fun extractTransactions_incompleteTransfer_flagsWarning() = runTest {
        repository.setAiFeatureEnabled(true)
        fakeStorage.saveKey("valid_key")
        fakeProvider.mockResult = AiResult.Success(
            ExtractedFinancialPayload(
                items = listOf(
                    ExtractedFinancialItem(
                        transactionType = "TRANSFER",
                        rawAmount = "100000",
                        currency = "UZS",
                        description = "O'tkazma",
                        sourceAccountSuggestion = "Card",
                        destinationAccountSuggestion = null // Incomplete destination!
                    )
                )
            )
        )

        val result = repository.extractTransactions("Kartamdan pul o'tkazdim")
        assertTrue(result is AiResult.Success)

        val candidate = (result as AiResult.Success).data.first()
        assertEquals(TransactionType.TRANSFER, candidate.transactionType)
        assertTrue(candidate.warnings.contains(ParseWarning.AMBIGUOUS_TYPE))
        assertEquals(ParseConfidence.AMBIGUOUS, candidate.confidence)
    }

    @Test
    fun extractTransactions_ambiguousCurrency_flagsWarning() = runTest {
        repository.setAiFeatureEnabled(true)
        fakeStorage.saveKey("valid_key")
        fakeProvider.mockResult = AiResult.Success(
            ExtractedFinancialPayload(
                items = listOf(
                    ExtractedFinancialItem(
                        transactionType = "EXPENSE",
                        rawAmount = "50",
                        currency = "USD",
                        description = "Book",
                        currencyAmbiguous = true
                    )
                )
            )
        )

        val result = repository.extractTransactions("bought book 50")
        assertTrue(result is AiResult.Success)

        val candidate = (result as AiResult.Success).data.first()
        assertTrue(candidate.warnings.contains(ParseWarning.UNSUPPORTED_CURRENCY))
    }

    @Test
    fun extractTransactions_shorthandAmountFromAi_parsesMinorUnitsSafely() = runTest {
        repository.setAiFeatureEnabled(true)
        fakeStorage.saveKey("valid_key")
        fakeProvider.mockResult = AiResult.Success(
            ExtractedFinancialPayload(
                items = listOf(
                    ExtractedFinancialItem(
                        transactionType = "EXPENSE",
                        rawAmount = "50k",
                        currency = "UZS",
                        description = "Ovqatlanishga"
                    ),
                    ExtractedFinancialItem(
                        transactionType = "EXPENSE",
                        rawAmount = "1.5k",
                        currency = "UZS",
                        description = "Kofe"
                    )
                )
            )
        )

        val result = repository.extractTransactions("50k ovqatlanishga va 1.5k kofe")
        assertTrue(result is AiResult.Success)

        val candidates = (result as AiResult.Success).data
        assertEquals(2, candidates.size)
        assertEquals(50000L, candidates[0].amount?.amountInMinorUnits)
        assertEquals(1500L, candidates[1].amount?.amountInMinorUnits)
    }

    @Test
    fun extractTransactions_physicalMeasurementInRawAmount_flagsMissingOrInvalidAmount() = runTest {
        repository.setAiFeatureEnabled(true)
        fakeStorage.saveKey("valid_key")
        fakeProvider.mockResult = AiResult.Success(
            ExtractedFinancialPayload(
                items = listOf(
                    ExtractedFinancialItem(
                        transactionType = "EXPENSE",
                        rawAmount = "50 kg",
                        currency = "UZS",
                        description = "Kartoshka"
                    )
                )
            )
        )

        val result = repository.extractTransactions("50 kg kartoshka")
        assertTrue(result is AiResult.Success)

        val candidate = (result as AiResult.Success).data.first()
        assertNull(candidate.amount)
        assertTrue(candidate.warnings.contains(ParseWarning.INVALID_AMOUNT))
    }
}
