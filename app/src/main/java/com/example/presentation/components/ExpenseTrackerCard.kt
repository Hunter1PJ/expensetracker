package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

/**
 * Foundation Card component for ExpenseTracker with subtle borders and consistent elevation.
 */
@Composable
fun ExpenseTrackerCard(
    modifier: Modifier = Modifier,
    shape: Shape = ExpenseTrackerRadius.card,
    containerColor: Color = ExpenseTrackerTheme.extendedColors.cardBackground,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = ExpenseTrackerTheme.extendedColors.cardBorder,
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
