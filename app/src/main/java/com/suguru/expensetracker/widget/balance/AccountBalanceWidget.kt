package com.suguru.expensetracker.widget.balance

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

class AccountBalanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContainer = (context.applicationContext as ExpenseTrackerApplication).appContainer
        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = manager.getAppWidgetId(id)
        val dataProvider = appContainer.accountBalanceWidgetDataProvider

        provideContent {
            val widgetData by dataProvider.getWidgetData(appWidgetId).collectAsState(
                initial = AccountBalanceWidgetData.loading()
            )
            WidgetContent(widgetData)
        }
    }

    @Composable
    private fun WidgetContent(data: AccountBalanceWidgetData) {
        val context = LocalContext.current
        val size = LocalSize.current
        val isSmall = size.width < 180.dp

        // Premium dark/neutral styling that looks superb on all wallpaper backgrounds
        val bgProvider = ColorProvider(Color(0xFF131118))
        val textPrimary = ColorProvider(Color(0xFFE6E1E5))
        val textSecondary = ColorProvider(Color(0xFFCAC4D0))
        val accentColor = ColorProvider(Color(0xFFD0BCFF))

        // Navigation click action
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
                    WidgetDataState.LOADING -> {
                        Text(
                            text = "Loading...",
                            style = TextStyle(
                                color = textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    WidgetDataState.CONFIGURATION_REQUIRED -> {
                        Text(
                            text = "Set up Widget",
                            style = TextStyle(
                                color = accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "Tap to select account",
                            style = TextStyle(
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    WidgetDataState.ACCOUNT_UNAVAILABLE -> {
                        Text(
                            text = "Account Unavailable",
                            style = TextStyle(
                                color = textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "Tap to open app",
                            style = TextStyle(
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    WidgetDataState.ERROR -> {
                        Text(
                            text = "Error Loading Balance",
                            style = TextStyle(
                                color = textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "Tap to retry",
                            style = TextStyle(
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    WidgetDataState.READY -> {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left purple decorative bar matching ExpenseTracker design
                            Box(
                                modifier = GlanceModifier
                                    .width(3.dp)
                                    .height(16.dp)
                                    .background(accentColor)
                            ) {}
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Text(
                                text = data.accountName,
                                style = TextStyle(
                                    color = textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(if (isSmall) 4.dp else 8.dp))

                        Text(
                            text = data.formattedBalance,
                            style = TextStyle(
                                color = textPrimary,
                                fontSize = if (isSmall) 18.sp else 22.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )

                        if (!isSmall) {
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ExpenseTracker",
                                    style = TextStyle(
                                        color = accentColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Spacer(modifier = GlanceModifier.defaultWeight())
                                if (data.privacyMode) {
                                    Text(
                                        text = "Privacy Mode Active",
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

    private fun createNavigateAction(context: Context, data: AccountBalanceWidgetData): Action {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.suguru.expensetracker.action.NAVIGATE"
            putExtra("destination", "account_management")
            putExtra("accountId", data.accountId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        return actionStartActivity(intent)
    }
}
