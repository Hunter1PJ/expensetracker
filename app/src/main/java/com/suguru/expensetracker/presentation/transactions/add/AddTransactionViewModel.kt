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

    private fun defaultAccountId(accounts: List<com.suguru.expensetracker.domain.model.Account>, currentSelection: Long?): Long? {
        if (currentSelection != null && accounts.any { it.id == currentSelection }) return currentSelection
        return accounts.firstOrNull { it.internalKey == "default" }?.id ?: accounts.firstOrNull()?.id
    }

    private fun observeAccounts() {
        viewModelScope.launch {
            observeActiveAccountsUseCase().collect { accounts ->
                _uiState.update { state ->
                    if (state.isAiAssisted) {
                        val accountMatch = matchAccount(state.aiAccountSuggestion, accounts)
                        val updatedAccountId = when (accountMatch) {
                            is MatchResult.Single -> accountMatch.item.id
                            else -> defaultAccountId(accounts, state.selectedAccountId)
                        }
                        val isAccountAmbiguous = accountMatch is MatchResult.Ambiguous

                        val (updatedDestId, isDestAmbiguous) = if (state.transactionType == TransactionType.TRANSFER) {
                            val destMatch = matchDestinationAccount(state.aiDestinationAccountSuggestion, updatedAccountId, accounts)
                            val destId = when (destMatch) {
                                is MatchResult.Single -> destMatch.item.id
                                else -> if (accounts.any { it.id == state.selectedDestinationAccountId }) state.selectedDestinationAccountId else null
                            }
                            Pair(destId, destMatch is MatchResult.Ambiguous)
                        } else {
                            Pair(null, false)
                        }

                        state.copy(
                            accounts = accounts,
                            selectedAccountId = updatedAccountId,
                            selectedDestinationAccountId = updatedDestId,
                            aiAccountMatchAmbiguous = isAccountAmbiguous,
                            aiDestinationAccountMatchAmbiguous = isDestAmbiguous
                        )
                    } else {
                        val updatedAccountId = defaultAccountId(accounts, state.selectedAccountId)

                        // Also check destination account validity if transfer
                        val isDestValid = accounts.any { it.id == state.selectedDestinationAccountId }
                        val updatedDestId = if (isDestValid) state.selectedDestinationAccountId else null

                        state.copy(
                            accounts = accounts,
                            selectedAccountId = updatedAccountId,
                            selectedDestinationAccountId = updatedDestId
                        )
                    }
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
                            val (updatedCategoryId, isCatAmbiguous) = if (state.transactionType == TransactionType.TRANSFER) {
                                Pair(null, false)
                            } else {
                                val catMatch = matchCategory(state.aiCategorySuggestion, categories)
                                val catId = when (catMatch) {
                                    is MatchResult.Single -> catMatch.item.id
                                    else -> if (categories.any { it.id == state.selectedCategoryId }) state.selectedCategoryId else null
                                }
                                Pair(catId, catMatch is MatchResult.Ambiguous)
                            }

                            state.copy(
                                categories = categories,
                                selectedCategoryId = updatedCategoryId,
                                aiCategoryMatchAmbiguous = isCatAmbiguous
                            )
                        } else {
                            val isSelectedCategoryValid = categories.any { it.id == state.selectedCategoryId }
                            val updatedCategoryId = if (state.transactionType == TransactionType.TRANSFER) {
                                null
                            } else if (isSelectedCategoryValid) {
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
        val suggestedAccount = candidate.suggestedAccountName ?: candidate.transferSourceAccountName
        val suggestedDestAccount = candidate.transferDestinationAccountName
        val suggestedCategory = candidate.suggestedCategoryName

        val amountStr = candidate.amount?.let {
            MoneyParser.format(it, includeSymbol = false, useGrouping = false)
        } ?: ""

        val amountWarning = if (candidate.amount == null) {
            "Amount was not detected in the AI draft. Please enter an amount."
        } else null

        val warnings = mutableListOf<String>()
        if (candidate.warnings.contains(ParseWarning.UNSUPPORTED_CURRENCY)) {
            warnings.add("Currency may be unconfirmed. Please verify the account and amount.")
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
            val accountMatch = matchAccount(suggestedAccount, state.accounts)
            val selectedAccountId = when (accountMatch) {
                is MatchResult.Single -> accountMatch.item.id
                else -> null
            }
            val accountAmbiguous = accountMatch is MatchResult.Ambiguous

            val (selectedDestId, destAmbiguous) = if (candidate.transactionType == TransactionType.TRANSFER) {
                val destMatch = matchDestinationAccount(suggestedDestAccount, selectedAccountId, state.accounts)
                val destId = when (destMatch) {
                    is MatchResult.Single -> destMatch.item.id
                    else -> null
                }
                Pair(destId, destMatch is MatchResult.Ambiguous)
            } else {
                Pair(null, false)
            }

            val (selectedCatId, catAmbiguous) = if (candidate.transactionType != TransactionType.TRANSFER) {
                val catMatch = matchCategory(suggestedCategory, state.categories)
                val catId = when (catMatch) {
                    is MatchResult.Single -> catMatch.item.id
                    else -> null
                }
                Pair(catId, catMatch is MatchResult.Ambiguous)
            } else {
                Pair(null, false)
            }

            state.copy(
                transactionType = candidate.transactionType,
                amount = amountStr,
                selectedAccountId = selectedAccountId,
                selectedDestinationAccountId = selectedDestId,
                selectedCategoryId = selectedCatId,
                note = candidate.suggestedTitle,
                transactionTime = resolvedInstant,
                isAiAssisted = true,
                aiAmountWarning = amountWarning,
                aiAccountSuggestion = suggestedAccount,
                aiAccountMatchAmbiguous = accountAmbiguous,
                aiDestinationAccountSuggestion = suggestedDestAccount,
                aiDestinationAccountMatchAmbiguous = destAmbiguous,
                aiCategorySuggestion = suggestedCategory,
                aiCategoryMatchAmbiguous = catAmbiguous,
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
                aiAccountSuggestion = null,
                aiAccountMatchAmbiguous = false,
                aiDestinationAccountSuggestion = null,
                aiDestinationAccountMatchAmbiguous = false,
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
                accounts = state.accounts,
                categories = state.categories,
                selectedAccountId = defaultAccountId(state.accounts, null),
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

    private fun matchAccount(suggestedName: String?, accounts: List<com.suguru.expensetracker.domain.model.Account>): MatchResult<com.suguru.expensetracker.domain.model.Account> {
        if (suggestedName.isNullOrBlank() || accounts.isEmpty()) return MatchResult.None
        val query = suggestedName.trim()
        val exactMatches = accounts.filter { it.name.trim().equals(query, ignoreCase = true) }
        if (exactMatches.size == 1) return MatchResult.Single(exactMatches.first())
        if (exactMatches.size > 1) return MatchResult.Ambiguous(exactMatches)

        val partialMatches = accounts.filter {
            it.name.trim().startsWith(query, ignoreCase = true) ||
            it.name.trim().split(Regex("\\s+")).any { word -> word.equals(query, ignoreCase = true) }
        }
        if (partialMatches.size == 1) return MatchResult.Single(partialMatches.first())
        if (partialMatches.size > 1) return MatchResult.Ambiguous(partialMatches)
        return MatchResult.None
    }

    private fun matchDestinationAccount(
        suggestedName: String?,
        sourceAccountId: Long?,
        accounts: List<com.suguru.expensetracker.domain.model.Account>
    ): MatchResult<com.suguru.expensetracker.domain.model.Account> {
        if (suggestedName.isNullOrBlank() || accounts.isEmpty()) return MatchResult.None
        val sourceAccount = accounts.find { it.id == sourceAccountId }
        val validCandidates = accounts.filter {
            it.id != sourceAccountId && (sourceAccount == null || it.initialBalance.currencyCode == sourceAccount.initialBalance.currencyCode)
        }
        return matchAccount(suggestedName, validCandidates)
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
        _uiState.update { state ->
            state.copy(
                transactionType = type,
                selectedCategoryId = if (type == TransactionType.TRANSFER) null else state.selectedCategoryId,
                selectedDestinationAccountId = if (type == TransactionType.TRANSFER) state.selectedDestinationAccountId else null,
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

    fun onAccountSelected(accountId: Long) {
        _uiState.update { state ->
            val newSelectedAccount = state.accounts.find { it.id == accountId }
            val newCurrency = newSelectedAccount?.initialBalance?.currencyCode

            // If in transfer mode and destination account now has mismatched currency or same account, reset destination
            val destAccount = state.accounts.find { it.id == state.selectedDestinationAccountId }
            val isDestStillValid = destAccount != null &&
                    destAccount.id != accountId &&
                    destAccount.initialBalance.currencyCode == newCurrency

            state.copy(
                selectedAccountId = accountId,
                selectedDestinationAccountId = if (isDestStillValid) state.selectedDestinationAccountId else null,
                aiAccountMatchAmbiguous = false,
                error = null
            )
        }
    }

    fun onDestinationAccountSelected(accountId: Long) {
        _uiState.update { it.copy(selectedDestinationAccountId = accountId, aiDestinationAccountMatchAmbiguous = false, error = null) }
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

        // 1. Validate Account existence
        if (!state.hasAccounts || state.selectedAccountId == null) {
            _uiState.update { it.copy(error = AddTransactionError.AccountRequired) }
            return
        }

        val sourceAccount = state.selectedAccount
        if (sourceAccount == null) {
            _uiState.update { it.copy(error = AddTransactionError.AccountRequired) }
            return
        }

        // 2. Validate Amount
        val parseResult = MoneyParser.parse(state.amount, sourceAccount.initialBalance.currencyCode)
        if (parseResult.isFailure) {
            _uiState.update { it.copy(error = AddTransactionError.InvalidAmount) }
            return
        }

        val money = parseResult.getOrThrow()
        if (money.amountInMinorUnits <= 0L) {
            _uiState.update { it.copy(error = AddTransactionError.AmountMustBePositive) }
            return
        }

        // 3. Validate Category / Destination based on type
        when (state.transactionType) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                if (state.selectedCategoryId == null) {
                    _uiState.update { it.copy(error = AddTransactionError.CategoryRequired) }
                    return
                }
            }
            TransactionType.TRANSFER -> {
                if (state.selectedDestinationAccountId == null) {
                    _uiState.update { it.copy(error = AddTransactionError.DestinationRequired) }
                    return
                }
                if (state.selectedAccountId == state.selectedDestinationAccountId) {
                    _uiState.update { it.copy(error = AddTransactionError.SameSourceAndDestination) }
                    return
                }
                val destAccount = state.selectedDestinationAccount
                if (destAccount == null || destAccount.initialBalance.currencyCode != sourceAccount.initialBalance.currencyCode) {
                    _uiState.update { it.copy(error = AddTransactionError.CurrencyMismatch) }
                    return
                }
            }
        }

        // 4. Construct domain transaction
        val transaction = Transaction(
            type = state.transactionType,
            amount = money,
            accountId = sourceAccount.id,
            destinationAccountId = if (state.transactionType == TransactionType.TRANSFER) state.selectedDestinationAccountId else null,
            categoryId = if (state.transactionType != TransactionType.TRANSFER) state.selectedCategoryId else null,
            note = state.note.trim().ifEmpty { null },
            transactionTime = state.transactionTime
        )

        // 5. Execute use case
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                createTransactionUseCase(transaction)
                widgetRefreshCoordinator?.refreshAccountBalanceWidgets(transaction.accountId)
                transaction.destinationAccountId?.let { destId ->
                    widgetRefreshCoordinator?.refreshAccountBalanceWidgets(destId)
                }
                _uiState.update { it.copy(isSaving = false, isSavedSuccessfully = true, error = null) }
                onSuccess()
            } catch (e: DomainException) {
                _uiState.update { it.copy(isSaving = false, error = AddTransactionError.fromDomainException(e)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = AddTransactionError.Unknown(e.localizedMessage ?: e.message)) }
            }
        }
    }
}
