package com.suguru.expensetracker.widget.budget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.suguru.expensetracker.ExpenseTrackerApplication
import com.suguru.expensetracker.MainActivity

class BudgetProgressWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContainer = (context.applicationContext as ExpenseTrackerApplication).appContainer
        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = manager.getAppWidgetId(id)
        val dataProvider = appContainer.budgetProgressWidgetDataProvider

        provideContent {
            val widgetData by dataProvider.getWidgetData(appWidgetId).collectAsState(
                initial = BudgetProgressWidgetData.loading()
            )
            WidgetContent(widgetData)
        }
    }

    @Composable
    private fun WidgetContent(data: BudgetProgressWidgetData) {
        val context = LocalContext.current
        val size = LocalSize.current
        val isSmall = size.width < 180.dp || size.height < 120.dp
        val isLarge = size.width >= 260.dp

        // Premium dark/neutral styling matching ExpenseTracker aesthetics
        val bgProvider = ColorProvider(Color(0xFF131118))
        val textPrimary = ColorProvider(Color(0xFFE6E1E5))
        val textSecondary = ColorProvider(Color(0xFFCAC4D0))
        val accentColor = ColorProvider(Color(0xFFD0BCFF))
        val redColor = ColorProvider(Color(0xFFEF4444))

        val clickAction = createNavigateAction(context, data)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgProvider)
                .padding(12.dp)
                .clickable(clickAction),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (data.state) {
                    BudgetWidgetState.LOADING -> {
                        Text(
                            text = "Loading budget...",
                            style = TextStyle(
                                color = textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    BudgetWidgetState.CONFIGURATION_REQUIRED -> {
                        Text(
                            text = "Track Budget (Pro)",
                            style = TextStyle(
                                color = accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "Tap to choose active budget",
                            style = TextStyle(
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    BudgetWidgetState.BUDGET_UNAVAILABLE -> {
                        Text(
                            text = "Budget Unavailable",
                            style = TextStyle(
                                color = redColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "Tap to reconfigure or open",
                            style = TextStyle(
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    BudgetWidgetState.INACTIVE -> {
                        Text(
                            text = "Budget Inactive",
                            style = TextStyle(
                                color = textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "Tap to open Budgets",
                            style = TextStyle(
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    BudgetWidgetState.ERROR -> {
                        Text(
                            text = "Error Loading Budget",
                            style = TextStyle(
                                color = redColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = data.errorMessage ?: "Tap to retry",
                            style = TextStyle(
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    BudgetWidgetState.READY -> {
                        // Title row
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .width(3.dp)
                                    .height(14.dp)
                                    .background(if (data.isExceeded) redColor else accentColor)
                            ) {}
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Text(
                                text = data.title,
                                style = TextStyle(
                                    color = textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            if (isSmall) {
                                Spacer(modifier = GlanceModifier.width(4.dp))
                                Text(
                                    text = "• ${data.progressLabel}",
                                    style = TextStyle(
                                        color = if (data.isExceeded) redColor else accentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        // Financial values line (Hidden in Small layout)
                        if (!isSmall) {
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            val spentText = if (data.privacyMode) "Amounts Hidden" else "${data.spentFormatted} of ${data.limitFormatted}"
                            Text(
                                text = spentText,
                                style = TextStyle(
                                    color = textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(if (isSmall) 6.dp else 8.dp))

                        // Segmented Progress Bar (10 premium segments)
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val progressPercent = (data.progressBasisPoints / 1000) // 0 to 10
                            val barColor = if (data.isExceeded) Color(0xFFEF4444) else Color(0xFFD0BCFF)
                            val inactiveColor = Color(0xFF33303E)

                            for (i in 0 until 10) {
                                val isFilled = progressPercent > i
                                Box(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .fillMaxHeight()
                                        .background(if (isFilled) barColor else inactiveColor)
                                ) {}
                                if (i < 9) {
                                    Spacer(modifier = GlanceModifier.width(2.dp))
                                }
                            }
                        }

                        Spacer(modifier = GlanceModifier.height(if (isSmall) 4.dp else 6.dp))

                        // Status Row
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val statusText = if (data.isExceeded) "Exceeded" else "On Track"
                            val statusColor = if (data.isExceeded) redColor else accentColor

                            Text(
                                text = if (isSmall) statusText else "${data.progressLabel} spent • $statusText",
                                style = TextStyle(
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )

                            if (isLarge && !isSmall) {
                                Spacer(modifier = GlanceModifier.defaultWeight())
                                if (data.periodLabel != null) {
                                    Text(
                                        text = data.periodLabel,
                                        style = TextStyle(
                                            color = textSecondary,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                                if (data.privacyMode) {
                                    Spacer(modifier = GlanceModifier.width(6.dp))
                                    Text(
                                        text = "Private",
                                        style = TextStyle(
                                            color = textSecondary,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createNavigateAction(context: Context, data: BudgetProgressWidgetData): Action {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.suguru.expensetracker.action.NAVIGATE"
            putExtra("destination", "budgets")
            putExtra("budgetId", data.budgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        return actionStartActivity(intent)
    }
}
