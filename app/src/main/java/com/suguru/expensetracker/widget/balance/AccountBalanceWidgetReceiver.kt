package com.suguru.expensetracker.widget.balance

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.suguru.expensetracker.ExpenseTrackerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccountBalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AccountBalanceWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val appContainer = (context.applicationContext as ExpenseTrackerApplication).appContainer
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { id ->
                try {
                    appContainer.widgetConfigurationRepository.deleteConfiguration(id)
                } catch (_: Exception) {
                }
            }
        }
    }
}
