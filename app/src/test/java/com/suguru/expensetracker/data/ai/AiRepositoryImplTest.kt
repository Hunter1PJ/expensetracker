package com.suguru.expensetracker.data.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.suguru.expensetracker.data.security.SecureKeyStorage
import com.suguru.expensetracker.domain.ai.AiConfiguration
import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiProvider
import com.suguru.expensetracker.domain.ai.AiResult
import com.suguru.expensetracker.domain.ai.ExtractedFinancialItem
import com.suguru.expensetracker.domain.ai.ExtractedFinancialPayload
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.parser.ParseConfidence
import com.suguru.expensetracker.domain.parser.ParseWarning
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AiRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeStorage: FakeSecureKeyStorage
    private lateinit var fakeProvider: FakeAiProvider
    private lateinit var repository: AiRepositoryImpl

    private class FakeSecureKeyStorage : SecureKeyStorage {
        var storedKey: String? = null
        override fun saveKey(key: String) { storedKey = key.trim().takeIf { it.isNotBlank() } }
        override fun getKey(): String? = storedKey
        override fun deleteKey() { storedKey = null }
        override fun hasKey(): Boolean = storedKey != null
    }

    private class FakeAiProvider : AiProvider {
        override val providerId: String = "test_gemini"
        override val displayName: String = "Test Gemini"
        var mockResult: AiResult<ExtractedFinancialPayload>? = null
        var mockValidationResult: AiResult<Boolean> = AiResult.Success(true)

        override suspend fun isConfigured(): Boolean = true
        override suspend fun validateKey(apiKey: String): AiResult<Boolean> = mockValidationResult
        override suspend fun extractFinancialTransactions(
            prompt: String,
            config: AiConfiguration
        ): AiResult<ExtractedFinancialPayload> {
            return mockResult ?: AiResult.Failure(AiError.MalformedResponse("No mock set"))
        }
    }

    @Before
    fun setup() {
        val testDataStoreFile = tempFolder.newFile("test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testDataStoreFile }
        )
        fakeStorage = FakeSecureKeyStorage()
        fakeProvider = FakeAiProvider()
        repository = AiRepositoryImpl(
            aiProvider = fakeProvider,
            secureKeyStorage = fakeStorage,
            dataStore = dataStore,
            todayProvider = { LocalDate.of(2026, 11, 12) }
        )
    }

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
    fun extractTransactions_withoutKey_failsWithMissingApiKey() = runTest {
        val result = repository.extractTransactions("spent 50 dollars")
        assertTrue(result is AiResult.Failure)
        assertEquals(AiError.MissingApiKey, (result as AiResult.Failure).error)
    }

    @Test
    fun extractTransactions_validExpense_convertsSafelyToCandidate() = runTest {
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
    }

    @Test
    fun extractTransactions_missingAmount_keepsAmountNullAndFlagsWarning() = runTest {
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
}
