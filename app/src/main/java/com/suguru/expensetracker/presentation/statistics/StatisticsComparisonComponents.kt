package com.suguru.expensetracker.presentation.statistics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.suguru.expensetracker.presentation.common.FinanceVisuals
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suguru.expensetracker.domain.model.statistics.ComparisonSemantic
import com.suguru.expensetracker.domain.model.statistics.StatisticsComparisonMode
import com.suguru.expensetracker.presentation.components.EmptyState
import com.suguru.expensetracker.presentation.components.ExpenseTrackerCard
import com.suguru.expensetracker.presentation.components.ExpenseTrackerFilterChip
import com.suguru.expensetracker.presentation.components.IconAvatar
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

@Composable
fun CompareControlsRow(
    isComparisonEnabled: Boolean,
    comparisonMode: StatisticsComparisonMode,
    onToggleComparison: () -> Unit,
    onModeSelected: (StatisticsComparisonMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpenseTrackerFilterChip(
                selected = isComparisonEnabled,
                onClick = onToggleComparison,
                text = if (isComparisonEnabled) "Comparing Active" else "Compare",
                icon = Icons.Default.CompareArrows,
                modifier = Modifier.testTag("compare_toggle_chip")
            )
        }

        AnimatedVisibility(
            visible = isComparisonEnabled,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "vs",
                    style = MaterialTheme.typography.labelMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary,
                    modifier = Modifier.padding(end = ExpenseTrackerSpacing.xs)
                )

                ExpenseTrackerFilterChip(
                    selected = comparisonMode == StatisticsComparisonMode.PREVIOUS_PERIOD,
                    onClick = { onModeSelected(StatisticsComparisonMode.PREVIOUS_PERIOD) },
                    text = "Previous Period",
                    modifier = Modifier.testTag("compare_mode_prev_period_chip")
                )

                ExpenseTrackerFilterChip(
                    selected = comparisonMode == StatisticsComparisonMode.PREVIOUS_YEAR,
                    onClick = { onModeSelected(StatisticsComparisonMode.PREVIOUS_YEAR) },
                    text = "Previous Year",
                    modifier = Modifier.testTag("compare_mode_prev_year_chip")
                )
            }
        }
    }
}

@Composable
fun ComparisonRangeHeaderCard(
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExpenseTrackerRadius.card)
            .background(ExpenseTrackerTheme.extendedColors.surfaceElevated.copy(alpha = 0.85f))
            .border(1.dp, ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.3f), ExpenseTrackerRadius.card)
            .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.sm)
            .testTag("comparison_range_header")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = ExpenseTrackerTheme.extendedColors.primaryBright,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = ExpenseTrackerTheme.extendedColors.textPrimary
            )
        }
    }
}

@Composable
fun ComparisonSummaryCards(
    comparison: ComparisonUiModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("comparison_summary_section"),
        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
    ) {
        // Net Card (Hero)
        ComparisonMetricCard(
            title = "Net Savings / Flow",
            metric = comparison.netMetric,
            isHero = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("comparison_net_card")
        )

        // Income and Expense Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
        ) {
            ComparisonMetricCard(
                title = "Income",
                metric = comparison.incomeMetric,
                isHero = false,
                modifier = Modifier
                    .weight(1f)
                    .testTag("comparison_income_card")
            )

            ComparisonMetricCard(
                title = "Expenses",
                metric = comparison.expenseMetric,
                isHero = false,
                modifier = Modifier
                    .weight(1f)
                    .testTag("comparison_expense_card")
            )
        }
    }
}

@Composable
private fun ComparisonMetricCard(
    title: String,
    metric: ComparisonMetricUiModel,
    isHero: Boolean,
    modifier: Modifier = Modifier
) {
    val semanticColor = when (metric.semantic) {
        ComparisonSemantic.FAVORABLE -> ExpenseTrackerTheme.extendedColors.financialPositive
        ComparisonSemantic.UNFAVORABLE -> ExpenseTrackerTheme.extendedColors.financialNegative
        ComparisonSemantic.NEUTRAL -> ExpenseTrackerTheme.extendedColors.textSecondary
    }

    ExpenseTrackerCard(
        modifier = modifier.semantics { contentDescription = metric.contentDescription },
        shape = if (isHero) ExpenseTrackerRadius.cardHero else ExpenseTrackerRadius.card,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = ExpenseTrackerTheme.extendedColors.textSecondary
            )

            // Current primary amount
            Text(
                text = metric.primaryAmount,
                style = if (isHero) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ExpenseTrackerTheme.extendedColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Previous amount subtitle
            Text(
                text = "Previous: ${metric.comparisonAmount}",
                style = MaterialTheme.typography.bodySmall,
                color = ExpenseTrackerTheme.extendedColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))

            // Delta & Relative Change Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(semanticColor.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (metric.relativeChangeText.isNotBlank()) {
                            "${metric.formattedDelta} (${metric.relativeChangeText})"
                        } else {
                            metric.formattedDelta
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = semanticColor
                    )
                }
            }
        }
    }
}

@Composable
fun ComparisonTrendChartCard(
    trendPoints: List<AlignedTrendPointUiModel>,
    mode: StatisticsComparisonMode,
    modifier: Modifier = Modifier
) {
    if (trendPoints.isEmpty()) return

    val currentLegend = "Current period"
    val previousLegend = if (mode == StatisticsComparisonMode.PREVIOUS_YEAR) "Previous year" else "Previous period"

    val currentBarColor = ExpenseTrackerTheme.extendedColors.primaryPurple
    val previousBarColor = Color(0xFF64748B)

    // Construct TalkBack readable description
    val chartDescription = buildString {
        append("Comparison trend chart. ")
        trendPoints.take(5).forEach { pt ->
            append("${pt.label}: current expenses ${pt.formattedPrimaryExpense}, previous ${pt.formattedComparisonExpense}. ")
        }
    }

    ExpenseTrackerCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = chartDescription }
            .testTag("comparison_trend_chart_card"),
        shape = ExpenseTrackerRadius.card,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
            // Header with legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spending Comparison Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseTrackerTheme.extendedColors.textPrimary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(currentBarColor)
                        )
                        Text(
                            text = currentLegend,
                            style = MaterialTheme.typography.labelSmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(previousBarColor)
                        )
                        Text(
                            text = previousLegend,
                            style = MaterialTheme.typography.labelSmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                    }
                }
            }

            // Dual Series Canvas
            val maxExpense = trendPoints.maxOfOrNull {
                maxOf(it.primaryExpenseMinor, it.comparisonExpenseMinor)
            } ?: 1L
            val safeMax = if (maxExpense <= 0L) 1L else maxExpense

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val width = size.width
                    val height = size.height
                    val bucketCount = trendPoints.size
                    if (bucketCount == 0) return@Canvas

                    val availableWidthPerBucket = width / bucketCount
                    val barWidth = (availableWidthPerBucket * 0.35f).coerceIn(4.dp.toPx(), 18.dp.toPx())
                    val barSpacing = 2.dp.toPx()

                    for (i in trendPoints.indices) {
                        val pt = trendPoints[i]
                        val bucketCenterX = availableWidthPerBucket * i + availableWidthPerBucket / 2f

                        val primaryHeight = (pt.primaryExpenseMinor.toFloat() / safeMax.toFloat()) * (height * 0.88f)
                        val compHeight = (pt.comparisonExpenseMinor.toFloat() / safeMax.toFloat()) * (height * 0.88f)

                        // Draw Previous (Secondary) Bar on the left
                        val compLeft = bucketCenterX - barWidth - barSpacing / 2f
                        val compTop = height - compHeight
                        if (compHeight > 0f) {
                            drawRoundRect(
                                color = previousBarColor,
                                topLeft = Offset(compLeft, compTop),
                                size = Size(barWidth, compHeight),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                        }

                        // Draw Current (Primary) Bar on the right
                        val primaryLeft = bucketCenterX + barSpacing / 2f
                        val primaryTop = height - primaryHeight
                        if (primaryHeight > 0f) {
                            drawRoundRect(
                                color = currentBarColor,
                                topLeft = Offset(primaryLeft, primaryTop),
                                size = Size(barWidth, primaryHeight),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComparisonCategoryBreakdownCard(
    categories: List<CategoryComparisonUiModel>,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) return

    ExpenseTrackerCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("comparison_categories_card"),
        shape = ExpenseTrackerRadius.card,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
            Text(
                text = "Category Changes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ExpenseTrackerTheme.extendedColors.textPrimary
            )

            Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                categories.forEachIndexed { index, cat ->
                    CategoryComparisonRow(category = cat)
                    if (index < categories.size - 1) {
                        HorizontalDivider(
                            color = ExpenseTrackerTheme.extendedColors.borderSubtle,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryComparisonRow(
    category: CategoryComparisonUiModel,
    modifier: Modifier = Modifier
) {
    val semanticColor = when (category.semantic) {
        ComparisonSemantic.FAVORABLE -> ExpenseTrackerTheme.extendedColors.financialPositive
        ComparisonSemantic.UNFAVORABLE -> ExpenseTrackerTheme.extendedColors.financialNegative
        ComparisonSemantic.NEUTRAL -> ExpenseTrackerTheme.extendedColors.textSecondary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ExpenseTrackerSpacing.xs)
            .semantics { contentDescription = category.contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
            modifier = Modifier.weight(1f)
        ) {
            val categoryColor = FinanceVisuals.parseColorHex(category.colorHex ?: "")
            IconAvatar(
                icon = FinanceVisuals.getCategoryIcon(category.iconName ?: ""),
                contentDescription = null,
                size = 36.dp,
                tint = categoryColor,
                backgroundColor = categoryColor.copy(alpha = 0.16f),
                borderColor = categoryColor.copy(alpha = 0.35f)
            )

            Column {
                Text(
                    text = category.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ExpenseTrackerTheme.extendedColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Prev: ${category.formattedComparisonAmount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = category.formattedPrimaryAmount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = ExpenseTrackerTheme.extendedColors.textPrimary
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(semanticColor.copy(alpha = 0.16f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (category.relativeChangeText.isNotBlank()) {
                        "${category.formattedDelta} (${category.relativeChangeText})"
                    } else {
                        category.formattedDelta
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = semanticColor
                )
            }
        }
    }
}
