package com.suguru.expensetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.key
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.presentation.navigation.ExpenseTrackerApp
import com.suguru.expensetracker.presentation.util.AppLanguageManager
import com.suguru.expensetracker.presentation.navigation.NavDestination
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(AppLanguageManager.wrap(newBase))
  }

  private val currentDestinationState = mutableStateOf<NavDestination?>(null)
  private val currentBudgetIdState = mutableStateOf<Long?>(null)
  private val currentTransactionTypeState = mutableStateOf<TransactionType?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleIntent(intent)
    val application = application as ExpenseTrackerApplication

    setContent {
      val requestedLanguageTag by AppLanguageManager.languageChanges.collectAsState(
        initial = AppLanguageManager.selectedLanguageTag(this)
      )
      val activeLanguageTag = requestedLanguageTag ?: AppLanguageManager.selectedLanguageTag(this)
      val localizedContext = remember(activeLanguageTag) {
        AppLanguageManager.wrap(this, activeLanguageTag)
      }
      CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration
      ) {
      val container by application.activeAppContainer.collectAsState()
      val prompt by application.guestImportPrompt.collectAsState()
      val migrationWarning by application.workspaceMigrationWarning.collectAsState()
      val destination by currentDestinationState
      val budgetId by currentBudgetIdState
      val transactionType by currentTransactionTypeState
      val scope = androidx.compose.runtime.rememberCoroutineScope()
      var migrationWarningDismissed by androidx.compose.runtime.remember { mutableStateOf(false) }
      var previouslyActiveWorkspace by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }

      LaunchedEffect(container?.workspaceId?.storageKey) {
        val newWorkspaceKey = container?.workspaceId?.storageKey ?: return@LaunchedEffect
        if (previouslyActiveWorkspace != null && previouslyActiveWorkspace != newWorkspaceKey) {
          currentDestinationState.value = null
          currentBudgetIdState.value = null
          currentTransactionTypeState.value = null
        }
        previouslyActiveWorkspace = newWorkspaceKey
      }

      if (container != null) {
        val activeContainer = container!!
        val settingsState = activeContainer.settingsRepository.settings.collectAsState(initial = null).value
        val darkTheme = when (settingsState?.themeMode) {
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
          else -> isSystemInDarkTheme()
        }
        key(activeContainer.workspaceId.storageKey) {
          ExpenseTrackerTheme(darkTheme = darkTheme) {
            ExpenseTrackerApp(
              appContainer = activeContainer,
              initialDestination = destination,
              initialBudgetId = budgetId,
              initialTransactionType = transactionType,
              onResetNavigation = {
                currentDestinationState.value = null
                currentBudgetIdState.value = null
                currentTransactionTypeState.value = null
              }
            )
          }
        }
      } else {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
          CircularProgressIndicator()
        }
      }

      prompt?.let { request ->
        AlertDialog(
          onDismissRequest = { },
          title = {
            Text(
              text = stringResource(R.string.guest_import_dialog_title),
              fontSize = 20.sp,
              fontWeight = FontWeight.SemiBold
            )
          },
          text = {
            Text(stringResource(R.string.guest_import_dialog_message))
          },
          confirmButton = {
            TextButton(onClick = {
              scope.launch { application.resolveGuestImport(request.uid, importGuestData = true) }
            }) { Text(stringResource(R.string.action_continue)) }
          },
          dismissButton = {
            TextButton(onClick = {
              scope.launch { application.resolveGuestImport(request.uid, importGuestData = false) }
            }) { Text(stringResource(R.string.action_not_now)) }
          }
        )
      } ?: if (migrationWarning && !migrationWarningDismissed) {
        AlertDialog(
          onDismissRequest = { },
          title = { Text(stringResource(R.string.migration_warning_title)) },
          text = {
            Text(stringResource(R.string.migration_warning_message))
          },
          confirmButton = {
            TextButton(onClick = { migrationWarningDismissed = true }) { Text(stringResource(R.string.action_ok)) }
          }
        )
      } else {
        Unit
      }
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
      val budgetId = intent.getLongExtra("budgetId", -1L).takeIf { it != -1L }
      val typeStr = intent.getStringExtra("transactionType")
      currentDestinationState.value = when (destStr) {
        "home" -> NavDestination.Home
        "add_transaction" -> NavDestination.AddTransaction
        "budgets" -> NavDestination.Budgets
        else -> null
      }
      currentBudgetIdState.value = budgetId
      currentTransactionTypeState.value = typeStr?.let {
        try { TransactionType.valueOf(it) } catch (_: Exception) { null }
      }
    }
  }
}
