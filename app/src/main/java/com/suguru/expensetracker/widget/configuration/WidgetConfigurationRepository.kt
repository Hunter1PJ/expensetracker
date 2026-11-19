package com.suguru.expensetracker.widget.configuration

import kotlinx.coroutines.flow.Flow

data class AccountWidgetConfiguration(
    val appWidgetId: Int,
    val accountId: Long,
    val privacyMode: Boolean,
    val workspaceKey: String = "guest"
)

data class BudgetProgressWidgetConfiguration(
    val appWidgetId: Int,
    val budgetId: Long,
    val privacyMode: Boolean,
    val workspaceKey: String = "guest"
)

interface WidgetConfigurationRepository {
    fun getConfiguration(appWidgetId: Int): Flow<AccountWidgetConfiguration?>
    fun getAllConfigurations(): Flow<List<AccountWidgetConfiguration>>
    suspend fun saveConfiguration(
        appWidgetId: Int,
        accountId: Long,
        privacyMode: Boolean,
        workspaceKey: String = "guest"
    )
    suspend fun deleteConfiguration(appWidgetId: Int)

    fun getBudgetProgressConfiguration(appWidgetId: Int): Flow<BudgetProgressWidgetConfiguration?>
    fun getAllBudgetProgressConfigurations(): Flow<List<BudgetProgressWidgetConfiguration>>
    suspend fun saveBudgetProgressConfiguration(
        appWidgetId: Int,
        budgetId: Long,
        privacyMode: Boolean,
        workspaceKey: String = "guest"
    )
    suspend fun deleteBudgetProgressConfiguration(appWidgetId: Int)
}
