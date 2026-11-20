package com.suguru.expensetracker.presentation.export

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.presentation.export.localizedPdfReportLabels
import com.suguru.expensetracker.presentation.components.BackgroundGlowDecoration
import com.suguru.expensetracker.presentation.components.IconAvatar
import com.suguru.expensetracker.presentation.components.InteractiveCard
import com.suguru.expensetracker.presentation.components.PrimaryButton
import com.suguru.expensetracker.presentation.components.SectionHeader
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedExportScreen(
    viewModel: ExportViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pdfReportLabels = remember(context) { localizedPdfReportLabels(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val localizedSuccessMessage = uiState.successMessage?.let { stringResource(it.resourceId) }
    val localizedErrorMessage = uiState.errorMessage?.let { stringResource(it.resourceId) }
    val localizedProgressMessage = uiState.exportProgressMessage?.let { stringResource(it.resourceId) }

    var showDatePickerDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onNavigateBack)

    // SAF Document Creators
    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        viewModel.executeExportToStream(stream, pdfLabels = pdfReportLabels)
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar(context.getString(com.suguru.expensetracker.R.string.ui_export_write_failed))
                        viewModel.onExportCancelled()
                    }
                }
            }
        } else {
            viewModel.onExportCancelled()
        }
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        viewModel.executeExportToStream(stream, pdfLabels = pdfReportLabels)
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar(context.getString(com.suguru.expensetracker.R.string.ui_export_write_failed))
                        viewModel.onExportCancelled()
                    }
                }
            }
        } else {
            viewModel.onExportCancelled()
        }
    }

    LaunchedEffect(localizedSuccessMessage) {
        localizedSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(localizedErrorMessage) {
        localizedErrorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("advanced_export_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.title_financial_export),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("export_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(com.suguru.expensetracker.R.string.ui_back),
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
                    .padding(
                        horizontal = ExpenseTrackerSpacing.screenHorizontal,
                        vertical = ExpenseTrackerSpacing.screenVertical
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xl)
            ) {
                // Intro Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.export_overview_heading),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.export_overview_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.export_overview_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }

                // 1. Export Format Selection
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                ) {
                    SectionHeader(title = stringResource(com.suguru.expensetracker.R.string.ui_export_choose_format))

                    FormatOptionCard(
                        title = stringResource(com.suguru.expensetracker.R.string.ui_export_basic_csv),
                        subtitle = stringResource(com.suguru.expensetracker.R.string.ui_export_basic_csv_desc),
                        badgeText = stringResource(com.suguru.expensetracker.R.string.label_free),
                        badgeColor = MaterialTheme.colorScheme.primary,
                        icon = Icons.Default.TableChart,
                        isSelected = uiState.selectedFormat == ExportFormat.BASIC_CSV,
                        onClick = { viewModel.selectFormat(ExportFormat.BASIC_CSV) },
                        testTag = "format_basic_csv"
                    )

                    FormatOptionCard(
                        title = stringResource(com.suguru.expensetracker.R.string.ui_export_filtered_csv),
                        subtitle = stringResource(com.suguru.expensetracker.R.string.ui_export_filtered_csv_desc),
                        badgeText = stringResource(com.suguru.expensetracker.R.string.label_free),
                        badgeColor = MaterialTheme.colorScheme.primary,
                        icon = Icons.Default.FilterList,
                        isSelected = uiState.selectedFormat == ExportFormat.ADVANCED_CSV,
                        onClick = { viewModel.selectFormat(ExportFormat.ADVANCED_CSV) },
                        testTag = "format_advanced_csv"
                    )

                    FormatOptionCard(
                        title = stringResource(com.suguru.expensetracker.R.string.ui_export_pdf),
                        subtitle = stringResource(com.suguru.expensetracker.R.string.ui_export_pdf_desc),
                        badgeText = stringResource(com.suguru.expensetracker.R.string.label_free),
                        badgeColor = MaterialTheme.colorScheme.primary,
                        icon = Icons.Default.PictureAsPdf,
                        isSelected = uiState.selectedFormat == ExportFormat.PDF_REPORT,
                        onClick = { viewModel.selectFormat(ExportFormat.PDF_REPORT) },
                        testTag = "format_pdf_report"
                    )
                }

                // 2. Filters Section (Active for Advanced CSV & PDF Report)
                if (uiState.selectedFormat != ExportFormat.BASIC_CSV) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader(title = stringResource(com.suguru.expensetracker.R.string.ui_export_filter_params))
                            if (uiState.filter.isFiltered) {
                                TextButton(
                                    onClick = { viewModel.clearFilters() },
                                    modifier = Modifier.testTag("clear_export_filters_button")
                                ) {
                                    Text(
                                        text = stringResource(com.suguru.expensetracker.R.string.action_reset),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Date Range Filter Card
                        InteractiveCard(
                            onClick = { showDatePickerDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpenseTrackerRadius.card,
                            containerColor = ExpenseTrackerTheme.extendedColors.surface,
                            borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                            contentPadding = PaddingValues(ExpenseTrackerSpacing.md),
                            testTag = "export_date_filter_card"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                            ) {
                                IconAvatar(
                                    icon = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    size = 36.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(com.suguru.expensetracker.R.string.budget_period_label),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    val dateLabel = if (uiState.filter.startDate != null && uiState.filter.endDate != null) {
                                        "${uiState.filter.startDate} → ${uiState.filter.endDate}"
                                    } else {
                                        "All Time (Tap to select dates)"
                                    }
                                    Text(
                                        text = dateLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )
                                }
                            }
                        }

                        // Transaction Types Filter Chips
                        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                            Text(
                                text = stringResource(com.suguru.expensetracker.R.string.export_transaction_type),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ExpenseTrackerTheme.extendedColors.textSecondary
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                            ) {
                                TransactionType.entries.forEach { type ->
                                    val isSelected = uiState.filter.transactionTypes.contains(type)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.toggleTransactionTypeFilter(type) },
                                        label = { Text(type.name) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        // Currencies Filter Chips
                        if (uiState.availableCurrencies.size > 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                                Text(
                                    text = stringResource(com.suguru.expensetracker.R.string.currency_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs),
                                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                                ) {
                                    uiState.availableCurrencies.forEach { curr ->
                                        val isSelected = uiState.filter.currencyCodes.contains(curr)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.toggleCurrencyFilter(curr) },
                                            label = { Text(curr) },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }

                        // Categories Filter Chips
                        if (uiState.availableCategories.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                                Text(
                                    text = stringResource(com.suguru.expensetracker.R.string.category_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs),
                                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                                ) {
                                    uiState.availableCategories.take(12).forEach { cat ->
                                        val isSelected = uiState.filter.categoryIds.contains(cat.id)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.toggleCategoryFilter(cat.id) },
                                            label = { Text(cat.name) },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                ) {
                    PrimaryButton(
                        text = if (uiState.isWritingFile) stringResource(com.suguru.expensetracker.R.string.export_progress_action) else {
                            when (uiState.selectedFormat) {
                                ExportFormat.BASIC_CSV -> stringResource(com.suguru.expensetracker.R.string.export_action_full_csv)
                                ExportFormat.ADVANCED_CSV -> stringResource(com.suguru.expensetracker.R.string.export_action_filtered_csv)
                                ExportFormat.PDF_REPORT -> stringResource(com.suguru.expensetracker.R.string.export_action_pdf_report)
                            }
                        },
                        onClick = { viewModel.onExportRequested() },
                        enabled = !uiState.isWritingFile,
                        leadingIcon = if (uiState.selectedFormat == ExportFormat.PDF_REPORT) Icons.Default.PictureAsPdf else Icons.Default.FileDownload,
                        testTag = "submit_export_button",
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.isWritingFile) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.sm))
                            Text(
                                text = localizedProgressMessage ?: stringResource(com.suguru.expensetracker.R.string.ui_export_generating),
                                style = MaterialTheme.typography.bodySmall,
                                color = ExpenseTrackerTheme.extendedColors.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))
            }
        }
    }

    // Privacy Warning Confirmation Dialog
    if (uiState.showPrivacyWarningDialog) {
        val fileName = uiState.targetFileName ?: "ExpenseTracker_Export"
        AlertDialog(
            onDismissRequest = { viewModel.dismissPrivacyWarning() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(com.suguru.expensetracker.R.string.export_save_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.export_confirm_path),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.export_privacy_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(com.suguru.expensetracker.R.string.export_choose_location),
                    onClick = {
                        val format = uiState.pendingExportAction ?: uiState.selectedFormat
                        viewModel.proceedWithSafExport()
                        if (format == ExportFormat.PDF_REPORT) {
                            createPdfLauncher.launch(fileName)
                        } else {
                            createCsvLauncher.launch(fileName)
                        }
                    },
                    testTag = "confirm_save_export_location_button"
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissPrivacyWarning() },
                    modifier = Modifier.testTag("cancel_export_dialog_button")
                ) {
                    Text(stringResource(com.suguru.expensetracker.R.string.ui_cancel))
                }
            }
        )
    }

    // Date Range Picker Dialog
    if (showDatePickerDialog) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            val startDate = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                            val endDate = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                            viewModel.setDateRange(startDate, endDate)
                        }
                        showDatePickerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_date_range_picker")
                ) {
                    Text(stringResource(com.suguru.expensetracker.R.string.ui_apply))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePickerDialog = false }
                ) {
                    Text(stringResource(com.suguru.expensetracker.R.string.ui_cancel))
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text(stringResource(com.suguru.expensetracker.R.string.ui_select_date_range), modifier = Modifier.padding(16.dp)) },
                headline = { Text(stringResource(com.suguru.expensetracker.R.string.ui_filter_export_period), modifier = Modifier.padding(horizontal = 16.dp)) },
                showModeToggle = false
            )
        }
    }
}

@Composable
private fun FormatOptionCard(
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        ExpenseTrackerTheme.extendedColors.borderSubtle
    }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        ExpenseTrackerTheme.extendedColors.surface
    }

    InteractiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ExpenseTrackerRadius.card,
        containerColor = containerColor,
        borderColor = borderColor,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.md),
        testTag = testTag
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
        ) {
            IconAvatar(
                icon = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else ExpenseTrackerTheme.extendedColors.textSecondary,
                size = 40.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        color = badgeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
