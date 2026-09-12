package com.suguru.expensetracker.widget.common

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.suguru.expensetracker.widget.balance.AccountBalanceWidget
import com.suguru.expensetracker.widget.configuration.WidgetConfigurationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

interface WidgetRefreshCoordinator {
    fun refreshAll()
    fun refreshAccountBalanceWidgets(accountId: Long)
    fun refreshBudgetWidgets(budgetId: Long)
}

class GlanceWidgetRefreshCoordinator(
    private val context: Context,
    private val widgetConfigurationRepository: WidgetConfigurationRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : WidgetRefreshCoordinator {

    override fun refreshAll() {
        coroutineScope.launch {
            try {
                AccountBalanceWidget().updateAll(context)
            } catch (_: Exception) {
            }
            try {
                com.suguru.expensetracker.widget.budget.BudgetProgressWidget().updateAll(context)
            } catch (_: Exception) {
            }
        }
    }

    override fun refreshAccountBalanceWidgets(accountId: Long) {
        coroutineScope.launch {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(AccountBalanceWidget::class.java)
                val configs = widgetConfigurationRepository.getAllConfigurations().first()
                
                for (glanceId in glanceIds) {
                    val appWidgetId = manager.getAppWidgetId(glanceId)
                    val config = configs.find { it.appWidgetId == appWidgetId }
                    if (config?.accountId == accountId) {
                        AccountBalanceWidget().update(context, glanceId)
                    }
                }
            } catch (_: Exception) {
                refreshAll()
            }
        }
    }

    override fun refreshBudgetWidgets(budgetId: Long) {
        coroutineScope.launch {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(com.suguru.expensetracker.widget.budget.BudgetProgressWidget::class.java)
                val configs = widgetConfigurationRepository.getAllBudgetProgressConfigurations().first()
                
                for (glanceId in glanceIds) {
                    val appWidgetId = manager.getAppWidgetId(glanceId)
                    val config = configs.find { it.appWidgetId == appWidgetId }
                    if (config?.budgetId == budgetId) {
                        com.suguru.expensetracker.widget.budget.BudgetProgressWidget().update(context, glanceId)
                    }
                }
            } catch (_: Exception) {
                refreshAll()
            }
        }
    }
}
