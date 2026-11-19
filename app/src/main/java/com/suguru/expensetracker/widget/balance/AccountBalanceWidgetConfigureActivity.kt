package com.suguru.expensetracker.widget.balance

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suguru.expensetracker.ExpenseTrackerApplication
import com.suguru.expensetracker.MainActivity
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import kotlinx.coroutines.launch

class AccountBalanceWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED by default, so if the user backs out, the launcher deletes the widget.
        setResult(RESULT_CANCELED)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val appContainer = (application as ExpenseTrackerApplication).appContainer

        setContent {
            val scope = rememberCoroutineScope()
            
            val accountRepository = appContainer.accountRepository
            val widgetConfigurationRepository = appContainer.widgetConfigurationRepository
            val widgetRefreshCoordinator = appContainer.widgetRefreshCoordinator

            val accounts by accountRepository.observeActiveAccounts().collectAsState(initial = emptyList())

            var selectedAccount by remember { mutableStateOf<Account?>(null) }
            var privacyMode by remember { mutableStateOf(false) }

            ExpenseTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (accounts.isEmpty()) {
                        EmptyAccountsScreen(
                            onOpenApp = {
                                val intent = Intent(this@AccountBalanceWidgetConfigureActivity, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                                startActivity(intent)
                                finish()
                            }
                        )
                    } else {
                        // If selected account is not set yet, default to the first available active account
                        if (selectedAccount == null && accounts.isNotEmpty()) {
                            selectedAccount = accounts.first()
                        }

                        ConfigurationScreen(
                            accounts = accounts,
                            selectedAccount = selectedAccount,
                            privacyMode = privacyMode,
                            onAccountSelected = { selectedAccount = it },
                            onPrivacyModeChanged = { privacyMode = it },
                            onSave = {
                                val acc = selectedAccount
                                if (acc != null) {
                                    scope.launch {
                                        // 1. Save config per appWidgetId
                                        widgetConfigurationRepository.saveConfiguration(
                                            appWidgetId = appWidgetId,
                                            accountId = acc.id,
                                            privacyMode = privacyMode,
                                            workspaceKey = appContainer.workspaceId.storageKey
                                        )
                                        // 2. Refresh widget immediately
                                        widgetRefreshCoordinator.refreshAccountBalanceWidgets(acc.id)
                                        
                                        // 3. Return RESULT_OK with appWidgetId
                                        val resultValue = Intent().apply {
                                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                        }
                                        setResult(RESULT_OK, resultValue)
                                        finish()
                                    }
                                }
                            },
                            onCancel = {
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyAccountsScreen(onOpenApp: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("empty_accounts_screen"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.widget_config_no_accounts),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.widget_config_create_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onOpenApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_app_button")
            ) {
                Text(stringResource(R.string.widget_config_open_app))
            }
        }
    }

    @Composable
    private fun ConfigurationScreen(
        accounts: List<Account>,
        selectedAccount: Account?,
        privacyMode: Boolean,
        onAccountSelected: (Account) -> Unit,
        onPrivacyModeChanged: (Boolean) -> Unit,
        onSave: () -> Unit,
        onCancel: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("config_screen")
        ) {
            Text(
                text = stringResource(R.string.widget_config_account_balance_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = stringResource(R.string.widget_config_select_account),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Scrollable list of active accounts with Radio buttons
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("accounts_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts) { account ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAccountSelected(account) }
                            .testTag("account_card_${account.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedAccount?.id == account.id) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAccount?.id == account.id,
                                onClick = { onAccountSelected(account) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                val typeLabel = stringResource(
                                    when (account.type) {
                                        AccountType.CASH -> R.string.account_type_cash
                                        AccountType.BANK -> R.string.account_type_bank
                                        AccountType.CARD -> R.string.account_type_card
                                        AccountType.SAVINGS -> R.string.account_type_savings
                                        AccountType.INVESTMENT -> R.string.account_type_investment
                                        AccountType.OTHER -> R.string.account_type_other
                                    }
                                )
                                Text(
                                    text = "$typeLabel • ${account.initialBalance.currencyCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy Mode Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.widget_config_privacy_mode),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.widget_config_hide_balance),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = privacyMode,
                            onCheckedChange = onPrivacyModeChanged,
                            modifier = Modifier.testTag("privacy_mode_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.widget_config_visibility_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_cancel")
                ) {
                    Text(stringResource(R.string.widget_config_cancel))
                }
                Button(
                    onClick = onSave,
                    enabled = selectedAccount != null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_save")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.widget_config_add_widget))
                }
            }
        }
    }
}
