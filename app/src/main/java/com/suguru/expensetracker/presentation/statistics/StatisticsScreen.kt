package com.suguru.expensetracker.presentation.statistics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.model.statistics.StatisticsPeriodOption
import com.suguru.expensetracker.presentation.common.FinanceVisuals
import com.suguru.expensetracker.presentation.components.AvatarShape
import com.suguru.expensetracker.presentation.components.BackgroundGlowDecoration
import com.suguru.expensetracker.presentation.components.EmptyState
import com.suguru.expensetracker.presentation.components.ExpenseTrackerCard
import com.suguru.expensetracker.presentation.components.ExpenseTrackerFilterChip
import com.suguru.expensetracker.presentation.components.IconAvatar
import com.suguru.expensetracker.ui.theme.ExpenseTrackerChartColors
import com.suguru.expensetracker.ui.theme.ExpenseTrackerGradients
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient background glow decoration
        BackgroundGlowDecoration(
            glowColor = ExpenseTrackerTheme.extendedColors.primaryPurple,
            alpha = 0.12f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = ExpenseTrackerSpacing.screenHorizontal,
                    vertical = ExpenseTrackerSpacing.screenVertical
                )
                .testTag("statistics_screen"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section
            StatisticsHeader()

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))

            // Period Selector Row
            PeriodSelectorRow(
                selectedPeriod = uiState.selectedPeriod,
                onPeriodSelected = { viewModel.onPeriodSelected(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("period_selector")
            )

            // Currency Selector Row (if multiple available)
            if (uiState.availableCurrencies.size > 1) {
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                CurrencySelectorRow(
                    currencies = uiState.availableCurrencies,
                    selectedCurrency = uiState.selectedCurrencyCode,
                    onCurrencySelected = { viewModel.onCurrencySelected(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("currency_selector")
                )
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))

            if (uiState.isLoading) {
                StatisticsLoadingState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
            } else if (!uiState.hasAnyTransactionsInPeriod) {
                // Empty State for period
                ExpenseTrackerCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                        .testTag("empty_statistics_card"),
                    shape = ExpenseTrackerRadius.cardHero,
                    contentPadding = PaddingValues(ExpenseTrackerSpacing.xxl)
                ) {
                    EmptyState(
                        title = "No activity for this period",
                        description = "There are no recorded transactions in ${uiState.selectedPeriod.displayName} for ${uiState.selectedCurrencyCode}.",
                        icon = Icons.Outlined.PieChart,
                        testTag = "statistics_empty_state"
                    )
                }
            } else {
                // Hero Summary Card
                uiState.summary?.let { summary ->
                    HeroStatisticsCard(
                        summary = summary,
                        periodDisplayName = uiState.selectedPeriod.displayName,
                        transactionCount = uiState.totalTransactionsCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("summary_net_card")
                    )
                }

                // Transfers-Only Notice (when transactions exist, but both income and expense categories are empty)
                val isTransfersOnly = uiState.summary != null &&
                    uiState.expenseCategories.isEmpty() &&
                    uiState.incomeCategories.isEmpty() &&
                    uiState.totalTransactionsCount > 0

                if (isTransfersOnly) {
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))
                    TransfersOnlyBanner(
                        transferCount = uiState.totalTransactionsCount,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))

                // Trend Chart Card
                if (uiState.trendPoints.isNotEmpty()) {
                    TrendChartCard(
                        trendPoints = uiState.trendPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trend_chart_card")
                    )
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                }

                // Expense Categories Breakdown
                if (uiState.expenseCategories.isNotEmpty()) {
                    CategoryBreakdownCard(
                        title = "Spending by Category",
                        categories = uiState.expenseCategories,
                        isExpense = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expense_categories_card")
                    )
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                }

                // Income Categories Breakdown
                if (uiState.incomeCategories.isNotEmpty()) {
                    CategoryBreakdownCard(
                        title = "Income Sources",
                        categories = uiState.incomeCategories,
                        isExpense = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("income_categories_card")
                    )
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                }

                // Account Breakdown
                if (uiState.accountBreakdown.isNotEmpty()) {
                    AccountAnalyticsCard(
                        accounts = uiState.accountBreakdown,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_breakdown_card")
                    )
                }
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))
        }
    }
}

@Composable
private fun StatisticsHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "INSIGHTS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = ExpenseTrackerTheme.extendedColors.accentViolet
        )
        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
        Text(
            text = stringResource(R.string.title_statistics),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = ExpenseTrackerTheme.extendedColors.textPrimary
        )
        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
        Text(
            text = "Understand your money flow and category patterns",
            style = MaterialTheme.typography.bodySmall,
            color = ExpenseTrackerTheme.extendedColors.textSecondary
        )
    }
}

@Composable
private fun PeriodSelectorRow(
    selectedPeriod: StatisticsPeriodOption,
    onPeriodSelected: (StatisticsPeriodOption) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
    ) {
        items(StatisticsPeriodOption.entries) { period ->
            val isSelected = period == selectedPeriod
            ExpenseTrackerFilterChip(
                text = period.displayName,
                selected = isSelected,
                onClick = { onPeriodSelected(period) },
                testTag = "period_chip_${period.name.lowercase()}"
            )
        }
    }
}

@Composable
private fun CurrencySelectorRow(
    currencies: List<String>,
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Text(
                text = "CURRENCY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = ExpenseTrackerTheme.extendedColors.textSecondary,
                modifier = Modifier.padding(end = ExpenseTrackerSpacing.xs)
            )
        }
        items(currencies) { currency ->
            val isSelected = currency == selectedCurrency
            ExpenseTrackerFilterChip(
                text = currency,
                selected = isSelected,
                onClick = { onCurrencySelected(currency) },
                shape = ExpenseTrackerRadius.chipPill,
                testTag = "currency_chip_${currency.lowercase()}"
            )
        }
    }
}

@Composable
private fun StatisticsLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = ExpenseTrackerTheme.extendedColors.primaryPurple,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))
            Text(
                text = "Analyzing finances...",
                style = MaterialTheme.typography.bodySmall,
                color = ExpenseTrackerTheme.extendedColors.textSecondary
            )
        }
    }
}

@Composable
private fun HeroStatisticsCard(
    summary: SummaryCardUiModel,
    periodDisplayName: String,
    transactionCount: Int,
    modifier: Modifier = Modifier
) {
    val netColor = when (summary.isNetPositive) {
        true -> ExpenseTrackerTheme.extendedColors.financialPositive
        false -> ExpenseTrackerTheme.extendedColors.financialNegative
        else -> ExpenseTrackerTheme.extendedColors.textPrimary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExpenseTrackerRadius.cardHero)
            .background(ExpenseTrackerGradients.heroCard)
            .border(1.25.dp, ExpenseTrackerGradients.heroCardBorder, ExpenseTrackerRadius.cardHero)
            .drawBehind {
                val glowRadius = size.width * 0.6f
                val glowCenter = Offset(size.width * 0.85f, size.height * 0.15f)
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
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Eyebrow and metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NET CASH FLOW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(ExpenseTrackerRadius.chipPill)
                            .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                            .padding(horizontal = ExpenseTrackerSpacing.sm, vertical = ExpenseTrackerSpacing.xxs)
                    ) {
                        Text(
                            text = periodDisplayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                    }

                    if (transactionCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(ExpenseTrackerRadius.chipPill)
                                .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                                .padding(horizontal = ExpenseTrackerSpacing.sm, vertical = ExpenseTrackerSpacing.xxs)
                        ) {
                            Text(
                                text = "$transactionCount tx",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExpenseTrackerTheme.extendedColors.accentViolet
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))

            // Animated Net Amount
            AnimatedContent(
                targetState = summary.formattedNet,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith
                        fadeOut(animationSpec = tween(150))
                },
                label = "StatisticsNetTransition"
            ) { targetNet ->
                val netStyle = when {
                    targetNet.length > 20 -> MaterialTheme.typography.headlineMedium
                    targetNet.length > 14 -> MaterialTheme.typography.headlineLarge
                    else -> MaterialTheme.typography.displayMedium
                }

                Text(
                    text = targetNet,
                    style = netStyle,
                    fontWeight = FontWeight.Bold,
                    color = netColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xl))

            // Income & Expense Breakdown Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
            ) {
                // Income Pill
                HeroStatPill(
                    label = "Income",
                    amount = summary.formattedIncome,
                    isIncome = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("summary_income_card")
                )

                // Expense Pill
                HeroStatPill(
                    label = "Expense",
                    amount = summary.formattedExpense,
                    isIncome = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("summary_expense_card")
                )
            }
        }
    }
}

@Composable
private fun HeroStatPill(
    label: String,
    amount: String,
    isIncome: Boolean,
    modifier: Modifier = Modifier
) {
    val semanticColor = if (isIncome) {
        ExpenseTrackerTheme.extendedColors.financialPositive
    } else {
        ExpenseTrackerTheme.extendedColors.financialNegative
    }

    Box(
        modifier = modifier
            .clip(ExpenseTrackerRadius.card)
            .background(ExpenseTrackerTheme.extendedColors.surfaceLow.copy(alpha = 0.85f))
            .border(1.dp, ExpenseTrackerTheme.extendedColors.borderSubtle, ExpenseTrackerRadius.card)
            .padding(ExpenseTrackerSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(semanticColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = semanticColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.sm))

            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
                Text(
                    text = amount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = semanticColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TransfersOnlyBanner(
    transferCount: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(ExpenseTrackerRadius.card)
            .background(ExpenseTrackerTheme.extendedColors.surfaceHigh)
            .border(1.dp, ExpenseTrackerTheme.extendedColors.borderSubtle, ExpenseTrackerRadius.card)
            .padding(ExpenseTrackerSpacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = "Transfers",
                tint = ExpenseTrackerTheme.extendedColors.accentIndigo,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Only account transfers occurred in this period ($transferCount ${if (transferCount == 1) "transfer" else "transfers"}). Transfers do not affect net income or expenses.",
                style = MaterialTheme.typography.bodySmall,
                color = ExpenseTrackerTheme.extendedColors.textSecondary
            )
        }
    }
}

@Composable
private fun TrendChartCard(
    trendPoints: List<TrendPointUiModel>,
    modifier: Modifier = Modifier
) {
    var selectedPointIndex by remember(trendPoints) { mutableStateOf<Int?>(null) }
    val hasCashFlowActivity = trendPoints.any { it.incomeMinor > 0L || it.expenseMinor > 0L }

    ExpenseTrackerCard(
        modifier = modifier,
        shape = ExpenseTrackerRadius.card,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.cardContentPadding)
    ) {
        Column {
            // Header & Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ShowChart,
                        contentDescription = "Trend Chart",
                        tint = ExpenseTrackerTheme.extendedColors.primaryBright,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.xs))
                    Text(
                        text = "Cash Flow Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseTrackerTheme.extendedColors.textPrimary
                    )
                }

                // Legend
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ExpenseTrackerChartColors.incomeSeries, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                    Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.md))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ExpenseTrackerChartColors.expenseSeries, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Expense",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))

            if (!hasCashFlowActivity) {
                // Empty state within chart card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(ExpenseTrackerRadius.card)
                        .background(ExpenseTrackerTheme.extendedColors.surfaceLow),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ShowChart,
                            contentDescription = null,
                            tint = ExpenseTrackerTheme.extendedColors.textMuted.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                        Text(
                            text = "No cash-flow activity for this period",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ExpenseTrackerTheme.extendedColors.textMuted
                        )
                    }
                }
            } else {
                // Tooltip when bucket is tapped
                AnimatedVisibility(
                    visible = selectedPointIndex != null && selectedPointIndex in trendPoints.indices,
                    enter = fadeIn(animationSpec = tween(180)),
                    exit = fadeOut(animationSpec = tween(120))
                ) {
                    selectedPointIndex?.let { index ->
                        if (index in trendPoints.indices) {
                            val pt = trendPoints[index]
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ExpenseTrackerRadius.chip)
                                    .background(ExpenseTrackerTheme.extendedColors.surfaceHigh)
                                    .border(1.dp, ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.5f), ExpenseTrackerRadius.chip)
                                    .clickable { selectedPointIndex = null }
                                    .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.xs)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pt.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseTrackerTheme.extendedColors.primaryBright
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
                                        Text(
                                            text = "+${pt.formattedIncome}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ExpenseTrackerChartColors.incomeSeries
                                        )
                                        Text(
                                            text = "-${pt.formattedExpense}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ExpenseTrackerChartColors.expenseSeries
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                        }
                    }
                }

                // Canvas-based Bar Chart with Touch Feedback
                TrendChartCanvas(
                    trendPoints = trendPoints,
                    selectedIndex = selectedPointIndex,
                    onPointSelected = { index ->
                        selectedPointIndex = if (selectedPointIndex == index) null else index
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                // X-Axis Labels Row
                TrendAxisLabels(trendPoints = trendPoints)
            }
        }
    }
}

@Composable
private fun TrendChartCanvas(
    trendPoints: List<TrendPointUiModel>,
    selectedIndex: Int?,
    onPointSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val incomeColor = ExpenseTrackerChartColors.incomeSeries
    val expenseColor = ExpenseTrackerChartColors.expenseSeries
    val gridColor = ExpenseTrackerChartColors.gridLine
    val highlightColor = ExpenseTrackerTheme.extendedColors.primaryBright.copy(alpha = 0.12f)
    val highlightBorder = ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.4f)

    Canvas(
        modifier = modifier
            .pointerInput(trendPoints) {
                detectTapGestures { offset ->
                    if (trendPoints.isNotEmpty()) {
                        val slotWidth = size.width / trendPoints.size.toFloat()
                        val tappedIndex = (offset.x / slotWidth).toInt().coerceIn(0, trendPoints.lastIndex)
                        onPointSelected(tappedIndex)
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height

        if (trendPoints.isEmpty()) return@Canvas

        // Find max value strictly for visual Float normalization
        val maxValMinor = trendPoints.maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor) } ?: 1L
        val maxVal = if (maxValMinor <= 0L) 1.0f else maxValMinor.toFloat()

        // Draw horizontal gridlines (3 lines)
        val gridLines = 3
        for (i in 0..gridLines) {
            val y = height * (i.toFloat() / gridLines)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val pointCount = trendPoints.size
        val slotWidth = width / pointCount.toFloat()
        val barWidth = (slotWidth * 0.28f).coerceIn(4.dp.toPx(), 18.dp.toPx())
        val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        // Highlight selected column
        if (selectedIndex != null && selectedIndex in 0 until pointCount) {
            val highlightLeft = slotWidth * selectedIndex
            drawRoundRect(
                color = highlightColor,
                topLeft = Offset(highlightLeft + 2.dp.toPx(), 0f),
                size = Size(slotWidth - 4.dp.toPx(), height),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )
            drawRoundRect(
                color = highlightBorder,
                topLeft = Offset(highlightLeft + 2.dp.toPx(), 0f),
                size = Size(slotWidth - 4.dp.toPx(), height),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        }

        for (i in trendPoints.indices) {
            val pt = trendPoints[i]
            val xCenter = slotWidth * i + (slotWidth / 2f)

            // Income Bar (Emerald)
            val incHeight = (height * (pt.incomeMinor.toFloat() / maxVal)).coerceAtLeast(0f)
            val incTop = height - incHeight
            if (incHeight > 0f) {
                drawRoundRect(
                    color = incomeColor,
                    topLeft = Offset(xCenter - barWidth - 1.dp.toPx(), incTop),
                    size = Size(barWidth, incHeight),
                    cornerRadius = cornerRadius
                )
            }

            // Expense Bar (Coral / Red)
            val expHeight = (height * (pt.expenseMinor.toFloat() / maxVal)).coerceAtLeast(0f)
            val expTop = height - expHeight
            if (expHeight > 0f) {
                drawRoundRect(
                    color = expenseColor,
                    topLeft = Offset(xCenter + 1.dp.toPx(), expTop),
                    size = Size(barWidth, expHeight),
                    cornerRadius = cornerRadius
                )
            }
        }
    }
}

@Composable
private fun TrendAxisLabels(
    trendPoints: List<TrendPointUiModel>,
    modifier: Modifier = Modifier
) {
    if (trendPoints.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val count = trendPoints.size
        // Prevent label crowding when there are many points
        val step = when {
            count <= 6 -> 1
            count <= 12 -> 2
            else -> 3
        }

        for (i in trendPoints.indices step step) {
            Text(
                text = trendPoints[i].label,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = ExpenseTrackerTheme.extendedColors.textMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    title: String,
    categories: List<CategoryBreakdownUiModel>,
    isExpense: Boolean,
    modifier: Modifier = Modifier
) {
    ExpenseTrackerCard(
        modifier = modifier,
        shape = ExpenseTrackerRadius.card,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.cardContentPadding)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpense) Icons.Outlined.TrendingDown else Icons.Outlined.TrendingUp,
                    contentDescription = title,
                    tint = if (isExpense) ExpenseTrackerTheme.extendedColors.financialNegative else ExpenseTrackerTheme.extendedColors.financialPositive,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.xs))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseTrackerTheme.extendedColors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))

            Column {
                categories.forEachIndexed { index, cat ->
                    CategoryProgressRow(
                        category = cat,
                        isExpense = isExpense
                    )

                    if (index < categories.lastIndex) {
                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))
                        HorizontalDivider(
                            color = ExpenseTrackerTheme.extendedColors.divider,
                            thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryProgressRow(
    category: CategoryBreakdownUiModel,
    isExpense: Boolean
) {
    val categoryColor = category.colorHex?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { null }
    } ?: if (isExpense) ExpenseTrackerTheme.extendedColors.primaryPurple else ExpenseTrackerTheme.extendedColors.financialPositive

    val animatedProgress by animateFloatAsState(
        targetValue = category.percentage.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350),
        label = "category_progress_fill"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconAvatar(
                    icon = FinanceVisuals.getCategoryIcon(category.iconName ?: ""),
                    contentDescription = category.categoryName,
                    size = 40.dp,
                    tint = categoryColor,
                    backgroundColor = categoryColor.copy(alpha = 0.16f),
                    borderColor = categoryColor.copy(alpha = 0.35f),
                    shapeType = AvatarShape.ROUNDED_SQUARE
                )

                Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.md))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = category.categoryName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ExpenseTrackerTheme.extendedColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (category.isArchived) {
                            Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.xs))
                            Box(
                                modifier = Modifier
                                    .clip(ExpenseTrackerRadius.chipPill)
                                    .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Archived",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = ExpenseTrackerTheme.extendedColors.textMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${category.transactionCount} ${if (category.transactionCount == 1) "transaction" else "transactions"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textMuted
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = category.formattedAmount,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseTrackerTheme.extendedColors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = category.percentageText,
                    style = MaterialTheme.typography.labelMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

        // Slim rounded progress bar with animated fill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(categoryColor)
            )
        }
    }
}

@Composable
private fun AccountAnalyticsCard(
    accounts: List<AccountAnalyticsUiModel>,
    modifier: Modifier = Modifier
) {
    ExpenseTrackerCard(
        modifier = modifier,
        shape = ExpenseTrackerRadius.card,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.cardContentPadding)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalance,
                    contentDescription = "Account Breakdown",
                    tint = ExpenseTrackerTheme.extendedColors.accentIndigo,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.xs))
                Text(
                    text = "Accounts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseTrackerTheme.extendedColors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))

            Column {
                accounts.forEachIndexed { index, acc ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconAvatar(
                                icon = Icons.Outlined.AccountBalance,
                                contentDescription = acc.accountName,
                                size = 36.dp,
                                tint = ExpenseTrackerTheme.extendedColors.accentIndigo,
                                backgroundColor = ExpenseTrackerTheme.extendedColors.accentIndigo.copy(alpha = 0.16f),
                                borderColor = ExpenseTrackerTheme.extendedColors.accentIndigo.copy(alpha = 0.35f),
                                shapeType = AvatarShape.ROUNDED_SQUARE
                            )

                            Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.md))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = acc.accountName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ExpenseTrackerTheme.extendedColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (acc.isArchived) {
                                    Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.xs))
                                    Box(
                                        modifier = Modifier
                                            .clip(ExpenseTrackerRadius.chipPill)
                                            .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Archived",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = ExpenseTrackerTheme.extendedColors.textMuted
                                        )
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "In",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ExpenseTrackerTheme.extendedColors.textMuted
                                )
                                Text(
                                    text = acc.formattedIncome,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ExpenseTrackerTheme.extendedColors.financialPositive
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Out",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ExpenseTrackerTheme.extendedColors.textMuted
                                )
                                Text(
                                    text = acc.formattedExpense,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ExpenseTrackerTheme.extendedColors.financialNegative
                                )
                            }
                        }
                    }

                    if (index < accounts.lastIndex) {
                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))
                        HorizontalDivider(
                            color = ExpenseTrackerTheme.extendedColors.divider,
                            thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))
                    }
                }
            }
        }
    }
}
