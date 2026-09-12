package com.suguru.expensetracker.presentation.accounts.management

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.presentation.common.FinanceVisuals
import com.suguru.expensetracker.presentation.components.BackgroundGlowDecoration
import com.suguru.expensetracker.presentation.components.EmptyState
import com.suguru.expensetracker.presentation.components.ErrorBanner
import com.suguru.expensetracker.presentation.components.ExpenseTrackerCard
import com.suguru.expensetracker.presentation.components.ExpenseTrackerConfirmationDialog
import com.suguru.expensetracker.presentation.components.HeroCard
import com.suguru.expensetracker.presentation.components.IconAvatar
import com.suguru.expensetracker.presentation.components.LoadingState
import com.suguru.expensetracker.presentation.components.PrimaryButton
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

@Composable
fun AccountManagementScreen(
    viewModel: AccountManagementViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    onNavigateToEditAccount: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onNavigateBack)

    AccountManagementContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToAddAccount = onNavigateToAddAccount,
        onEditAccount = onNavigateToEditAccount,
        onArchiveClicked = viewModel::onArchiveClicked,
        onDismissArchiveDialog = viewModel::onDismissArchiveDialog,
        onConfirmArchive = viewModel::onConfirmArchive,
        onDismissError = viewModel::onDismissError,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementContent(
    uiState: AccountManagementUiState,
    onNavigateBack: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    onEditAccount: (Long) -> Unit,
    onArchiveClicked: (Account) -> Unit,
    onDismissArchiveDialog: () -> Unit,
    onConfirmArchive: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("account_management_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.title_account_management),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "PORTFOLIO & BALANCES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddAccount,
                shape = ExpenseTrackerRadius.button,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                modifier = Modifier.testTag("add_account_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_account),
                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.lg)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BackgroundGlowDecoration(alpha = 0.10f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = ExpenseTrackerSpacing.screenHorizontal,
                        vertical = ExpenseTrackerSpacing.screenVertical
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Error banner
                AnimatedVisibility(visible = uiState.error != null) {
                    uiState.error?.let { err ->
                        val errorMessage = when (err) {
                            AccountManagementError.AccountNotFound -> stringResource(R.string.error_account_not_found)
                            is AccountManagementError.ArchiveFailed -> err.message ?: stringResource(R.string.error_archive_account_failed)
                        }

                        ErrorBanner(
                            message = errorMessage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp)
                                .padding(bottom = ExpenseTrackerSpacing.md),
                            testTag = "account_management_error_banner"
                        )
                    }
                }

                if (uiState.isLoading) {
                    LoadingState(
                        message = stringResource(R.string.loading_accounts),
                        modifier = Modifier.fillMaxSize(),
                        testTag = "accounts_loading_state"
                    )
                } else if (uiState.accounts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 540.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.lg)
                        ) {
                            EmptyState(
                                title = stringResource(R.string.no_accounts_yet_title),
                                description = stringResource(R.string.no_accounts_yet_desc),
                                icon = Icons.Outlined.AccountBalanceWallet,
                                testTag = "no_accounts_management_empty_state"
                            )
                            PrimaryButton(
                                text = stringResource(R.string.action_add_account),
                                onClick = onNavigateToAddAccount,
                                testTag = "empty_state_add_account_button"
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                            .weight(1f)
                            .testTag("accounts_list"),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        // Portfolio Header Card
                        item(key = "accounts_overview_hero") {
                            AccountPortfolioOverviewCard(accounts = uiState.accounts)
                        }

                        items(
                            items = uiState.accounts,
                            key = { it.account.id }
                        ) { item ->
                            AccountItemCard(
                                item = item,
                                onClick = { onEditAccount(item.account.id) },
                                onArchive = { onArchiveClicked(item.account) },
                                testTag = "account_card_${item.account.id}"
                            )
                        }
                    }
                }
            }
        }
    }

    // Archive Confirmation Dialog
    if (uiState.accountToArchive != null) {
        val account = uiState.accountToArchive
        ExpenseTrackerConfirmationDialog(
            title = stringResource(R.string.dialog_archive_account_title, account.name),
            message = stringResource(R.string.dialog_archive_account_message),
            confirmText = stringResource(R.string.action_archive),
            dismissText = stringResource(R.string.action_cancel),
            isDestructive = true,
            onConfirm = onConfirmArchive,
            onDismissRequest = onDismissArchiveDialog,
            confirmTestTag = "confirm_archive_button",
            dismissTestTag = "cancel_archive_button",
            testTag = "archive_account_confirmation_dialog"
        )
    }
}

@Composable
private fun AccountPortfolioOverviewCard(
    accounts: List<AccountItemUiState>,
    modifier: Modifier = Modifier
) {
    HeroCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("accounts_portfolio_hero")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ACTIVE ACCOUNTS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                    color = ExpenseTrackerTheme.extendedColors.primaryPurple
                )
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
                Text(
                    text = "${accounts.size} ${if (accounts.size == 1) "Account" else "Accounts"}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.16f))
                    .border(1.dp, ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalance,
                    contentDescription = null,
                    tint = ExpenseTrackerTheme.extendedColors.primaryPurple,
                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                )
            }
        }
    }
}

@Composable
private fun AccountItemCard(
    item: AccountItemUiState,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val account = item.account
    val icon = FinanceVisuals.getAccountIcon(account.iconName)
    val accentColor = FinanceVisuals.parseColorHex(account.colorHex)
    val typeLabel = FinanceVisuals.getAccountTypeLabel(account.type)

    ExpenseTrackerCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = ExpenseTrackerRadius.cardInteractive,
        containerColor = ExpenseTrackerTheme.extendedColors.surface,
        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
            ) {
                // Icon Avatar
                IconAvatar(
                    icon = icon,
                    contentDescription = account.name,
                    tint = accentColor,
                    backgroundColor = accentColor.copy(alpha = 0.15f),
                    borderColor = accentColor.copy(alpha = 0.35f),
                    size = 46.dp
                )

                // Account Name & Metadata
                Column(
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xxs)
                ) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                    ) {
                        // Type Tag
                        Surface(
                            shape = ExpenseTrackerRadius.chipPill,
                            color = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                ExpenseTrackerTheme.extendedColors.borderSubtle
                            )
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = ExpenseTrackerTheme.extendedColors.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        // Currency Tag
                        Surface(
                            shape = ExpenseTrackerRadius.chipPill,
                            color = ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = account.initialBalance.currencyCode,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseTrackerTheme.extendedColors.primaryPurple,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.sm))

            // Right side: Balance & Archive
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.balanceFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.testTag("account_balance_${account.id}")
                    )
                    Text(
                        text = stringResource(R.string.current_balance_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = ExpenseTrackerTheme.extendedColors.textTertiary
                    )
                }

                IconButton(
                    onClick = onArchive,
                    modifier = Modifier.testTag("archive_account_button_${account.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = stringResource(R.string.action_archive),
                        tint = ExpenseTrackerTheme.extendedColors.textSecondary,
                        modifier = Modifier.size(ExpenseTrackerTheme.iconSize.sm)
                    )
                }
            }
        }
    }
}
