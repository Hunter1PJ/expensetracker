package com.example.presentation.accounts.management

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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.Account
import com.example.presentation.common.FinanceVisuals
import com.example.presentation.components.EmptyState
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.LoadingState
import com.example.presentation.components.PrimaryButton
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

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
                    Text(
                        text = stringResource(R.string.title_account_management),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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

                    ExpenseTrackerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                            .padding(bottom = ExpenseTrackerSpacing.md),
                        containerColor = ExpenseTrackerTheme.extendedColors.financialNegativeContainer.copy(alpha = 0.4f),
                        borderColor = ExpenseTrackerTheme.extendedColors.financialNegative,
                        testTag = "account_management_error_banner"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = ExpenseTrackerTheme.extendedColors.financialNegative,
                                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
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
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
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

    // Archive Confirmation Dialog
    if (uiState.accountToArchive != null) {
        val account = uiState.accountToArchive
        AlertDialog(
            onDismissRequest = onDismissArchiveDialog,
            title = {
                Text(
                    text = stringResource(R.string.dialog_archive_account_title, account.name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_archive_account_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmArchive,
                    enabled = !uiState.isArchiving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpenseTrackerTheme.extendedColors.financialNegative,
                        contentColor = Color.White
                    ),
                    shape = ExpenseTrackerRadius.button,
                    modifier = Modifier.testTag("confirm_archive_button")
                ) {
                    if (uiState.isArchiving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(text = stringResource(R.string.action_archive))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissArchiveDialog,
                    enabled = !uiState.isArchiving,
                    modifier = Modifier.testTag("cancel_archive_button")
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
            containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
            shape = ExpenseTrackerRadius.card
        )
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
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
        borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
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
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                    )
                }

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
                            shape = RoundedCornerShape(ExpenseTrackerRadius.xs),
                            color = ExpenseTrackerTheme.extendedColors.surfaceHighlight
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = ExpenseTrackerTheme.extendedColors.textSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Currency Tag
                        Surface(
                            shape = RoundedCornerShape(ExpenseTrackerRadius.xs),
                            color = ExpenseTrackerTheme.extendedColors.surfaceHighlight
                        ) {
                            Text(
                                text = account.initialBalance.currencyCode,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

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
