package com.example.presentation.navigation

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
    );

    companion object {
        val items: List<NavDestination> get() = entries.filter { it.isBottomBarItem }
    }
}

