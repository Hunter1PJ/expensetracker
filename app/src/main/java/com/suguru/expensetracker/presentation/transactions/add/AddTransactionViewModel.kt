package com.suguru.expensetracker.presentation.transactions.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.usecase.account.ObserveActiveAccountsUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.suguru.expensetracker.domain.usecase.transaction.CreateTransactionUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.domain.parser.ParseWarning
import com.suguru.expensetracker.domain.parser.TransactionCandidate
import com.suguru.expensetracker.widget.common.WidgetRefreshCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private sealed interface MatchResult<out T> {
    data object None : MatchResult<Nothing>
    data class Single<T>(val item: T) : MatchResult<T>
    data class Ambiguous<T>(val items: List<T>) : MatchResult<T>
}

/**
 * ViewModel managing the state and business operations for creating a new financial transaction.
 * Communicates strictly with domain use cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModel(
    private val observeActiveAccountsUseCase: ObserveActiveAccountsUseCase,
    private val observeCategoriesByTypeUseCase: ObserveCategoriesByTypeUseCase,
    private val createTransactionUseCase: CreateTransactionUseCase,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        observeAccounts()
        observeCategories()
    }

    private fun observeAccounts() {
        viewModelScope.launch {
            observeActiveAccountsUseCase().collect { accounts ->
                val defaultAccount = accounts.firstOrNull { it.internalKey == INTERNAL_DEFAULT_ACCOUNT_KEY }
                _uiState.update { state ->
                    state.copy(defaultAccount = defaultAccount)
                }
            }
        }
    }

    private fun observeCategories() {
        viewModelScope.launch {
            _uiState
                .map { it.transactionType }
                .distinctUntilChanged()
                .flatMapLatest { type ->
                    when (type) {
                        TransactionType.EXPENSE -> observeCategoriesByTypeUseCase(CategoryType.EXPENSE)
                        TransactionType.INCOME -> observeCategoriesByTypeUseCase(CategoryType.INCOME)
                        TransactionType.TRANSFER -> flowOf(emptyList())
                    }
                }
                .collect { categories ->
                    _uiState.update { state ->
                        if (state.isAiAssisted) {
                            val catMatch = matchCategory(state.aiCategorySuggestion, categories)
                            val catId = when (catMatch) {
                                is MatchResult.Single -> catMatch.item.id
                                else -> if (categories.any { it.id == state.selectedCategoryId }) state.selectedCategoryId else null
                            }

                            state.copy(
                                categories = categories,
                                selectedCategoryId = catId,
                                aiCategoryMatchAmbiguous = catMatch is MatchResult.Ambiguous
                            )
                        } else {
                            val isSelectedCategoryValid = categories.any { it.id == state.selectedCategoryId }
                            val updatedCategoryId = if (isSelectedCategoryValid) {
                                state.selectedCategoryId
                            } else {
                                categories.firstOrNull()?.id
                            }

                            state.copy(
                                categories = categories,
                                selectedCategoryId = updatedCategoryId
                            )
                        }
                    }
                }
        }
    }

    fun populateFromCandidate(candidate: TransactionCandidate) {
        if (candidate.transactionType == TransactionType.TRANSFER) {
            _uiState.update { state ->
                AddTransactionUiState(
                    defaultAccount = state.defaultAccount,
                    categories = state.categories,
                    selectedCategoryId = state.categories.firstOrNull()?.id,
                    transactionType = TransactionType.EXPENSE,
                    amount = "",
                    note = "",
                    transactionTime = Instant.now(),
                    isAiAssisted = false,
                    candidateSource = null,
                    error = AddTransactionError.Unknown(TRANSFER_NOT_SUPPORTED_MESSAGE),
                    isSavedSuccessfully = false
                )
            }
            return
        }

        val suggestedCategory = candidate.suggestedCategoryName

        val amountStr = candidate.amount?.let {
            MoneyParser.format(it, includeSymbol = false, useGrouping = false)
        } ?: ""

        val amountWarning = if (candidate.amount == null) {
            "Amount was not detected in the AI draft. Please enter an amount."
        } else null

        val warnings = mutableListOf<String>()
        if (candidate.warnings.contains(ParseWarning.UNSUPPORTED_CURRENCY)) {
            warnings.add("Currency may be unconfirmed. Please verify the amount.")
        }
        if (candidate.warnings.contains(ParseWarning.UNIT_PRICE_AMBIGUITY)) {
            warnings.add("Total amount was calculated from unit price or quantity.")
        }
        if (candidate.warnings.contains(ParseWarning.AMBIGUOUS_TYPE)) {
            warnings.add("Transaction type was ambiguous. Defaulted to ${candidate.transactionType.name}.")
        }
        if (candidate.warnings.contains(ParseWarning.INVALID_AMOUNT)) {
            warnings.add("Detected amount string was invalid. Please enter manually.")
        }

        val resolvedInstant = try {
            candidate.date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        } catch (_: Exception) {
            Instant.now()
        }

        _uiState.update { state ->
            val catMatch = matchCategory(suggestedCategory, state.categories)
            val selectedCatId = when (catMatch) {
                is MatchResult.Single -> catMatch.item.id
                else -> null
            }

            state.copy(
                transactionType = candidate.transactionType,
                amount = amountStr,
                selectedCategoryId = selectedCatId,
                note = candidate.suggestedTitle,
                transactionTime = resolvedInstant,
                isAiAssisted = true,
                aiAmountWarning = amountWarning,
                aiCategorySuggestion = suggestedCategory,
                aiCategoryMatchAmbiguous = catMatch is MatchResult.Ambiguous,
                aiWarnings = warnings,
                aiSourceWarnings = candidate.warnings.filter {
                    it == ParseWarning.AI_FALLBACK || it == ParseWarning.INVALID_API_KEY
                },
                candidateSource = candidate.source,
                error = null,
                isSavedSuccessfully = false
            )
        }
    }

    fun clearAiDraftState() {
        _uiState.update { state ->
            state.copy(
                isAiAssisted = false,
                aiAmountWarning = null,
                aiCategorySuggestion = null,
                aiCategoryMatchAmbiguous = false,
                aiWarnings = emptyList(),
                aiSourceWarnings = emptyList(),
                candidateSource = null
            )
        }
    }

    fun clearForm() {
        _uiState.update { state ->
            AddTransactionUiState(
                defaultAccount = state.defaultAccount,
                categories = state.categories,
                selectedCategoryId = state.categories.firstOrNull()?.id,
                transactionType = TransactionType.EXPENSE,
                amount = "",
                note = "",
                transactionTime = Instant.now(),
                isAiAssisted = false,
                candidateSource = null
            )
        }
    }

    private fun matchCategory(suggestedName: String?, categories: List<com.suguru.expensetracker.domain.model.Category>): MatchResult<com.suguru.expensetracker.domain.model.Category> {
        if (suggestedName.isNullOrBlank() || categories.isEmpty()) return MatchResult.None
        val query = suggestedName.trim()
        val exactMatches = categories.filter { it.name.trim().equals(query, ignoreCase = true) }
        if (exactMatches.size == 1) return MatchResult.Single(exactMatches.first())
        if (exactMatches.size > 1) return MatchResult.Ambiguous(exactMatches)

        val partialMatches = categories.filter {
            it.name.trim().startsWith(query, ignoreCase = true) ||
            it.name.trim().split(Regex("\\s+")).any { word -> word.equals(query, ignoreCase = true) }
        }
        if (partialMatches.size == 1) return MatchResult.Single(partialMatches.first())
        if (partialMatches.size > 1) return MatchResult.Ambiguous(partialMatches)
        return MatchResult.None
    }

    fun onTransactionTypeChanged(type: TransactionType) {
        if (type == TransactionType.TRANSFER) {
            _uiState.update { it.copy(error = AddTransactionError.Unknown(TRANSFER_NOT_SUPPORTED_MESSAGE)) }
            return
        }
        _uiState.update { state ->
            state.copy(
                transactionType = type,
                error = null
            )
        }
    }

    fun onAmountChanged(amount: String) {
        val sanitized = amount.replace(',', '.')
        // Allow empty or valid decimal input patterns (e.g., "12", "12.5", "12.50")
        if (sanitized.isEmpty() || sanitized.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
            _uiState.update { it.copy(amount = sanitized, aiAmountWarning = null, error = null) }
        }
    }

    fun onCategorySelected(categoryId: Long) {
        _uiState.update { it.copy(selectedCategoryId = categoryId, aiCategoryMatchAmbiguous = false, error = null) }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onTransactionTimeChanged(time: Instant) {
        _uiState.update { it.copy(transactionTime = time) }
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun saveTransaction(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isSaving) return

        if (state.transactionType == TransactionType.TRANSFER) {
            _uiState.update { it.copy(error = AddTransactionError.Unknown(TRANSFER_NOT_SUPPORTED_MESSAGE)) }
            return
        }

        // 1. Validate internal default account existence
        val defaultAccount = state.defaultAccount
        if (defaultAccount == null) {
            _uiState.update { it.copy(error = AddTransactionError.AccountRequired) }
            return
        }

        // 2. Validate Amount
        val parseResult = MoneyParser.parse(state.amount, defaultAccount.initialBalance.currencyCode)
        if (parseResult.isFailure) {
            _uiState.update { it.copy(error = AddTransactionError.InvalidAmount) }
            return
        }

        val money = parseResult.getOrThrow()
        if (money.amountInMinorUnits <= 0L) {
            _uiState.update { it.copy(error = AddTransactionError.AmountMustBePositive) }
            return
        }

        // 3. Validate Category
        if (state.selectedCategoryId == null) {
            _uiState.update { it.copy(error = AddTransactionError.CategoryRequired) }
            return
        }

        // 4. Construct domain transaction using the internal default account
        val transaction = Transaction(
            type = state.transactionType,
            amount = money,
            accountId = defaultAccount.id,
            destinationAccountId = null,
            categoryId = state.selectedCategoryId,
            note = state.note.trim().ifEmpty { null },
            transactionTime = state.transactionTime
        )

        // 5. Execute use case
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                createTransactionUseCase(transaction)
                widgetRefreshCoordinator?.refreshAccountBalanceWidgets(defaultAccount.id)
                _uiState.update { it.copy(isSaving = false, isSavedSuccessfully = true, error = null) }
                onSuccess()
            } catch (e: DomainException) {
                _uiState.update { it.copy(isSaving = false, error = AddTransactionError.fromDomainException(e)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = AddTransactionError.Unknown(e.localizedMessage ?: e.message)) }
            }
        }
    }

    private companion object {
        const val INTERNAL_DEFAULT_ACCOUNT_KEY = "default"
        const val TRANSFER_NOT_SUPPORTED_MESSAGE =
            "New transfer transactions are not supported. Only expenses and incomes can be created."
    }
}
