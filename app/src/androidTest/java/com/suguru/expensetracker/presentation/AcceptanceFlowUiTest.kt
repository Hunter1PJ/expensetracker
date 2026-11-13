package com.suguru.expensetracker.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.suguru.expensetracker.domain.ai.AiRepository
import com.suguru.expensetracker.domain.ai.AiResult
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.AppSettings
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.model.WeekStart
import com.suguru.expensetracker.domain.parser.CandidateSource
import com.suguru.expensetracker.domain.parser.ParseConfidence
import com.suguru.expensetracker.domain.parser.ParseWarning
import com.suguru.expensetracker.domain.parser.TransactionCandidate
import com.suguru.expensetracker.domain.repository.SettingsRepository
import com.suguru.expensetracker.presentation.home.AccountSummaryUiModel
import com.suguru.expensetracker.presentation.home.HomeScreenContent
import com.suguru.expensetracker.presentation.home.HomeUiState
import com.suguru.expensetracker.presentation.transactions.add.AddTransactionScreenContent
import com.suguru.expensetracker.presentation.transactions.add.AddTransactionUiState
import com.suguru.expensetracker.presentation.settings.SettingsScreen
import com.suguru.expensetracker.presentation.settings.SettingsViewModel
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.suguru.expensetracker.domain.ai.AiError
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcceptanceFlowUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun candidatePreviewRendersAndInvokesEditNavigation() {
        val candidate = TransactionCandidate(
            rawText = "I spent 25 dollars on lunch today.",
            suggestedTitle = "Lunch",
            transactionType = TransactionType.EXPENSE,
            amount = Money(2500, "USD"),
            date = LocalDate.of(2026, 11, 12),
            suggestedCategoryName = "Food",
            confidence = ParseConfidence.HIGH,
            source = CandidateSource.GEMINI_AI
        )
        var selectedCandidate: TransactionCandidate? = null

        composeRule.setContent {
            ExpenseTrackerTheme {
                HomeScreenContent(
                    uiState = HomeUiState(
                        isAiConfigured = true,
                        accounts = listOf(
                            AccountSummaryUiModel(
                                id = 1,
                                name = "Cash",
                                type = AccountType.CASH,
                                typeName = "Cash",
                                currencyCode = "USD",
                                iconName = "wallet",
                                colorHex = "#000000",
                                balanceFormatted = "$0.00"
                            )
                        ),
                        extractedCandidates = listOf(candidate)
                    ),
                    onNavigateToAddTransactionWithCandidate = { selectedCandidate = it }
                )
            }
        }

        composeRule.onNodeWithTag("ai_candidate_preview_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_candidate_confirm_button").performClick()

        assertEquals(candidate, selectedCandidate)
    }

    @Test
    fun aiDraftShowsMissingAmountWarningAndKeepsSaveAndBackExplicit() {
        var saveRequested = false
        var backRequested = false
        val state = AddTransactionUiState(
            accounts = listOf(
                Account(
                    id = 1,
                    name = "Cash",
                    type = AccountType.CASH,
                    initialBalance = Money(0, "USD")
                )
            ),
            categories = listOf(
                Category(
                    id = 1,
                    name = "Food",
                    type = CategoryType.EXPENSE,
                    iconName = "food",
                    colorHex = "#000000"
                )
            ),
            selectedAccountId = 1,
            selectedCategoryId = 1,
            isAiAssisted = true,
            candidateSource = CandidateSource.OFFLINE_PARSER,
            aiAmountWarning = "Amount was not detected in the AI draft. Please enter an amount.",
            aiSourceWarnings = listOf(ParseWarning.MISSING_AMOUNT)
        )

        composeRule.setContent {
            ExpenseTrackerTheme {
                AddTransactionScreenContent(
                    uiState = state,
                    onNavigateBack = { backRequested = true },
                    onTransactionTypeChanged = {},
                    onAmountChanged = {},
                    onAccountSelected = {},
                    onDestinationAccountSelected = {},
                    onCategorySelected = {},
                    onNoteChanged = {},
                    onTransactionTimeChanged = {},
                    onDismissError = {},
                    onSaveTransaction = { saveRequested = true }
                )
            }
        }

        composeRule.onNodeWithTag("ai_assisted_draft_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_missing_amount_warning").assertIsDisplayed()
        composeRule.onNodeWithTag("save_transaction_button").performClick()
        composeRule.onNodeWithTag("back_button").performClick()

        assertEquals(true, saveRequested)
        assertEquals(true, backRequested)
    }

    @Test
    fun keyVerificationShowsProgressDisablesVerifyAndCancelsFromDialog() {
        val response = CompletableDeferred<AiResult<Boolean>>()
        val viewModel = SettingsViewModel(FakeSettingsRepository(), FakeAiRepository(response))

        composeRule.setContent {
            ExpenseTrackerTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToAccounts = {},
                    onNavigateToCategories = {},
                    onNavigateToRecurring = {},
                    onNavigateToDataAndStorage = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_item_gemini_key").performScrollTo().performClick()
        composeRule.onNodeWithTag("verify_api_key_button").performClick()
        composeRule.onNodeWithTag("verify_api_key_progress_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("verify_api_key_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("cancel_verify_api_key_button").performClick()
        composeRule.onAllNodesWithTag("verify_api_key_progress_dialog").assertCountEquals(0)
        composeRule.onNodeWithTag("verify_api_key_button").assertIsDisplayed()
    }

    @Test
    fun savingKeyShowsConfirmationOnlyAfterSuccessfulStorage() {
        val viewModel = SettingsViewModel(
            FakeSettingsRepository(),
            FakeAiRepository(CompletableDeferred(), saveResult = AiResult.Success(Unit))
        )

        composeRule.setContent {
            ExpenseTrackerTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToAccounts = {},
                    onNavigateToCategories = {},
                    onNavigateToRecurring = {},
                    onNavigateToDataAndStorage = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_item_gemini_key").performScrollTo().performClick()
        composeRule.onNodeWithTag("gemini_api_key_input").performTextInput("test-key")
        composeRule.onNodeWithTag("save_api_key_button").performClick()
        composeRule.onNodeWithText(
            "API key successfully saved. Now verify your key to enable Gemini AI."
        ).assertIsDisplayed()
    }

    @Test
    fun failedSaveDoesNotShowSuccessConfirmation() {
        val viewModel = SettingsViewModel(
            FakeSettingsRepository(),
            FakeAiRepository(
                CompletableDeferred(),
                saveResult = AiResult.Failure(AiError.ExecutionError("safe test failure"))
            )
        )

        composeRule.setContent {
            ExpenseTrackerTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToAccounts = {},
                    onNavigateToCategories = {},
                    onNavigateToRecurring = {},
                    onNavigateToDataAndStorage = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_item_gemini_key").performScrollTo().performClick()
        composeRule.onNodeWithTag("gemini_api_key_input").performTextInput("test-key")
        composeRule.onNodeWithTag("save_api_key_button").performClick()
        composeRule.onAllNodesWithText(
            "API key successfully saved. Now verify your key to enable Gemini AI."
        ).assertCountEquals(0)
    }

    @Test
    fun successfulVerificationClosesManageDialogAndShowsReadyState() {
        val response = CompletableDeferred<AiResult<Boolean>>()
        val viewModel = SettingsViewModel(FakeSettingsRepository(), FakeAiRepository(response))

        composeRule.setContent {
            ExpenseTrackerTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToAccounts = {},
                    onNavigateToCategories = {},
                    onNavigateToRecurring = {},
                    onNavigateToDataAndStorage = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_item_gemini_key").performScrollTo().performClick()
        composeRule.onNodeWithTag("verify_api_key_button").performClick()
        composeRule.runOnIdle { response.complete(AiResult.Success(true)) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("verify_api_key_button").assertCountEquals(0)
        composeRule.onNodeWithText("Valid & Ready", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText(
            "Gemini API successfully verified! You can now use AI to manage your transactions."
        ).assertCountEquals(1)
    }

    @Test
    fun failedVerificationKeepsManageDialogOpen() {
        val response = CompletableDeferred<AiResult<Boolean>>()
        val viewModel = SettingsViewModel(FakeSettingsRepository(), FakeAiRepository(response))

        composeRule.setContent {
            ExpenseTrackerTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToAccounts = {},
                    onNavigateToCategories = {},
                    onNavigateToRecurring = {},
                    onNavigateToDataAndStorage = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_item_gemini_key").performScrollTo().performClick()
        composeRule.onNodeWithTag("verify_api_key_button").performClick()
        composeRule.runOnIdle { response.complete(AiResult.Failure(AiError.InvalidApiKey)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("verify_api_key_button").assertIsDisplayed()
        composeRule.onAllNodesWithText(
            "Gemini API successfully verified! You can now use AI to manage your transactions."
        ).assertCountEquals(0)
    }

    private class FakeAiRepository(
        private val validationResponse: CompletableDeferred<AiResult<Boolean>>,
        private val saveResult: AiResult<Unit> = AiResult.Success(Unit)
    ) : AiRepository {
        override val isAiConfigured: Flow<Boolean> = flowOf(true)
        override val isAiFeatureEnabled: Flow<Boolean> = flowOf(true)
        override suspend fun getMaskedApiKey(): String = "********"
        override suspend fun saveApiKey(apiKey: String): AiResult<Unit> = saveResult
        override suspend fun validateApiKey(apiKey: String): AiResult<Boolean> = validationResponse.await()
        override suspend fun validateConfiguredKey(): AiResult<Boolean> = validationResponse.await()
        override suspend fun deleteApiKey() = Unit
        override suspend fun setAiFeatureEnabled(enabled: Boolean) = Unit
        override suspend fun extractTransactions(
            input: String,
            defaultCurrency: String
        ): AiResult<List<TransactionCandidate>> = AiResult.Success(emptyList())
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val settings: Flow<AppSettings> = flowOf(
            AppSettings(
                themeMode = ThemeMode.SYSTEM,
                preferredCurrencyCode = null,
                weekStart = WeekStart.MONDAY,
                dateFormat = DateFormatPreference.SYSTEM_DEFAULT,
                timeFormat = TimeFormatPreference.SYSTEM_DEFAULT,
                showCurrencyCode = true,
                confirmBeforeDelete = true
            )
        )
        override val lastBackupAt: Flow<String?> = flowOf(null)
        override val lastBackupFileName: Flow<String?> = flowOf(null)
        override suspend fun restoreSettings(value: AppSettings) = Unit
        override suspend fun setThemeMode(value: ThemeMode) = Unit
        override suspend fun setPreferredCurrency(value: String?) = Unit
        override suspend fun setWeekStart(value: WeekStart) = Unit
        override suspend fun setDateFormat(value: DateFormatPreference) = Unit
        override suspend fun setTimeFormat(value: TimeFormatPreference) = Unit
        override suspend fun setShowCurrencyCode(value: Boolean) = Unit
        override suspend fun setConfirmBeforeDelete(value: Boolean) = Unit
        override suspend fun setLastBackupMetadata(at: String?, fileName: String?) = Unit
    }
}