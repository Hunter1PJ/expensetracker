package com.suguru.expensetracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.presentation.navigation.ExpenseTrackerApp
import com.suguru.expensetracker.presentation.navigation.NavDestination
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {
  private val currentDestinationState = mutableStateOf<NavDestination?>(null)
  private val currentAccountIdState = mutableStateOf<Long?>(null)
  private val currentBudgetIdState = mutableStateOf<Long?>(null)
  private val currentTransactionTypeState = mutableStateOf<TransactionType?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleIntent(intent)

    val appContainer = (application as ExpenseTrackerApplication).appContainer
    setContent {
      val settingsState = appContainer.settingsRepository.settings.collectAsState(initial = null).value
      val darkTheme = when (settingsState?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
      }
      val dest by currentDestinationState
      val accId by currentAccountIdState
      val budgetId by currentBudgetIdState
      val txType by currentTransactionTypeState

      ExpenseTrackerTheme(darkTheme = darkTheme) {
        ExpenseTrackerApp(
          appContainer = appContainer,
          initialDestination = dest,
          initialAccountId = accId,
          initialBudgetId = budgetId,
          initialTransactionType = txType,
          onResetNavigation = {
            currentDestinationState.value = null
            currentAccountIdState.value = null
            currentBudgetIdState.value = null
            currentTransactionTypeState.value = null
          }
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    if (intent?.action == "com.suguru.expensetracker.action.NAVIGATE") {
      val destStr = intent.getStringExtra("destination")
      val accId = intent.getLongExtra("accountId", -1L).takeIf { it != -1L }
      val budgetId = intent.getLongExtra("budgetId", -1L).takeIf { it != -1L }
      val typeStr = intent.getStringExtra("transactionType")
      currentDestinationState.value = when (destStr) {
        "account_management" -> NavDestination.AccountManagement
        "pro" -> NavDestination.Pro
        "add_transaction" -> NavDestination.AddTransaction
        "budgets" -> NavDestination.Budgets
        else -> null
      }
      currentAccountIdState.value = accId
      currentBudgetIdState.value = budgetId
      currentTransactionTypeState.value = typeStr?.let {
        try {
          TransactionType.valueOf(it)
        } catch (_: Exception) {
          null
        }
      }
    }
  }
}
