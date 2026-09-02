package com.example.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.presentation.components.AmountFormatStyle
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.FinancialAmount
import com.example.presentation.components.SectionHeader
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(
        uiState = uiState,
        modifier = modifier
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
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
            .testTag("home_screen_content"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xl)
    ) {
        // Balance Hero Card using ExpenseTrackerCard
        ExpenseTrackerCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp),
            shape = ExpenseTrackerRadius.xl.let { RoundedCornerShape(it) },
            containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
            borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(ExpenseTrackerSpacing.xxl),
            testTag = "balance_card"
        ) {
            Text(
                text = stringResource(R.string.current_balance_label).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                color = ExpenseTrackerTheme.extendedColors.textSecondary
            )

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

            FinancialAmount(
                amountText = uiState.balanceFormatted,
                style = AmountFormatStyle.HERO,
                overrideColor = MaterialTheme.colorScheme.onBackground,
                testTag = "current_balance_text"
            )

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))

            Surface(
                shape = RoundedCornerShape(ExpenseTrackerRadius.full),
                color = ExpenseTrackerTheme.extendedColors.surfaceHighlight
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = ExpenseTrackerSpacing.md,
                        vertical = ExpenseTrackerSpacing.xs
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = ExpenseTrackerTheme.extendedColors.financialPositive,
                        modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
                    )
                    Text(
                        text = stringResource(R.string.status_offline_first),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Section for architectural foundation
        SectionHeader(
            title = stringResource(R.string.status_architecture_ready),
            subtitle = "Design system & structure established"
        )

        // Architectural Foundation Ready Card
        ExpenseTrackerCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp),
            shape = ExpenseTrackerRadius.card,
            containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
            borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(ExpenseTrackerSpacing.xxxl),
            testTag = "architecture_status_card"
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ExpenseTrackerTheme.extendedColors.surfaceElevated)
                        .border(
                            width = 1.dp,
                            color = ExpenseTrackerTheme.extendedColors.cardBorder,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = stringResource(R.string.status_architecture_ready),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xl)
                    )
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))

                Text(
                    text = stringResource(R.string.status_architecture_ready),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                Text(
                    text = stringResource(R.string.status_architecture_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = ExpenseTrackerSpacing.sm)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ExpenseTrackerTheme {
        HomeScreenContent(uiState = HomeUiState())
    }
}
