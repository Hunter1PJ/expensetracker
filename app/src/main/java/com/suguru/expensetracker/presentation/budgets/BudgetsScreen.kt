package com.suguru.expensetracker.presentation.budgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.model.BudgetPeriodType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.budget.BudgetProgress
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.presentation.common.FinanceVisuals
import com.suguru.expensetracker.presentation.components.AvatarShape
import com.suguru.expensetracker.presentation.components.BackgroundGlowDecoration
import com.suguru.expensetracker.presentation.components.EmptyState
import com.suguru.expensetracker.presentation.components.ExpenseTrackerCard
import com.suguru.expensetracker.presentation.components.ExpenseTrackerConfirmationDialog
import com.suguru.expensetracker.presentation.components.ExpenseTrackerFilterChip
import com.suguru.expensetracker.presentation.components.ExpenseTrackerProgressBar
import com.suguru.expensetracker.presentation.components.ExpenseTrackerStatusChip
import com.suguru.expensetracker.presentation.components.IconAvatar
import com.suguru.expensetracker.presentation.components.LoadingState
import com.suguru.expensetracker.presentation.components.ProgressVariant
import com.suguru.expensetracker.presentation.components.SectionHeader
import com.suguru.expensetracker.presentation.util.DateTimeFormatterHelper
import com.suguru.expensetracker.ui.theme.ExpenseTrackerGradients
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel,
    onNavigateToAddBudget: () -> Unit,
    onNavigateToEditBudget: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.testTag("budgets_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!uiState.isLoading && uiState.budgets.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onNavigateToAddBudget,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = ExpenseTrackerRadius.button,
                    modifier = Modifier.testTag("fab_add_budget")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add_budget)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Subtle ambient violet glow decoration
            BackgroundGlowDecoration()

            when {
                uiState.isLoading -> {
                    LoadingState(
                        message = "Loading active budgets…",
                        testTag = "budgets_loading"
                    )
                }

                uiState.budgets.isEmpty() && uiState.availableCurrencies.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ExpenseTrackerSpacing.screenHorizontal),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpenseTrackerCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp),
                            shape = ExpenseTrackerRadius.card,
                            contentPadding = PaddingValues(ExpenseTrackerSpacing.xl),
                            testTag = "budgets_empty_card"
                        ) {
                            EmptyState(
                                title = stringResource(R.string.no_budgets_title),
                                description = stringResource(R.string.no_budgets_desc),
                                icon = Icons.Outlined.AccountBalanceWallet,
                                testTag = "budgets_empty_state",
                                action = {
                                    TextButton(
                                        onClick = onNavigateToAddBudget,
                                        modifier = Modifier.testTag("budgets_empty_action")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_add_budget),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                else -> {
                    val overallBudgets = uiState.budgets.filter { it.budget.categoryId == null }
                    val categoryBudgets = uiState.budgets.filter { it.budget.categoryId != null }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("budgets_list"),
                        contentPadding = PaddingValues(
                            horizontal = ExpenseTrackerSpacing.screenHorizontal,
                            vertical = ExpenseTrackerSpacing.screenVertical
                        ),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.lg)
                    ) {
                        // Header
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "PLANNING",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.5.sp,
                                    color = ExpenseTrackerTheme.extendedColors.primaryPurple
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.title_budgets),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Stay in control of your spending limits",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                            }
                        }

                        // Currency Filter Chips (if multiple currencies exist)
                        if (uiState.availableCurrencies.size > 1) {
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                                    modifier = Modifier.testTag("currency_filter_chips")
                                ) {
                                    item {
                                        ExpenseTrackerFilterChip(
                                            text = "All Currencies",
                                            selected = uiState.selectedCurrencyFilter == null,
                                            onClick = { viewModel.selectCurrencyFilter(null) },
                                            testTag = "currency_chip_all"
                                        )
                                    }
                                    items(uiState.availableCurrencies) { curr ->
                                        ExpenseTrackerFilterChip(
                                            text = curr,
                                            selected = uiState.selectedCurrencyFilter == curr,
                                            onClick = { viewModel.selectCurrencyFilter(curr) },
                                            testTag = "currency_chip_$curr"
                                        )
                                    }
                                }
                            }
                        }

                        // Budget Overview Hero
                        item {
                            BudgetOverviewHero(
                                budgets = uiState.budgets,
                                selectedCurrency = uiState.selectedCurrencyFilter,
                                availableCurrencies = uiState.availableCurrencies,
                                onSelectCurrency = { viewModel.selectCurrencyFilter(it) }
                            )
                        }

                        // Currency-specific empty state if filter is active but no budgets
                        if (uiState.budgets.isEmpty() && uiState.selectedCurrencyFilter != null) {
                            item {
                                ExpenseTrackerCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ExpenseTrackerRadius.card,
                                    contentPadding = PaddingValues(ExpenseTrackerSpacing.xl),
                                    testTag = "budgets_empty_card"
                                ) {
                                    EmptyState(
                                        title = "No ${uiState.selectedCurrencyFilter} budgets",
                                        description = "Create a spending limit for ${uiState.selectedCurrencyFilter} to track your progress.",
                                        icon = Icons.Outlined.Savings,
                                        testTag = "budgets_empty_state",
                                        action = {
                                            TextButton(
                                                onClick = onNavigateToAddBudget,
                                                modifier = Modifier.testTag("budgets_empty_action")
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.action_add_budget),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Overall Budgets Section
                        if (overallBudgets.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Overall Budgets",
                                    subtitle = "Total spending limits across accounts"
                                )
                            }
                            items(overallBudgets, key = { it.budget.id }) { item ->
                                OverallBudgetCard(
                                    progress = item,
                                    onEditClick = { onNavigateToEditBudget(item.budget.id) },
                                    onDeactivateClick = { viewModel.promptDeactivate(item.budget.id) }
                                )
                            }
                        }

                        // Category Budgets Section
                        if (categoryBudgets.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Category Budgets",
                                    subtitle = "Category-specific allocations"
                                )
                            }
                            items(categoryBudgets, key = { it.budget.id }) { item ->
                                CategoryBudgetCard(
                                    progress = item,
                                    onEditClick = { onNavigateToEditBudget(item.budget.id) },
                                    onDeactivateClick = { viewModel.promptDeactivate(item.budget.id) }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }
    }

    // Deactivate Confirmation Dialog
    if (uiState.deactivatingBudgetId != null) {
        ExpenseTrackerConfirmationDialog(
            title = stringResource(R.string.dialog_deactivate_budget_title),
            message = stringResource(R.string.dialog_deactivate_budget_message),
            confirmText = stringResource(R.string.action_deactivate),
            dismissText = stringResource(R.string.action_cancel),
            isDestructive = true,
            onConfirm = { viewModel.confirmDeactivate() },
            onDismissRequest = { viewModel.cancelDeactivate() },
            confirmTestTag = "dialog_confirm_deactivate_budget",
            dismissTestTag = "dialog_cancel_deactivate_budget",
            testTag = "deactivate_budget_dialog"
        )
    }
}

/**
 * Premium Hero Summary Card for Active Budgets with multi-currency isolation safety.
 */
@Composable
private fun BudgetOverviewHero(
    budgets: List<BudgetProgress>,
    selectedCurrency: String?,
    availableCurrencies: List<String>,
    onSelectCurrency: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCurrency = selectedCurrency ?: if (availableCurrencies.size == 1) availableCurrencies.first() else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExpenseTrackerRadius.cardHero)
            .background(ExpenseTrackerGradients.heroCard)
            .border(1.25.dp, ExpenseTrackerGradients.heroCardBorder, ExpenseTrackerRadius.cardHero)
            .drawBehind {
                val glowRadius = size.width * 0.55f
                val glowCenter = Offset(size.width * 0.88f, size.height * 0.12f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x28A855F7),
                            Color.Transparent
                        ),
                        center = glowCenter,
                        radius = glowRadius
                    ),
                    center = glowCenter,
                    radius = glowRadius
                )
            }
            .padding(ExpenseTrackerSpacing.xxl)
            .testTag("budget_overview_hero")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BUDGET OVERVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )

                val countText = "${budgets.size} active budget${if (budgets.size != 1) "s" else ""}"
                Box(
                    modifier = Modifier
                        .clip(ExpenseTrackerRadius.chipPill)
                        .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                        .padding(horizontal = ExpenseTrackerSpacing.sm, vertical = ExpenseTrackerSpacing.xxs)
                ) {
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelSmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))

            if (activeCurrency != null) {
                // Single active currency view: aggregate spent & limit safely
                val currencyBudgets = budgets.filter { it.budget.limitAmount.currencyCode == activeCurrency }
                var totalSpentMinor = 0L
                var totalLimitMinor = 0L
                for (b in currencyBudgets) {
                    totalSpentMinor += b.spent.amountInMinorUnits
                    totalLimitMinor += b.budget.limitAmount.amountInMinorUnits
                }

                val totalSpentMoney = Money(totalSpentMinor, activeCurrency)
                val totalLimitMoney = Money(totalLimitMinor, activeCurrency)

                val totalSpentStr = MoneyParser.format(totalSpentMoney, useGrouping = true)
                val totalLimitStr = MoneyParser.format(totalLimitMoney, useGrouping = true)

                val ratio = if (totalLimitMinor > 0L) totalSpentMinor.toFloat() / totalLimitMinor.toFloat() else 0f
                val percentUsed = (ratio * 100).toInt()
                val isExceeded = totalSpentMinor > totalLimitMinor

                val heroVariant = when {
                    isExceeded -> ProgressVariant.EXCEEDED
                    ratio >= 0.8f -> ProgressVariant.WARNING
                    else -> ProgressVariant.NORMAL
                }

                Text(
                    text = "$totalSpentStr / $totalLimitStr",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseTrackerTheme.extendedColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$percentUsed% total used",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when (heroVariant) {
                            ProgressVariant.EXCEEDED -> ExpenseTrackerTheme.extendedColors.financialDanger
                            ProgressVariant.WARNING -> ExpenseTrackerTheme.extendedColors.financialWarning
                            ProgressVariant.NORMAL -> ExpenseTrackerTheme.extendedColors.primaryPurple
                        }
                    )

                    val remainingMinor = if (totalSpentMinor >= totalLimitMinor) 0L else (totalLimitMinor - totalSpentMinor)
                    val remainingText = if (isExceeded) {
                        val overMoney = Money(totalSpentMinor - totalLimitMinor, activeCurrency)
                        "${MoneyParser.format(overMoney, useGrouping = true)} over limit"
                    } else {
                        val remMoney = Money(remainingMinor, activeCurrency)
                        "${MoneyParser.format(remMoney, useGrouping = true)} remaining"
                    }

                    Text(
                        text = remainingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isExceeded) ExpenseTrackerTheme.extendedColors.financialDanger else ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                ExpenseTrackerProgressBar(
                    progress = ratio,
                    variant = heroVariant,
                    height = 8.dp
                )
            } else {
                // Multi-currency overview without fake summing
                Text(
                    text = "${budgets.size} Active Budgets",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseTrackerTheme.extendedColors.textPrimary
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                Text(
                    text = "Spending limits active across ${availableCurrencies.size} currencies",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))

                // Currency mini-summaries
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                ) {
                    availableCurrencies.forEach { curr ->
                        val currBudgets = budgets.filter { it.budget.limitAmount.currencyCode == curr }
                        var spentMinor = 0L
                        var limitMinor = 0L
                        for (b in currBudgets) {
                            spentMinor += b.spent.amountInMinorUnits
                            limitMinor += b.budget.limitAmount.amountInMinorUnits
                        }
                        val currRatio = if (limitMinor > 0L) spentMinor.toFloat() / limitMinor.toFloat() else 0f
                        val currPercent = (currRatio * 100).toInt()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(ExpenseTrackerRadius.card)
                                .background(ExpenseTrackerTheme.extendedColors.surfaceLow.copy(alpha = 0.8f))
                                .border(1.dp, ExpenseTrackerTheme.extendedColors.borderSubtle, ExpenseTrackerRadius.card)
                                .clickable { onSelectCurrency(curr) }
                                .padding(ExpenseTrackerSpacing.sm)
                        ) {
                            Column {
                                Text(
                                    text = curr,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "$currPercent% used",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (currRatio > 1f) ExpenseTrackerTheme.extendedColors.financialDanger else ExpenseTrackerTheme.extendedColors.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Large, prominent card for Overall spending limits (categoryId == null).
 */
@Composable
private fun OverallBudgetCard(
    progress: BudgetProgress,
    onEditClick: () -> Unit,
    onDeactivateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val title = stringResource(
        R.string.budget_overall_title,
        progress.budget.limitAmount.currencyCode
    )

    val periodLabel = when (progress.budget.periodType) {
        BudgetPeriodType.MONTHLY -> "Monthly"
        BudgetPeriodType.WEEKLY -> "Weekly"
        BudgetPeriodType.YEARLY -> "Yearly"
        BudgetPeriodType.CUSTOM -> "Custom"
    }

    val dateRangeStr = "${DateTimeFormatterHelper.formatLocalDate(progress.budget.startDate)} – ${DateTimeFormatterHelper.formatLocalDate(progress.budget.endDate)}"

    val limitStr = MoneyParser.format(progress.budget.limitAmount, useGrouping = true)
    val spentStr = MoneyParser.format(progress.spent, useGrouping = true)

    val percentageUsed = progress.progressBasisPoints / 100
    val progressFloat = (progress.progressBasisPoints / 10000f).coerceIn(0f, 1f)

    val (statusText, statusBg, statusColor, progressVariant) = when {
        progress.isExceeded -> Quadruple(
            "Exceeded",
            ExpenseTrackerTheme.extendedColors.financialDangerContainer,
            ExpenseTrackerTheme.extendedColors.financialDanger,
            ProgressVariant.EXCEEDED
        )
        progress.progressBasisPoints >= 8000 -> Quadruple(
            "Approaching",
            ExpenseTrackerTheme.extendedColors.financialWarningContainer,
            ExpenseTrackerTheme.extendedColors.financialWarning,
            ProgressVariant.WARNING
        )
        else -> Quadruple(
            "On Track",
            ExpenseTrackerTheme.extendedColors.financialPositiveContainer,
            ExpenseTrackerTheme.extendedColors.financialPositive,
            ProgressVariant.NORMAL
        )
    }

    val remainingOrOverStr = when {
        progress.isExceeded -> {
            val overMoney = progress.spent - progress.budget.limitAmount
            "${MoneyParser.format(overMoney, useGrouping = true)} over budget"
        }
        progress.remaining.amountInMinorUnits == 0L && progress.spent.amountInMinorUnits == progress.budget.limitAmount.amountInMinorUnits -> {
            "Budget fully used"
        }
        else -> {
            "${MoneyParser.format(progress.remaining, useGrouping = true)} remaining"
        }
    }

    ExpenseTrackerCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("budget_card_${progress.budget.id}"),
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
        borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.xl)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
            // Header Row: Title + Period and Status + Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md),
                    modifier = Modifier.weight(1f)
                ) {
                    IconAvatar(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = title,
                        size = 44.dp,
                        tint = MaterialTheme.colorScheme.primary,
                        shapeType = AvatarShape.ROUNDED_SQUARE
                    )

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$periodLabel • $dateRangeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                ) {
                    ExpenseTrackerStatusChip(
                        text = statusText,
                        backgroundColor = statusBg,
                        contentColor = statusColor,
                        testTag = "status_chip_${progress.budget.id}"
                    )

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("budget_menu_button_${progress.budget.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = ExpenseTrackerTheme.extendedColors.textSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_edit)) },
                                onClick = {
                                    menuExpanded = false
                                    onEditClick()
                                },
                                modifier = Modifier.testTag("budget_menu_edit_${progress.budget.id}")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.action_deactivate),
                                        color = ExpenseTrackerTheme.extendedColors.financialDanger
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeactivateClick()
                                },
                                modifier = Modifier.testTag("budget_menu_deactivate_${progress.budget.id}")
                            )
                        }
                    }
                }
            }

            // Spent vs Limit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$spentStr of $limitStr",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "$percentageUsed% used",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            // Progress Bar
            ExpenseTrackerProgressBar(
                progress = progressFloat,
                variant = progressVariant,
                height = 8.dp,
                modifier = Modifier.testTag("budget_progress_bar_${progress.budget.id}")
            )

            // Remaining or Over status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = remainingOrOverStr,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (progress.isExceeded) ExpenseTrackerTheme.extendedColors.financialDanger else ExpenseTrackerTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

/**
 * Compact premium card for Category spending limits (categoryId != null).
 */
@Composable
private fun CategoryBudgetCard(
    progress: BudgetProgress,
    onEditClick: () -> Unit,
    onDeactivateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val categoryColor = progress.categoryColorHex?.let {
        FinanceVisuals.parseColorHex(it, MaterialTheme.colorScheme.primary)
    } ?: MaterialTheme.colorScheme.primary

    val categoryIcon = progress.categoryIconName?.let {
        FinanceVisuals.getCategoryIcon(it)
    } ?: Icons.Outlined.PieChart

    val title = progress.categoryName ?: "Category Budget"

    val periodLabel = when (progress.budget.periodType) {
        BudgetPeriodType.MONTHLY -> "Monthly"
        BudgetPeriodType.WEEKLY -> "Weekly"
        BudgetPeriodType.YEARLY -> "Yearly"
        BudgetPeriodType.CUSTOM -> "Custom"
    }

    val dateRangeStr = "${DateTimeFormatterHelper.formatLocalDate(progress.budget.startDate)} – ${DateTimeFormatterHelper.formatLocalDate(progress.budget.endDate)}"

    val limitStr = MoneyParser.format(progress.budget.limitAmount, useGrouping = true)
    val spentStr = MoneyParser.format(progress.spent, useGrouping = true)

    val percentageUsed = progress.progressBasisPoints / 100
    val progressFloat = (progress.progressBasisPoints / 10000f).coerceIn(0f, 1f)

    val (statusText, statusBg, statusColor, progressVariant) = when {
        progress.isExceeded -> Quadruple(
            "Exceeded",
            ExpenseTrackerTheme.extendedColors.financialDangerContainer,
            ExpenseTrackerTheme.extendedColors.financialDanger,
            ProgressVariant.EXCEEDED
        )
        progress.progressBasisPoints >= 8000 -> Quadruple(
            "Approaching",
            ExpenseTrackerTheme.extendedColors.financialWarningContainer,
            ExpenseTrackerTheme.extendedColors.financialWarning,
            ProgressVariant.WARNING
        )
        else -> Quadruple(
            "On Track",
            ExpenseTrackerTheme.extendedColors.financialPositiveContainer,
            ExpenseTrackerTheme.extendedColors.financialPositive,
            ProgressVariant.NORMAL
        )
    }

    val remainingOrOverStr = when {
        progress.isExceeded -> {
            val overMoney = progress.spent - progress.budget.limitAmount
            "${MoneyParser.format(overMoney, useGrouping = true)} over budget"
        }
        progress.remaining.amountInMinorUnits == 0L && progress.spent.amountInMinorUnits == progress.budget.limitAmount.amountInMinorUnits -> {
            "Budget fully used"
        }
        else -> {
            "${MoneyParser.format(progress.remaining, useGrouping = true)} remaining"
        }
    }

    ExpenseTrackerCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("budget_card_${progress.budget.id}"),
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
        borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
            // Header Row: Avatar + Title + Status + Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md),
                    modifier = Modifier.weight(1f)
                ) {
                    IconAvatar(
                        icon = categoryIcon,
                        contentDescription = title,
                        size = 40.dp,
                        tint = categoryColor,
                        backgroundColor = categoryColor.copy(alpha = 0.16f),
                        borderColor = categoryColor.copy(alpha = 0.35f),
                        shapeType = AvatarShape.ROUNDED_SQUARE
                    )

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$periodLabel • $dateRangeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                ) {
                    ExpenseTrackerStatusChip(
                        text = statusText,
                        backgroundColor = statusBg,
                        contentColor = statusColor,
                        testTag = "status_chip_${progress.budget.id}"
                    )

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("budget_menu_button_${progress.budget.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = ExpenseTrackerTheme.extendedColors.textSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_edit)) },
                                onClick = {
                                    menuExpanded = false
                                    onEditClick()
                                },
                                modifier = Modifier.testTag("budget_menu_edit_${progress.budget.id}")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.action_deactivate),
                                        color = ExpenseTrackerTheme.extendedColors.financialDanger
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeactivateClick()
                                },
                                modifier = Modifier.testTag("budget_menu_deactivate_${progress.budget.id}")
                            )
                        }
                    }
                }
            }

            // Spent vs Limit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$spentStr / $limitStr",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "$percentageUsed% used",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            // Progress Bar
            ExpenseTrackerProgressBar(
                progress = progressFloat,
                variant = progressVariant,
                height = 8.dp,
                modifier = Modifier.testTag("budget_progress_bar_${progress.budget.id}")
            )

            // Remaining or Over status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = remainingOrOverStr,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (progress.isExceeded) ExpenseTrackerTheme.extendedColors.financialDanger else ExpenseTrackerTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
