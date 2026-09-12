package com.suguru.expensetracker.widget.balance

import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.usecase.balance.GetAccountBalanceUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.widget.configuration.WidgetConfigurationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class AccountBalanceWidgetDataProvider(
    private val accountRepository: AccountRepository,
    private val getAccountBalanceUseCase: GetAccountBalanceUseCase,
    private val widgetConfigurationRepository: WidgetConfigurationRepository
) {
    fun getWidgetData(appWidgetId: Int): Flow<AccountBalanceWidgetData> = flow {
        emit(AccountBalanceWidgetData.loading())

        val config = widgetConfigurationRepository.getConfiguration(appWidgetId).first()
        if (config == null) {
            emit(AccountBalanceWidgetData.configurationRequired())
            return@flow
        }

        try {
            val account = accountRepository.getAccountById(config.accountId)
            if (account == null) {
                emit(AccountBalanceWidgetData.unavailable(config.accountId))
                return@flow
            }

            val balance = getAccountBalanceUseCase(config.accountId)
            val formatted = if (config.privacyMode) {
                "••••••"
            } else {
                MoneyParser.format(balance, includeSymbol = true, useGrouping = true)
            }

            emit(
                AccountBalanceWidgetData(
                    accountId = config.accountId,
                    accountName = account.name,
                    formattedBalance = formatted,
                    currencyCode = balance.currencyCode,
                    privacyMode = config.privacyMode,
                    state = WidgetDataState.READY
                )
            )
        } catch (_: Exception) {
            emit(AccountBalanceWidgetData.error(config.accountId))
        }
    }
}
