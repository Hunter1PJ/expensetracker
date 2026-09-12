package com.suguru.expensetracker.widget.quickadd

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.suguru.expensetracker.MainActivity

class QuickAddWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }

    @Composable
    private fun WidgetContent() {
        val context = LocalContext.current
        val size = LocalSize.current
        val width = size.width
        val height = size.height

        // Define colors matching ExpenseTracker design
        val bgProvider = ColorProvider(Color(0xFF131118))
        val cardBgProvider = ColorProvider(Color(0xFF232128))
        val textPrimary = ColorProvider(Color(0xFFE6E1E5))
        val textSecondary = ColorProvider(Color(0xFFCAC4D0))
        val expenseColor = ColorProvider(Color(0xFFF2B8B5)) // Soft red for Expense
        val incomeColor = ColorProvider(Color(0xFFC4E7C4))  // Soft green for Income
        val accentColor = ColorProvider(Color(0xFFD0BCFF))  // Soft purple

        // Create deep-link actions
        val expenseAction = createNavigateAction(context, "EXPENSE")
        val incomeAction = createNavigateAction(context, "INCOME")

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgProvider)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val isLarge = width >= 220.dp && height >= 110.dp
            val isHorizontal = width >= 150.dp && height < 110.dp
            val isVertical = width < 150.dp && height >= 110.dp

            if (isLarge) {
                // Large expanded panel
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .width(3.dp)
                                .height(14.dp)
                                .background(accentColor)
                        ) {}
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = "Quick Add Transaction",
                            style = TextStyle(
                                color = textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Expense Button
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .height(56.dp)
                                .background(cardBgProvider)
                                .padding(8.dp)
                                .clickable(expenseAction),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column {
                                Text(
                                    text = "+ Expense",
                                    style = TextStyle(
                                        color = expenseColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = "Log expense",
                                    style = TextStyle(
                                        color = textSecondary,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        // Income Button
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .height(56.dp)
                                .background(cardBgProvider)
                                .padding(8.dp)
                                .clickable(incomeAction),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column {
                                Text(
                                    text = "+ Income",
                                    style = TextStyle(
                                        color = incomeColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = "Log income",
                                    style = TextStyle(
                                        color = textSecondary,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }
                }
            } else if (isHorizontal) {
                // Compact horizontal (2x1)
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .background(cardBgProvider)
                            .padding(8.dp)
                            .clickable(expenseAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Expense",
                            style = TextStyle(
                                color = expenseColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .background(cardBgProvider)
                            .padding(8.dp)
                            .clickable(incomeAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Income",
                            style = TextStyle(
                                color = incomeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            } else if (isVertical) {
                // Compact vertical (1x2)
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .background(cardBgProvider)
                            .padding(8.dp)
                            .clickable(expenseAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Expense",
                            style = TextStyle(
                                color = expenseColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .background(cardBgProvider)
                            .padding(8.dp)
                            .clickable(incomeAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Income",
                            style = TextStyle(
                                color = incomeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            } else {
                // Small (1x1) mini dual-stack or single action
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .background(cardBgProvider)
                            .padding(4.dp)
                            .clickable(expenseAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Exp",
                            style = TextStyle(
                                color = expenseColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .background(cardBgProvider)
                            .padding(4.dp)
                            .clickable(incomeAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Inc",
                            style = TextStyle(
                                color = incomeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }

    private fun createNavigateAction(context: Context, type: String): Action {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.suguru.expensetracker.action.NAVIGATE"
            putExtra("destination", "add_transaction")
            putExtra("transactionType", type)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            data = android.net.Uri.parse("expensetracker://navigate/add_transaction?type=$type")
        }
        return actionStartActivity(intent)
    }
}
