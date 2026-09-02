package com.example.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

/**
 * Reusable Loading State component for content containers or screens.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
    testTag: String = "loading_state"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ExpenseTrackerSpacing.xxl)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp
        )

        if (message != null) {
            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = ExpenseTrackerTheme.extendedColors.textSecondary
            )
        }
    }
}
