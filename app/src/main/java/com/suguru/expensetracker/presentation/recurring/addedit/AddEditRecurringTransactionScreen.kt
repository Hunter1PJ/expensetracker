package com.suguru.expensetracker.presentation.recurring.addedit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suguru.expensetracker.R
import com.suguru.expensetracker.presentation.util.localized
import com.suguru.expensetracker.domain.model.RecurrenceFrequency
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.presentation.components.BackgroundGlowDecoration
import com.suguru.expensetracker.presentation.components.ErrorBanner
import com.suguru.expensetracker.presentation.components.ExpenseTrackerCard
import com.suguru.expensetracker.presentation.components.LoadingState
import com.suguru.expensetracker.presentation.components.SectionHeader
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun AddEditRecurringTransactionScreen(
    viewModel: AddEditRecurringTransactionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onNavigateBack)

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onNavigateBack()
        }
    }

    AddEditRecurringTransactionScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onTypeSelected = viewModel::onTypeSelected,
        onAmountChanged = viewModel::onAmountChanged,
        onCategorySelected = viewModel::onCategorySelected,
        onFrequencySelected = viewModel::onFrequencySelected,
        onStartDateSelected = viewModel::onStartDateSelected,
        onHasEndDateToggled = viewModel::onHasEndDateToggled,
        onEndDateSelected = viewModel::onEndDateSelected,
        onNoteChanged = viewModel::onNoteChanged,
        onSaveRule = viewModel::saveRule,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRecurringTransactionScreenContent(
    uiState: AddEditRecurringUiState,
    onNavigateBack: () -> Unit,
    onTypeSelected: (TransactionType) -> Unit,
    onAmountChanged: (String) -> Unit,
    onCategorySelected: (Long) -> Unit,
    onFrequencySelected: (RecurrenceFrequency) -> Unit,
    onStartDateSelected: (LocalDate) -> Unit,
    onHasEndDateToggled: (Boolean) -> Unit,
    onEndDateSelected: (LocalDate) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSaveRule: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_edit_recurring_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(
                                when {
                                    uiState.isReadOnlyTransfer -> R.string.title_recurring_transfer_readonly
                                    uiState.isEditMode -> R.string.title_edit_recurring
                                    else -> R.string.title_add_recurring
                                }
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = when {
                                uiState.isReadOnlyTransfer -> stringResource(R.string.recurring_header_read_only)
                                uiState.isEditMode -> stringResource(R.string.recurring_header_edit)
                                else -> stringResource(R.string.recurring_header_add)
                            },
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
                        modifier = Modifier.testTag("button_back")
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
        if (uiState.isLoading) {
            LoadingState(
                message = stringResource(R.string.ui_recurring_loading),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                BackgroundGlowDecoration(alpha = 0.08f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(
                            horizontal = ExpenseTrackerSpacing.screenHorizontal,
                            vertical = ExpenseTrackerSpacing.screenVertical
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.lg)
                ) {
                    // Error Banner
                    AnimatedVisibility(visible = uiState.errorMessage != null) {
                        uiState.errorMessage?.let { errorText ->
                            ErrorBanner(
                                message = errorText.localized(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 540.dp),
                                testTag = "error_message"
                            )
                        }
                    }

                    if (uiState.isReadOnlyTransfer) {
                        ExpenseTrackerCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp),
                            shape = RoundedCornerShape(ExpenseTrackerRadius.xl),
                            containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                            borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                            contentPadding = PaddingValues(ExpenseTrackerSpacing.xxl),
                            testTag = "recurring_transfer_readonly_card"
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                        contentDescription = null,
                                        tint = ExpenseTrackerTheme.extendedColors.financialNeutral,
                                        modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                                    )
                                    Column {
                                        Text(
                                            text = stringResource(R.string.detail_transfer_readonly_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = stringResource(R.string.recurring_transfer_readonly_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                                        )
                                    }
                                }

                                if (uiState.readOnlyAmountFormatted.isNotBlank()) {
                                    Text(
                                        text = uiState.readOnlyAmountFormatted,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseTrackerTheme.extendedColors.financialNeutral
                                    )
                                }

                                Text(
                                    text = stringResource(R.string.recurring_transfer_readonly_subtitle),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )

                                Text(
                                    text = stringResource(R.string.recurring_frequency_value, stringResource(R.string.frequency_label), formatFrequency(uiState.frequency)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Text(
                                    text = stringResource(R.string.recurring_start_date_value, stringResource(R.string.start_date_label), uiState.startDate.toString()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )

                                uiState.nextOccurrence?.let { nextDate ->
                                    Text(
                                        text = stringResource(R.string.recurring_next_date_value, stringResource(R.string.next_occurrence_label), nextDate.toString()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )
                                }

                                uiState.endDate?.let { endDate ->
                                    Text(
                                        text = stringResource(R.string.recurring_end_date_value, stringResource(R.string.end_date_label), endDate.toString()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )
                                }

                                if (uiState.noteInput.isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.recurring_note_value, stringResource(R.string.note_label), uiState.noteInput),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )
                                }
                            }
                        }
                    } else if (uiState.isRuleLoaded) {
                        // Card 1: Transaction Type (Expense / Income only) & Amount
                        ExpenseTrackerCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp),
                            containerColor = ExpenseTrackerTheme.extendedColors.surface,
                            borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                            contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
                                SectionHeader(title = stringResource(R.string.ui_recurring_type_amount))

                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME)
                                    types.forEachIndexed { index, type ->
                                        val isSelected = uiState.type == type
                                        SegmentedButton(
                                            selected = isSelected,
                                            onClick = { onTypeSelected(type) },
                                            enabled = if (uiState.isEditMode) isSelected else true,
                                            shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                                            colors = SegmentedButtonDefaults.colors(
                                                activeContainerColor = when (type) {
                                                    TransactionType.EXPENSE -> ExpenseTrackerTheme.extendedColors.financialNegativeContainer
                                                    TransactionType.INCOME -> ExpenseTrackerTheme.extendedColors.financialPositiveContainer
                                                    TransactionType.TRANSFER -> MaterialTheme.colorScheme.primaryContainer
                                                },
                                                activeContentColor = MaterialTheme.colorScheme.onBackground,
                                                inactiveContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                                inactiveContentColor = ExpenseTrackerTheme.extendedColors.textSecondary
                                            ),
                                            modifier = Modifier.testTag("type_segmented_${type.name.lowercase()}")
                                        ) {
                                            Text(
                                                text = when (type) {
                                                    TransactionType.EXPENSE -> stringResource(R.string.type_expense)
                                                    TransactionType.INCOME -> stringResource(R.string.type_income)
                                                    TransactionType.TRANSFER -> stringResource(R.string.type_transfer)
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Amount input
                                OutlinedTextField(
                                    value = uiState.amountInput,
                                    onValueChange = onAmountChanged,
                                    label = { Text(stringResource(R.string.amount_label)) },
                                    placeholder = { Text(stringResource(R.string.amount_hint)) },
                                    prefix = { Text("${uiState.currencySymbol} ") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = ExpenseTrackerRadius.button,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                        unfocusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = ExpenseTrackerTheme.extendedColors.borderSubtle
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("amount_input")
                                )
                            }
                        }

                        // Card 2: Category
                        ExpenseTrackerCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp),
                            containerColor = ExpenseTrackerTheme.extendedColors.surface,
                            borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                            contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
                                SectionHeader(title = stringResource(R.string.category_label))

                                var categoryExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = categoryExpanded,
                                    onExpandedChange = { categoryExpanded = !categoryExpanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val selectedCategory = uiState.availableCategories.find { it.id == uiState.selectedCategoryId }
                                    val selectedLabel = when {
                                        selectedCategory == null -> ""
                                        selectedCategory.isArchived -> stringResource(R.string.category_archived_label, com.suguru.expensetracker.presentation.util.categoryDisplayName(selectedCategory), stringResource(R.string.status_archived))
                                        else -> com.suguru.expensetracker.presentation.util.categoryDisplayName(selectedCategory)
                                    }
                                    OutlinedTextField(
                                        value = selectedLabel,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.category_label)) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                        shape = ExpenseTrackerRadius.button,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                            unfocusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = ExpenseTrackerTheme.extendedColors.borderSubtle
                                        ),
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                            .testTag("category_selector")
                                    )
                                    ExposedDropdownMenu(
                                        expanded = categoryExpanded,
                                        onDismissRequest = { categoryExpanded = false },
                                        modifier = Modifier.background(ExpenseTrackerTheme.extendedColors.surfaceElevated)
                                    ) {
                                        uiState.availableCategories.forEach { category ->
                                            val itemLabel = if (category.isArchived) {
                                                stringResource(R.string.category_archived_label, com.suguru.expensetracker.presentation.util.categoryDisplayName(category), stringResource(R.string.status_archived))
                                            } else {
                                                com.suguru.expensetracker.presentation.util.categoryDisplayName(category)
                                            }
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        itemLabel,
                                                        color = MaterialTheme.colorScheme.onBackground
                                                    )
                                                },
                                                onClick = {
                                                    onCategorySelected(category.id)
                                                    categoryExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Card 3: Recurrence Schedule
                        ExpenseTrackerCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp),
                            containerColor = ExpenseTrackerTheme.extendedColors.surface,
                            borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                            contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
                                SectionHeader(title = stringResource(R.string.ui_recurring_schedule))

                                // Frequency selector (locked in edit mode to reflect persisted immutable frequency)
                                var freqExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = freqExpanded && !uiState.isEditMode,
                                    onExpandedChange = {
                                        if (!uiState.isEditMode) {
                                            freqExpanded = !freqExpanded
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = formatFrequency(uiState.frequency),
                                        onValueChange = {},
                                        readOnly = true,
                                        enabled = !uiState.isEditMode,
                                        label = { Text(stringResource(R.string.frequency_label)) },
                                        supportingText = if (uiState.isEditMode) {
                                            { Text(stringResource(R.string.recurring_frequency_locked_desc)) }
                                        } else null,
                                        trailingIcon = {
                                            if (!uiState.isEditMode) {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded)
                                            }
                                        },
                                        shape = ExpenseTrackerRadius.button,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                            unfocusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                            disabledContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = ExpenseTrackerTheme.extendedColors.borderSubtle
                                        ),
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                            .testTag("frequency_selector")
                                    )
                                    if (!uiState.isEditMode) {
                                        ExposedDropdownMenu(
                                            expanded = freqExpanded,
                                            onDismissRequest = { freqExpanded = false },
                                            modifier = Modifier.background(ExpenseTrackerTheme.extendedColors.surfaceElevated)
                                        ) {
                                            RecurrenceFrequency.entries.forEach { freq ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            formatFrequency(freq),
                                                            color = MaterialTheme.colorScheme.onBackground
                                                        )
                                                    },
                                                    onClick = {
                                                        onFrequencySelected(freq)
                                                        freqExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Start Date
                                OutlinedTextField(
                                    value = uiState.startDate.toString(),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.start_date_label)) },
                                    trailingIcon = {
                                        IconButton(onClick = { showStartDatePicker = true }) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    shape = ExpenseTrackerRadius.button,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                        unfocusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = ExpenseTrackerTheme.extendedColors.borderSubtle
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showStartDatePicker = true }
                                        .testTag("start_date_picker")
                                )

                                // End Date switch
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(ExpenseTrackerRadius.card)
                                        .background(ExpenseTrackerTheme.extendedColors.surfaceElevated)
                                        .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.sm),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.end_date_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Switch(
                                        checked = uiState.hasEndDate,
                                        onCheckedChange = onHasEndDateToggled,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                        modifier = Modifier.testTag("has_end_date_switch")
                                    )
                                }

                                if (uiState.hasEndDate) {
                                    OutlinedTextField(
                                        value = uiState.endDate?.toString() ?: "",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.end_date_label)) },
                                        trailingIcon = {
                                            IconButton(onClick = { showEndDatePicker = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.CalendarToday,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        shape = ExpenseTrackerRadius.button,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                            unfocusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = ExpenseTrackerTheme.extendedColors.borderSubtle
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showEndDatePicker = true }
                                            .testTag("end_date_picker")
                                    )
                                }

                                // Note input
                                OutlinedTextField(
                                    value = uiState.noteInput,
                                    onValueChange = onNoteChanged,
                                    label = { Text(stringResource(R.string.note_label)) },
                                    placeholder = { Text(stringResource(R.string.note_hint)) },
                                    shape = ExpenseTrackerRadius.button,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                        unfocusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = ExpenseTrackerTheme.extendedColors.borderSubtle
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("note_input")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                        // Save Button
                        Button(
                            onClick = onSaveRule,
                            enabled = !uiState.isSaving && !uiState.isReadOnlyTransfer && (uiState.isEditMode || uiState.hasDefaultAccount),
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
                                .height(54.dp)
                                .testTag("save_recurring_button")
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
                                    text = stringResource(
                                        if (uiState.isEditMode) R.string.action_update_recurring else R.string.action_save_recurring
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))
                }
            }
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            onStartDateSelected(localDate)
                        }
                        showStartDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated
                )
            )
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (uiState.endDate ?: uiState.startDate.plusMonths(6)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            onEndDateSelected(localDate)
                        }
                        showEndDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated
                )
            )
        }
    }
}

@Composable
private fun formatFrequency(frequency: RecurrenceFrequency): String {
    return when (frequency) {
        RecurrenceFrequency.DAILY -> stringResource(R.string.frequency_daily)
        RecurrenceFrequency.WEEKLY -> stringResource(R.string.frequency_weekly)
        RecurrenceFrequency.BIWEEKLY -> stringResource(R.string.frequency_biweekly)
        RecurrenceFrequency.MONTHLY -> stringResource(R.string.frequency_monthly)
        RecurrenceFrequency.YEARLY -> stringResource(R.string.frequency_yearly)
    }
}
