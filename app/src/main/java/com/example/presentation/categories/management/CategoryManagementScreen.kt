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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Category
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.presentation.common.FinanceVisuals
import com.example.presentation.components.BackgroundGlowDecoration
import com.example.presentation.components.EmptyState
import com.example.presentation.components.ErrorBanner
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.ExpenseTrackerConfirmationDialog
import com.example.presentation.components.HeroCard
import com.example.presentation.components.IconAvatar
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
                    Column {
                        Text(
                            text = stringResource(R.string.title_category_management),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "EXPENSE & INCOME TAGS",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BackgroundGlowDecoration(alpha = 0.10f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
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

                        ErrorBanner(
                            message = errorMessage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp)
                                .padding(bottom = ExpenseTrackerSpacing.md),
                            testTag = "category_management_error_banner"
                        )
                    }
                }

                // Filter Tabs in card-like container
                Surface(
                    shape = ExpenseTrackerRadius.card,
                    color = ExpenseTrackerTheme.extendedColors.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        ExpenseTrackerTheme.extendedColors.borderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = uiState.selectedTab.ordinal,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        edgePadding = ExpenseTrackerSpacing.xs,
                        divider = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
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
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        // Category Overview Card
                        item(key = "categories_overview_hero") {
                            CategoryOverviewCard(
                                totalCount = uiState.allCategories.size,
                                filteredCount = uiState.filteredCategories.size,
                                selectedTab = uiState.selectedTab
                            )
                        }

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
    }

    // Archive Confirmation Dialog
    if (uiState.categoryToArchive != null) {
        val category = uiState.categoryToArchive
        ExpenseTrackerConfirmationDialog(
            title = stringResource(R.string.dialog_archive_category_title, category.name),
            message = stringResource(R.string.dialog_archive_category_message),
            confirmText = stringResource(R.string.action_archive),
            dismissText = stringResource(R.string.action_cancel),
            isDestructive = true,
            onConfirm = onConfirmArchive,
            onDismissRequest = onDismissArchiveDialog,
            confirmTestTag = "confirm_archive_category_button",
            dismissTestTag = "cancel_archive_category_button",
            testTag = "archive_category_confirmation_dialog"
        )
    }
}

@Composable
private fun CategoryOverviewCard(
    totalCount: Int,
    filteredCount: Int,
    selectedTab: CategoryFilterTab,
    modifier: Modifier = Modifier
) {
    HeroCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("categories_overview_hero")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CATEGORIES DIRECTORY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                    color = ExpenseTrackerTheme.extendedColors.primaryPurple
                )
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
                Text(
                    text = "$filteredCount Active ${if (filteredCount == 1) "Category" else "Categories"}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.16f))
                    .border(1.dp, ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Category,
                    contentDescription = null,
                    tint = ExpenseTrackerTheme.extendedColors.primaryPurple,
                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                )
            }
        }
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
        shape = ExpenseTrackerRadius.cardInteractive,
        containerColor = ExpenseTrackerTheme.extendedColors.surface,
        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
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
                IconAvatar(
                    icon = icon,
                    contentDescription = category.name,
                    tint = accentColor,
                    backgroundColor = accentColor.copy(alpha = 0.15f),
                    borderColor = accentColor.copy(alpha = 0.35f),
                    size = 46.dp
                )

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
                            shape = ExpenseTrackerRadius.chipPill,
                            color = when (category.type) {
                                CategoryType.EXPENSE -> ExpenseTrackerTheme.extendedColors.financialNegativeContainer.copy(alpha = 0.5f)
                                CategoryType.INCOME -> ExpenseTrackerTheme.extendedColors.financialPositiveContainer.copy(alpha = 0.5f)
                                CategoryType.BOTH -> ExpenseTrackerTheme.extendedColors.surfaceHighlight
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                ExpenseTrackerTheme.extendedColors.borderSubtle
                            )
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = when (category.type) {
                                    CategoryType.EXPENSE -> ExpenseTrackerTheme.extendedColors.financialNegative
                                    CategoryType.INCOME -> ExpenseTrackerTheme.extendedColors.financialPositive
                                    CategoryType.BOTH -> ExpenseTrackerTheme.extendedColors.textSecondary
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        // System Tag if applicable
                        if (category.isSystem) {
                            Surface(
                                shape = ExpenseTrackerRadius.chipPill,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.badge_system),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.sm))

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
