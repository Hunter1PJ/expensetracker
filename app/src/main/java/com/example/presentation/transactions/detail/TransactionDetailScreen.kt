package com.example.presentation.transactions.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.TransactionType
import com.example.presentation.components.BackgroundGlowDecoration
import com.example.presentation.components.DangerButton
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.ExpenseTrackerConfirmationDialog
import com.example.presentation.components.ExpenseTrackerStatusChip
import com.example.presentation.components.IconAvatar
import com.example.presentation.components.PrimaryButton
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

@Composable
fun TransactionDetailScreen(
    viewModel: TransactionDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    confirmBeforeDelete: Boolean = true,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isDeletedSuccessfully) {
        if (uiState.isDeletedSuccessfully) {
            onNavigateBack()
        }
    }

    TransactionDetailScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToEdit = { uiState.transaction?.id?.let { onNavigateToEdit(it) } },
        onDeleteTransaction = viewModel::deleteTransaction,
        confirmBeforeDelete = confirmBeforeDelete,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreenContent(
    uiState: TransactionDetailUiState,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onDeleteTransaction: () -> Unit,
    confirmBeforeDelete: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundGlowDecoration()

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("transaction_detail_screen"),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.title_transaction_details),
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
                    actions = {
                        if (uiState.transaction != null) {
                            IconButton(
                                onClick = onNavigateToEdit,
                                modifier = Modifier.testTag("edit_transaction_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.action_edit),
                                    tint = ExpenseTrackerTheme.extendedColors.primaryPurple
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (confirmBeforeDelete) {
                                        showDeleteDialog = true
                                    } else {
                                        onDeleteTransaction()
                                    }
                                },
                                modifier = Modifier.testTag("delete_transaction_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = ExpenseTrackerTheme.extendedColors.financialNegative
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { innerPadding ->
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = ExpenseTrackerTheme.extendedColors.primaryPurple,
                        modifier = Modifier.testTag("detail_loading_indicator")
                    )
                }
            } else if (uiState.errorMessage != null && uiState.transaction == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(ExpenseTrackerSpacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    ExpenseTrackerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp),
                        containerColor = ExpenseTrackerTheme.extendedColors.financialNegativeContainer.copy(alpha = 0.3f),
                        borderColor = ExpenseTrackerTheme.extendedColors.financialNegative
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = ExpenseTrackerTheme.extendedColors.financialNegative,
                                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.lg)
                            )
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(scrollState)
                        .padding(
                            horizontal = ExpenseTrackerSpacing.screenHorizontal,
                            vertical = ExpenseTrackerSpacing.screenVertical
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.lg)
                ) {
                    // 1. Hero Amount Card
                    val (typeColor, typeBgColor, typeIcon, typeLabel) = when (uiState.type) {
                        TransactionType.EXPENSE -> Quad(
                            ExpenseTrackerTheme.extendedColors.financialNegative,
                            ExpenseTrackerTheme.extendedColors.financialNegative.copy(alpha = 0.16f),
                            Icons.Default.ArrowDownward,
                            stringResource(R.string.type_expense)
                        )
                        TransactionType.INCOME -> Quad(
                            ExpenseTrackerTheme.extendedColors.financialPositive,
                            ExpenseTrackerTheme.extendedColors.financialPositive.copy(alpha = 0.16f),
                            Icons.Default.ArrowUpward,
                            stringResource(R.string.type_income)
                        )
                        TransactionType.TRANSFER -> Quad(
                            ExpenseTrackerTheme.extendedColors.financialNeutral,
                            ExpenseTrackerTheme.extendedColors.financialNeutral.copy(alpha = 0.16f),
                            Icons.AutoMirrored.Filled.CompareArrows,
                            stringResource(R.string.type_transfer)
                        )
                        null -> Quad(
                            MaterialTheme.colorScheme.onBackground,
                            MaterialTheme.colorScheme.surfaceVariant,
                            Icons.Default.Description,
                            ""
                        )
                    }

                    ExpenseTrackerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp),
                        shape = RoundedCornerShape(ExpenseTrackerRadius.xl),
                        containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                        borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.xxl),
                        testTag = "detail_amount_card"
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Avatar
                            IconAvatar(
                                icon = typeIcon,
                                contentDescription = typeLabel,
                                tint = typeColor,
                                backgroundColor = typeBgColor,
                                size = 52.dp
                            )

                            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))

                            // Type Status Chip
                            ExpenseTrackerStatusChip(
                                text = typeLabel.uppercase(),
                                backgroundColor = typeBgColor,
                                contentColor = typeColor
                            )

                            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                            // Amount Text
                            Text(
                                text = uiState.amountFormatted,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = typeColor,
                                modifier = Modifier.testTag("detail_amount_text")
                            )
                        }
                    }

                    // 2. Info Cards Container
                    ExpenseTrackerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp),
                        shape = RoundedCornerShape(ExpenseTrackerRadius.lg),
                        containerColor = ExpenseTrackerTheme.extendedColors.surface,
                        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
                        testTag = "detail_info_card"
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                        ) {
                            // Source Account
                            DetailInfoRow(
                                icon = Icons.Default.AccountBalance,
                                label = if (uiState.isTransfer) stringResource(R.string.from_account_label) else stringResource(R.string.account_label),
                                value = uiState.sourceAccount?.name ?: "Unknown Account",
                                isArchived = uiState.isSourceAccountArchived,
                                testTag = "detail_source_account"
                            )

                            // Destination Account (if Transfer)
                            if (uiState.isTransfer) {
                                HorizontalDivider(color = ExpenseTrackerTheme.extendedColors.borderSubtle, thickness = 0.5.dp)
                                DetailInfoRow(
                                    icon = Icons.AutoMirrored.Filled.CompareArrows,
                                    label = stringResource(R.string.to_account_label),
                                    value = uiState.destinationAccount?.name ?: "Unknown Account",
                                    isArchived = uiState.isDestinationAccountArchived,
                                    testTag = "detail_dest_account"
                                )
                            }

                            // Category (if Expense/Income)
                            if (!uiState.isTransfer) {
                                HorizontalDivider(color = ExpenseTrackerTheme.extendedColors.borderSubtle, thickness = 0.5.dp)
                                DetailInfoRow(
                                    icon = Icons.Default.Category,
                                    label = stringResource(R.string.category_label),
                                    value = uiState.category?.name ?: "Uncategorized",
                                    isArchived = uiState.isCategoryArchived,
                                    testTag = "detail_category"
                                )
                            }

                            // Date & Time
                            HorizontalDivider(color = ExpenseTrackerTheme.extendedColors.borderSubtle, thickness = 0.5.dp)
                            DetailInfoRow(
                                icon = Icons.Default.CalendarToday,
                                label = stringResource(R.string.date_time_label),
                                value = uiState.dateTimeFormatted,
                                testTag = "detail_date_time"
                            )

                            // Note (if present)
                            if (!uiState.note.isNullOrBlank()) {
                                HorizontalDivider(color = ExpenseTrackerTheme.extendedColors.borderSubtle, thickness = 0.5.dp)
                                DetailInfoRow(
                                    icon = Icons.Default.Description,
                                    label = stringResource(R.string.note_label),
                                    value = uiState.note,
                                    testTag = "detail_note"
                                )
                            }
                        }
                    }

                    // 3. Recurring rule indicator (if applicable)
                    if (uiState.transaction?.recurringRuleId != null) {
                        ExpenseTrackerCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp),
                            shape = RoundedCornerShape(ExpenseTrackerRadius.lg),
                            containerColor = ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.12f),
                            borderColor = ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.35f),
                            contentPadding = PaddingValues(ExpenseTrackerSpacing.md)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = ExpenseTrackerTheme.extendedColors.primaryPurple,
                                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                                )
                                Column {
                                    Text(
                                        text = "Recurring Transaction",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "Generated automatically from a recurring schedule",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                    // 4. Primary Actions
                    PrimaryButton(
                        text = stringResource(R.string.action_edit),
                        onClick = onNavigateToEdit,
                        leadingIcon = Icons.Default.Edit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                    )

                    DangerButton(
                        text = stringResource(R.string.action_delete),
                        onClick = {
                            if (confirmBeforeDelete) {
                                showDeleteDialog = true
                            } else {
                                onDeleteTransaction()
                            }
                        },
                        leadingIcon = Icons.Default.Delete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                    )

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        ExpenseTrackerConfirmationDialog(
            title = stringResource(R.string.delete_transaction_title),
            message = stringResource(R.string.delete_transaction_message),
            confirmText = stringResource(R.string.action_delete),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                showDeleteDialog = false
                onDeleteTransaction()
            },
            onDismissRequest = {
                if (!uiState.isDeleting) showDeleteDialog = false
            },
            isDestructive = true,
            confirmTestTag = "confirm_delete_button",
            dismissTestTag = "cancel_delete_button",
            testTag = "delete_transaction_dialog"
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun DetailInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isArchived: Boolean = false,
    testTag: String
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ExpenseTrackerTheme.extendedColors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ExpenseTrackerTheme.extendedColors.primaryPurple,
                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.sm)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ExpenseTrackerTheme.extendedColors.textSecondary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (isArchived) {
                    Surface(
                        shape = ExpenseTrackerRadius.chip,
                        color = ExpenseTrackerTheme.extendedColors.surfaceHighlight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                tint = ExpenseTrackerTheme.extendedColors.textTertiary,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = stringResource(R.string.status_archived),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = ExpenseTrackerTheme.extendedColors.textTertiary
                            )
                        }
                    }
                }
            }
        }
    }
}
