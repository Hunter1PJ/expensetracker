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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suguru.expensetracker.ExpenseTrackerApplication
import com.suguru.expensetracker.MainActivity
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import com.suguru.expensetracker.widget.common.WidgetFeature
import com.suguru.expensetracker.widget.common.WidgetGateResult
import kotlinx.coroutines.flow.first
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
            
            // Gather dependencies from our manual DI container
            val accountRepository = appContainer.accountRepository
            val widgetConfigurationRepository = appContainer.widgetConfigurationRepository
            val widgetEntitlementPolicy = appContainer.widgetEntitlementPolicy
            val widgetRefreshCoordinator = appContainer.widgetRefreshCoordinator
            val observeProEntitlementUseCase = appContainer.observeProEntitlementUseCase

            // Core state collection
            val accounts by accountRepository.observeActiveAccounts().collectAsState(initial = emptyList())
            val entitlement by observeProEntitlementUseCase().collectAsState(initial = ProEntitlement.Checking)

            var selectedAccount by remember { mutableStateOf<Account?>(null) }
            var privacyMode by remember { mutableStateOf(false) }
            var isBlockedByEntitlement by remember { mutableStateOf(false) }

            // Check if adding this widget violates Free tier limit (Free tier only allows 1 widget)
            LaunchedEffect(entitlement) {
                if (entitlement !is ProEntitlement.Checking) {
                    val currentConfigs = widgetConfigurationRepository.getAllConfigurations().first()
                    // Filter out current widget if it's already configured (reconfiguration is always allowed)
                    val otherConfigs = currentConfigs.filter { it.appWidgetId != appWidgetId }
                    val checkResult = widgetEntitlementPolicy.checkEntitlement(
                        feature = WidgetFeature.ACCOUNT_BALANCE,
                        currentProEntitlement = entitlement,
                        currentConfiguredCount = otherConfigs.size
                    )
                    isBlockedByEntitlement = checkResult is WidgetGateResult.LimitReached
                }
            }

            ExpenseTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isBlockedByEntitlement) {
                        ProBlockScreen(
                            onViewPro = {
                                val intent = Intent(this@AccountBalanceWidgetConfigureActivity, MainActivity::class.java).apply {
                                    action = "com.suguru.expensetracker.action.NAVIGATE"
                                    putExtra("destination", "pro")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                                startActivity(intent)
                                finish()
                            },
                            onCancel = {
                                finish()
                            }
                        )
                    } else if (accounts.isEmpty()) {
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
                                            privacyMode = privacyMode
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
    private fun ProBlockScreen(
        onViewPro: () -> Unit,
        onCancel: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("pro_block_screen"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Account Balance Widget",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Free tier includes 1 Account Balance widget.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "ExpenseTracker Pro unlocks unlimited widgets.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onViewPro,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("view_pro_button")
            ) {
                Text("View Pro")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cancel_button")
            ) {
                Text("Cancel")
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
                text = "No accounts yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create an account in ExpenseTracker before adding this widget.",
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
                Text("Open ExpenseTracker")
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
                text = "Add Account Balance Widget",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Select Account",
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
                                Text(
                                    text = "${account.type.name} • ${account.initialBalance.currencyCode}",
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
                                text = "Privacy Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hide the balance amount on your Home Screen.",
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
                        text = "Home Screen widgets may be visible to anyone using your unlocked device.",
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
                    Text("Cancel")
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
                    Text("Add Widget")
                }
            }
        }
    }
}
