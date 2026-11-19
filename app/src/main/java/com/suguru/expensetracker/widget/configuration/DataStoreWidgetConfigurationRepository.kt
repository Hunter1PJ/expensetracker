package com.suguru.expensetracker.widget.configuration

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.widgetDataStore by preferencesDataStore(name = "expense_tracker_widgets")

class DataStoreWidgetConfigurationRepository(private val context: Context) : WidgetConfigurationRepository {
    companion object {
        fun accountIdKey(appWidgetId: Int) = longPreferencesKey("widget_${appWidgetId}_account_id")
        fun privacyModeKey(appWidgetId: Int) = booleanPreferencesKey("widget_${appWidgetId}_privacy_mode")
        fun workspaceKey(appWidgetId: Int) = stringPreferencesKey("widget_${appWidgetId}_workspace")

        fun budgetIdKey(appWidgetId: Int) = longPreferencesKey("widget_${appWidgetId}_budget_id")
        fun budgetPrivacyModeKey(appWidgetId: Int) = booleanPreferencesKey("widget_${appWidgetId}_budget_privacy_mode")
        fun budgetWorkspaceKey(appWidgetId: Int) = stringPreferencesKey("widget_${appWidgetId}_budget_workspace")
    }

    override fun getConfiguration(appWidgetId: Int): Flow<AccountWidgetConfiguration?> =
        context.widgetDataStore.data.map { preferences ->
            val accountId = preferences[accountIdKey(appWidgetId)]
            if (accountId == null) null else AccountWidgetConfiguration(
                appWidgetId = appWidgetId,
                accountId = accountId,
                privacyMode = preferences[privacyModeKey(appWidgetId)] ?: false,
                workspaceKey = preferences[workspaceKey(appWidgetId)] ?: "guest"
            )
        }

    override fun getAllConfigurations(): Flow<List<AccountWidgetConfiguration>> =
        context.widgetDataStore.data.map { preferences ->
            preferences.asMap().mapNotNull { (key, value) ->
                if (!key.name.startsWith("widget_") || !key.name.endsWith("_account_id")) return@mapNotNull null
                runCatching {
                    val id = key.name.substringAfter("widget_").substringBefore("_account_id").toInt()
                    val accountId = value as? Long ?: return@runCatching null
                    AccountWidgetConfiguration(
                        id,
                        accountId,
                        preferences[privacyModeKey(id)] ?: false,
                        preferences[workspaceKey(id)] ?: "guest"
                    )
                }.getOrNull()
            }
        }

    override suspend fun saveConfiguration(
        appWidgetId: Int,
        accountId: Long,
        privacyMode: Boolean,
        workspaceKey: String
    ) {
        context.widgetDataStore.edit { preferences ->
            preferences[accountIdKey(appWidgetId)] = accountId
            preferences[privacyModeKey(appWidgetId)] = privacyMode
            preferences[workspaceKey(appWidgetId)] = workspaceKey
        }
    }

    override suspend fun deleteConfiguration(appWidgetId: Int) {
        context.widgetDataStore.edit { preferences ->
            preferences.remove(accountIdKey(appWidgetId))
            preferences.remove(privacyModeKey(appWidgetId))
            preferences.remove(workspaceKey(appWidgetId))
        }
    }

    override fun getBudgetProgressConfiguration(appWidgetId: Int): Flow<BudgetProgressWidgetConfiguration?> =
        context.widgetDataStore.data.map { preferences ->
            val budgetId = preferences[budgetIdKey(appWidgetId)]
            if (budgetId == null) null else BudgetProgressWidgetConfiguration(
                appWidgetId = appWidgetId,
                budgetId = budgetId,
                privacyMode = preferences[budgetPrivacyModeKey(appWidgetId)] ?: false,
                workspaceKey = preferences[budgetWorkspaceKey(appWidgetId)] ?: "guest"
            )
        }

    override fun getAllBudgetProgressConfigurations(): Flow<List<BudgetProgressWidgetConfiguration>> =
        context.widgetDataStore.data.map { preferences ->
            preferences.asMap().mapNotNull { (key, value) ->
                if (!key.name.startsWith("widget_") || !key.name.endsWith("_budget_id")) return@mapNotNull null
                runCatching {
                    val id = key.name.substringAfter("widget_").substringBefore("_budget_id").toInt()
                    val budgetId = value as? Long ?: return@runCatching null
                    BudgetProgressWidgetConfiguration(
                        id,
                        budgetId,
                        preferences[budgetPrivacyModeKey(id)] ?: false,
                        preferences[budgetWorkspaceKey(id)] ?: "guest"
                    )
                }.getOrNull()
            }
        }

    override suspend fun saveBudgetProgressConfiguration(
        appWidgetId: Int,
        budgetId: Long,
        privacyMode: Boolean,
        workspaceKey: String
    ) {
        context.widgetDataStore.edit { preferences ->
            preferences[budgetIdKey(appWidgetId)] = budgetId
            preferences[budgetPrivacyModeKey(appWidgetId)] = privacyMode
            preferences[budgetWorkspaceKey(appWidgetId)] = workspaceKey
        }
    }

    override suspend fun deleteBudgetProgressConfiguration(appWidgetId: Int) {
        context.widgetDataStore.edit { preferences ->
            preferences.remove(budgetIdKey(appWidgetId))
            preferences.remove(budgetPrivacyModeKey(appWidgetId))
            preferences.remove(budgetWorkspaceKey(appWidgetId))
        }
    }
}
