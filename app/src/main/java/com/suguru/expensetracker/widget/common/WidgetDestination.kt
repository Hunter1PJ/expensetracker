package com.suguru.expensetracker.widget.common

/**
 * Clean navigation contract for deeplinks originating from widgets.
 */
sealed interface WidgetDestination {
    data class AccountDetail(val accountId: Long) : WidgetDestination
    data object AddExpense : WidgetDestination
    data object AddIncome : WidgetDestination
    data object Pro : WidgetDestination
}
