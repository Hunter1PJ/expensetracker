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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.RecurrenceFrequency
import com.example.domain.model.TransactionType
import com.example.presentation.components.BackgroundGlowDecoration
import com.example.presentation.components.EmptyState
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.ExpenseTrackerConfirmationDialog
import com.example.presentation.components.FinancialAmount
import com.example.presentation.components.HeroCard
import com.example.presentation.components.IconAvatar
import com.example.presentation.components.LoadingState
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.title_recurring_transactions),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "AUTOMATED SCHEDULES & RULES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("button_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.processDueNow() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = ExpenseTrackerRadius.button,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = ExpenseTrackerSpacing.sm)
                            .testTag("button_process_due_now")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_process_due),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddRule,
                shape = ExpenseTrackerRadius.button,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier.testTag("add_recurring_rule_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_recurring)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BackgroundGlowDecoration(alpha = 0.08f)

            when (val state = uiState) {
                is RecurringTransactionsUiState.Loading -> {
                    LoadingState(
                        message = "Loading recurring schedules...",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is RecurringTransactionsUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
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
                        EmptyState(
                            icon = Icons.Default.Repeat,
                            title = stringResource(R.string.no_recurring_title),
                            description = stringResource(R.string.no_recurring_desc),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val activeCount = state.rules.count { it.isActive }
                        val pausedCount = state.rules.size - activeCount

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = ExpenseTrackerSpacing.screenHorizontal,
                                vertical = ExpenseTrackerSpacing.screenVertical
                            ),
                            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Hero Overview Card
                            item(key = "recurring_hero_card") {
                                HeroCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 540.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                                        Text(
                                            text = "AUTOMATION SUMMARY",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            Column {
                                                Text(
                                                    text = "${state.rules.size} Rules",
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                                Text(
                                                    text = "$activeCount active • $pausedCount paused",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                                )
                                            }

                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                shape = CircleShape
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .padding(10.dp)
                                                        .size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "recurring_section_header") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 540.dp)
                                ) {
                                    SectionHeader(title = "Configured Schedules")
                                }
                            }

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

                            item {
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    selectedDeactivateRuleId?.let { ruleId ->
        ExpenseTrackerConfirmationDialog(
            title = stringResource(R.string.dialog_deactivate_recurring_title),
            message = stringResource(R.string.dialog_deactivate_recurring_message),
            confirmText = stringResource(R.string.action_pause),
            dismissText = stringResource(R.string.action_cancel),
            isDestructive = true,
            onConfirm = {
                viewModel.deactivateRule(ruleId)
                selectedDeactivateRuleId = null
            },
            onDismissRequest = { selectedDeactivateRuleId = null },
            confirmTestTag = "confirm_deactivate_button",
            dismissTestTag = "cancel_deactivate_button",
            testTag = "deactivate_recurring_dialog"
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
        containerColor = ExpenseTrackerTheme.extendedColors.surface,
        borderColor = when (rule.status) {
            RecurringStatus.NEEDS_ATTENTION -> ExpenseTrackerTheme.extendedColors.financialNegative.copy(alpha = 0.5f)
            RecurringStatus.ENDING_SOON -> ExpenseTrackerTheme.extendedColors.financialNeutral.copy(alpha = 0.5f)
            else -> ExpenseTrackerTheme.extendedColors.borderSubtle
        },
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
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
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("rule_menu_button_${rule.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Actions",
                            tint = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(ExpenseTrackerTheme.extendedColors.surfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.action_edit),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                            modifier = Modifier.testTag("rule_edit_item_${rule.id}")
                        )
                        if (rule.isActive) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.action_pause),
                                        color = ExpenseTrackerTheme.extendedColors.financialNegative
                                    )
                                },
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
                        .background(ExpenseTrackerTheme.extendedColors.financialNegativeContainer.copy(alpha = 0.4f))
                        .padding(ExpenseTrackerSpacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ExpenseTrackerTheme.extendedColors.financialNegative,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = rule.statusDetail,
                        style = MaterialTheme.typography.labelSmall,
                        color = ExpenseTrackerTheme.extendedColors.financialNegative
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
                    fontWeight = FontWeight.SemiBold,
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
    val (labelRes, color, bgColor) = when (type) {
        TransactionType.EXPENSE -> Triple(
            R.string.type_expense,
            ExpenseTrackerTheme.extendedColors.financialNegative,
            ExpenseTrackerTheme.extendedColors.financialNegativeContainer
        )
        TransactionType.INCOME -> Triple(
            R.string.type_income,
            ExpenseTrackerTheme.extendedColors.financialPositive,
            ExpenseTrackerTheme.extendedColors.financialPositiveContainer
        )
        TransactionType.TRANSFER -> Triple(
            R.string.type_transfer,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    }
    Surface(
        color = bgColor,
        shape = ExpenseTrackerRadius.button
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StatusChip(status: RecurringStatus) {
    val (label, bgColor, textColor) = when (status) {
        RecurringStatus.ACTIVE -> Triple(
            stringResource(R.string.status_active),
            ExpenseTrackerTheme.extendedColors.financialPositiveContainer,
            ExpenseTrackerTheme.extendedColors.financialPositive
        )
        RecurringStatus.PAUSED -> Triple(
            stringResource(R.string.status_paused),
            ExpenseTrackerTheme.extendedColors.surfaceElevated,
            ExpenseTrackerTheme.extendedColors.textSecondary
        )
        RecurringStatus.ENDING_SOON -> Triple(
            stringResource(R.string.status_ending_soon),
            ExpenseTrackerTheme.extendedColors.financialNeutralContainer,
            ExpenseTrackerTheme.extendedColors.financialNeutral
        )
        RecurringStatus.NEEDS_ATTENTION -> Triple(
            stringResource(R.string.status_needs_attention),
            ExpenseTrackerTheme.extendedColors.financialNegativeContainer,
            ExpenseTrackerTheme.extendedColors.financialNegative
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
