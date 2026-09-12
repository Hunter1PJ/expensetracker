package com.suguru.expensetracker.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suguru.expensetracker.ui.theme.ExpenseTrackerGradients
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

/**
 * Premium Hero Financial Card displaying the main balance, decorative purple gradient,
 * subtle violet highlight, animated balance transitions, and optional breakdown pills.
 */
@Composable
fun HeroFinancialCard(
    title: String,
    balanceText: String,
    modifier: Modifier = Modifier,
    periodLabel: String? = null,
    incomeText: String? = null,
    expenseText: String? = null,
    currencySelector: (@Composable () -> Unit)? = null,
    balanceTestTag: String? = null,
    testTag: String = "hero_financial_card",
    customContent: (@Composable () -> Unit)? = null
) {
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
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )

                if (periodLabel != null) {
                    Box(
                        modifier = Modifier
                            .clip(ExpenseTrackerRadius.chipPill)
                            .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                            .padding(horizontal = ExpenseTrackerSpacing.sm, vertical = ExpenseTrackerSpacing.xxs)
                    ) {
                        Text(
                            text = periodLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                    }
                }
            }

            if (currencySelector != null) {
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                currencySelector()
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))

            AnimatedContent(
                targetState = balanceText,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith
                        fadeOut(animationSpec = tween(150))
                },
                label = "HeroBalanceTransition"
            ) { targetBalance ->
                val balanceStyle = when {
                    targetBalance.length > 20 -> MaterialTheme.typography.headlineLarge
                    targetBalance.length > 15 -> MaterialTheme.typography.displaySmall
                    targetBalance.length > 11 -> MaterialTheme.typography.displayMedium
                    else -> MaterialTheme.typography.displayLarge
                }

                Text(
                    text = targetBalance,
                    style = balanceStyle,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseTrackerTheme.extendedColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (balanceTestTag != null) Modifier.testTag(balanceTestTag) else Modifier
                )
            }

            if (incomeText != null || expenseText != null) {
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xl))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                ) {
                    if (incomeText != null) {
                        HeroStatPill(
                            label = "Income",
                            amount = incomeText,
                            isIncome = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (expenseText != null) {
                        HeroStatPill(
                            label = "Expense",
                            amount = expenseText,
                            isIncome = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (customContent != null) {
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))
                customContent()
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
            .background(ExpenseTrackerTheme.extendedColors.surfaceLow.copy(alpha = 0.8f))
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
                    color = semanticColor
                )
            }
        }
    }
}
