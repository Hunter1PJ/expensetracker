package com.example.presentation.accounts.add

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.AccountType
import com.example.domain.model.CurrencyInfo
import com.example.presentation.common.ColorOption
import com.example.presentation.common.FinanceVisuals
import com.example.presentation.common.IconOption
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.LoadingState
import com.example.presentation.components.SectionHeader
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

@Composable
fun AddAccountScreen(
    viewModel: AddAccountViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onNavigateBack)

    LaunchedEffect(uiState.isSavedSuccessfully) {
        if (uiState.isSavedSuccessfully) {
            onNavigateBack()
        }
    }

    AddAccountScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNameChanged = viewModel::onNameChanged,
        onTypeChanged = viewModel::onTypeChanged,
        onCurrencyChanged = viewModel::onCurrencyChanged,
        onInitialBalanceChanged = viewModel::onInitialBalanceChanged,
        onIconChanged = viewModel::onIconChanged,
        onColorChanged = viewModel::onColorChanged,
        onSaveAccount = viewModel::saveAccount,
        onDismissError = viewModel::onDismissError,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAccountScreenContent(
    uiState: AddAccountUiState,
    onNavigateBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onTypeChanged: (AccountType) -> Unit,
    onCurrencyChanged: (CurrencyInfo) -> Unit,
    onInitialBalanceChanged: (String) -> Unit,
    onIconChanged: (String) -> Unit,
    onColorChanged: (String) -> Unit,
    onSaveAccount: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_account_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditMode) {
                            stringResource(R.string.title_edit_account)
                        } else {
                            stringResource(R.string.title_add_account)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            LoadingState(
                message = stringResource(R.string.loading_account_details),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .imePadding()
                    .padding(
                        horizontal = ExpenseTrackerSpacing.screenHorizontal,
                        vertical = ExpenseTrackerSpacing.screenVertical
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xl)
            ) {
                // Error Banner
                AnimatedVisibility(visible = uiState.error != null) {
                    uiState.error?.let { err ->
                        val errorMessage = when (err) {
                            AddAccountError.NameRequired -> stringResource(R.string.error_account_name_required)
                            is AddAccountError.InvalidInitialBalance -> err.message ?: stringResource(R.string.error_invalid_initial_balance)
                            AddAccountError.AccountNotFound -> stringResource(R.string.error_account_not_found)
                            is AddAccountError.SaveFailed -> err.message ?: stringResource(R.string.error_save_account_failed)
                        }

                        ExpenseTrackerCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp),
                            containerColor = ExpenseTrackerTheme.extendedColors.financialNegativeContainer.copy(alpha = 0.4f),
                            borderColor = ExpenseTrackerTheme.extendedColors.financialNegative,
                            testTag = "add_account_error_banner"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = ExpenseTrackerTheme.extendedColors.financialNegative,
                                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                                )
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 1. Account Name Input
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                ) {
                    SectionHeader(title = stringResource(R.string.account_name_label))
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = onNameChanged,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.account_name_hint),
                                color = ExpenseTrackerTheme.extendedColors.textTertiary
                            )
                        },
                        singleLine = true,
                        shape = ExpenseTrackerRadius.button,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                            unfocusedContainerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = ExpenseTrackerTheme.extendedColors.cardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_name_input_field")
                    )
                }

                // 2. Account Type Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                ) {
                    SectionHeader(title = stringResource(R.string.account_type_label))
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_type_selector"),
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        AccountType.entries.forEach { type ->
                            val isSelected = uiState.type == type
                            val typeLabel = FinanceVisuals.getAccountTypeLabel(type)

                            Surface(
                                shape = ExpenseTrackerRadius.chip,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else ExpenseTrackerTheme.extendedColors.cardBackground,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else ExpenseTrackerTheme.extendedColors.cardBorder
                                ),
                                modifier = Modifier
                                    .clip(ExpenseTrackerRadius.chip)
                                    .clickable { onTypeChanged(type) }
                                    .testTag("account_type_${type.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = ExpenseTrackerSpacing.md,
                                        vertical = ExpenseTrackerSpacing.sm
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                                ) {
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Currency Selector (Immutable in edit mode)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                    ) {
                        SectionHeader(title = stringResource(R.string.currency_label))
                        if (!uiState.isCurrencyEditable) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.currency_locked_desc),
                                tint = ExpenseTrackerTheme.extendedColors.textTertiary,
                                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("currency_selector"),
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        CurrencyInfo.SUPPORTED_CURRENCIES.forEach { currency ->
                            val isSelected = uiState.selectedCurrency.currencyCode == currency.currencyCode

                            Surface(
                                shape = ExpenseTrackerRadius.chip,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else ExpenseTrackerTheme.extendedColors.cardBackground,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else ExpenseTrackerTheme.extendedColors.cardBorder
                                ),
                                modifier = Modifier
                                    .clip(ExpenseTrackerRadius.chip)
                                    .clickable(enabled = uiState.isCurrencyEditable) {
                                        onCurrencyChanged(currency)
                                    }
                                    .testTag("currency_chip_${currency.currencyCode.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = ExpenseTrackerSpacing.md,
                                        vertical = ExpenseTrackerSpacing.sm
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                                ) {
                                    Text(
                                        text = "${currency.currencyCode} (${currency.symbol})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Initial Balance Input (Immutable in edit mode)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                    ) {
                        SectionHeader(title = stringResource(R.string.initial_balance_label))
                        if (!uiState.isInitialBalanceEditable) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.initial_balance_locked_desc),
                                tint = ExpenseTrackerTheme.extendedColors.textTertiary,
                                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                    OutlinedTextField(
                        value = uiState.initialBalanceInput,
                        onValueChange = onInitialBalanceChanged,
                        enabled = uiState.isInitialBalanceEditable,
                        placeholder = {
                            Text(
                                text = "0",
                                color = ExpenseTrackerTheme.extendedColors.textTertiary
                            )
                        },
                        leadingIcon = {
                            Text(
                                text = uiState.selectedCurrency.symbol,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = ExpenseTrackerSpacing.md)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = ExpenseTrackerRadius.button,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                            unfocusedContainerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                            disabledContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                            disabledBorderColor = ExpenseTrackerTheme.extendedColors.cardBorder.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("initial_balance_input_field")
                    )
                }

                // 5. Icon Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                ) {
                    SectionHeader(title = stringResource(R.string.icon_label))
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_icon_picker"),
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        FinanceVisuals.ACCOUNT_ICONS.forEach { iconOption ->
                            val isSelected = uiState.selectedIconName == iconOption.id
                            IconPickerItem(
                                iconOption = iconOption,
                                isSelected = isSelected,
                                onClick = { onIconChanged(iconOption.id) },
                                testTag = "account_icon_${iconOption.id}"
                            )
                        }
                    }
                }

                // 6. Color Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                ) {
                    SectionHeader(title = stringResource(R.string.color_label))
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_color_picker"),
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        FinanceVisuals.CURATED_COLORS.forEach { colorOption ->
                            val isSelected = uiState.selectedColorHex.equals(colorOption.hex, ignoreCase = true)
                            ColorPickerItem(
                                colorOption = colorOption,
                                isSelected = isSelected,
                                onClick = { onColorChanged(colorOption.hex) },
                                testTag = "account_color_${colorOption.hex.removePrefix("#").lowercase()}"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))

                // 7. Save Button
                Button(
                    onClick = onSaveAccount,
                    enabled = !uiState.isSaving,
                    shape = ExpenseTrackerRadius.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
                        disabledContentColor = ExpenseTrackerTheme.extendedColors.textSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                        .height(52.dp)
                        .testTag("save_account_button")
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.sm))
                        Text(text = stringResource(R.string.action_saving))
                    } else {
                        Text(
                            text = if (uiState.isEditMode) {
                                stringResource(R.string.action_update_account)
                            } else {
                                stringResource(R.string.action_save_account)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))
            }
        }
    }
}

@Composable
fun IconPickerItem(
    iconOption: IconOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(ExpenseTrackerRadius.chip)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else ExpenseTrackerTheme.extendedColors.cardBackground
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else ExpenseTrackerTheme.extendedColors.cardBorder,
                shape = ExpenseTrackerRadius.chip
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconOption.icon,
            contentDescription = iconOption.label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else ExpenseTrackerTheme.extendedColors.textSecondary,
            modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
        )
    }
}

@Composable
fun ColorPickerItem(
    colorOption: ColorOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(colorOption.color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = colorOption.label,
                tint = Color.White,
                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
            )
        }
    }
}
