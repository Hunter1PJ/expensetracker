package com.example.presentation.statistics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.presentation.components.EmptyState
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.SectionHeader
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing

@Composable
fun StatisticsPlaceholderScreen(
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                horizontal = ExpenseTrackerSpacing.screenHorizontal,
                vertical = ExpenseTrackerSpacing.screenVertical
            )
            .testTag("statistics_placeholder_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionHeader(
            title = stringResource(R.string.placeholder_statistics_title),
            subtitle = "Visual trends & analytics"
        )

        ExpenseTrackerCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .padding(top = ExpenseTrackerSpacing.lg),
            shape = ExpenseTrackerRadius.card,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(ExpenseTrackerSpacing.xl),
            testTag = "statistics_empty_card"
        ) {
            EmptyState(
                title = stringResource(R.string.placeholder_statistics_title),
                description = stringResource(R.string.placeholder_statistics_desc),
                icon = Icons.Outlined.PieChart,
                testTag = "statistics_empty_state"
            )
        }
    }
}
