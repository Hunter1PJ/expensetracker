package com.suguru.expensetracker.widget.configuration

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import com.suguru.expensetracker.widget.common.WidgetFeature
import com.suguru.expensetracker.widget.common.WidgetGateResult

class QuickAddWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED by default
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
            val observeProEntitlementUseCase = appContainer.observeProEntitlementUseCase
            val widgetEntitlementPolicy = appContainer.widgetEntitlementPolicy

            val entitlement by observeProEntitlementUseCase().collectAsState(initial = ProEntitlement.Checking)
            var isBlockedByEntitlement by remember { mutableStateOf(false) }

            LaunchedEffect(entitlement) {
                if (entitlement !is ProEntitlement.Checking) {
                    val checkResult = widgetEntitlementPolicy.checkEntitlement(
                        feature = WidgetFeature.QUICK_ADD,
                        currentProEntitlement = entitlement,
                        currentConfiguredCount = 0
                    )
                    isBlockedByEntitlement = checkResult is WidgetGateResult.ProRequired
                }
            }

            ExpenseTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (entitlement is ProEntitlement.Checking) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (isBlockedByEntitlement) {
                        ProBlockScreen(
                            onViewPro = {
                                val intent = Intent(this@QuickAddWidgetConfigureActivity, MainActivity::class.java).apply {
                                    action = "com.suguru.expensetracker.action.NAVIGATE"
                                    putExtra("destination", "pro")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                                startActivity(intent)
                                // Do NOT finish here so that if the user purchases and presses back, they return to this activity and unlock!
                            },
                            onCancel = {
                                finish()
                            }
                        )
                    } else {
                        ConfigurationScreen(
                            onConfirm = {
                                val resultValue = Intent().apply {
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                }
                                setResult(RESULT_OK, resultValue)
                                finish()
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
                text = "Quick Add Widget",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ExpenseTracker Pro unlocks Quick Add widgets for faster transaction entry.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
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
    private fun ConfigurationScreen(
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("config_screen"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Add Quick Add Widget",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This widget provides quick launcher actions to log expenses and incomes directly from your home screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("btn_confirm")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm")
                }
            }
        }
    }
}
