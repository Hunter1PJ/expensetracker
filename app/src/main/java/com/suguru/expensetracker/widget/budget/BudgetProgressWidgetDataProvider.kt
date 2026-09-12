package com.suguru.expensetracker.widget.budget

import com.suguru.expensetracker.domain.repository.BudgetRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.usecase.budget.ObserveActiveBudgetProgressUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.domain.model.BudgetPeriodType
import com.suguru.expensetracker.widget.configuration.WidgetConfigurationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class BudgetProgressWidgetDataProvider(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val observeActiveBudgetProgressUseCase: ObserveActiveBudgetProgressUseCase,
    private val widgetConfigurationRepository: WidgetConfigurationRepository
) {
    fun getWidgetData(appWidgetId: Int): Flow<BudgetProgressWidgetData> = flow {
        emit(BudgetProgressWidgetData.loading())

        val config = widgetConfigurationRepository.getBudgetProgressConfiguration(appWidgetId).first()
        if (config == null) {
            emit(BudgetProgressWidgetData.configurationRequired())
            return@flow
        }

        try {
            val budget = budgetRepository.getBudgetById(config.budgetId)
            if (budget == null) {
                emit(BudgetProgressWidgetData.unavailable())
                return@flow
            }

            val category = budget.categoryId?.let { categoryRepository.getCategoryById(it) }
            val budgetTitle = category?.name ?: "Overall Budget"

            if (!budget.isActive) {
                emit(BudgetProgressWidgetData.inactive(budgetTitle))
                return@flow
            }

            val progressList = observeActiveBudgetProgressUseCase().first()
            val progress = progressList.find { it.budget.id == budget.id }

            if (progress == null) {
                val spentFormatted = if (config.privacyMode) "Amounts Hidden" else MoneyParser.format(com.suguru.expensetracker.domain.model.Money(0L, budget.limitAmount.currencyCode), includeSymbol = true, useGrouping = true)
                val limitFormatted = if (config.privacyMode) "Amounts Hidden" else MoneyParser.format(budget.limitAmount, includeSymbol = true, useGrouping = true)
                val remainingFormatted = if (config.privacyMode) "Amounts Hidden" else MoneyParser.format(budget.limitAmount, includeSymbol = true, useGrouping = true)

                emit(
                    BudgetProgressWidgetData(
                        budgetId = budget.id,
                        title = budgetTitle,
                        spentFormatted = spentFormatted,
                        limitFormatted = limitFormatted,
                        remainingFormatted = remainingFormatted,
                        progressBasisPoints = 0,
                        progressLabel = "0%",
                        isExceeded = false,
                        periodLabel = formatPeriodType(budget.periodType),
                        privacyMode = config.privacyMode,
                        state = BudgetWidgetState.READY
                    )
                )
            } else {
                val percentage = progress.progressBasisPoints / 100
                val progressLabel = "$percentage%"

                val spentFormatted = if (config.privacyMode) "Amounts Hidden" else MoneyParser.format(progress.spent, includeSymbol = true, useGrouping = true)
                val limitFormatted = if (config.privacyMode) "Amounts Hidden" else MoneyParser.format(progress.budget.limitAmount, includeSymbol = true, useGrouping = true)
                val remainingFormatted = if (config.privacyMode) "Amounts Hidden" else MoneyParser.format(progress.remaining, includeSymbol = true, useGrouping = true)

                emit(
                    BudgetProgressWidgetData(
                        budgetId = budget.id,
                        title = budgetTitle,
                        spentFormatted = spentFormatted,
                        limitFormatted = limitFormatted,
                        remainingFormatted = remainingFormatted,
                        progressBasisPoints = progress.progressBasisPoints,
                        progressLabel = progressLabel,
                        isExceeded = progress.isExceeded,
                        periodLabel = formatPeriodType(budget.periodType),
                        privacyMode = config.privacyMode,
                        state = BudgetWidgetState.READY
                    )
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            }
            emit(BudgetProgressWidgetData.error(e.message ?: "Unknown error"))
        }
    }

    private fun formatPeriodType(type: BudgetPeriodType): String {
        return when (type) {
            BudgetPeriodType.WEEKLY -> "Weekly"
            BudgetPeriodType.MONTHLY -> "Monthly"
            BudgetPeriodType.YEARLY -> "Yearly"
            BudgetPeriodType.CUSTOM -> "Custom"
        }
    }
}
