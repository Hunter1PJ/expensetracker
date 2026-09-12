package com.suguru.expensetracker.presentation.budgets.addedit

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.model.BudgetPeriodType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.presentation.common.FinanceVisuals
import com.suguru.expensetracker.presentation.components.AppIconButton
import com.suguru.expensetracker.presentation.components.AvatarShape
import com.suguru.expensetracker.presentation.components.BackgroundGlowDecoration
import com.suguru.expensetracker.presentation.components.ErrorBanner
import com.suguru.expensetracker.presentation.components.ExpenseTrackerCard
import com.suguru.expensetracker.presentation.components.ExpenseTrackerFilterChip
import com.suguru.expensetracker.presentation.components.ExpenseTrackerTopBar
import com.suguru.expensetracker.presentation.components.IconAvatar
import com.suguru.expensetracker.presentation.components.LoadingState
import com.suguru.expensetracker.presentation.components.PrimaryButton
import com.suguru.expensetracker.presentation.components.SectionHeader
import com.suguru.expensetracker.presentation.util.DateTimeFormatterHelper
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditBudgetScreen(
    viewModel: AddEditBudgetViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPro: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as? com.suguru.expensetracker.ExpenseTrackerApplication)?.appContainer
    }
    val entitlementState = remember(appContainer) {
        appContainer?.observeProEntitlementUseCase?.invoke() ?: kotlinx.coroutines.flow.flowOf(com.suguru.expensetracker.domain.model.ProEntitlement.Free)
    }.collectAsState(initial = com.suguru.expensetracker.domain.model.ProEntitlement.Checking).value

    val isPending = entitlementState is com.suguru.expensetracker.domain.model.ProEntitlement.Pending

    BackHandler(onBack = onNavigateBack)

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    uiState.limitReachedState?.let { limitState ->
        com.suguru.expensetracker.presentation.components.ContextualPaywallSheet(
            feature = limitState.feature,
            limit = limitState.freeLimit,
            onViewPro = onNavigateToPro,
            onDismiss = viewModel::onDismissLimitSheet,
            isPending = isPending
        )
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }

    val screenTitle = if (uiState.isEditMode) {
        stringResource(R.string.title_edit_budget)
    } else {
        stringResource(R.string.title_add_budget)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_edit_budget_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ExpenseTrackerTopBar(
                title = screenTitle,
                subtitle = "PLANNING",
                onNavigateBack = onNavigateBack,
                testTag = "expense_tracker_top_bar"
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BackgroundGlowDecoration()

            if (uiState.isLoading) {
                LoadingState(message = "Loading budget details…", testTag = "add_edit_budget_loading")
            } else {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                    AnimatedVisibility(visible = uiState.validationError != null) {
                        uiState.validationError?.let { errMessage ->
                            ErrorBanner(
                                message = errMessage,
                                title = "Validation Error",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 540.dp),
                                testTag = "budget_error_banner"
                            )
                        }
                    }

                    // 1. Scope Selector
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                    ) {
                        SectionHeader(
                            title = stringResource(R.string.budget_scope_label),
                            subtitle = "Choose overall total or category-specific limit"
                        )

                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("budget_scope_selector")
                        ) {
                            val scopes = listOf(
                                BudgetScope.OVERALL to stringResource(R.string.budget_scope_overall),
                                BudgetScope.CATEGORY to stringResource(R.string.budget_scope_category)
                            )

                            scopes.forEachIndexed { index, (scope, label) ->
                                val isSelected = uiState.scope == scope
                                SegmentedButton(
                                    selected = isSelected,
                                    onClick = { viewModel.onScopeChanged(scope) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = scopes.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.25f),
                                        activeContentColor = ExpenseTrackerTheme.extendedColors.textPrimary,
                                        activeBorderColor = ExpenseTrackerTheme.extendedColors.primaryPurple,
                                        inactiveContainerColor = ExpenseTrackerTheme.extendedColors.surfaceHigh,
                                        inactiveContentColor = ExpenseTrackerTheme.extendedColors.textSecondary,
                                        inactiveBorderColor = ExpenseTrackerTheme.extendedColors.borderSubtle
                                    ),
                                    modifier = Modifier.testTag("scope_tab_${scope.name.lowercase()}")
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // 2. Category Selector (if CATEGORY scope)
                    if (uiState.scope == BudgetScope.CATEGORY) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp)
                        ) {
                            SectionHeader(
                                title = stringResource(R.string.category_label),
                                subtitle = "Select an expense category to limit"
                            )

                            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                            if (uiState.availableCategories.isEmpty()) {
                                Text(
                                    text = "No expense categories found. Please create an expense category first.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                            } else {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("budget_category_selector"),
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                                ) {
                                    uiState.availableCategories.forEach { category ->
                                        val isSelected = uiState.selectedCategoryId == category.id
                                        CategoryChip(
                                            category = category,
                                            isSelected = isSelected,
                                            onClick = { viewModel.onCategorySelected(category.id) },
                                            testTag = "budget_category_chip_${category.id}"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Amount & Currency Card
                    ExpenseTrackerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp),
                        shape = RoundedCornerShape(ExpenseTrackerRadius.xl),
                        containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                        borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.xxl),
                        testTag = "budget_limit_card"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.budget_limit_label).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                                color = ExpenseTrackerTheme.extendedColors.textSecondary
                            )

                            // Currency Selector Dropdown Box
                            Box {
                                OutlinedButton(
                                    onClick = { currencyDropdownExpanded = true },
                                    shape = ExpenseTrackerRadius.button,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = ExpenseTrackerTheme.extendedColors.surfaceHigh,
                                        contentColor = MaterialTheme.colorScheme.onBackground
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ExpenseTrackerTheme.extendedColors.borderSubtle),
                                    contentPadding = PaddingValues(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.xs),
                                    modifier = Modifier.testTag("budget_currency_selector")
                                ) {
                                    Text(
                                        text = uiState.selectedCurrency,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.xs))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = ExpenseTrackerTheme.extendedColors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = currencyDropdownExpanded,
                                    onDismissRequest = { currencyDropdownExpanded = false }
                                ) {
                                    uiState.availableCurrencies.forEach { curr ->
                                        DropdownMenuItem(
                                            text = { Text(curr, fontWeight = if (curr == uiState.selectedCurrency) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                currencyDropdownExpanded = false
                                                viewModel.onCurrencySelected(curr)
                                            },
                                            modifier = Modifier.testTag("currency_item_$curr")
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val symbol = MoneyParser.getCurrencySymbol(uiState.selectedCurrency)
                            Text(
                                text = symbol,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.sm))

                            OutlinedTextField(
                                value = uiState.amountText,
                                onValueChange = viewModel::onAmountChanged,
                                placeholder = {
                                    Text(
                                        text = "0.00",
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
                                    .testTag("budget_amount_field")
                            )
                        }
                    }

                    // 4. Period Type Selector
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                    ) {
                        SectionHeader(
                            title = stringResource(R.string.budget_period_label),
                            subtitle = "Duration and recurrence cycle"
                        )

                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("budget_period_selector"),
                            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                        ) {
                            listOf(
                                BudgetPeriodType.MONTHLY to stringResource(R.string.budget_period_monthly),
                                BudgetPeriodType.WEEKLY to stringResource(R.string.budget_period_weekly),
                                BudgetPeriodType.YEARLY to stringResource(R.string.budget_period_yearly),
                                BudgetPeriodType.CUSTOM to stringResource(R.string.budget_period_custom)
                            ).forEach { (period, label) ->
                                ExpenseTrackerFilterChip(
                                    text = label,
                                    selected = uiState.periodType == period,
                                    onClick = { viewModel.onPeriodTypeChanged(period) },
                                    testTag = "period_chip_${period.name.lowercase()}"
                                )
                            }
                        }
                    }

                    // 5. Date Range Display or Date Pickers
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                    ) {
                        SectionHeader(
                            title = "Budget Period Dates",
                            subtitle = "Active timeframe for expense tracking"
                        )

                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                        if (uiState.periodType == BudgetPeriodType.CUSTOM) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                            ) {
                                // Start Date Box
                                ExpenseTrackerCard(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { showStartDatePicker = true }
                                        .testTag("budget_start_date_picker"),
                                    shape = ExpenseTrackerRadius.button,
                                    containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                                    borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                                    contentPadding = PaddingValues(ExpenseTrackerSpacing.md)
                                ) {
                                    Text(
                                        text = stringResource(R.string.budget_start_date),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = DateTimeFormatterHelper.formatLocalDate(uiState.startDate),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                // End Date Box
                                ExpenseTrackerCard(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { showEndDatePicker = true }
                                        .testTag("budget_end_date_picker"),
                                    shape = ExpenseTrackerRadius.button,
                                    containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                                    borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                                    contentPadding = PaddingValues(ExpenseTrackerSpacing.md)
                                ) {
                                    Text(
                                        text = stringResource(R.string.budget_end_date),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = DateTimeFormatterHelper.formatLocalDate(uiState.endDate),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        } else {
                            ExpenseTrackerCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ExpenseTrackerRadius.button,
                                containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                                borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                                contentPadding = PaddingValues(ExpenseTrackerSpacing.md)
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
                                        text = "${DateTimeFormatterHelper.formatLocalDate(uiState.startDate)} – ${DateTimeFormatterHelper.formatLocalDate(uiState.endDate)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))

                    // 6. Save Button
                    val saveBtnText = if (uiState.isEditMode) {
                        stringResource(R.string.action_update_budget)
                    } else {
                        stringResource(R.string.action_save_budget)
                    }

                    Button(
                        onClick = { viewModel.saveBudget() },
                        enabled = !uiState.isSubmitting,
                        shape = ExpenseTrackerRadius.button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            disabledContainerColor = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
                            disabledContentColor = ExpenseTrackerTheme.extendedColors.textMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                            .height(52.dp)
                            .testTag("save_budget_button")
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.sm))
                            Text(text = "Saving…", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                text = saveBtnText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))
                }
            }
        }
    }

    // Date Pickers for Custom period
    if (showStartDatePicker) {
        val startMillis = uiState.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startMillis)

        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            viewModel.onStartDateChanged(localDate)
                        }
                        showStartDatePicker = false
                    }
                ) {
                    Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel), color = ExpenseTrackerTheme.extendedColors.textSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val endMillis = uiState.endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endMillis)

        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            viewModel.onEndDateChanged(localDate)
                        }
                        showEndDatePicker = false
                    }
                ) {
                    Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel), color = ExpenseTrackerTheme.extendedColors.textSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
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
    val categoryColor = category.colorHex?.let {
        FinanceVisuals.parseColorHex(it, MaterialTheme.colorScheme.primary)
    } ?: MaterialTheme.colorScheme.primary

    val categoryIcon = category.iconName?.let {
        FinanceVisuals.getCategoryIcon(it)
    } ?: Icons.Outlined.Category

    Surface(
        shape = ExpenseTrackerRadius.chip,
        color = if (isSelected) {
            ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.22f)
        } else {
            ExpenseTrackerTheme.extendedColors.surfaceHigh
        },
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                ExpenseTrackerTheme.extendedColors.primaryPurple
            } else {
                ExpenseTrackerTheme.extendedColors.borderSubtle
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
                imageVector = categoryIcon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else categoryColor,
                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
            )
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onBackground else ExpenseTrackerTheme.extendedColors.textSecondary
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
