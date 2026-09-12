package com.suguru.expensetracker.widget.budget

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
import com.suguru.expensetracker.domain.model.budget.BudgetProgress
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import com.suguru.expensetracker.widget.common.WidgetFeature
import com.suguru.expensetracker.widget.common.WidgetGateResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BudgetProgressWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            
            val budgetRepository = appContainer.budgetRepository
            val categoryRepository = appContainer.categoryRepository
            val widgetConfigurationRepository = appContainer.widgetConfigurationRepository
            val widgetEntitlementPolicy = appContainer.widgetEntitlementPolicy
            val widgetRefreshCoordinator = appContainer.widgetRefreshCoordinator
            val observeProEntitlementUseCase = appContainer.observeProEntitlementUseCase
            val observeActiveBudgetProgressUseCase = appContainer.observeActiveBudgetProgressUseCase

            val budgetProgressList by observeActiveBudgetProgressUseCase().collectAsState(initial = emptyList())
            val categories by categoryRepository.observeActiveCategories().collectAsState(initial = emptyList())
            val entitlement by observeProEntitlementUseCase().collectAsState(initial = ProEntitlement.Checking)

            var selectedBudgetProgress by remember { mutableStateOf<BudgetProgress?>(null) }
            var privacyMode by remember { mutableStateOf(false) }
            var isBlockedByEntitlement by remember { mutableStateOf(false) }

            val categoryMap = remember(categories) {
                categories.associate { it.id to it.name }
            }

            LaunchedEffect(entitlement) {
                if (entitlement !is ProEntitlement.Checking) {
                    val checkResult = widgetEntitlementPolicy.checkEntitlement(
                        feature = WidgetFeature.BUDGET_PROGRESS,
                        currentProEntitlement = entitlement,
                        currentConfiguredCount = 0
                    )
                    isBlockedByEntitlement = checkResult !is WidgetGateResult.Allowed
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
                                val intent = Intent(this@BudgetProgressWidgetConfigureActivity, MainActivity::class.java).apply {
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
                    } else if (budgetProgressList.isEmpty()) {
                        EmptyBudgetsScreen(
                            onOpenApp = {
                                val intent = Intent(this@BudgetProgressWidgetConfigureActivity, MainActivity::class.java).apply {
                                    action = "com.suguru.expensetracker.action.NAVIGATE"
                                    putExtra("destination", "budgets")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                                startActivity(intent)
                                finish()
                            }
                        )
                    } else {
                        if (selectedBudgetProgress == null && budgetProgressList.isNotEmpty()) {
                            selectedBudgetProgress = budgetProgressList.first()
                        }

                        ConfigurationScreen(
                            budgetProgressList = budgetProgressList,
                            categoryMap = categoryMap,
                            selectedBudgetProgress = selectedBudgetProgress,
                            privacyMode = privacyMode,
                            onBudgetSelected = { selectedBudgetProgress = it },
                            onPrivacyModeChanged = { privacyMode = it },
                            onSave = {
                                val progress = selectedBudgetProgress
                                if (progress != null) {
                                    scope.launch {
                                        widgetConfigurationRepository.saveBudgetProgressConfiguration(
                                            appWidgetId = appWidgetId,
                                            budgetId = progress.budget.id,
                                            privacyMode = privacyMode
                                        )
                                        widgetRefreshCoordinator.refreshBudgetWidgets(progress.budget.id)
                                        
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
                text = "Budget Progress Widget",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Budget widgets require ExpenseTracker Pro.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Upgrade to track budgets on your Home Screen.",
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
    private fun EmptyBudgetsScreen(onOpenApp: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("empty_budgets_screen"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No active budgets yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create an active budget in ExpenseTracker before adding this widget.",
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
                Text("Open Budgets")
            }
        }
    }

    @Composable
    private fun ConfigurationScreen(
        budgetProgressList: List<BudgetProgress>,
        categoryMap: Map<Long, String>,
        selectedBudgetProgress: BudgetProgress?,
        privacyMode: Boolean,
        onBudgetSelected: (BudgetProgress) -> Unit,
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
                text = "Track Budget Progress",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Select Active Budget",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("budgets_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(budgetProgressList) { progress ->
                    val categoryName = progress.budget.categoryId?.let { categoryMap[it] } ?: "Overall Budget"
                    val limitFormatted = MoneyParser.format(progress.budget.limitAmount, includeSymbol = true, useGrouping = true)
                    val periodText = progress.budget.periodType.name.lowercase().replaceFirstChar { it.uppercase() }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBudgetSelected(progress) }
                            .testTag("budget_card_${progress.budget.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedBudgetProgress?.budget?.id == progress.budget.id) {
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
                                selected = selectedBudgetProgress?.budget?.id == progress.budget.id,
                                onClick = { onBudgetSelected(progress) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Limit: $limitFormatted • $periodText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                                text = "Hide spending and limit numbers on the Home Screen.",
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
                    enabled = selectedBudgetProgress != null,
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
