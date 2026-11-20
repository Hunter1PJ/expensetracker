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
import com.suguru.expensetracker.domain.util.MoneyParser
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
import androidx.compose.material.icons.filled.Add
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
import com.suguru.expensetracker.presentation.util.localized
import com.suguru.expensetracker.domain.ai.AiError
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
    onNavigateToAddTransactionWithCandidate: (com.suguru.expensetracker.domain.parser.TransactionCandidate) -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToTransactionDetail: (Long) -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreenContent(
        uiState = uiState,
        onNavigateToAddTransaction = onNavigateToAddTransaction,
        onNavigateToAddTransactionWithCandidate = onNavigateToAddTransactionWithCandidate,
        onNavigateToTransactions = onNavigateToTransactions,
        onNavigateToTransactionDetail = onNavigateToTransactionDetail,
        onNavigateToSettings = onNavigateToSettings,
        onExtractTransactions = { input -> viewModel.extractTransactionsFromText(input) },
        onClearCandidates = { viewModel.clearExtractedCandidates() },
        modifier = modifier
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
    onNavigateToAddTransaction: () -> Unit = {},
    onNavigateToAddTransactionWithCandidate: (com.suguru.expensetracker.domain.parser.TransactionCandidate) -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToTransactionDetail: (Long) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onExtractTransactions: (String) -> Unit = {},
    onClearCandidates: () -> Unit = {}
) {
    if (uiState.isLoading) {
        LoadingState(
            message = stringResource(R.string.loading_accounts),
            modifier = modifier.fillMaxSize(),
            testTag = "home_loading_state"
        )
        return
    }

    // Keep each currency isolated; changing the selected currency never combines balances.
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen_content"),
            contentPadding = PaddingValues(
                horizontal = ExpenseTrackerSpacing.screenHorizontal,
                vertical = ExpenseTrackerSpacing.xl
            ),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xl)
        ) {
            if (uiState.errorMessage != null) {
                item {
                    ErrorBanner(
                        message = uiState.errorMessage.localized(),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 540.dp)
                    )
                }
            }

            item {
                HomeHeroBalanceSection(
                    currencySummaries = uiState.currencySummaries,
                    activeSummary = activeCurrencySummary,
                    onSelectCurrency = { selectedCurrencyCode = it },
                    periodLabel = uiState.currentMonthName,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 540.dp)
                )
            }

            item {
                PrimaryButton(
                    text = stringResource(R.string.title_add_transaction),
                    onClick = onNavigateToAddTransaction,
                    leadingIcon = Icons.Default.Add,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                        .testTag("home_add_transaction_button")
                )
            }

            item {
                HomeAiSection(
                    isConfigured = uiState.isAiConfigured,
                    isAiEnabled = uiState.isAiFeatureEnabled,
                    isExtracting = uiState.isAiExtracting,
                    extractionError = uiState.aiExtractionError,
                    candidates = uiState.extractedCandidates,
                    onConfigureInSettings = onNavigateToSettings,
                    onExtract = onExtractTransactions,
                    onClearCandidates = onClearCandidates,
                    onNavigateToAddTransaction = onNavigateToAddTransaction,
                    onEditCandidate = onNavigateToAddTransactionWithCandidate,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 540.dp)
                )
            }

            item {
                HomeRecentActivitySection(
                    transactions = uiState.recentTransactions,
                    onSeeAll = onNavigateToTransactions,
                    onNavigateToDetail = onNavigateToTransactionDetail,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 540.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
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
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.home_header_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
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
    val netColor = when {
        activeSummary?.isNetPositive == true -> ExpenseTrackerTheme.extendedColors.financialPositive
        activeSummary?.isNetNegative == true -> ExpenseTrackerTheme.extendedColors.financialNegative
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ExpenseTrackerCard(
        modifier = modifier.testTag("balance_card"),
        shape = RoundedCornerShape(12.dp),
        containerColor = ExpenseTrackerTheme.extendedColors.surfaceHigh,
        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.xl)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
                Text(
                    text = activeSummary?.currencyCode ?: periodLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseTrackerTheme.extendedColors.textMuted
                )
            }

            Text(
                text = activeSummary?.balanceFormatted ?: "—",
                modifier = Modifier.testTag("current_balance_text"),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isMultiCurrency && activeSummary != null) {
                Text(
                    text = activeSummary.currencyCode,
                    style = MaterialTheme.typography.labelMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
            }

            if (isMultiCurrency) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                ) {
                    currencySummaries.forEach { summary ->
                        ExpenseTrackerFilterChip(
                            text = summary.currencyCode,
                            selected = summary.currencyCode == activeSummary?.currencyCode,
                            onClick = { onSelectCurrency(summary.currencyCode) },
                            testTag = "hero_currency_chip_${summary.currencyCode}"
                        )
                    }
                }
            }

            HorizontalDivider(color = ExpenseTrackerTheme.extendedColors.borderSubtle)

            if (activeSummary != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("monthly_summary_section"),
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                ) {
                    HomeBalanceMetric(
                        title = stringResource(R.string.home_income),
                        value = activeSummary.incomeFormatted,
                        valueColor = ExpenseTrackerTheme.extendedColors.financialPositive,
                        modifier = Modifier.weight(1f),
                        testTag = "home_income_metric_card"
                    )
                    Box(
                        modifier = Modifier.width(1.dp).height(34.dp)
                            .background(ExpenseTrackerTheme.extendedColors.borderSubtle)
                    )
                    HomeBalanceMetric(
                        title = stringResource(R.string.home_expense),
                        value = activeSummary.expenseFormatted,
                        valueColor = ExpenseTrackerTheme.extendedColors.financialNegative,
                        modifier = Modifier.weight(1f),
                        testTag = "home_expense_metric_card"
                    )
                    Box(
                        modifier = Modifier.width(1.dp).height(34.dp)
                            .background(ExpenseTrackerTheme.extendedColors.borderSubtle)
                    )
                    HomeBalanceMetric(
                        title = stringResource(R.string.home_net),
                        value = activeSummary.netFormatted,
                        valueColor = netColor,
                        modifier = Modifier.weight(1f),
                        testTag = "home_net_metric_card"
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeBalanceMetric(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Column(
        modifier = modifier.testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = ExpenseTrackerTheme.extendedColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
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
            activeSummary.isNetPositive -> stringResource(R.string.home_net_savings_subtitle)
            activeSummary.isNetNegative -> stringResource(R.string.home_net_deficit_subtitle)
            else -> stringResource(R.string.home_net_break_even_subtitle)
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
 * Recent Activity Section: Header with "See all", List of FinancialListRow items or empty state.
 */
@Composable
private fun HomeRecentActivitySection(
    transactions: List<RecentTransactionUiModel>,
    onSeeAll: () -> Unit,
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
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
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

                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("recent_transactions_list")
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
                            title = (if (tx.categoryIsSystem) com.suguru.expensetracker.presentation.util.categoryDisplayName(tx.title, true) else tx.title).ifBlank {
                                when (tx.type) {
                                    TransactionType.INCOME -> stringResource(R.string.type_income)
                                    TransactionType.EXPENSE -> stringResource(R.string.type_expense)
                                    TransactionType.TRANSFER -> stringResource(R.string.type_transfer)
                                }
                            },
                            amountText = tx.amountFormatted,
                            subtitle = tx.subtitle.ifBlank {
                                if (tx.type == TransactionType.TRANSFER) stringResource(R.string.type_transfer) else ""
                            },
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

/**
 * AI Assistant Entry Section on Home:
 * - When unconfigured: shows prompt card directing user to Settings to add their Gemini API key.
 * - When configured: shows natural language input field with parse trigger and preview/review cards.
 */
@Composable
private fun HomeAiSection(
    isConfigured: Boolean,
    isAiEnabled: Boolean,
    isExtracting: Boolean,
    extractionError: AiError?,
    candidates: List<com.suguru.expensetracker.domain.parser.TransactionCandidate>,
    onConfigureInSettings: () -> Unit,
    onExtract: (String) -> Unit,
    onClearCandidates: () -> Unit,
    onNavigateToAddTransaction: () -> Unit,
    onEditCandidate: (com.suguru.expensetracker.domain.parser.TransactionCandidate) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var aiInputText by rememberSaveable { mutableStateOf("") }

    ExpenseTrackerCard(
        modifier = modifier.testTag("home_ai_assistant_card"),
        shape = RoundedCornerShape(12.dp),
        containerColor = ExpenseTrackerTheme.extendedColors.surfaceHigh,
        borderColor = if (isConfigured && isAiEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else ExpenseTrackerTheme.extendedColors.borderSubtle,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                ) {
                    IconAvatar(
                        icon = androidx.compose.material.icons.Icons.Default.AccountBalanceWallet,
                        contentDescription = stringResource(R.string.section_ai_assistant),
                        tint = MaterialTheme.colorScheme.primary,
                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        size = 32.dp
                    )
                    Text(
                        text = stringResource(R.string.home_ai_unconfigured_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Surface(
                    shape = ExpenseTrackerRadius.chipPill,
                    color = if (isConfigured) {
                        ExpenseTrackerTheme.extendedColors.financialPositive.copy(alpha = 0.15f)
                    } else {
                        ExpenseTrackerTheme.extendedColors.surfaceHighlight
                    }
                ) {
                    Text(
                        text = if (isConfigured && isAiEnabled) "Gemini AI" else stringResource(R.string.candidate_source_offline),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isConfigured && isAiEnabled) ExpenseTrackerTheme.extendedColors.financialPositive else ExpenseTrackerTheme.extendedColors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (!isConfigured || !isAiEnabled) {
                Text(
                    text = stringResource(R.string.home_ai_unconfigured_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )

                SecondaryButton(
                    text = stringResource(R.string.home_ai_action_configure),
                    onClick = onConfigureInSettings,
                    testTag = "home_ai_configure_button",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            androidx.compose.material3.OutlinedTextField(
                    value = aiInputText,
                    onValueChange = { aiInputText = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.home_ai_input_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_ai_prompt_input"),
                    shape = RoundedCornerShape(ExpenseTrackerRadius.sm),
                    maxLines = 3,
                    enabled = !isExtracting
                )

            if (extractionError != null) {
                    Text(
                        text = stringResource(
                            when (extractionError) {
                                is AiError.MissingApiKey -> R.string.ai_error_missing_key
                                is AiError.InvalidApiKey -> R.string.ai_error_invalid_key
                                is AiError.RequestTimeout -> R.string.ai_error_timeout
                                is AiError.NoTransactionsFound -> R.string.ai_error_no_transactions
                                is AiError.NetworkError -> R.string.ai_error_network
                                is AiError.RateLimitExceeded -> R.string.ai_error_rate_limit
                                is AiError.ServerError -> R.string.ai_error_server
                                is AiError.MalformedResponse -> R.string.ai_error_malformed
                                is AiError.ExecutionError -> R.string.ai_error_generic
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.financialDanger
                    )
            }

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
            ) {
                    PrimaryButton(
                        text = if (isExtracting) stringResource(R.string.home_ai_extracting) else stringResource(R.string.home_ai_extract_action),
                        onClick = {
                            if (aiInputText.isNotBlank()) {
                                onExtract(aiInputText)
                            }
                        },
                        enabled = !isExtracting && aiInputText.isNotBlank(),
                        testTag = "home_ai_extract_button",
                        modifier = Modifier.weight(1f)
                    )

                    if (candidates.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                aiInputText = ""
                                onClearCandidates()
                            },
                            modifier = Modifier.testTag("home_ai_clear_button")
                        ) {
                            Text(stringResource(R.string.ui_dismiss))
                        }
                    }
            }

                // Parsed Suggestions Preview (Preview -> Edit -> Confirm -> Save)
            if (candidates.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                    ) {
                        Text(
                            text = stringResource(R.string.home_ai_preview_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.home_ai_preview_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )

                        candidates.forEach { candidate ->
                            val amountStr = candidate.amount?.let {
                                MoneyParser.format(it, includeSymbol = true, useGrouping = true)
                            } ?: stringResource(R.string.ai_amount_not_specified)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_candidate_preview_card"),
                                shape = ExpenseTrackerRadius.cardInteractive,
                                colors = CardDefaults.cardColors(
                                    containerColor = ExpenseTrackerTheme.extendedColors.surfaceHighlight
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(ExpenseTrackerSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = if (candidate.suggestedTitleIsFallback) stringResource(R.string.transaction_fallback_title) else candidate.suggestedTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(
                                                if (candidate.source == com.suguru.expensetracker.domain.parser.CandidateSource.GEMINI_AI) {
                                                    R.string.candidate_source_gemini
                                                } else {
                                                    R.string.candidate_source_offline
                                                }
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                                        )
                                        val candidateTypeLabel = when (candidate.transactionType) {
                                            TransactionType.EXPENSE -> stringResource(R.string.type_expense)
                                            TransactionType.INCOME -> stringResource(R.string.type_income)
                                            TransactionType.TRANSFER -> stringResource(R.string.type_transfer)
                                        }
                                        Text(
                                            text = stringResource(R.string.home_ai_candidate_summary, candidateTypeLabel, amountStr),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                                        )
                                        if (candidate.warnings.contains(com.suguru.expensetracker.domain.parser.ParseWarning.INVALID_API_KEY)) {
                                            Text(
                                                text = stringResource(R.string.candidate_warning_invalid_key),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ExpenseTrackerTheme.extendedColors.financialWarning
                                            )
                                        } else if (candidate.warnings.contains(com.suguru.expensetracker.domain.parser.ParseWarning.AI_FALLBACK)) {
                                            Text(
                                                text = stringResource(R.string.candidate_warning_ai_fallback),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ExpenseTrackerTheme.extendedColors.financialWarning
                                            )
                                        }
                                        if (candidate.suggestedCategoryName != null) {
                                            Text(
                                                text = stringResource(R.string.home_ai_category_prefix, candidate.suggestedCategoryName),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    SecondaryButton(
                                        text = stringResource(R.string.home_ai_action_edit_candidate),
                                        onClick = { onEditCandidate(candidate) },
                                        testTag = "ai_candidate_confirm_button"
                                    )
                                }
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
