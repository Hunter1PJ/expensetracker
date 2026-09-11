package com.example.presentation.categories.management

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.domain.model.Category
import com.example.presentation.common.FinanceVisuals
import com.example.presentation.components.EmptyState
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.LoadingState
import com.example.presentation.components.PrimaryButton
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

@Composable
fun CategoryManagementScreen(
    viewModel: CategoryManagementViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddCategory: () -> Unit,
    onNavigateToEditCategory: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onNavigateBack)

    CategoryManagementContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToAddCategory = onNavigateToAddCategory,
        onEditCategory = onNavigateToEditCategory,
        onTabSelected = viewModel::onTabSelected,
        onArchiveClicked = viewModel::onArchiveCategoryClicked,
        onDismissArchiveDialog = viewModel::onDismissArchiveDialog,
        onConfirmArchive = viewModel::onConfirmArchive,
        onDismissError = viewModel::onDismissError,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementContent(
    uiState: CategoryManagementUiState,
    onNavigateBack: () -> Unit,
    onNavigateToAddCategory: () -> Unit,
    onEditCategory: (Long) -> Unit,
    onTabSelected: (CategoryFilterTab) -> Unit,
    onArchiveClicked: (Category) -> Unit,
    onDismissArchiveDialog: () -> Unit,
    onConfirmArchive: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("category_management_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_category_management),
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddCategory,
                shape = ExpenseTrackerRadius.button,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                modifier = Modifier.testTag("add_category_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_category),
                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.lg)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(
                    horizontal = ExpenseTrackerSpacing.screenHorizontal,
                    vertical = ExpenseTrackerSpacing.screenVertical
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Error banner
            AnimatedVisibility(visible = uiState.error != null) {
                uiState.error?.let { err ->
                    val errorMessage = when (err) {
                        CategoryManagementError.CategoryNotFound -> stringResource(R.string.error_category_not_found)
                        CategoryManagementError.SystemCategoryCannotBeArchived -> stringResource(R.string.error_system_category_cannot_archive)
                        is CategoryManagementError.ArchiveFailed -> err.message ?: stringResource(R.string.error_archive_category_failed)
                    }

                    ExpenseTrackerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                            .padding(bottom = ExpenseTrackerSpacing.md),
                        containerColor = ExpenseTrackerTheme.extendedColors.financialNegativeContainer.copy(alpha = 0.4f),
                        borderColor = ExpenseTrackerTheme.extendedColors.financialNegative,
                        testTag = "category_management_error_banner"
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

            // Filter Tabs
            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 0.dp,
                divider = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .testTag("category_filter_tabs")
            ) {
                CategoryFilterTab.entries.forEach { tab ->
                    val isSelected = uiState.selectedTab == tab
                    val tabLabel = when (tab) {
                        CategoryFilterTab.ALL -> stringResource(R.string.tab_all)
                        CategoryFilterTab.EXPENSE -> stringResource(R.string.tab_expense)
                        CategoryFilterTab.INCOME -> stringResource(R.string.tab_income)
                        CategoryFilterTab.BOTH -> stringResource(R.string.tab_both)
                    }

                    Tab(
                        selected = isSelected,
                        onClick = { onTabSelected(tab) },
                        text = {
                            Text(
                                text = tabLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = ExpenseTrackerTheme.extendedColors.textSecondary,
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))

            if (uiState.isLoading) {
                LoadingState(
                    message = stringResource(R.string.loading_categories),
                    modifier = Modifier.fillMaxSize(),
                    testTag = "categories_loading_state"
                )
            } else if (uiState.filteredCategories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 540.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.lg)
                    ) {
                        EmptyState(
                            title = stringResource(R.string.no_categories_yet_title),
                            description = stringResource(R.string.no_categories_yet_desc),
                            icon = Icons.Outlined.Category,
                            testTag = "no_categories_empty_state"
                        )
                        PrimaryButton(
                            text = stringResource(R.string.action_add_category),
                            onClick = onNavigateToAddCategory,
                            testTag = "empty_state_add_category_button"
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                        .weight(1f)
                        .testTag("categories_list"),
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(
                        items = uiState.filteredCategories,
                        key = { it.id }
                    ) { category ->
                        CategoryItemCard(
                            category = category,
                            onClick = {
                                if (!category.isSystem) {
                                    onEditCategory(category.id)
                                }
                            },
                            onArchive = { onArchiveClicked(category) },
                            testTag = "category_card_${category.id}"
                        )
                    }
                }
            }
        }
    }

    // Archive Confirmation Dialog
    if (uiState.categoryToArchive != null) {
        val category = uiState.categoryToArchive
        AlertDialog(
            onDismissRequest = onDismissArchiveDialog,
            title = {
                Text(
                    text = stringResource(R.string.dialog_archive_category_title, category.name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_archive_category_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmArchive,
                    enabled = !uiState.isArchiving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpenseTrackerTheme.extendedColors.financialNegative,
                        contentColor = Color.White
                    ),
                    shape = ExpenseTrackerRadius.button,
                    modifier = Modifier.testTag("confirm_archive_category_button")
                ) {
                    if (uiState.isArchiving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(text = stringResource(R.string.action_archive))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissArchiveDialog,
                    enabled = !uiState.isArchiving,
                    modifier = Modifier.testTag("cancel_archive_category_button")
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
            containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
            shape = ExpenseTrackerRadius.card
        )
    }
}

@Composable
private fun CategoryItemCard(
    category: Category,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val icon = FinanceVisuals.getCategoryIcon(category.iconName)
    val accentColor = FinanceVisuals.parseColorHex(category.colorHex)
    val typeLabel = FinanceVisuals.getCategoryTypeLabel(category.type)

    ExpenseTrackerCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !category.isSystem, onClick = onClick)
            .testTag(testTag),
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
        borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
            ) {
                // Icon Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                    )
                }

                // Name & Metadata
                Column(
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xxs)
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                    ) {
                        // Type Tag
                        Surface(
                            shape = RoundedCornerShape(ExpenseTrackerRadius.xs),
                            color = ExpenseTrackerTheme.extendedColors.surfaceHighlight
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = ExpenseTrackerTheme.extendedColors.textSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // System Tag if applicable
                        if (category.isSystem) {
                            Surface(
                                shape = RoundedCornerShape(ExpenseTrackerRadius.xs),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.badge_system),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Right side: Archive button or lock indicator for system categories
            if (!category.isSystem) {
                IconButton(
                    onClick = onArchive,
                    modifier = Modifier.testTag("archive_category_button_${category.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = stringResource(R.string.action_archive),
                        tint = ExpenseTrackerTheme.extendedColors.textSecondary,
                        modifier = Modifier.size(ExpenseTrackerTheme.iconSize.sm)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.system_category_protected_desc),
                    tint = ExpenseTrackerTheme.extendedColors.textTertiary,
                    modifier = Modifier
                        .padding(end = ExpenseTrackerSpacing.sm)
                        .size(ExpenseTrackerTheme.iconSize.xs)
                )
            }
        }
    }
}
