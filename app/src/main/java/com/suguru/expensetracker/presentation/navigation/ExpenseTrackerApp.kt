package com.suguru.expensetracker.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.suguru.expensetracker.domain.model.CurrencyInfo
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
import com.suguru.expensetracker.presentation.startup.StartupProvisioningState
import com.suguru.expensetracker.presentation.startup.allowsNavigation
import com.suguru.expensetracker.presentation.startup.SupportedStartupCurrencies
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerApp(
    appContainer: AppContainer? = null,
    homeViewModel: HomeViewModel? = null,
    addTransactionViewModel: AddTransactionViewModel? = null,
    initialDestination: NavDestination? = null,
    initialBudgetId: Long? = null,
    initialTransactionType: com.suguru.expensetracker.domain.model.TransactionType? = null,
    onResetNavigation: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val resolvedContainer = remember(appContainer, context) {
        appContainer ?: (context.applicationContext as? ExpenseTrackerApplication)?.appContainer
    }

    val startupState by (resolvedContainer?.provisioningState ?: kotlinx.coroutines.flow.MutableStateFlow(StartupProvisioningState.Loading)).collectAsState()
    var hasRunStartupCheck by remember(resolvedContainer) { mutableStateOf(false) }
    var selectedStartupCurrency by rememberSaveable { mutableStateOf<String?>(null) }
    var isSubmittingStartupCurrency by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Track whether the app has completed initial provisioning at least once.
    // Once true, transient provisioning state changes (e.g. during manual restore)
    // should NOT tear down the main UI tree and reset navigation to Home.
    var hasCompletedInitialProvisioning by rememberSaveable { mutableStateOf(false) }

    // Declare navigation state ABOVE the provisioning gate so it survives
    // provisioning state transitions without being torn down and reset.
    var currentDestination by rememberSaveable { mutableStateOf(NavDestination.Home) }
    var previousDestination by rememberSaveable { mutableStateOf(NavDestination.Home) }

    val startupAiInvalidKey by (resolvedContainer?.startupAiInvalidKey
        ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    LaunchedEffect(startupAiInvalidKey, startupState.allowsNavigation) {
        if (startupAiInvalidKey && startupState.allowsNavigation) {
            previousDestination = currentDestination
            currentDestination = NavDestination.Settings
            resolvedContainer?.consumeStartupAiInvalidKey()
        }
    }

    LaunchedEffect(resolvedContainer, hasRunStartupCheck) {
        if (resolvedContainer != null && !hasRunStartupCheck) {
            hasRunStartupCheck = true
            resolvedContainer.ensureInternalDefaultAccountProvisioning()
            val firebaseReady = com.suguru.expensetracker.presentation.auth.isFirebaseInitialized(context)
            if (firebaseReady) {
                com.suguru.expensetracker.presentation.auth.attemptAutoSignIn(
                    context = context,
                    credentialManager = androidx.credentials.CredentialManager.create(context),
                    onAuthSuccess = {},
                    onUnauthenticated = {},
                    scope = coroutineScope
                )
            }
        }
    }

    // Mark initial provisioning complete when the app first reaches Ready state.
    LaunchedEffect(startupState) {
        if (startupState.allowsNavigation) {
            hasCompletedInitialProvisioning = true
        }
    }

    // Only show the startup provisioning gate during initial app startup.
    // Once the app has completed initial provisioning, transient provisioning
    // state changes (e.g. from manual cloud restore) are handled in-context
    // by the CloudBackupSection dialogs, not by tearing down the entire UI.
    if (!startupState.allowsNavigation && !hasCompletedInitialProvisioning) {
        StartupProvisioningGate(
            state = startupState,
            selectedCurrencyCode = selectedStartupCurrency,
            isSubmitting = isSubmittingStartupCurrency,
            onCurrencySelected = { selectedStartupCurrency = it },
            onRetry = {
                if (resolvedContainer != null) {
                    coroutineScope.launch {
                        resolvedContainer.ensureInternalDefaultAccountProvisioning()
                    }
                }
            },
            onSubmit = {
                val currencyCode = selectedStartupCurrency ?: return@StartupProvisioningGate
                if (resolvedContainer == null || isSubmittingStartupCurrency) return@StartupProvisioningGate
                isSubmittingStartupCurrency = true
                coroutineScope.launch {
                    try {
                        resolvedContainer.settingsRepository.setPreferredCurrency(currencyCode)
                        resolvedContainer.ensureInternalDefaultAccountProvisioning()
                    } finally {
                        isSubmittingStartupCurrency = false
                    }
                }
            }
        )
        return
    }

    val resolvedHomeViewModel: HomeViewModel = homeViewModel ?: remember(resolvedContainer) {
        resolvedContainer?.createHomeViewModel() ?: HomeViewModel()
    }

    val resolvedAddTransactionViewModel: AddTransactionViewModel = addTransactionViewModel ?: remember(resolvedContainer) {
        resolvedContainer?.createAddTransactionViewModel() ?: AddTransactionViewModel(
            observeActiveAccountsUseCase = resolvedContainer!!.observeActiveAccountsUseCase,
            observeCategoriesByTypeUseCase = resolvedContainer.observeCategoriesByTypeUseCase,
            createTransactionUseCase = resolvedContainer.createTransactionUseCase
        )
    }

    val settingsViewModelHolder = remember(resolvedContainer) {
        androidx.compose.runtime.mutableStateOf<com.suguru.expensetracker.presentation.settings.SettingsViewModel?>(null)
    }
    var selectedEditCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedEditBudgetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedEditRecurringRuleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedTransactionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var activeAiCandidate by remember { mutableStateOf<com.suguru.expensetracker.domain.parser.TransactionCandidate?>(null) }

    androidx.compose.runtime.LaunchedEffect(initialDestination, initialBudgetId, initialTransactionType) {
        if (initialDestination != null) {
            currentDestination = initialDestination
            if (initialBudgetId != null) {
                val budget = resolvedContainer?.budgetRepository?.getBudgetById(initialBudgetId)
                if (budget != null && budget.isActive) {
                    selectedEditBudgetId = initialBudgetId
                    currentDestination = NavDestination.EditBudget
                }
            }
            if (initialDestination == NavDestination.AddTransaction && initialTransactionType != null) {
                resolvedAddTransactionViewModel.onTransactionTypeChanged(initialTransactionType)
            }
            onResetNavigation?.invoke()
        }
    }

    val settingsState by remember(resolvedContainer) {
        resolvedContainer?.settingsRepository?.settings ?: kotlinx.coroutines.flow.flowOf()
    }.collectAsState(initial = null)

    when (currentDestination) {
        NavDestination.AddTransaction -> {
            AddTransactionScreen(
                viewModel = resolvedAddTransactionViewModel,
                onNavigateBack = {
                    activeAiCandidate = null
                    resolvedAddTransactionViewModel.clearAiDraftState()
                    currentDestination = previousDestination
                },
                onSaveSuccess = {
                    activeAiCandidate?.let { candidate ->
                        resolvedHomeViewModel.consumeCandidate(candidate)
                        activeAiCandidate = null
                    }
                    resolvedAddTransactionViewModel.clearAiDraftState()
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
                    observeActiveCategoriesUseCase = resolvedContainer.observeActiveCategoriesUseCase,
                    getCategoryUseCase = resolvedContainer.getCategoryUseCase
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
            val container = resolvedContainer ?: throw IllegalStateException("AppContainer required")
            val dataStorageViewModel = remember(container) {
                container.createDataStorageViewModel()
            }
            com.suguru.expensetracker.presentation.settings.DataAndStorageScreen(
                viewModel = dataStorageViewModel,
                onNavigateBack = {
                    currentDestination = NavDestination.Settings
                },
                createCloudBackupViewModel = { uid ->
                    container.createCloudBackupViewModel(uid)
                }
            )
        }

        NavDestination.AdvancedExport -> {
            val exportViewModel = remember(resolvedContainer) {
                resolvedContainer?.createExportViewModel() ?: throw IllegalStateException("AppContainer required")
            }
            com.suguru.expensetracker.presentation.export.AdvancedExportScreen(
                viewModel = exportViewModel,
                onNavigateBack = {
                    currentDestination = previousDestination
                }
            )
        }

        else -> {
            val snackbarHostState = remember { SnackbarHostState() }
            BackHandler(enabled = currentDestination != NavDestination.Home) {
                currentDestination = NavDestination.Home
            }

            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("expense_tracker_root_scaffold"),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = if (currentDestination == NavDestination.Home) {
                                        stringResource(R.string.app_name)
                                    } else {
                                        stringResource(currentDestination.labelRes)
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
                                        contentDescription = stringResource(destination.labelRes),
                                        modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(destination.labelRes),
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
                }
            ) { innerPadding ->
                when (currentDestination) {
                    NavDestination.Home -> {
                        HomeScreen(
                            viewModel = resolvedHomeViewModel,
                            onNavigateToAddTransaction = {
                                activeAiCandidate = null
                                resolvedAddTransactionViewModel.clearForm()
                                previousDestination = NavDestination.Home
                                currentDestination = NavDestination.AddTransaction
                            },
                            onNavigateToAddTransactionWithCandidate = { candidate ->
                                activeAiCandidate = candidate
                                resolvedAddTransactionViewModel.populateFromCandidate(candidate)
                                previousDestination = NavDestination.Home
                                currentDestination = NavDestination.AddTransaction
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
                            onNavigateToSettings = {
                                previousDestination = NavDestination.Home
                                currentDestination = NavDestination.Settings
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
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    NavDestination.Statistics -> {
                        val statisticsViewModel: StatisticsViewModel = remember(resolvedContainer) {
                            resolvedContainer?.createStatisticsViewModel() ?: StatisticsViewModel(
                                observeStatisticsUseCase = resolvedContainer!!.observeStatisticsUseCase,
                                observeStatisticsComparisonUseCase = resolvedContainer.observeStatisticsComparisonUseCase
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
                        val settingsViewModel = settingsViewModelHolder.value ?: run {
                            (resolvedContainer?.createSettingsViewModel() ?: com.suguru.expensetracker.presentation.settings.SettingsViewModel(
                                settingsRepository = resolvedContainer!!.settingsRepository
                            )).also { settingsViewModelHolder.value = it }
                        }

                        SettingsScreen(
                            viewModel = settingsViewModel,
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
                            onNavigateToExport = {
                                previousDestination = NavDestination.Settings
                                currentDestination = NavDestination.AdvancedExport
                            },
                            createCloudBackupViewModel = { uid ->
                                resolvedContainer?.createCloudBackupViewModel(uid) ?: throw IllegalStateException("AppContainer required")
                            },
                            onShowMessage = { msg ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                }
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

@Composable
private fun StartupProvisioningGate(
    state: StartupProvisioningState,
    selectedCurrencyCode: String?,
    isSubmitting: Boolean,
    onCurrencySelected: (String) -> Unit,
    onRetry: () -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ExpenseTrackerRadius.card
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    when (state) {
                        StartupProvisioningState.Loading -> {
                            Text(
                                text = stringResource(R.string.setup_checking),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.setup_checking_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        StartupProvisioningState.SettingsRecoveryPending -> {
                            Text(
                                text = stringResource(R.string.setup_restoring),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.setup_restoring_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        StartupProvisioningState.RequiresCurrencySelection -> {
                            Text(
                                text = stringResource(R.string.setup_one_time_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.setup_one_time_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SupportedStartupCurrencies.forEach { currency ->
                                    val isSelected = selectedCurrencyCode == currency.currencyCode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isSubmitting) {
                                                onCurrencySelected(currency.currencyCode)
                                            }
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                shape = ExpenseTrackerRadius.button
                                            )
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = currency.currencyCode,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = when (currency.currencyCode) {
                                                    "UZS" -> stringResource(R.string.currency_name_uzs)
                                                    "USD" -> stringResource(R.string.currency_name_usd)
                                                    "EUR" -> stringResource(R.string.currency_name_eur)
                                                    "KRW" -> stringResource(R.string.currency_name_krw)
                                                    "JPY" -> stringResource(R.string.currency_name_jpy)
                                                    else -> currency.displayName
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = currency.symbol,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = onSubmit,
                                    enabled = selectedCurrencyCode != null && !isSubmitting,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isSubmitting) stringResource(R.string.action_saving) else stringResource(R.string.setup_continue))
                                }
                            }
                        }
                        is StartupProvisioningState.RecoverableError -> {
                            Text(
                                text = stringResource(R.string.setup_failed_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.setup_error_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = onRetry,
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(stringResource(R.string.ui_retry))
                            }
                        }
                        is StartupProvisioningState.UnrecoverableError -> {
                            Text(
                                text = stringResource(R.string.setup_app_failed),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.setup_error_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = onRetry,
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(R.string.ui_retry))
                            }
                        }
                        StartupProvisioningState.Ready -> {
                            Text(stringResource(R.string.ui_ready))
                        }
                    }
                }
            }
        }
    }
}
