package com.suguru.expensetracker.presentation.home

import androidx.lifecycle.ViewModel
import com.suguru.expensetracker.R
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiRepository
import com.suguru.expensetracker.domain.ai.AiResult
import com.suguru.expensetracker.domain.model.AccountBalanceSummary
import com.suguru.expensetracker.domain.model.CurrencySummary
import com.suguru.expensetracker.domain.model.RecentTransactionDetail
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.parser.TransactionCandidate
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveAccountSummariesUseCase
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveDashboardSummaryUseCase
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveRecentTransactionsUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.presentation.common.FinanceVisuals
import com.suguru.expensetracker.presentation.util.UiMessage
import com.suguru.expensetracker.presentation.util.DateTimeFormatterHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase? = null,
    observeAccountSummariesUseCase: ObserveAccountSummariesUseCase? = null,
    observeRecentTransactionsUseCase: ObserveRecentTransactionsUseCase? = null,
    private val aiRepository: AiRepository? = null
) : ViewModel() {

    private val _isAiExtracting = MutableStateFlow(false)
    private val _aiExtractionError = MutableStateFlow<AiError?>(null)
    private val _extractedCandidates = MutableStateFlow<List<TransactionCandidate>>(emptyList())

    private data class AiUiData(
        val enabled: Boolean,
        val configured: Boolean,
        val isExtracting: Boolean,
        val error: AiError?,
        val candidates: List<TransactionCandidate>
    )

    val uiState: StateFlow<HomeUiState> = if (
        observeDashboardSummaryUseCase != null &&
        observeAccountSummariesUseCase != null &&
        observeRecentTransactionsUseCase != null
    ) {
        val aiEnabledFlow = aiRepository?.isAiFeatureEnabled ?: kotlinx.coroutines.flow.flowOf(true)
        val aiConfiguredFlow = aiRepository?.isAiConfigured ?: kotlinx.coroutines.flow.flowOf(false)

        val financialFlow = combine(
            observeDashboardSummaryUseCase(),
            observeAccountSummariesUseCase(),
            observeRecentTransactionsUseCase(limit = 5)
        ) { currencySummaries, accountSummaries, recentTransactions ->
            Triple(currencySummaries, accountSummaries, recentTransactions)
        }

        val aiStatusFlow = combine(
            aiEnabledFlow,
            aiConfiguredFlow,
            _isAiExtracting,
            _aiExtractionError,
            _extractedCandidates
        ) { aiEnabled, aiConfigured, isExtracting, extractionError, candidates ->
            AiUiData(aiEnabled, aiConfigured, isExtracting, extractionError, candidates)
        }

        combine(financialFlow, aiStatusFlow) { financialData, aiData ->
            mapToUiState(
                currencySummaries = financialData.first,
                accountSummaries = financialData.second,
                recentTransactions = financialData.third,
                isAiEnabled = aiData.enabled,
                isAiConfigured = aiData.configured,
                isExtracting = aiData.isExtracting,
                extractionError = aiData.error,
                candidates = aiData.candidates
            )
        }.catch { error ->
            emit(HomeUiState(isLoading = false, errorMessage = UiMessage(R.string.error_unknown)))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState(isLoading = true)
        )
    } else {
        MutableStateFlow(HomeUiState(isLoading = false))
    }

    fun extractTransactionsFromText(input: String, defaultCurrency: String = "UZS") {
        val aiRepo = aiRepository ?: return
        if (input.trim().isBlank()) return

        viewModelScope.launch {
            _isAiExtracting.value = true
            _aiExtractionError.value = null
            _extractedCandidates.value = emptyList()

            val result = aiRepo.extractTransactions(input, defaultCurrency)
            _isAiExtracting.value = false

            when (result) {
                is AiResult.Success -> {
                    _extractedCandidates.value = result.data
                }
                is AiResult.Failure -> {
                    _aiExtractionError.value = result.error
                }
            }
        }
    }

    fun clearExtractedCandidates() {
        _extractedCandidates.value = emptyList()
        _aiExtractionError.value = null
    }

    fun consumeCandidate(candidate: TransactionCandidate) {
        _extractedCandidates.value = _extractedCandidates.value.filterNot {
            it === candidate || (it.suggestedTitle == candidate.suggestedTitle && it.rawText == candidate.rawText && it.amount == candidate.amount)
        }
    }

    private fun mapToUiState(
        currencySummaries: List<CurrencySummary>,
        accountSummaries: List<AccountBalanceSummary>,
        recentTransactions: List<RecentTransactionDetail>,
        isAiEnabled: Boolean,
        isAiConfigured: Boolean,
        isExtracting: Boolean,
        extractionError: AiError?,
        candidates: List<TransactionCandidate>
    ): HomeUiState {
        val currencyUiModels = currencySummaries.map { cs ->
            val isNetPos = cs.monthlyNet.amountInMinorUnits > 0L
            val isNetNeg = cs.monthlyNet.amountInMinorUnits < 0L

            val expenseFormatted = if (cs.monthlyExpense.amountInMinorUnits > 0L) {
                "- " + MoneyParser.format(cs.monthlyExpense, includeSymbol = true, useGrouping = true)
            } else {
                MoneyParser.format(cs.monthlyExpense, includeSymbol = true, useGrouping = true)
            }

            val incomeFormatted = if (cs.monthlyIncome.amountInMinorUnits > 0L) {
                "+ " + MoneyParser.format(cs.monthlyIncome, includeSymbol = true, useGrouping = true)
            } else {
                MoneyParser.format(cs.monthlyIncome, includeSymbol = true, useGrouping = true)
            }

            val netFormatted = when {
                isNetPos -> "+ " + MoneyParser.format(cs.monthlyNet, includeSymbol = true, useGrouping = true)
                isNetNeg -> "- " + MoneyParser.format(cs.monthlyNet.copy(amountInMinorUnits = -cs.monthlyNet.amountInMinorUnits), includeSymbol = true, useGrouping = true)
                else -> MoneyParser.format(cs.monthlyNet, includeSymbol = true, useGrouping = true)
            }

            CurrencySummaryUiModel(
                currencyCode = cs.currencyCode,
                balanceFormatted = MoneyParser.format(cs.totalBalance, includeSymbol = true, useGrouping = true),
                incomeFormatted = incomeFormatted,
                expenseFormatted = expenseFormatted,
                netFormatted = netFormatted,
                isNetPositive = isNetPos,
                isNetNegative = isNetNeg
            )
        }

        val accountUiModels = accountSummaries.map { acctSummary ->
            val account = acctSummary.account
            AccountSummaryUiModel(
                id = account.id,
                name = account.name,
                type = account.type,
                    currencyCode = account.initialBalance.currencyCode,
                iconName = account.iconName,
                colorHex = account.colorHex,
                balanceFormatted = MoneyParser.format(acctSummary.balance, includeSymbol = true, useGrouping = true)
            )
        }

        val recentTxUiModels = recentTransactions.map { detail ->
            val tx = detail.transaction
            val isPositive = when (tx.type) {
                TransactionType.INCOME -> true
                TransactionType.EXPENSE -> false
                TransactionType.TRANSFER -> null
            }

            val amountFormatted = when (tx.type) {
                TransactionType.INCOME -> "+ " + MoneyParser.format(tx.amount, includeSymbol = true, useGrouping = true)
                TransactionType.EXPENSE -> "- " + MoneyParser.format(tx.amount, includeSymbol = true, useGrouping = true)
                TransactionType.TRANSFER -> MoneyParser.format(tx.amount, includeSymbol = true, useGrouping = true)
            }

            val title = when (tx.type) {
                TransactionType.INCOME -> detail.category?.name.orEmpty()
                TransactionType.EXPENSE -> detail.category?.name.orEmpty()
                TransactionType.TRANSFER -> {
                    val fromName = detail.account?.name.orEmpty()
                    val toName = detail.destinationAccount?.name.orEmpty()
                    "$fromName → $toName"
                }
            }

            val subtitle = when (tx.type) {
                TransactionType.TRANSFER -> tx.note.orEmpty()
                else -> {
                    val accountName = detail.account?.name ?: ""
                    if (!tx.note.isNullOrBlank()) {
                        "${tx.note} • $accountName"
                    } else {
                        accountName
                    }
                }
            }

            val iconName = when (tx.type) {
                TransactionType.TRANSFER -> "compare_arrows"
                else -> detail.category?.iconName ?: "category"
            }

            val colorHex = when (tx.type) {
                TransactionType.TRANSFER -> "#3B82F6"
                else -> detail.category?.colorHex ?: "#10B981"
            }

            RecentTransactionUiModel(
                id = tx.id,
                type = tx.type,
                title = title,
                subtitle = subtitle,
                accountName = detail.account?.name ?: "",
                amountFormatted = amountFormatted,
                formattedDate = DateTimeFormatterHelper.formatTransactionTime(tx.transactionTime),
                iconName = iconName,
                colorHex = colorHex,
                isPositive = isPositive,
                isRecurring = tx.recurringRuleId != null
            )
        }

        return HomeUiState(
            isLoading = false,
            currencySummaries = currencyUiModels,
            accounts = accountUiModels,
            recentTransactions = recentTxUiModels,
            currentMonthName = DateTimeFormatterHelper.formatCurrentMonthLabel(),
            errorMessage = null,
            isAiFeatureEnabled = isAiEnabled,
            isAiConfigured = isAiConfigured,
            isAiExtracting = isExtracting,
            aiExtractionError = extractionError,
            extractedCandidates = candidates
        )
    }
}
