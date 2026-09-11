package com.example.presentation.recurring

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.RecurrenceFrequency
import com.example.domain.model.TransactionType
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.SectionHeader
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionsScreen(
    viewModel: RecurringTransactionsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddRule: () -> Unit,
    onNavigateToEditRule: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedDeactivateRuleId by remember { mutableStateOf<Long?>(null) }

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("recurring_transactions_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_recurring_transactions),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("button_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.processDueNow() },
                        modifier = Modifier.testTag("button_process_due_now")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(ExpenseTrackerSpacing.xxs))
                        Text(text = stringResource(R.string.action_process_due))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddRule,
                shape = ExpenseTrackerRadius.button,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                modifier = Modifier.testTag("add_recurring_rule_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_recurring)
                )
            }
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is RecurringTransactionsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is RecurringTransactionsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is RecurringTransactionsUiState.Success -> {
                if (state.rules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                            modifier = Modifier.padding(ExpenseTrackerSpacing.lg)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = null,
                                tint = ExpenseTrackerTheme.extendedColors.textSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.no_recurring_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.no_recurring_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = ExpenseTrackerTheme.extendedColors.textSecondary,
                                modifier = Modifier.widthIn(max = 300.dp)
                            )
                            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                            Button(
                                onClick = onNavigateToAddRule,
                                modifier = Modifier.testTag("empty_add_recurring_button")
                            ) {
                                Text(text = stringResource(R.string.action_add_recurring))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(
                            horizontal = ExpenseTrackerSpacing.screenHorizontal,
                            vertical = ExpenseTrackerSpacing.screenVertical
                        ),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(
                            items = state.rules,
                            key = { it.id }
                        ) { rule ->
                            RecurringRuleCard(
                                rule = rule,
                                onEdit = { onNavigateToEditRule(rule.id) },
                                onPause = { selectedDeactivateRuleId = rule.id },
                                modifier = Modifier.widthIn(max = 540.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    selectedDeactivateRuleId?.let { ruleId ->
        AlertDialog(
            onDismissRequest = { selectedDeactivateRuleId = null },
            title = { Text(text = stringResource(R.string.dialog_deactivate_recurring_title)) },
            text = { Text(text = stringResource(R.string.dialog_deactivate_recurring_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deactivateRule(ruleId)
                        selectedDeactivateRuleId = null
                    },
                    modifier = Modifier.testTag("confirm_deactivate_button")
                ) {
                    Text(text = stringResource(R.string.action_pause))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { selectedDeactivateRuleId = null }
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun RecurringRuleCard(
    rule: RecurringTransactionUiModel,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ExpenseTrackerCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("rule_card_${rule.id}"),
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
        borderColor = when (rule.status) {
            RecurringStatus.NEEDS_ATTENTION -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            RecurringStatus.ENDING_SOON -> Color(0xFFFFB74D).copy(alpha = 0.5f)
            else -> ExpenseTrackerTheme.extendedColors.cardBorder
        },
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransactionTypeBadge(type = rule.type)
                    StatusChip(status = rule.status)
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("rule_menu_button_${rule.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                            modifier = Modifier.testTag("rule_edit_item_${rule.id}")
                        )
                        if (rule.isActive) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_pause)) },
                                onClick = {
                                    menuExpanded = false
                                    onPause()
                                },
                                modifier = Modifier.testTag("rule_pause_item_${rule.id}")
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xxs)
                ) {
                    val title = rule.note?.ifBlank { null }
                        ?: rule.categoryName
                        ?: if (rule.type == TransactionType.TRANSFER) "${rule.accountName} → ${rule.destinationAccountName ?: ""}" else rule.accountName

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    val subtitle = when (rule.type) {
                        TransactionType.EXPENSE, TransactionType.INCOME -> {
                            "${rule.accountName} • ${rule.categoryName ?: ""}"
                        }
                        TransactionType.TRANSFER -> {
                            "${rule.accountName} → ${rule.destinationAccountName ?: ""}"
                        }
                    }

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }

                Text(
                    text = rule.formattedAmount,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (rule.type) {
                        TransactionType.EXPENSE -> ExpenseTrackerTheme.extendedColors.financialNegative
                        TransactionType.INCOME -> ExpenseTrackerTheme.extendedColors.financialPositive
                        TransactionType.TRANSFER -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            if (rule.statusDetail != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ExpenseTrackerRadius.card)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                        .padding(ExpenseTrackerSpacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = rule.statusDetail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatFrequency(rule.frequency),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "${stringResource(R.string.next_occurrence_label)}: ${rule.nextOccurrence}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun TransactionTypeBadge(type: TransactionType) {
    val (labelRes, color) = when (type) {
        TransactionType.EXPENSE -> R.string.type_expense to ExpenseTrackerTheme.extendedColors.financialNegative
        TransactionType.INCOME -> R.string.type_income to ExpenseTrackerTheme.extendedColors.financialPositive
        TransactionType.TRANSFER -> R.string.type_transfer to ExpenseTrackerTheme.extendedColors.financialNeutral
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = ExpenseTrackerRadius.button
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StatusChip(status: RecurringStatus) {
    val (label, bgColor, textColor) = when (status) {
        RecurringStatus.ACTIVE -> Triple(
            stringResource(R.string.status_active),
            ExpenseTrackerTheme.extendedColors.financialPositive.copy(alpha = 0.15f),
            ExpenseTrackerTheme.extendedColors.financialPositive
        )
        RecurringStatus.PAUSED -> Triple(
            stringResource(R.string.status_paused),
            ExpenseTrackerTheme.extendedColors.surfaceElevated,
            ExpenseTrackerTheme.extendedColors.textSecondary
        )
        RecurringStatus.ENDING_SOON -> Triple(
            stringResource(R.string.status_ending_soon),
            Color(0xFFFFB74D).copy(alpha = 0.15f),
            Color(0xFFE65100)
        )
        RecurringStatus.NEEDS_ATTENTION -> Triple(
            stringResource(R.string.status_needs_attention),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error
        )
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .padding(horizontal = ExpenseTrackerSpacing.sm, vertical = ExpenseTrackerSpacing.xxs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
private fun formatFrequency(frequency: RecurrenceFrequency): String {
    return when (frequency) {
        RecurrenceFrequency.DAILY -> stringResource(R.string.frequency_daily)
        RecurrenceFrequency.WEEKLY -> stringResource(R.string.frequency_weekly)
        RecurrenceFrequency.BIWEEKLY -> stringResource(R.string.frequency_biweekly)
        RecurrenceFrequency.MONTHLY -> stringResource(R.string.frequency_monthly)
        RecurrenceFrequency.YEARLY -> stringResource(R.string.frequency_yearly)
    }
}
