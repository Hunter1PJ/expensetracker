package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "primary_button"
) {
    Button(
        onClick = onClick,
        modifier = modifier.testTag(testTag),
        enabled = enabled,
        shape = ExpenseTrackerRadius.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
            disabledContentColor = ExpenseTrackerTheme.extendedColors.textSecondary
        ),
        contentPadding = PaddingValues(
            horizontal = ExpenseTrackerSpacing.xxl,
            vertical = ExpenseTrackerSpacing.md
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "secondary_button"
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.testTag(testTag),
        enabled = enabled,
        shape = ExpenseTrackerRadius.button,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onBackground,
            disabledContentColor = ExpenseTrackerTheme.extendedColors.textSecondary
        ),
        border = BorderStroke(1.dp, ExpenseTrackerTheme.extendedColors.cardBorder),
        contentPadding = PaddingValues(
            horizontal = ExpenseTrackerSpacing.xxl,
            vertical = ExpenseTrackerSpacing.md
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
