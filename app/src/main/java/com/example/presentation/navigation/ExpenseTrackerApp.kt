package com.example.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ExpenseTrackerApplication
import com.example.R
import com.example.di.AppContainer
import com.example.presentation.budgets.BudgetsPlaceholderScreen
import com.example.presentation.home.HomeScreen
import com.example.presentation.home.HomeViewModel
import com.example.presentation.settings.SettingsPlaceholderScreen
import com.example.presentation.statistics.StatisticsPlaceholderScreen
import com.example.presentation.transactions.TransactionsPlaceholderScreen
import com.example.presentation.transactions.add.AddTransactionScreen
import com.example.presentation.transactions.add.AddTransactionViewModel
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerApp(
    appContainer: AppContainer? = null,
    homeViewModel: HomeViewModel? = null,
    addTransactionViewModel: AddTransactionViewModel? = null
) {
    val context = LocalContext.current
    val resolvedContainer = remember(appContainer, context) {
        appContainer ?: (context.applicationContext as? ExpenseTrackerApplication)?.appContainer
    }

    val resolvedHomeViewModel: HomeViewModel = homeViewModel ?: remember(resolvedContainer) {
        resolvedContainer?.createHomeViewModel() ?: HomeViewModel()
    }

    var currentDestination by rememberSaveable { mutableStateOf(NavDestination.Home) }
    var previousDestination by rememberSaveable { mutableStateOf(NavDestination.Home) }

    if (currentDestination == NavDestination.AddTransaction) {
        val resolvedAddTransactionViewModel: AddTransactionViewModel = addTransactionViewModel ?: remember(resolvedContainer) {
            resolvedContainer?.createAddTransactionViewModel() ?: AddTransactionViewModel(
                observeActiveAccountsUseCase = resolvedContainer!!.observeActiveAccountsUseCase,
                observeCategoriesByTypeUseCase = resolvedContainer.observeCategoriesByTypeUseCase,
                createTransactionUseCase = resolvedContainer.createTransactionUseCase
            )
        }

        AddTransactionScreen(
            viewModel = resolvedAddTransactionViewModel,
            onNavigateBack = {
                currentDestination = previousDestination
            }
        )
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("expense_tracker_root_scaffold"),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (currentDestination == NavDestination.Home) {
                                    stringResource(R.string.app_name)
                                } else {
                                    currentDestination.label
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.foundation_subtitle).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.5.sp,
                                color = ExpenseTrackerTheme.extendedColors.textSecondary
                            )
                        }
                    },
                    actions = {
                        // Initials Avatar
                        Box(
                            modifier = Modifier
                                .padding(end = ExpenseTrackerSpacing.md)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ExpenseTrackerTheme.extendedColors.surfaceElevated)
                                .border(
                                    width = 1.dp,
                                    color = ExpenseTrackerTheme.extendedColors.cardBorder,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ET",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = ExpenseTrackerTheme.extendedColors.cardBorder.copy(alpha = 0.6f)
                        )
                        .testTag("bottom_navigation_bar")
                ) {
                    NavDestination.items.forEach { destination ->
                        val isSelected = currentDestination == destination
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                previousDestination = currentDestination
                                currentDestination = destination
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                                )
                            },
                            label = {
                                Text(
                                    text = destination.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = ExpenseTrackerTheme.extendedColors.textSecondary,
                                unselectedTextColor = ExpenseTrackerTheme.extendedColors.textSecondary
                            ),
                            modifier = Modifier.testTag(destination.testTag)
                        )
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        previousDestination = currentDestination
                        currentDestination = NavDestination.AddTransaction
                    },
                    shape = ExpenseTrackerRadius.button,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                    modifier = Modifier.testTag("add_transaction_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add_transaction),
                        modifier = Modifier.size(ExpenseTrackerTheme.iconSize.lg)
                    )
                }
            }
        ) { innerPadding ->
            when (currentDestination) {
                NavDestination.Home -> {
                    HomeScreen(
                        viewModel = resolvedHomeViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                NavDestination.Transactions -> {
                    TransactionsPlaceholderScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                NavDestination.Statistics -> {
                    StatisticsPlaceholderScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                NavDestination.Budgets -> {
                    BudgetsPlaceholderScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                NavDestination.Settings -> {
                    SettingsPlaceholderScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                NavDestination.AddTransaction -> {
                    // Handled above
                }
            }
        }
    }
}
