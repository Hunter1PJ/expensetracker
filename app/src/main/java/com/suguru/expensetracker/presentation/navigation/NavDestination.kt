package com.suguru.expensetracker.presentation.navigation

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
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val isBottomBarItem: Boolean = true
) {
    Home(
        route = "home",
        label = "Home",
        icon = Icons.Outlined.Home,
        testTag = "nav_item_home"
    ),
    Transactions(
        route = "transactions",
        label = "Transactions",
        icon = Icons.Outlined.ReceiptLong,
        testTag = "nav_item_transactions"
    ),
    Statistics(
        route = "statistics",
        label = "Statistics",
        icon = Icons.Outlined.PieChart,
        testTag = "nav_item_statistics"
    ),
    Budgets(
        route = "budgets",
        label = "Budgets",
        icon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_item_budgets"
    ),
    Settings(
        route = "settings",
        label = "Settings",
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_settings"
    ),
    AddTransaction(
        route = "add_transaction",
        label = "Add Transaction",
        icon = Icons.Default.Add,
        testTag = "nav_item_add_transaction",
        isBottomBarItem = false
    ),
    AccountManagement(
        route = "account_management",
        label = "Accounts",
        icon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_item_account_management",
        isBottomBarItem = false
    ),
    AddAccount(
        route = "add_account",
        label = "Add Account",
        icon = Icons.Default.Add,
        testTag = "nav_item_add_account",
        isBottomBarItem = false
    ),
    EditAccount(
        route = "edit_account",
        label = "Edit Account",
        icon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_item_edit_account",
        isBottomBarItem = false
    ),
    CategoryManagement(
        route = "category_management",
        label = "Categories",
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_category_management",
        isBottomBarItem = false
    ),
    AddCategory(
        route = "add_category",
        label = "Add Category",
        icon = Icons.Default.Add,
        testTag = "nav_item_add_category",
        isBottomBarItem = false
    ),
    EditCategory(
        route = "edit_category",
        label = "Edit Category",
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_edit_category",
        isBottomBarItem = false
    ),
    TransactionDetail(
        route = "transaction_detail",
        label = "Transaction Details",
        icon = Icons.Outlined.ReceiptLong,
        testTag = "nav_item_transaction_detail",
        isBottomBarItem = false
    ),
    EditTransaction(
        route = "edit_transaction",
        label = "Edit Transaction",
        icon = Icons.Outlined.ReceiptLong,
        testTag = "nav_item_edit_transaction",
        isBottomBarItem = false
    ),
    AddBudget(
        route = "add_budget",
        label = "Add Budget",
        icon = Icons.Default.Add,
        testTag = "nav_item_add_budget",
        isBottomBarItem = false
    ),
    EditBudget(
        route = "edit_budget",
        label = "Edit Budget",
        icon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_item_edit_budget",
        isBottomBarItem = false
    ),
    RecurringTransactions(
        route = "recurring_transactions",
        label = "Recurring Rules",
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_recurring_transactions",
        isBottomBarItem = false
    ),
    AddRecurringTransaction(
        route = "add_recurring_transaction",
        label = "Add Recurring Rule",
        icon = Icons.Default.Add,
        testTag = "nav_item_add_recurring_transaction",
        isBottomBarItem = false
    ),
    EditRecurringTransaction(
        route = "edit_recurring_transaction",
        label = "Edit Recurring Rule",
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_edit_recurring_transaction",
        isBottomBarItem = false
    ),
    DataAndStorage(
        route = "data_and_storage",
        label = "Data & Storage",
        icon = Icons.Outlined.Settings,
        testTag = "nav_item_data_and_storage",
        isBottomBarItem = false
    );

    companion object {
        val items: List<NavDestination> get() = entries.filter { it.isBottomBarItem }
    }
}

