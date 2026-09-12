package com.suguru.expensetracker.presentation.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

/**
 * Reusable Premium Text Field with dark elevated surface, subtle borders,
 * and purple focused states.
 */
@Composable
fun ExpenseTrackerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    testTag: String = "expense_tracker_text_field"
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = ExpenseTrackerTheme.extendedColors.textSecondary
            )
            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            placeholder = if (placeholder != null) {
                {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ExpenseTrackerTheme.extendedColors.textMuted
                    )
                }
            } else null,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            interactionSource = interactionSource,
            shape = ExpenseTrackerRadius.button,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceHigh,
                unfocusedContainerColor = ExpenseTrackerTheme.extendedColors.surface,
                disabledContainerColor = ExpenseTrackerTheme.extendedColors.surfaceLow,
                errorContainerColor = ExpenseTrackerTheme.extendedColors.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                disabledBorderColor = ExpenseTrackerTheme.extendedColors.borderSubtle.copy(alpha = 0.5f),
                errorBorderColor = ExpenseTrackerTheme.extendedColors.financialDanger,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                disabledTextColor = ExpenseTrackerTheme.extendedColors.textMuted
            )
        )

        if (isError && errorMessage != null) {
            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = ExpenseTrackerTheme.extendedColors.financialDanger
            )
        }
    }
}
