package com.example.presentation.transactions.add

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.TransactionType
import com.example.presentation.components.EmptyState
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.SectionHeader
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AddTransactionScreen(
    viewModel: AddTransactionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onNavigateBack)

    LaunchedEffect(uiState.isSavedSuccessfully) {
        if (uiState.isSavedSuccessfully) {
            onNavigateBack()
        }
    }

    AddTransactionScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onTransactionTypeChanged = viewModel::onTransactionTypeChanged,
        onAmountChanged = viewModel::onAmountChanged,
        onAccountSelected = viewModel::onAccountSelected,
        onDestinationAccountSelected = viewModel::onDestinationAccountSelected,
        onCategorySelected = viewModel::onCategorySelected,
        onNoteChanged = viewModel::onNoteChanged,
        onTransactionTimeChanged = viewModel::onTransactionTimeChanged,
        onDismissError = viewModel::onDismissError,
        onSaveTransaction = { viewModel.saveTransaction() },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreenContent(
    uiState: AddTransactionUiState,
    onNavigateBack: () -> Unit,
    onTransactionTypeChanged: (TransactionType) -> Unit,
    onAmountChanged: (String) -> Unit,
    onAccountSelected: (Long) -> Unit,
    onDestinationAccountSelected: (Long) -> Unit,
    onCategorySelected: (Long) -> Unit,
    onNoteChanged: (String) -> Unit,
    onTransactionTimeChanged: (Instant) -> Unit,
    onDismissError: () -> Unit,
    onSaveTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_transaction_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_add_transaction),
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(
                    horizontal = ExpenseTrackerSpacing.screenHorizontal,
                    vertical = ExpenseTrackerSpacing.screenVertical
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xl)
        ) {
            // Error Banner
            AnimatedVisibility(visible = uiState.error != null) {
                uiState.error?.let { err ->
                    val errorMessage = when (err) {
                        is AddTransactionError.InvalidAmount -> stringResource(R.string.error_invalid_amount)
                        is AddTransactionError.AmountMustBePositive -> stringResource(R.string.error_amount_positive)
                        is AddTransactionError.AccountRequired -> stringResource(R.string.error_account_required)
                        is AddTransactionError.CategoryRequired -> stringResource(R.string.error_category_required)
                        is AddTransactionError.DestinationRequired -> stringResource(R.string.error_destination_required)
                        is AddTransactionError.SameSourceAndDestination -> stringResource(R.string.error_same_accounts)
                        is AddTransactionError.CurrencyMismatch -> stringResource(R.string.error_currency_mismatch)
                        is AddTransactionError.AccountArchived -> stringResource(R.string.error_account_archived)
                        is AddTransactionError.CategoryArchived -> stringResource(R.string.error_category_archived)
                        is AddTransactionError.IncompatibleCategory -> stringResource(R.string.error_incompatible_category)
                        is AddTransactionError.Unknown -> err.message ?: stringResource(R.string.error_unknown)
                    }

                    ExpenseTrackerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp),
                        containerColor = ExpenseTrackerTheme.extendedColors.financialNegativeContainer.copy(alpha = 0.4f),
                        borderColor = ExpenseTrackerTheme.extendedColors.financialNegative,
                        testTag = "add_transaction_error_banner"
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

            // 1. Transaction Type Segmented Row
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .testTag("transaction_type_selector")
            ) {
                val types = listOf(
                    TransactionType.EXPENSE to stringResource(R.string.type_expense),
                    TransactionType.INCOME to stringResource(R.string.type_income),
                    TransactionType.TRANSFER to stringResource(R.string.type_transfer)
                )

                types.forEachIndexed { index, (type, label) ->
                    val isSelected = uiState.transactionType == type
                    SegmentedButton(
                        selected = isSelected,
                        onClick = { onTransactionTypeChanged(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = when (type) {
                                TransactionType.EXPENSE -> ExpenseTrackerTheme.extendedColors.financialNegativeContainer
                                TransactionType.INCOME -> ExpenseTrackerTheme.extendedColors.financialPositiveContainer
                                TransactionType.TRANSFER -> MaterialTheme.colorScheme.primaryContainer
                            },
                            activeContentColor = MaterialTheme.colorScheme.onBackground,
                            inactiveContainerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                            inactiveContentColor = ExpenseTrackerTheme.extendedColors.textSecondary
                        ),
                        modifier = Modifier.testTag("type_tab_${type.name.lowercase()}")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // 2. Amount Input Hero Card
            ExpenseTrackerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp),
                shape = RoundedCornerShape(ExpenseTrackerRadius.xl),
                containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                contentPadding = PaddingValues(ExpenseTrackerSpacing.xxl),
                testTag = "amount_card"
            ) {
                Text(
                    text = stringResource(R.string.amount_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.currencySymbol,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = when (uiState.transactionType) {
                            TransactionType.EXPENSE -> ExpenseTrackerTheme.extendedColors.financialNegative
                            TransactionType.INCOME -> ExpenseTrackerTheme.extendedColors.financialPositive
                            TransactionType.TRANSFER -> MaterialTheme.colorScheme.primary
                        }
                    )

                    Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.sm))

                    OutlinedTextField(
                        value = uiState.amount,
                        onValueChange = onAmountChanged,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.amount_hint),
                                style = MaterialTheme.typography.displaySmall,
                                color = ExpenseTrackerTheme.extendedColors.textTertiary
                            )
                        },
                        textStyle = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            errorBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("amount_input_field")
                    )
                }
            }

            // Check if accounts exist
            if (!uiState.hasAccounts) {
                EmptyState(
                    title = stringResource(R.string.no_accounts_title),
                    description = stringResource(R.string.no_accounts_desc),
                    icon = Icons.Default.AccountBalance,
                    modifier = Modifier.widthIn(max = 540.dp),
                    testTag = "no_accounts_empty_state"
                )
            } else {
                // 3. Source Account Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                ) {
                    SectionHeader(
                        title = if (uiState.transactionType == TransactionType.TRANSFER) {
                            stringResource(R.string.from_account_label)
                        } else {
                            stringResource(R.string.account_label)
                        }
                    )

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_selector"),
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        uiState.accounts.forEach { account ->
                            val isSelected = uiState.selectedAccountId == account.id
                            AccountChip(
                                account = account,
                                isSelected = isSelected,
                                onClick = { onAccountSelected(account.id) },
                                testTag = "account_item_${account.id}"
                            )
                        }
                    }
                }

                // 4. Destination Account Selector (If Transfer)
                if (uiState.transactionType == TransactionType.TRANSFER) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                    ) {
                        SectionHeader(title = stringResource(R.string.to_account_label))

                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                        if (uiState.validDestinationAccounts.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_dest_accounts_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ExpenseTrackerTheme.extendedColors.textSecondary,
                                modifier = Modifier.padding(vertical = ExpenseTrackerSpacing.sm)
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("destination_account_selector"),
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                            ) {
                                uiState.validDestinationAccounts.forEach { account ->
                                    val isSelected = uiState.selectedDestinationAccountId == account.id
                                    AccountChip(
                                        account = account,
                                        isSelected = isSelected,
                                        onClick = { onDestinationAccountSelected(account.id) },
                                        testTag = "dest_account_item_${account.id}"
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Category Selector (If Expense or Income)
                if (uiState.transactionType != TransactionType.TRANSFER) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                    ) {
                        SectionHeader(title = stringResource(R.string.category_label))

                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                        if (uiState.categories.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_categories_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ExpenseTrackerTheme.extendedColors.textSecondary,
                                modifier = Modifier.padding(vertical = ExpenseTrackerSpacing.sm)
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_selector"),
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                            ) {
                                uiState.categories.forEach { category ->
                                    val isSelected = uiState.selectedCategoryId == category.id
                                    CategoryChip(
                                        category = category,
                                        isSelected = isSelected,
                                        onClick = { onCategorySelected(category.id) },
                                        testTag = "category_item_${category.id}"
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Note Field
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                ) {
                    SectionHeader(title = stringResource(R.string.note_label))

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                    OutlinedTextField(
                        value = uiState.note,
                        onValueChange = onNoteChanged,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.note_hint),
                                color = ExpenseTrackerTheme.extendedColors.textTertiary
                            )
                        },
                        shape = ExpenseTrackerRadius.button,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                            unfocusedContainerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = ExpenseTrackerTheme.extendedColors.cardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_input_field")
                    )
                }

                // 7. Date Selector Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                ) {
                    SectionHeader(title = stringResource(R.string.date_time_label))

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                    val formattedDate = remember(uiState.transactionTime) {
                        val localDate = uiState.transactionTime.atZone(ZoneId.systemDefault()).toLocalDate()
                        localDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                    }

                    ExpenseTrackerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                            .testTag("date_picker_button"),
                        shape = ExpenseTrackerRadius.button,
                        containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                        borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                        contentPadding = PaddingValues(
                            horizontal = ExpenseTrackerSpacing.lg,
                            vertical = ExpenseTrackerSpacing.md
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.sm)
                                )
                                Text(
                                    text = formattedDate,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))

                // 8. Save Button
                Button(
                    onClick = onSaveTransaction,
                    enabled = !uiState.isSaving && uiState.hasAccounts,
                    shape = ExpenseTrackerRadius.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
                        disabledContentColor = ExpenseTrackerTheme.extendedColors.textSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                        .height(52.dp)
                        .testTag("save_transaction_button")
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.sm))
                        Text(text = stringResource(R.string.action_saving))
                    } else {
                        Text(
                            text = stringResource(R.string.action_save),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))
        }
    }

    // Material 3 DatePickerDialog
    if (showDatePicker) {
        val initialMillis = uiState.transactionTime.toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val selectedInstant = Instant.ofEpochMilli(selectedMillis)
                            onTransactionTimeChanged(selectedInstant)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(text = "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun AccountChip(
    account: Account,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        shape = ExpenseTrackerRadius.chip,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            ExpenseTrackerTheme.extendedColors.cardBackground
        },
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                ExpenseTrackerTheme.extendedColors.cardBorder
            }
        ),
        modifier = modifier
            .clip(ExpenseTrackerRadius.chip)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ExpenseTrackerSpacing.md,
                vertical = ExpenseTrackerSpacing.sm
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else ExpenseTrackerTheme.extendedColors.textSecondary,
                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
            )
            Text(
                text = account.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        shape = ExpenseTrackerRadius.chip,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            ExpenseTrackerTheme.extendedColors.cardBackground
        },
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                ExpenseTrackerTheme.extendedColors.cardBorder
            }
        ),
        modifier = modifier
            .clip(ExpenseTrackerRadius.chip)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ExpenseTrackerSpacing.md,
                vertical = ExpenseTrackerSpacing.sm
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
        ) {
            Icon(
                imageVector = Icons.Default.Category,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else ExpenseTrackerTheme.extendedColors.textSecondary,
                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
            )
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
                )
            }
        }
    }
}
