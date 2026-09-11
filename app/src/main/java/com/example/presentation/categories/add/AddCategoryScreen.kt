package com.example.presentation.categories.add

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.CategoryType
import com.example.presentation.accounts.add.ColorPickerItem
import com.example.presentation.accounts.add.IconPickerItem
import com.example.presentation.common.FinanceVisuals
import com.example.presentation.components.BackgroundGlowDecoration
import com.example.presentation.components.ErrorBanner
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.LoadingState
import com.example.presentation.components.SectionHeader
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

@Composable
fun AddCategoryScreen(
    viewModel: AddCategoryViewModel,
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

    AddCategoryScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNameChanged = viewModel::onNameChanged,
        onTypeChanged = viewModel::onTypeChanged,
        onIconChanged = viewModel::onIconChanged,
        onColorChanged = viewModel::onColorChanged,
        onSaveCategory = viewModel::saveCategory,
        onDismissError = viewModel::onDismissError,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCategoryScreenContent(
    uiState: AddCategoryUiState,
    onNavigateBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onTypeChanged: (CategoryType) -> Unit,
    onIconChanged: (String) -> Unit,
    onColorChanged: (String) -> Unit,
    onSaveCategory: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_category_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (uiState.isEditMode) {
                                stringResource(R.string.title_edit_category)
                            } else {
                                stringResource(R.string.title_add_category)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (uiState.isEditMode) "UPDATE CATEGORY CONFIGURATION" else "CONFIGURE NEW CATEGORY",
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
                message = stringResource(R.string.loading_category_details),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                BackgroundGlowDecoration(alpha = 0.08f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .imePadding()
                        .padding(
                            horizontal = ExpenseTrackerSpacing.screenHorizontal,
                            vertical = ExpenseTrackerSpacing.screenVertical
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.lg)
                ) {
                    // Error Banner
                    AnimatedVisibility(visible = uiState.error != null) {
                        uiState.error?.let { err ->
                            val errorMessage = when (err) {
                                AddCategoryError.NameRequired -> stringResource(R.string.error_category_name_required)
                                AddCategoryError.CategoryNotFound -> stringResource(R.string.error_category_not_found)
                                is AddCategoryError.SaveFailed -> err.message ?: stringResource(R.string.error_save_category_failed)
                            }

                            ErrorBanner(
                                message = errorMessage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 540.dp),
                                testTag = "add_category_error_banner"
                            )
                        }
                    }

                    // Card 1: Category Name & Type
                    ExpenseTrackerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp),
                        containerColor = ExpenseTrackerTheme.extendedColors.surface,
                        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
                            SectionHeader(title = stringResource(R.string.category_name_label))

                            OutlinedTextField(
                                value = uiState.name,
                                onValueChange = onNameChanged,
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.category_name_hint),
                                        color = ExpenseTrackerTheme.extendedColors.textTertiary
                                    )
                                },
                                singleLine = true,
                                shape = ExpenseTrackerRadius.button,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                    unfocusedContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = ExpenseTrackerTheme.extendedColors.borderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_name_input_field")
                            )

                            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))
                            SectionHeader(title = stringResource(R.string.category_type_label))

                            val types = listOf(
                                CategoryType.EXPENSE to stringResource(R.string.type_expense),
                                CategoryType.INCOME to stringResource(R.string.type_income),
                                CategoryType.BOTH to stringResource(R.string.type_both)
                            )

                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_type_selector")
                            ) {
                                types.forEachIndexed { index, (type, label) ->
                                    val isSelected = uiState.type == type
                                    SegmentedButton(
                                        selected = isSelected,
                                        onClick = { onTypeChanged(type) },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                                        colors = SegmentedButtonDefaults.colors(
                                            activeContainerColor = when (type) {
                                                CategoryType.EXPENSE -> ExpenseTrackerTheme.extendedColors.financialNegativeContainer
                                                CategoryType.INCOME -> ExpenseTrackerTheme.extendedColors.financialPositiveContainer
                                                CategoryType.BOTH -> MaterialTheme.colorScheme.primaryContainer
                                            },
                                            activeContentColor = MaterialTheme.colorScheme.onBackground,
                                            inactiveContainerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                                            inactiveContentColor = ExpenseTrackerTheme.extendedColors.textSecondary
                                        ),
                                        modifier = Modifier.testTag("category_type_${type.name.lowercase()}")
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Card 2: Category Icon & Color
                    ExpenseTrackerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp),
                        containerColor = ExpenseTrackerTheme.extendedColors.surface,
                        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
                            SectionHeader(title = stringResource(R.string.icon_label))

                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_icon_picker"),
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                            ) {
                                FinanceVisuals.CATEGORY_ICONS.forEach { iconOption ->
                                    val isSelected = uiState.selectedIconName == iconOption.id
                                    IconPickerItem(
                                        iconOption = iconOption,
                                        isSelected = isSelected,
                                        onClick = { onIconChanged(iconOption.id) },
                                        testTag = "category_icon_${iconOption.id}"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))
                            SectionHeader(title = stringResource(R.string.color_label))

                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_color_picker"),
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                            ) {
                                FinanceVisuals.CURATED_COLORS.forEach { colorOption ->
                                    val isSelected = uiState.selectedColorHex.equals(colorOption.hex, ignoreCase = true)
                                    ColorPickerItem(
                                        colorOption = colorOption,
                                        isSelected = isSelected,
                                        onClick = { onColorChanged(colorOption.hex) },
                                        testTag = "category_color_${colorOption.hex.removePrefix("#").lowercase()}"
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                    // Save Button
                    Button(
                        onClick = onSaveCategory,
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
                            .height(54.dp)
                            .testTag("save_category_button")
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
                                    stringResource(R.string.action_update_category)
                                } else {
                                    stringResource(R.string.action_save_category)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))
                }
            }
        }
    }
}
