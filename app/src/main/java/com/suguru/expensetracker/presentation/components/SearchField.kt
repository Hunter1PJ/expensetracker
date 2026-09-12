package com.suguru.expensetracker.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

/**
 * Reusable Premium Search Field with dark elevated surface, search icon,
 * and clear action button.
 */
@Composable
fun ExpenseTrackerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    onClear: (() -> Unit)? = null,
    testTag: String = "expense_tracker_search_field"
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        singleLine = true,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = ExpenseTrackerTheme.extendedColors.textMuted
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = ExpenseTrackerTheme.extendedColors.textSecondary,
                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.sm)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        onClear?.invoke()
                    },
                    modifier = Modifier.testTag("clear_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = ExpenseTrackerTheme.extendedColors.textSecondary,
                        modifier = Modifier.size(ExpenseTrackerTheme.iconSize.sm)
                    )
                }
            }
        },
        shape = ExpenseTrackerRadius.searchBar,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceHigh,
            unfocusedContainerColor = ExpenseTrackerTheme.extendedColors.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
        )
    )
}
