package com.suguru.expensetracker.presentation.transactions.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.usecase.account.GetAccountUseCase
import com.suguru.expensetracker.domain.usecase.category.GetCategoryUseCase
import com.suguru.expensetracker.domain.usecase.transaction.DeleteTransactionUseCase
import com.suguru.expensetracker.domain.usecase.transaction.GetTransactionUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.presentation.util.DateTimeFormatterHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * ViewModel for loading, displaying details of, and deleting a specific transaction.
 */
class TransactionDetailViewModel(
    private val transactionId: Long,
    private val getTransactionUseCase: GetTransactionUseCase,
    private val getAccountUseCase: GetAccountUseCase,
    private val getCategoryUseCase: GetCategoryUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val settingsRepository: com.suguru.expensetracker.domain.repository.SettingsRepository? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionDetailUiState(isLoading = true))
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        loadTransaction()
    }

    fun loadTransaction() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val tx = getTransactionUseCase(transactionId)
                if (tx == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Transaction not found"
                        )
                    }
                    return@launch
                }

                val sourceAccount = getAccountUseCase(tx.accountId)
                val destAccount = tx.destinationAccountId?.let { getAccountUseCase(it) }
                val category = tx.categoryId?.let { getCategoryUseCase(it) }

                val showCurrencyCodeSetting = try {
                    settingsRepository?.settings?.first()?.showCurrencyCode ?: false
                } catch (_: Exception) {
                    false
                }

                val datePref = try {
                    settingsRepository?.settings?.first()?.dateFormat ?: com.suguru.expensetracker.domain.model.DateFormatPreference.SYSTEM_DEFAULT
                } catch (_: Exception) {
                    com.suguru.expensetracker.domain.model.DateFormatPreference.SYSTEM_DEFAULT
                }

                val timePref = try {
                    settingsRepository?.settings?.first()?.timeFormat ?: com.suguru.expensetracker.domain.model.TimeFormatPreference.SYSTEM_DEFAULT
                } catch (_: Exception) {
                    com.suguru.expensetracker.domain.model.TimeFormatPreference.SYSTEM_DEFAULT
                }

                val amountFormatted = when (tx.type) {
                    TransactionType.EXPENSE -> "- ${MoneyParser.format(tx.amount, includeSymbol = true, useGrouping = true, showExplicitSign = false, showCurrencyCode = showCurrencyCodeSetting)}"
                    TransactionType.INCOME -> "+ ${MoneyParser.format(tx.amount, includeSymbol = true, useGrouping = true, showExplicitSign = false, showCurrencyCode = showCurrencyCodeSetting)}"
                    TransactionType.TRANSFER -> MoneyParser.format(tx.amount, includeSymbol = true, useGrouping = true, showExplicitSign = false, showCurrencyCode = showCurrencyCodeSetting)
                }

                val dateTimeFormatted = DateTimeFormatterHelper.formatFullDateTime(
                    instant = tx.transactionTime,
                    zoneId = zoneId,
                    datePref = datePref,
                    timePref = timePref
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transaction = tx,
                        type = tx.type,
                        amountFormatted = amountFormatted,
                        currencyCode = tx.amount.currencyCode,
                        sourceAccount = sourceAccount,
                        destinationAccount = destAccount,
                        category = category,
                        note = tx.note,
                        dateTimeFormatted = dateTimeFormatted,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load transaction details"
                    )
                }
            }
        }
    }

    fun deleteTransaction(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isDeleting || state.transaction == null) return

        _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                deleteTransactionUseCase(transactionId)
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        isDeletedSuccessfully = true,
                        errorMessage = null
                    )
                }
                onSuccess()
            } catch (e: DomainException) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = e.message ?: "Failed to delete transaction"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = e.localizedMessage ?: "An unexpected error occurred"
                    )
                }
            }
        }
    }
}
