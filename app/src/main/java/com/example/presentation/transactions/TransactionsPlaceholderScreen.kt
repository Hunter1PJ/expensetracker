package com.example.presentation.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
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
fun TransactionsPlaceholderScreen(
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
            .testTag("transactions_placeholder_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionHeader(
            title = stringResource(R.string.placeholder_transactions_title),
            subtitle = "Transaction ledger & history"
        )

        ExpenseTrackerCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .padding(top = ExpenseTrackerSpacing.lg),
            shape = ExpenseTrackerRadius.card,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(ExpenseTrackerSpacing.xl),
            testTag = "transactions_empty_card"
        ) {
            EmptyState(
                title = stringResource(R.string.placeholder_transactions_title),
                description = stringResource(R.string.placeholder_transactions_desc),
                icon = Icons.Outlined.ReceiptLong,
                testTag = "transactions_empty_state"
            )
        }
    }
}
