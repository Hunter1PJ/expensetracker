package com.suguru.expensetracker.widget.configuration

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.widgetDataStore by preferencesDataStore(name = "expense_tracker_widgets")

class DataStoreWidgetConfigurationRepository(private val context: Context) : WidgetConfigurationRepository {

    companion object {
        fun accountIdKey(appWidgetId: Int) = longPreferencesKey("widget_${appWidgetId}_account_id")
        fun privacyModeKey(appWidgetId: Int) = booleanPreferencesKey("widget_${appWidgetId}_privacy_mode")

        fun budgetIdKey(appWidgetId: Int) = longPreferencesKey("widget_${appWidgetId}_budget_id")
        fun budgetPrivacyModeKey(appWidgetId: Int) = booleanPreferencesKey("widget_${appWidgetId}_budget_privacy_mode")
    }

    override fun getConfiguration(appWidgetId: Int): Flow<AccountWidgetConfiguration?> {
        return context.widgetDataStore.data.map { preferences ->
            val accountId = preferences[accountIdKey(appWidgetId)]
            val privacyMode = preferences[privacyModeKey(appWidgetId)] ?: false
            if (accountId != null) {
                AccountWidgetConfiguration(appWidgetId, accountId, privacyMode)
            } else {
                null
            }
        }
    }

    override fun getAllConfigurations(): Flow<List<AccountWidgetConfiguration>> {
        return context.widgetDataStore.data.map { preferences ->
            val configs = mutableListOf<AccountWidgetConfiguration>()
            preferences.asMap().forEach { (key, value) ->
                if (key.name.startsWith("widget_") && key.name.endsWith("_account_id")) {
                    try {
                        val widgetIdStr = key.name.substringAfter("widget_").substringBefore("_account_id")
                        val widgetId = widgetIdStr.toInt()
                        val accountId = value as? Long
                        if (accountId != null) {
                            val privacyMode = preferences[privacyModeKey(widgetId)] ?: false
                            configs.add(AccountWidgetConfiguration(widgetId, accountId, privacyMode))
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            configs
        }
    }

    override suspend fun saveConfiguration(appWidgetId: Int, accountId: Long, privacyMode: Boolean) {
        context.widgetDataStore.edit { preferences ->
            preferences[accountIdKey(appWidgetId)] = accountId
            preferences[privacyModeKey(appWidgetId)] = privacyMode
        }
    }

    override suspend fun deleteConfiguration(appWidgetId: Int) {
        context.widgetDataStore.edit { preferences ->
            preferences.remove(accountIdKey(appWidgetId))
            preferences.remove(privacyModeKey(appWidgetId))
        }
    }

    override fun getBudgetProgressConfiguration(appWidgetId: Int): Flow<BudgetProgressWidgetConfiguration?> {
        return context.widgetDataStore.data.map { preferences ->
            val budgetId = preferences[budgetIdKey(appWidgetId)]
            val privacyMode = preferences[budgetPrivacyModeKey(appWidgetId)] ?: false
            if (budgetId != null) {
                BudgetProgressWidgetConfiguration(appWidgetId, budgetId, privacyMode)
            } else {
                null
            }
        }
    }

    override fun getAllBudgetProgressConfigurations(): Flow<List<BudgetProgressWidgetConfiguration>> {
        return context.widgetDataStore.data.map { preferences ->
            val configs = mutableListOf<BudgetProgressWidgetConfiguration>()
            preferences.asMap().forEach { (key, value) ->
                if (key.name.startsWith("widget_") && key.name.endsWith("_budget_id")) {
                    try {
                        val widgetIdStr = key.name.substringAfter("widget_").substringBefore("_budget_id")
                        val widgetId = widgetIdStr.toInt()
                        val budgetId = value as? Long
                        if (budgetId != null) {
                            val privacyMode = preferences[budgetPrivacyModeKey(widgetId)] ?: false
                            configs.add(BudgetProgressWidgetConfiguration(widgetId, budgetId, privacyMode))
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            configs
        }
    }

    override suspend fun saveBudgetProgressConfiguration(appWidgetId: Int, budgetId: Long, privacyMode: Boolean) {
        context.widgetDataStore.edit { preferences ->
            preferences[budgetIdKey(appWidgetId)] = budgetId
            preferences[budgetPrivacyModeKey(appWidgetId)] = privacyMode
        }
    }

    override suspend fun deleteBudgetProgressConfiguration(appWidgetId: Int) {
        context.widgetDataStore.edit { preferences ->
            preferences.remove(budgetIdKey(appWidgetId))
            preferences.remove(budgetPrivacyModeKey(appWidgetId))
        }
    }
}
