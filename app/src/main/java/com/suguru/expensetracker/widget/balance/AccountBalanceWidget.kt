package com.suguru.expensetracker.widget.balance

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.text.Text

class AccountBalanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box {
                Text("Account Balance Widget")
            }
        }
    }
}
