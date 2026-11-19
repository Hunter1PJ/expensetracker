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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suguru.expensetracker.ExpenseTrackerApplication
import com.suguru.expensetracker.MainActivity
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.model.BudgetPeriodType
import com.suguru.expensetracker.domain.model.budget.BudgetProgress
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
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
            
            val categoryRepository = appContainer.categoryRepository
            val widgetConfigurationRepository = appContainer.widgetConfigurationRepository
            val widgetRefreshCoordinator = appContainer.widgetRefreshCoordinator
            val observeActiveBudgetProgressUseCase = appContainer.observeActiveBudgetProgressUseCase

            val budgetProgressList by observeActiveBudgetProgressUseCase().collectAsState(initial = emptyList())
            val categories by categoryRepository.observeActiveCategories().collectAsState(initial = emptyList())

            var selectedBudgetProgress by remember { mutableStateOf<BudgetProgress?>(null) }
            var privacyMode by remember { mutableStateOf(false) }

            val categoryMap = remember(categories) {
                categories.associate { it.id to it.name }
            }

            ExpenseTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (budgetProgressList.isEmpty()) {
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
                                            privacyMode = privacyMode,
                                            workspaceKey = appContainer.workspaceId.storageKey
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
                text = stringResource(R.string.widget_config_no_budgets),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.widget_config_create_budget),
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
                Text(stringResource(R.string.widget_config_open_budgets))
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
                text = stringResource(R.string.widget_config_budget_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = stringResource(R.string.widget_config_select_budget),
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
                    val categoryName = progress.budget.categoryId?.let { categoryMap[it] } ?: stringResource(R.string.widget_config_overall_budget)
                    val limitFormatted = MoneyParser.format(progress.budget.limitAmount, includeSymbol = true, useGrouping = true)
                    val periodText = stringResource(
                        when (progress.budget.periodType) {
                            BudgetPeriodType.MONTHLY -> R.string.budget_period_monthly
                            BudgetPeriodType.WEEKLY -> R.string.budget_period_weekly
                            BudgetPeriodType.YEARLY -> R.string.budget_period_yearly
                            BudgetPeriodType.CUSTOM -> R.string.budget_period_custom
                        }
                    )

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
                                    text = stringResource(R.string.widget_config_limit_period, limitFormatted, periodText),
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
                                text = stringResource(R.string.widget_config_privacy_mode),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.widget_config_hide_spending),
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
                    enabled = selectedBudgetProgress != null,
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
