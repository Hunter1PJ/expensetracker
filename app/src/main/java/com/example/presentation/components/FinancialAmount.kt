package com.example.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.ExpenseTrackerTheme

enum class AmountFormatStyle {
    HERO,      // For main balance display (displayLarge)
    MEDIUM,    // For summary cards / totals (headlineSmall)
    LIST_ITEM, // For transactions / list rows (titleMedium)
    COMPACT    // For small badges / metadata (bodySmall)
}

/**
 * Reusable Financial Amount component that displays amounts with proper typography,
 * optional sign (+ / -), and semantic color coding (positive emerald, negative red, or neutral).
 */
@Composable
fun FinancialAmount(
    amountText: String,
    modifier: Modifier = Modifier,
    isPositive: Boolean? = null,
    style: AmountFormatStyle = AmountFormatStyle.MEDIUM,
    overrideColor: Color? = null,
    testTag: String = "financial_amount"
) {
    val textStyle: TextStyle = when (style) {
        AmountFormatStyle.HERO -> MaterialTheme.typography.displayLarge
        AmountFormatStyle.MEDIUM -> MaterialTheme.typography.displaySmall
        AmountFormatStyle.LIST_ITEM -> MaterialTheme.typography.titleMedium
        AmountFormatStyle.COMPACT -> MaterialTheme.typography.bodySmall
    }

    val amountColor: Color = overrideColor ?: when (isPositive) {
        true -> ExpenseTrackerTheme.extendedColors.financialPositive
        false -> ExpenseTrackerTheme.extendedColors.financialNegative
        null -> MaterialTheme.colorScheme.onBackground
    }

    Row(
        modifier = modifier.testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = amountText,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = amountColor
        )
    }
}
