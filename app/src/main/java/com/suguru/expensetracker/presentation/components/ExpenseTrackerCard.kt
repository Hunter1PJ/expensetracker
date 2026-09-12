package com.suguru.expensetracker.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suguru.expensetracker.ui.theme.ExpenseTrackerGradients
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

/**
 * Foundation Card component for ExpenseTracker with subtle borders and consistent elevation.
 */
@Composable
fun ExpenseTrackerCard(
    modifier: Modifier = Modifier,
    shape: Shape = ExpenseTrackerRadius.card,
    containerColor: Color = ExpenseTrackerTheme.extendedColors.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = ExpenseTrackerTheme.extendedColors.borderSubtle,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    contentPadding: PaddingValues = PaddingValues(ExpenseTrackerSpacing.cardContentPadding),
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = elevation
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Clickable card with ripple and subtle border highlight.
 */
@Composable
fun InteractiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ExpenseTrackerRadius.cardInteractive,
    containerColor: Color = ExpenseTrackerTheme.extendedColors.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = ExpenseTrackerTheme.extendedColors.borderSubtle,
    contentPadding: PaddingValues = PaddingValues(ExpenseTrackerSpacing.cardContentPadding),
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Card(
        onClick = onClick,
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Premium Hero Card with subtle gradient background and delicate violet border glow.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    shape: Shape = ExpenseTrackerRadius.cardHero,
    backgroundBrush: Brush = ExpenseTrackerGradients.heroCard,
    borderBrush: Brush = ExpenseTrackerGradients.heroCardBorder,
    contentPadding: PaddingValues = PaddingValues(ExpenseTrackerSpacing.xxl),
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val baseModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Box(
        modifier = baseModifier
            .clip(shape)
            .background(backgroundBrush)
            .border(1.dp, borderBrush, shape)
            .padding(contentPadding)
    ) {
        Column(content = content)
    }
}

/**
 * Compact metric / KPI tile card.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: (@Composable () -> Unit)? = null,
    testTag: String? = null
) {
    ExpenseTrackerCard(
        modifier = modifier,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
        testTag = testTag
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textMuted
                    )
                }
            }
            if (icon != null) {
                icon()
            }
        }
    }
}

/**
 * Compact card for lists and repetitive row elements.
 */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = ExpenseTrackerRadius.card,
    containerColor: Color = ExpenseTrackerTheme.extendedColors.surface,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, ExpenseTrackerTheme.extendedColors.borderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = ExpenseTrackerSpacing.lg,
                    vertical = ExpenseTrackerSpacing.md
                ),
                content = content
            )
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, ExpenseTrackerTheme.extendedColors.borderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = ExpenseTrackerSpacing.lg,
                    vertical = ExpenseTrackerSpacing.md
                ),
                content = content
            )
        }
    }
}

/**
 * Danger / Destructive action card with red accent border.
 */
@Composable
fun DangerCard(
    modifier: Modifier = Modifier,
    shape: Shape = ExpenseTrackerRadius.card,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ExpenseTrackerCard(
        modifier = modifier,
        shape = shape,
        containerColor = ExpenseTrackerTheme.extendedColors.surface,
        borderColor = ExpenseTrackerTheme.extendedColors.financialDanger.copy(alpha = 0.5f),
        testTag = testTag,
        content = content
    )
}
