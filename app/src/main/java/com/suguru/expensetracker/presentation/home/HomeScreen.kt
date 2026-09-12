package com.suguru.expensetracker.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.presentation.common.FinanceVisuals
import com.suguru.expensetracker.presentation.components.AvatarShape
import com.suguru.expensetracker.presentation.components.BackgroundGlowDecoration
import com.suguru.expensetracker.presentation.components.EmptyState
import com.suguru.expensetracker.presentation.components.ErrorBanner
import com.suguru.expensetracker.presentation.components.ExpenseTrackerCard
import com.suguru.expensetracker.presentation.components.ExpenseTrackerFilterChip
import com.suguru.expensetracker.presentation.components.FinancialListRow
import com.suguru.expensetracker.presentation.components.HeroFinancialCard
import com.suguru.expensetracker.presentation.components.IconAvatar
import com.suguru.expensetracker.presentation.components.LoadingState
import com.suguru.expensetracker.presentation.components.MetricCard
import com.suguru.expensetracker.presentation.components.PrimaryButton
import com.suguru.expensetracker.presentation.components.SecondaryButton
import com.suguru.expensetracker.presentation.components.SectionHeader
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onNavigateToAddTransaction: () -> Unit = {},
    onNavigateToAccounts: () -> Unit = {},
    onNavigateToAddAccount: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToTransactionDetail: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreenContent(
        uiState = uiState,
        onNavigateToAddTransaction = onNavigateToAddTransaction,
        onNavigateToAccounts = onNavigateToAccounts,
        onNavigateToAddAccount = onNavigateToAddAccount,
        onNavigateToTransactions = onNavigateToTransactions,
        onNavigateToTransactionDetail = onNavigateToTransactionDetail,
        modifier = modifier
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
    onNavigateToAddTransaction: () -> Unit = {},
    onNavigateToAccounts: () -> Unit = {},
    onNavigateToAddAccount: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToTransactionDetail: (Long) -> Unit = {}
) {
    if (uiState.isLoading) {
        LoadingState(
            message = stringResource(R.string.loading_accounts),
            modifier = modifier.fillMaxSize(),
            testTag = "home_loading_state"
        )
        return
    }

    if (!uiState.hasAccounts) {
        // Empty State: No accounts created yet
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    horizontal = ExpenseTrackerSpacing.screenHorizontal,
                    vertical = ExpenseTrackerSpacing.screenVertical
                )
                .testTag("home_empty_state"),
            contentAlignment = Alignment.Center
        ) {
            BackgroundGlowDecoration(
                glowColor = ExpenseTrackerTheme.extendedColors.primaryPurple,
                alpha = 0.16f
            )

            Column(
                modifier = Modifier.widthIn(max = 540.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.lg)
            ) {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    description = stringResource(R.string.home_empty_desc),
                    icon = Icons.Outlined.AccountBalanceWallet,
                    testTag = "home_no_accounts_empty_state"
                )
                PrimaryButton(
                    text = stringResource(R.string.home_action_create_account),
                    onClick = onNavigateToAddAccount,
                    testTag = "home_create_first_account_button"
                )
            }
        }
        return
    }

    // Selected currency code state for multi-currency presentation
    var selectedCurrencyCode by rememberSaveable(uiState.currencySummaries) {
        mutableStateOf(uiState.currencySummaries.firstOrNull()?.currencyCode ?: "")
    }

    val activeCurrencySummary = uiState.currencySummaries.find { it.currencyCode == selectedCurrencyCode }
        ?: uiState.currencySummaries.firstOrNull()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient soft purple background glow near the hero area
        BackgroundGlowDecoration(
            glowColor = ExpenseTrackerTheme.extendedColors.primaryPurple,
            alpha = 0.12f
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen_content"),
            contentPadding = PaddingValues(
                horizontal = ExpenseTrackerSpacing.screenHorizontal,
                vertical = ExpenseTrackerSpacing.screenVertical
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xl)
        ) {
            // Optional error banner
            if (uiState.errorMessage != null) {
                item {
                    ErrorBanner(
                        message = uiState.errorMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                    )
                }
            }

            // --- 1. HEADER AREA ---
            item {
                HomeHeaderSection(
                    monthLabel = uiState.currentMonthName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                )
            }

            // --- 2. HERO BALANCE AREA ---
            item {
                HomeHeroBalanceSection(
                    currencySummaries = uiState.currencySummaries,
                    activeSummary = activeCurrencySummary,
                    onSelectCurrency = { selectedCurrencyCode = it },
                    periodLabel = uiState.currentMonthName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                )
            }

            // --- 3. THIS MONTH SECTION ---
            item {
                if (activeCurrencySummary != null) {
                    HomeMonthlyOverviewSection(
                        activeSummary = activeCurrencySummary,
                        currentMonthLabel = uiState.currentMonthName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                    )
                }
            }

            // --- 4. ACCOUNTS SECTION ---
            item {
                HomeAccountsSection(
                    accounts = uiState.accounts,
                    onManageAccounts = onNavigateToAccounts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                )
            }

            // --- 5. RECENT ACTIVITY SECTION ---
            item {
                HomeRecentActivitySection(
                    transactions = uiState.recentTransactions,
                    onSeeAll = onNavigateToTransactions,
                    onAddTransaction = onNavigateToAddTransaction,
                    onNavigateToDetail = onNavigateToTransactionDetail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                )
            }

            // Bottom clearance spacer so content is not obscured by FAB or bottom navigation
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

/**
 * Top Header: Clean, modern financial header displaying greeting / overview and current period pill.
 */
@Composable
private fun HomeHeaderSection(
    monthLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(top = ExpenseTrackerSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.home_header_subtitle).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                color = ExpenseTrackerTheme.extendedColors.accentViolet
            )
            Text(
                text = stringResource(R.string.home_header_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Surface(
            shape = ExpenseTrackerRadius.chipPill,
            color = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
            border = BorderStroke(1.dp, ExpenseTrackerTheme.extendedColors.borderSubtle)
        ) {
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = ExpenseTrackerTheme.extendedColors.textSecondary,
                modifier = Modifier.padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.xs)
            )
        }
    }
}

/**
 * Hero Balance Area: Uses HeroFinancialCard as the centerpiece.
 * Seamlessly handles single currency and multi-currency presentation without combining currencies.
 */
@Composable
private fun HomeHeroBalanceSection(
    currencySummaries: List<CurrencySummaryUiModel>,
    activeSummary: CurrencySummaryUiModel?,
    onSelectCurrency: (String) -> Unit,
    periodLabel: String,
    modifier: Modifier = Modifier
) {
    val isMultiCurrency = currencySummaries.size > 1
    val title = if (isMultiCurrency) {
        stringResource(R.string.home_balances)
    } else {
        stringResource(R.string.home_total_balance)
    }

    val displayBalance = activeSummary?.balanceFormatted ?: "$0.00"

    HeroFinancialCard(
        title = title,
        balanceText = displayBalance,
        periodLabel = if (isMultiCurrency) activeSummary?.currencyCode else periodLabel,
        incomeText = activeSummary?.incomeFormatted,
        expenseText = activeSummary?.expenseFormatted,
        currencySelector = if (isMultiCurrency) {
            {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                ) {
                    currencySummaries.forEach { summary ->
                        val isSelected = summary.currencyCode == activeSummary?.currencyCode
                        ExpenseTrackerFilterChip(
                            text = summary.currencyCode,
                            selected = isSelected,
                            onClick = { onSelectCurrency(summary.currencyCode) },
                            testTag = "hero_currency_chip_${summary.currencyCode}"
                        )
                    }
                }
            }
        } else null,
        balanceTestTag = "current_balance_text",
        testTag = "balance_card",
        modifier = modifier,
        customContent = if (isMultiCurrency) {
            {
                // Compact multi-currency balance list so user sees all currencies at a glance
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                ) {
                    currencySummaries.forEach { item ->
                        val isSelected = item.currencyCode == activeSummary?.currencyCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ExpenseTrackerRadius.xs))
                                .clickable { onSelectCurrency(item.currencyCode) }
                                .background(
                                    if (isSelected) {
                                        ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.14f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .padding(horizontal = ExpenseTrackerSpacing.sm, vertical = ExpenseTrackerSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(ExpenseTrackerRadius.xs),
                                color = if (isSelected) {
                                    ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.35f)
                                } else {
                                    ExpenseTrackerTheme.extendedColors.surfaceHighlight
                                }
                            ) {
                                Text(
                                    text = item.currencyCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) {
                                        ExpenseTrackerTheme.extendedColors.accentViolet
                                    } else {
                                        ExpenseTrackerTheme.extendedColors.textSecondary
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = item.balanceFormatted,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    ExpenseTrackerTheme.extendedColors.textPrimary
                                } else {
                                    ExpenseTrackerTheme.extendedColors.textSecondary
                                }
                            )
                        }
                    }
                }
            }
        } else null
    )
}

/**
 * Monthly Overview: 2 + 1 balanced layout of MetricCard components for Income, Expense, and Net.
 */
@Composable
private fun HomeMonthlyOverviewSection(
    activeSummary: CurrencySummaryUiModel,
    currentMonthLabel: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("monthly_summary_section"),
        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
    ) {
        SectionHeader(
            title = stringResource(R.string.home_this_month),
            subtitle = currentMonthLabel
        )

        // Income & Expense row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
        ) {
            MetricCard(
                title = stringResource(R.string.home_income),
                value = activeSummary.incomeFormatted,
                valueColor = ExpenseTrackerTheme.extendedColors.financialPositive,
                icon = {
                    IconAvatar(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = stringResource(R.string.home_income),
                        tint = ExpenseTrackerTheme.extendedColors.financialPositive,
                        backgroundColor = ExpenseTrackerTheme.extendedColors.financialPositive.copy(alpha = 0.14f),
                        borderColor = ExpenseTrackerTheme.extendedColors.financialPositive.copy(alpha = 0.3f),
                        size = 34.dp,
                        shapeType = AvatarShape.ROUNDED_SQUARE
                    )
                },
                modifier = Modifier.weight(1f),
                testTag = "home_income_metric_card"
            )

            MetricCard(
                title = stringResource(R.string.home_expense),
                value = activeSummary.expenseFormatted,
                valueColor = ExpenseTrackerTheme.extendedColors.financialNegative,
                icon = {
                    IconAvatar(
                        icon = Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = stringResource(R.string.home_expense),
                        tint = ExpenseTrackerTheme.extendedColors.financialNegative,
                        backgroundColor = ExpenseTrackerTheme.extendedColors.financialNegative.copy(alpha = 0.14f),
                        borderColor = ExpenseTrackerTheme.extendedColors.financialNegative.copy(alpha = 0.3f),
                        size = 34.dp,
                        shapeType = AvatarShape.ROUNDED_SQUARE
                    )
                },
                modifier = Modifier.weight(1f),
                testTag = "home_expense_metric_card"
            )
        }

        // Net Card (balanced wide tile)
        val netColor = when {
            activeSummary.isNetPositive -> ExpenseTrackerTheme.extendedColors.financialPositive
            activeSummary.isNetNegative -> ExpenseTrackerTheme.extendedColors.financialNegative
            else -> ExpenseTrackerTheme.extendedColors.primaryPurple
        }

        val netIcon = when {
            activeSummary.isNetPositive -> Icons.AutoMirrored.Filled.TrendingUp
            activeSummary.isNetNegative -> Icons.AutoMirrored.Filled.TrendingDown
            else -> Icons.Default.AccountBalanceWallet
        }

        val netSubtitle = when {
            activeSummary.isNetPositive -> "Net savings this month"
            activeSummary.isNetNegative -> "Net deficit this month"
            else -> "Break-even this month"
        }

        MetricCard(
            title = stringResource(R.string.home_net),
            value = activeSummary.netFormatted,
            valueColor = netColor,
            subtitle = netSubtitle,
            icon = {
                IconAvatar(
                    icon = netIcon,
                    contentDescription = stringResource(R.string.home_net),
                    tint = netColor,
                    backgroundColor = netColor.copy(alpha = 0.14f),
                    borderColor = netColor.copy(alpha = 0.3f),
                    size = 34.dp,
                    shapeType = AvatarShape.ROUNDED_SQUARE
                )
            },
            modifier = Modifier.fillMaxWidth(),
            testTag = "home_net_metric_card"
        )
    }
}

/**
 * Accounts Section: Header with "See all" action and horizontally scrollable account cards.
 */
@Composable
private fun HomeAccountsSection(
    accounts: List<AccountSummaryUiModel>,
    onManageAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("accounts_section"),
        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.home_accounts),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            TextButton(
                onClick = onManageAccounts,
                modifier = Modifier.testTag("manage_accounts_button")
            ) {
                Text(
                    text = stringResource(R.string.home_action_see_all),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(
                items = accounts,
                key = { it.id }
            ) { account ->
                AccountHorizontalCard(
                    account = account,
                    onClick = onManageAccounts
                )
            }
        }
    }
}

/**
 * Account Horizontal Card: Uses real account data with subtle radial glow tint from the account's color.
 */
@Composable
private fun AccountHorizontalCard(
    account: AccountSummaryUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = FinanceVisuals.parseColorHex(account.colorHex)
    val icon = FinanceVisuals.getAccountIcon(account.iconName)

    Card(
        onClick = onClick,
        modifier = modifier
            .width(210.dp)
            .testTag("home_account_card_${account.id}"),
        shape = ExpenseTrackerRadius.cardInteractive,
        colors = CardDefaults.cardColors(
            containerColor = ExpenseTrackerTheme.extendedColors.surface
        ),
        border = BorderStroke(1.dp, ExpenseTrackerTheme.extendedColors.borderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Subtle corner tint from account color
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.85f, 0f),
                            radius = size.width * 0.7f
                        ),
                        center = Offset(size.width * 0.85f, 0f),
                        radius = size.width * 0.7f
                    )
                }
                .padding(ExpenseTrackerSpacing.lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
            ) {
                // Top row: IconAvatar + Account Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                ) {
                    IconAvatar(
                        icon = icon,
                        contentDescription = account.name,
                        tint = accentColor,
                        backgroundColor = accentColor.copy(alpha = 0.16f),
                        borderColor = accentColor.copy(alpha = 0.35f),
                        size = 34.dp,
                        shapeType = AvatarShape.ROUNDED_SQUARE
                    )

                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Balance
                Text(
                    text = account.balanceFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Bottom row: Type label + Currency badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = account.typeName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.8.sp,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )

                    Surface(
                        shape = ExpenseTrackerRadius.chipPill,
                        color = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
                        border = BorderStroke(0.5.dp, ExpenseTrackerTheme.extendedColors.borderSubtle)
                    ) {
                        Text(
                            text = account.currencyCode,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseTrackerTheme.extendedColors.accentViolet,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recent Activity Section: Header with "See all", List of FinancialListRow items or empty state.
 */
@Composable
private fun HomeRecentActivitySection(
    transactions: List<RecentTransactionUiModel>,
    onSeeAll: () -> Unit,
    onAddTransaction: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("recent_transactions_section"),
        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.home_recent_transactions),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (transactions.isNotEmpty()) {
                TextButton(
                    onClick = onSeeAll,
                    modifier = Modifier.testTag("see_all_transactions_button")
                ) {
                    Text(
                        text = stringResource(R.string.home_action_see_all),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (transactions.isEmpty()) {
            ExpenseTrackerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recent_transactions_empty_card"),
                shape = ExpenseTrackerRadius.card,
                containerColor = ExpenseTrackerTheme.extendedColors.surface,
                borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                contentPadding = PaddingValues(ExpenseTrackerSpacing.xxl)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = ExpenseTrackerTheme.extendedColors.textMuted,
                        modifier = Modifier.size(ExpenseTrackerTheme.iconSize.lg)
                    )

                    Text(
                        text = stringResource(R.string.home_no_transactions_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stringResource(R.string.home_no_transactions_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                    SecondaryButton(
                        text = stringResource(R.string.title_add_transaction),
                        onClick = onAddTransaction,
                        testTag = "recent_transactions_empty_add_button"
                    )
                }
            }
        } else {
            ExpenseTrackerCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ExpenseTrackerRadius.card,
                containerColor = ExpenseTrackerTheme.extendedColors.surface,
                borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    transactions.forEachIndexed { index, tx ->
                        val icon = if (tx.type == TransactionType.TRANSFER) {
                            Icons.AutoMirrored.Filled.CompareArrows
                        } else {
                            FinanceVisuals.getCategoryIcon(tx.iconName)
                        }

                        val iconColor = if (tx.type == TransactionType.TRANSFER) {
                            ExpenseTrackerTheme.extendedColors.financialNeutral
                        } else {
                            FinanceVisuals.parseColorHex(tx.colorHex)
                        }

                        val amountColor = when (tx.isPositive) {
                            true -> ExpenseTrackerTheme.extendedColors.financialPositive
                            false -> ExpenseTrackerTheme.extendedColors.financialNegative
                            null -> ExpenseTrackerTheme.extendedColors.financialNeutral
                        }

                        FinancialListRow(
                            title = tx.title,
                            amountText = tx.amountFormatted,
                            subtitle = tx.subtitle,
                            icon = icon,
                            iconTint = iconColor,
                            iconBackground = iconColor.copy(alpha = 0.16f),
                            amountColor = amountColor,
                            isRecurring = tx.isRecurring,
                            showChevron = true,
                            onClick = { onNavigateToDetail(tx.id) },
                            testTag = "recent_tx_item_${tx.id}"
                        )

                        if (index < transactions.lastIndex) {
                            HorizontalDivider(
                                color = ExpenseTrackerTheme.extendedColors.borderSubtle.copy(alpha = 0.5f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(start = 68.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ExpenseTrackerTheme {
        HomeScreenContent(uiState = HomeUiState())
    }
}
