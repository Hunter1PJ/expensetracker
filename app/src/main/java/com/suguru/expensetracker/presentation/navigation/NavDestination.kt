package com.suguru.expensetracker.presentation.navigation

import androidx.annotation.StringRes
import com.suguru.expensetracker.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Strongly-typed navigation destinations for ExpenseTracker.
 */
enum class NavDestination(
    val route: String,
    @get:StringRes val labelRes: Int,
    val icon: ImageVector,
    val testTag: String,
    val isBottomBarItem: Boolean = true
) {
    Home(
        route = "home",
        labelRes = R.string.nav_home,
        icon = Icons.Outlined.Home,
        testTag = "nav_item_home"
    ),
    Transactions(
        route = "transactions",
        labelRes = R.string.nav_transactions,
        icon = Icons.Outlined.ReceiptLong,
        testTag = "nav_item_transactions"
    ),
    Statistics(
        route = "statistics",
        labelRes = R.string.nav_statistics,
        icon = Icons.Outlined.PieChart,
        testTag = "nav_item_statistics"
    ),
    Budgets(
        route = "budgets",
        labelRes = R.string.nav_budgets,
        icon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_item_budgets"
    ),
    Settings(
        route = "settings",
        labelRes = R.string.nav_settings,
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_settings"
    ),
    AddTransaction(
        route = "add_transaction",
        labelRes = R.string.title_add_transaction,
        icon = Icons.Default.Add,
        testTag = "nav_item_add_transaction",
        isBottomBarItem = false
    ),
    CategoryManagement(
        route = "category_management",
        labelRes = R.string.title_category_management,
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_category_management",
        isBottomBarItem = false
    ),
    AddCategory(
        route = "add_category",
        labelRes = R.string.title_add_category,
        icon = Icons.Default.Add,
        testTag = "nav_item_add_category",
        isBottomBarItem = false
    ),
    EditCategory(
        route = "edit_category",
        labelRes = R.string.title_edit_category,
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_edit_category",
        isBottomBarItem = false
    ),
    TransactionDetail(
        route = "transaction_detail",
        labelRes = R.string.title_transaction_details,
        icon = Icons.Outlined.ReceiptLong,
        testTag = "nav_item_transaction_detail",
        isBottomBarItem = false
    ),
    EditTransaction(
        route = "edit_transaction",
        labelRes = R.string.title_edit_transaction,
        icon = Icons.Outlined.ReceiptLong,
        testTag = "nav_item_edit_transaction",
        isBottomBarItem = false
    ),
    AddBudget(
        route = "add_budget",
        labelRes = R.string.title_add_budget,
        icon = Icons.Default.Add,
        testTag = "nav_item_add_budget",
        isBottomBarItem = false
    ),
    EditBudget(
        route = "edit_budget",
        labelRes = R.string.title_edit_budget,
        icon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_item_edit_budget",
        isBottomBarItem = false
    ),
    RecurringTransactions(
        route = "recurring_transactions",
        labelRes = R.string.title_recurring_transactions,
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_recurring_transactions",
        isBottomBarItem = false
    ),
    AddRecurringTransaction(
        route = "add_recurring_transaction",
        labelRes = R.string.title_add_recurring,
        icon = Icons.Default.Add,
        testTag = "nav_item_add_recurring_transaction",
        isBottomBarItem = false
    ),
    EditRecurringTransaction(
        route = "edit_recurring_transaction",
        labelRes = R.string.title_edit_recurring,
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_edit_recurring_transaction",
        isBottomBarItem = false
    ),
    DataAndStorage(
        route = "data_and_storage",
        labelRes = R.string.title_data_storage,
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_data_and_storage",
        isBottomBarItem = false
    ),
    AdvancedExport(
        route = "advanced_export",
        labelRes = R.string.title_financial_export,
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_advanced_export",
        isBottomBarItem = false
    ),
    About(
        route = "about",
        labelRes = R.string.about_title,
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_about",
        isBottomBarItem = false
    );

    companion object {
        val items: List<NavDestination> get() = entries.filter { it.isBottomBarItem }
    }
}

