package com.example.presentation.recurring.addedit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.RecurrenceFrequency
import com.example.domain.model.TransactionType
import com.example.presentation.components.ExpenseTrackerCard
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
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

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_edit_recurring_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isEditMode) R.string.title_edit_recurring else R.string.title_add_recurring
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("button_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = ExpenseTrackerSpacing.screenHorizontal,
                        vertical = ExpenseTrackerSpacing.screenVertical
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 540.dp),
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                ) {
                    // Type selector
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
                        types.forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = uiState.type == type,
                                onClick = { viewModel.onTypeSelected(type) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                                modifier = Modifier.testTag("type_segmented_${type.name.lowercase()}")
                            ) {
                                Text(
                                    text = when (type) {
                                        TransactionType.EXPENSE -> stringResource(R.string.type_expense)
                                        TransactionType.INCOME -> stringResource(R.string.type_income)
                                        TransactionType.TRANSFER -> stringResource(R.string.type_transfer)
                                    }
                                )
                            }
                        }
                    }

                    // Amount input
                    OutlinedTextField(
                        value = uiState.amountInput,
                        onValueChange = { viewModel.onAmountChanged(it) },
                        label = { Text(stringResource(R.string.amount_label)) },
                        placeholder = { Text(stringResource(R.string.amount_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("amount_input")
                    )

                    // Account selector
                    var accountExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = !accountExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val selectedAccount = uiState.availableAccounts.find { it.id == uiState.selectedAccountId }
                        OutlinedTextField(
                            value = selectedAccount?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = {
                                Text(
                                    stringResource(
                                        if (uiState.type == TransactionType.TRANSFER) R.string.from_account_label else R.string.account_label
                                    )
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("account_selector")
                        )
                        ExposedDropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false }
                        ) {
                            uiState.availableAccounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text("${account.name} (${account.initialBalance.currencyCode})") },
                                    onClick = {
                                        viewModel.onAccountSelected(account.id)
                                        accountExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Destination Account selector for Transfer
                    if (uiState.type == TransactionType.TRANSFER) {
                        var destExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = destExpanded,
                            onExpandedChange = { destExpanded = !destExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectedDest = uiState.availableAccounts.find { it.id == uiState.selectedDestinationAccountId }
                            OutlinedTextField(
                                value = selectedDest?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.to_account_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("destination_account_selector")
                            )
                            ExposedDropdownMenu(
                                expanded = destExpanded,
                                onDismissRequest = { destExpanded = false }
                            ) {
                                uiState.availableAccounts.filter { it.id != uiState.selectedAccountId }.forEach { account ->
                                    DropdownMenuItem(
                                        text = { Text("${account.name} (${account.initialBalance.currencyCode})") },
                                        onClick = {
                                            viewModel.onDestinationAccountSelected(account.id)
                                            destExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Category selector for Expense / Income
                    if (uiState.type != TransactionType.TRANSFER) {
                        var categoryExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = !categoryExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectedCategory = uiState.availableCategories.find { it.id == uiState.selectedCategoryId }
                            OutlinedTextField(
                                value = selectedCategory?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.category_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("category_selector")
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                uiState.availableCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            viewModel.onCategorySelected(category.id)
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Frequency selector
                    var freqExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = freqExpanded,
                        onExpandedChange = { freqExpanded = !freqExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = formatFrequency(uiState.frequency),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.frequency_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("frequency_selector")
                        )
                        ExposedDropdownMenu(
                            expanded = freqExpanded,
                            onDismissRequest = { freqExpanded = false }
                        ) {
                            RecurrenceFrequency.entries.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text(formatFrequency(freq)) },
                                    onClick = {
                                        viewModel.onFrequencySelected(freq)
                                        freqExpanded = false
                                    }
                                )
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
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showStartDatePicker = true }
                            .testTag("start_date_picker")
                    )

                    // End Date switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.end_date_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Switch(
                            checked = uiState.hasEndDate,
                            onCheckedChange = { viewModel.onHasEndDateToggled(it) },
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
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEndDatePicker = true }
                                .testTag("end_date_picker")
                        )
                    }

                    // Note input
                    OutlinedTextField(
                        value = uiState.noteInput,
                        onValueChange = { viewModel.onNoteChanged(it) },
                        label = { Text(stringResource(R.string.note_label)) },
                        placeholder = { Text(stringResource(R.string.note_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_input")
                    )

                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("error_message")
                        )
                    }

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                    Button(
                        onClick = { viewModel.saveRule() },
                        enabled = !uiState.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_recurring_button")
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    if (uiState.isEditMode) R.string.action_update_recurring else R.string.action_save_recurring
                                )
                            )
                        }
                    }
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
                            viewModel.onStartDateSelected(localDate)
                        }
                        showStartDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
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
                            viewModel.onEndDateSelected(localDate)
                        }
                        showEndDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
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
