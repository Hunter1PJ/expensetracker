package com.suguru.expensetracker.presentation.navigation

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
import androidx.compose.runtime.collectAsState
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
import com.suguru.expensetracker.ExpenseTrackerApplication
import com.suguru.expensetracker.R
import com.suguru.expensetracker.di.AppContainer
import com.suguru.expensetracker.presentation.accounts.add.AddAccountScreen
import com.suguru.expensetracker.presentation.accounts.add.AddAccountViewModel
import com.suguru.expensetracker.presentation.accounts.management.AccountManagementScreen
import com.suguru.expensetracker.presentation.accounts.management.AccountManagementViewModel
import com.suguru.expensetracker.presentation.budgets.BudgetsScreen
import com.suguru.expensetracker.presentation.budgets.BudgetsViewModel
import com.suguru.expensetracker.presentation.budgets.addedit.AddEditBudgetScreen
import com.suguru.expensetracker.presentation.budgets.addedit.AddEditBudgetViewModel
import com.suguru.expensetracker.presentation.categories.add.AddCategoryScreen
import com.suguru.expensetracker.presentation.categories.add.AddCategoryViewModel
import com.suguru.expensetracker.presentation.categories.management.CategoryManagementScreen
import com.suguru.expensetracker.presentation.categories.management.CategoryManagementViewModel
import com.suguru.expensetracker.presentation.home.HomeScreen
import com.suguru.expensetracker.presentation.home.HomeViewModel
import com.suguru.expensetracker.presentation.recurring.RecurringTransactionsScreen
import com.suguru.expensetracker.presentation.recurring.RecurringTransactionsViewModel
import com.suguru.expensetracker.presentation.recurring.addedit.AddEditRecurringTransactionScreen
import com.suguru.expensetracker.presentation.recurring.addedit.AddEditRecurringTransactionViewModel
import com.suguru.expensetracker.presentation.settings.SettingsScreen
import com.suguru.expensetracker.presentation.statistics.StatisticsScreen
import com.suguru.expensetracker.presentation.statistics.StatisticsViewModel
import com.suguru.expensetracker.presentation.transactions.TransactionsScreen
import com.suguru.expensetracker.presentation.transactions.TransactionsViewModel
import com.suguru.expensetracker.presentation.transactions.add.AddTransactionScreen
import com.suguru.expensetracker.presentation.transactions.add.AddTransactionViewModel
import com.suguru.expensetracker.presentation.transactions.detail.TransactionDetailScreen
import com.suguru.expensetracker.presentation.transactions.detail.TransactionDetailViewModel
import com.suguru.expensetracker.presentation.transactions.edit.EditTransactionScreen
import com.suguru.expensetracker.presentation.transactions.edit.EditTransactionViewModel
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

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
    var selectedEditAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedEditCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedEditBudgetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedEditRecurringRuleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedTransactionId by rememberSaveable { mutableStateOf<Long?>(null) }

    val settingsState by remember(resolvedContainer) {
        resolvedContainer?.settingsRepository?.settings ?: kotlinx.coroutines.flow.flowOf()
    }.collectAsState(initial = null)

    when (currentDestination) {
        NavDestination.AddTransaction -> {
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
        }

        NavDestination.TransactionDetail -> {
            val txId = selectedTransactionId ?: 0L
            val detailViewModel: TransactionDetailViewModel = remember(resolvedContainer, txId) {
                resolvedContainer?.createTransactionDetailViewModel(txId) ?: TransactionDetailViewModel(
                    transactionId = txId,
                    getTransactionUseCase = resolvedContainer!!.getTransactionUseCase,
                    getAccountUseCase = resolvedContainer.getAccountUseCase,
                    getCategoryUseCase = resolvedContainer.getCategoryUseCase,
                    deleteTransactionUseCase = resolvedContainer.deleteTransactionUseCase
                )
            }

            TransactionDetailScreen(
                viewModel = detailViewModel,
                onNavigateBack = {
                    currentDestination = previousDestination
                },
                onNavigateToEdit = { editTxId ->
                    selectedTransactionId = editTxId
                    currentDestination = NavDestination.EditTransaction
                },
                confirmBeforeDelete = settingsState?.confirmBeforeDelete ?: true
            )
        }

        NavDestination.EditTransaction -> {
            val txId = selectedTransactionId ?: 0L
            val editViewModel: EditTransactionViewModel = remember(resolvedContainer, txId) {
                resolvedContainer?.createEditTransactionViewModel(txId) ?: EditTransactionViewModel(
                    transactionId = txId,
                    getTransactionUseCase = resolvedContainer!!.getTransactionUseCase,
                    updateTransactionUseCase = resolvedContainer.updateTransactionUseCase,
                    observeActiveAccountsUseCase = resolvedContainer.observeActiveAccountsUseCase,
                    observeCategoriesByTypeUseCase = resolvedContainer.observeCategoriesByTypeUseCase,
                    getAccountUseCase = resolvedContainer.getAccountUseCase,
                    getCategoryUseCase = resolvedContainer.getCategoryUseCase
                )
            }

            EditTransactionScreen(
                viewModel = editViewModel,
                onNavigateBack = {
                    currentDestination = NavDestination.TransactionDetail
                }
            )
        }

        NavDestination.AccountManagement -> {
            val accountManagementViewModel: AccountManagementViewModel = remember(resolvedContainer) {
                resolvedContainer?.createAccountManagementViewModel() ?: AccountManagementViewModel(
                    observeActiveAccountsUseCase = resolvedContainer!!.observeActiveAccountsUseCase,
                    observeAccountBalanceUseCase = resolvedContainer.observeAccountBalanceUseCase,
                    archiveAccountUseCase = resolvedContainer.archiveAccountUseCase
                )
            }

            AccountManagementScreen(
                viewModel = accountManagementViewModel,
                onNavigateBack = {
                    currentDestination = NavDestination.Settings
                },
                onNavigateToAddAccount = {
                    selectedEditAccountId = null
                    currentDestination = NavDestination.AddAccount
                },
                onNavigateToEditAccount = { accountId ->
                    selectedEditAccountId = accountId
                    currentDestination = NavDestination.EditAccount
                }
            )
        }

        NavDestination.AddAccount, NavDestination.EditAccount -> {
            val editId = if (currentDestination == NavDestination.EditAccount) selectedEditAccountId ?: 0L else 0L
            val addAccountViewModel: AddAccountViewModel = remember(resolvedContainer, editId) {
                resolvedContainer?.createAddAccountViewModel(editId) ?: AddAccountViewModel(
                    accountId = editId,
                    createAccountUseCase = resolvedContainer!!.createAccountUseCase,
                    updateAccountUseCase = resolvedContainer.updateAccountUseCase,
                    getAccountUseCase = resolvedContainer.getAccountUseCase
                )
            }

            AddAccountScreen(
                viewModel = addAccountViewModel,
                onNavigateBack = {
                    currentDestination = NavDestination.AccountManagement
                }
            )
        }

        NavDestination.CategoryManagement -> {
            val categoryManagementViewModel: CategoryManagementViewModel = remember(resolvedContainer) {
                resolvedContainer?.createCategoryManagementViewModel() ?: CategoryManagementViewModel(
                    observeActiveCategoriesUseCase = resolvedContainer!!.observeActiveCategoriesUseCase,
                    archiveCategoryUseCase = resolvedContainer.archiveCategoryUseCase
                )
            }

            CategoryManagementScreen(
                viewModel = categoryManagementViewModel,
                onNavigateBack = {
                    currentDestination = NavDestination.Settings
                },
                onNavigateToAddCategory = {
                    selectedEditCategoryId = null
                    currentDestination = NavDestination.AddCategory
                },
                onNavigateToEditCategory = { categoryId ->
                    selectedEditCategoryId = categoryId
                    currentDestination = NavDestination.EditCategory
                }
            )
        }

        NavDestination.AddCategory, NavDestination.EditCategory -> {
            val editId = if (currentDestination == NavDestination.EditCategory) selectedEditCategoryId ?: 0L else 0L
            val addCategoryViewModel: AddCategoryViewModel = remember(resolvedContainer, editId) {
                resolvedContainer?.createAddCategoryViewModel(editId) ?: AddCategoryViewModel(
                    categoryId = editId,
                    createCategoryUseCase = resolvedContainer!!.createCategoryUseCase,
                    updateCategoryUseCase = resolvedContainer.updateCategoryUseCase,
                    getCategoryUseCase = resolvedContainer.getCategoryUseCase
                )
            }

            AddCategoryScreen(
                viewModel = addCategoryViewModel,
                onNavigateBack = {
                    currentDestination = NavDestination.CategoryManagement
                }
            )
        }

        NavDestination.AddBudget, NavDestination.EditBudget -> {
            val editId = if (currentDestination == NavDestination.EditBudget) selectedEditBudgetId ?: 0L else 0L
            val addEditBudgetViewModel: AddEditBudgetViewModel = remember(resolvedContainer, editId) {
                resolvedContainer?.createAddEditBudgetViewModel(editId) ?: AddEditBudgetViewModel(
                    budgetId = editId,
                    getBudgetUseCase = resolvedContainer!!.getBudgetUseCase,
                    createBudgetUseCase = resolvedContainer.createBudgetUseCase,
                    updateBudgetUseCase = resolvedContainer.updateBudgetUseCase,
                    observeActiveCategoriesUseCase = resolvedContainer.observeActiveCategoriesUseCase,
                    observeAllAccountsUseCase = resolvedContainer.observeAllAccountsUseCase
                )
            }

            AddEditBudgetScreen(
                viewModel = addEditBudgetViewModel,
                onNavigateBack = {
                    currentDestination = NavDestination.Budgets
                }
            )
        }

        NavDestination.RecurringTransactions -> {
            val recurringViewModel: RecurringTransactionsViewModel = remember(resolvedContainer) {
                resolvedContainer?.createRecurringTransactionsViewModel() ?: RecurringTransactionsViewModel(
                    observeRecurringTransactionsUseCase = resolvedContainer!!.observeRecurringTransactionsUseCase,
                    observeAllAccountsUseCase = resolvedContainer.observeAllAccountsUseCase,
                    observeAllCategoriesUseCase = resolvedContainer.observeAllCategoriesUseCase,
                    deactivateRecurringTransactionUseCase = resolvedContainer.deactivateRecurringTransactionUseCase,
                    processDueRecurringTransactionsUseCase = resolvedContainer.processDueRecurringTransactionsUseCase
                )
            }

            RecurringTransactionsScreen(
                viewModel = recurringViewModel,
                onNavigateBack = {
                    currentDestination = NavDestination.Settings
                },
                onNavigateToAddRule = {
                    selectedEditRecurringRuleId = null
                    currentDestination = NavDestination.AddRecurringTransaction
                },
                onNavigateToEditRule = { ruleId ->
                    selectedEditRecurringRuleId = ruleId
                    currentDestination = NavDestination.EditRecurringTransaction
                }
            )
        }

        NavDestination.AddRecurringTransaction, NavDestination.EditRecurringTransaction -> {
            val editId = if (currentDestination == NavDestination.EditRecurringTransaction) selectedEditRecurringRuleId ?: 0L else 0L
            val addEditRecurringViewModel: AddEditRecurringTransactionViewModel = remember(resolvedContainer, editId) {
                resolvedContainer?.createAddEditRecurringTransactionViewModel(editId) ?: AddEditRecurringTransactionViewModel(
                    ruleId = editId,
                    createRecurringTransactionUseCase = resolvedContainer!!.createRecurringTransactionUseCase,
                    updateRecurringTransactionUseCase = resolvedContainer.updateRecurringTransactionUseCase,
                    getRecurringTransactionUseCase = resolvedContainer.getRecurringTransactionUseCase,
                    observeActiveAccountsUseCase = resolvedContainer.observeActiveAccountsUseCase,
                    observeActiveCategoriesUseCase = resolvedContainer.observeActiveCategoriesUseCase
                )
            }

            AddEditRecurringTransactionScreen(
                viewModel = addEditRecurringViewModel,
                onNavigateBack = {
                    currentDestination = NavDestination.RecurringTransactions
                }
            )
        }

        NavDestination.DataAndStorage -> {
            val dataStorageViewModel = remember(resolvedContainer) {
                resolvedContainer?.createDataStorageViewModel() ?: throw IllegalStateException("AppContainer required")
            }
            com.suguru.expensetracker.presentation.settings.DataAndStorageScreen(
                viewModel = dataStorageViewModel,
                onNavigateBack = {
                    currentDestination = NavDestination.Settings
                }
            )
        }

        else -> {
            BackHandler(enabled = currentDestination != NavDestination.Home) {
                currentDestination = NavDestination.Home
            }

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
                        containerColor = ExpenseTrackerTheme.extendedColors.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = ExpenseTrackerTheme.extendedColors.borderSubtle
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
                                    selectedIconColor = ExpenseTrackerTheme.extendedColors.primaryBright,
                                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                    indicatorColor = ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.22f),
                                    unselectedIconColor = ExpenseTrackerTheme.extendedColors.textSecondary,
                                    unselectedTextColor = ExpenseTrackerTheme.extendedColors.textSecondary
                                ),
                                modifier = Modifier.testTag(destination.testTag)
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (currentDestination == NavDestination.Home) {
                        FloatingActionButton(
                            onClick = {
                                previousDestination = currentDestination
                                currentDestination = NavDestination.AddTransaction
                            },
                            shape = ExpenseTrackerRadius.button,
                            containerColor = ExpenseTrackerTheme.extendedColors.primaryPurple,
                            contentColor = androidx.compose.ui.graphics.Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 2.dp
                            ),
                            modifier = Modifier.testTag("add_transaction_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.action_add_transaction),
                                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.lg)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                when (currentDestination) {
                    NavDestination.Home -> {
                        HomeScreen(
                            viewModel = resolvedHomeViewModel,
                            onNavigateToAddTransaction = {
                                previousDestination = NavDestination.Home
                                currentDestination = NavDestination.AddTransaction
                            },
                            onNavigateToAccounts = {
                                previousDestination = NavDestination.Home
                                currentDestination = NavDestination.AccountManagement
                            },
                            onNavigateToAddAccount = {
                                previousDestination = NavDestination.Home
                                selectedEditAccountId = null
                                currentDestination = NavDestination.AddAccount
                            },
                            onNavigateToTransactions = {
                                previousDestination = NavDestination.Home
                                currentDestination = NavDestination.Transactions
                            },
                            onNavigateToTransactionDetail = { txId ->
                                previousDestination = NavDestination.Home
                                selectedTransactionId = txId
                                currentDestination = NavDestination.TransactionDetail
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    NavDestination.Transactions -> {
                        val transactionsViewModel: TransactionsViewModel = remember(resolvedContainer) {
                            resolvedContainer?.createTransactionsViewModel() ?: TransactionsViewModel(
                                observeTransactionsUseCase = resolvedContainer!!.observeTransactionsUseCase,
                                observeAllAccountsUseCase = resolvedContainer.observeAllAccountsUseCase,
                                observeAllCategoriesUseCase = resolvedContainer.observeAllCategoriesUseCase
                            )
                        }

                        TransactionsScreen(
                            viewModel = transactionsViewModel,
                            onNavigateToDetail = { txId ->
                                previousDestination = NavDestination.Transactions
                                selectedTransactionId = txId
                                currentDestination = NavDestination.TransactionDetail
                            },
                            onNavigateToAddTransaction = {
                                previousDestination = NavDestination.Transactions
                                currentDestination = NavDestination.AddTransaction
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    NavDestination.Statistics -> {
                        val statisticsViewModel: StatisticsViewModel = remember(resolvedContainer) {
                            resolvedContainer?.createStatisticsViewModel() ?: StatisticsViewModel(
                                observeStatisticsUseCase = resolvedContainer!!.observeStatisticsUseCase
                            )
                        }

                        StatisticsScreen(
                            viewModel = statisticsViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    NavDestination.Budgets -> {
                        val budgetsViewModel: BudgetsViewModel = remember(resolvedContainer) {
                            resolvedContainer?.createBudgetsViewModel() ?: BudgetsViewModel(
                                observeActiveBudgetProgressUseCase = resolvedContainer!!.observeActiveBudgetProgressUseCase,
                                deactivateBudgetUseCase = resolvedContainer.deactivateBudgetUseCase
                            )
                        }

                        BudgetsScreen(
                            viewModel = budgetsViewModel,
                            onNavigateToAddBudget = {
                                previousDestination = NavDestination.Budgets
                                selectedEditBudgetId = null
                                currentDestination = NavDestination.AddBudget
                            },
                            onNavigateToEditBudget = { budgetId ->
                                previousDestination = NavDestination.Budgets
                                selectedEditBudgetId = budgetId
                                currentDestination = NavDestination.EditBudget
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    NavDestination.Settings -> {
                        val settingsViewModel: com.suguru.expensetracker.presentation.settings.SettingsViewModel = remember(resolvedContainer) {
                            resolvedContainer?.createSettingsViewModel() ?: com.suguru.expensetracker.presentation.settings.SettingsViewModel(resolvedContainer!!.settingsRepository)
                        }

                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onNavigateToAccounts = {
                                previousDestination = NavDestination.Settings
                                currentDestination = NavDestination.AccountManagement
                            },
                            onNavigateToCategories = {
                                previousDestination = NavDestination.Settings
                                currentDestination = NavDestination.CategoryManagement
                            },
                            onNavigateToRecurring = {
                                previousDestination = NavDestination.Settings
                                currentDestination = NavDestination.RecurringTransactions
                            },
                            onNavigateToDataAndStorage = {
                                previousDestination = NavDestination.Settings
                                currentDestination = NavDestination.DataAndStorage
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    else -> {
                        // Covered by outer when
                    }
                }
            }
        }
    }
}
