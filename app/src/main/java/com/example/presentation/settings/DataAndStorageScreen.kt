package com.example.presentation.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.presentation.components.BackgroundGlowDecoration
import com.example.presentation.components.DangerButton
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.IconAvatar
import com.example.presentation.components.PrimaryButton
import com.example.presentation.components.SecondaryButton
import com.example.presentation.components.SectionHeader
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataAndStorageScreen(
    viewModel: DataStorageViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(onBack = onNavigateBack)

    // SAF Launchers
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && uiState.backupJsonToWrite != null) {
            val json = uiState.backupJsonToWrite!!
            val fileName = uiState.backupFileName ?: "ExpenseTracker_Backup.json"
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        viewModel.onBackupWritten(fileName)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Write failed: ${e.localizedMessage}")
                        viewModel.cancelBackupWrite()
                    }
                }
            }
        } else {
            viewModel.cancelBackupWrite()
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null && uiState.csvStringToWrite != null) {
            val csv = uiState.csvStringToWrite!!
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(csv.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        viewModel.onCsvWritten()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("CSV export failed: ${e.localizedMessage}")
                        viewModel.cancelCsvWrite()
                    }
                }
            }
        } else {
            viewModel.cancelCsvWrite()
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val stringBuilder = StringBuilder()
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                            var line = reader.readLine()
                            while (line != null) {
                                stringBuilder.append(line).append('\n')
                                line = reader.readLine()
                            }
                        }
                    }
                    val json = stringBuilder.toString()
                    withContext(Dispatchers.Main) {
                        viewModel.inspectBackupForRestore(json)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Read failed: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    // React to triggers in UI State
    LaunchedEffect(uiState.backupJsonToWrite) {
        if (uiState.backupJsonToWrite != null && uiState.backupFileName != null) {
            createBackupLauncher.launch(uiState.backupFileName!!)
        }
    }

    LaunchedEffect(uiState.csvStringToWrite) {
        if (uiState.csvStringToWrite != null && uiState.csvFileName != null) {
            createCsvLauncher.launch(uiState.csvFileName!!)
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier.testTag("data_and_storage_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Data & Storage",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Backup, restore & CSV export",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("data_and_storage_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
        Box(modifier = Modifier.fillMaxSize()) {
            BackgroundGlowDecoration(alpha = 0.08f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = ExpenseTrackerSpacing.screenHorizontal,
                        vertical = ExpenseTrackerSpacing.screenVertical
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xl)
            ) {
                // 1. Local & Offline Storage Hero Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                ) {
                    SectionHeader(
                        title = "Database & File Storage",
                        testTag = "section_data_storage_info"
                    )

                    ExpenseTrackerCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpenseTrackerRadius.cardHero,
                        containerColor = ExpenseTrackerTheme.extendedColors.surface,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.xl),
                        testTag = "local_data_card"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                        ) {
                            IconAvatar(
                                icon = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                size = 44.dp
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                                Text(
                                    text = "Private local app storage",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Your financial data is stored locally on this device. Back up regularly to protect your history against accidental loss or device replacement.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "Private local storage",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "No automatic cloud sync",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ExpenseTrackerTheme.extendedColors.textSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Data Operations Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                ) {
                    SectionHeader(
                        title = "Data Operations",
                        testTag = "section_backup_export_info"
                    )

                    // Last Backup Metadata Card
                    ExpenseTrackerCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpenseTrackerRadius.card,
                        containerColor = ExpenseTrackerTheme.extendedColors.surface,
                        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                        contentPadding = PaddingValues(
                            horizontal = ExpenseTrackerSpacing.lg,
                            vertical = ExpenseTrackerSpacing.md
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = ExpenseTrackerTheme.extendedColors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Last Full Backup:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                            }
                            Text(
                                text = uiState.lastBackupAt?.let { formatLastBackupAt(it) } ?: "Never",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.lastBackupAt != null) MaterialTheme.colorScheme.primary else ExpenseTrackerTheme.extendedColors.textMuted,
                                modifier = Modifier.testTag("last_backup_info")
                            )
                        }

                        if (uiState.lastBackupFileName != null) {
                            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
                            Text(
                                text = "File: ${uiState.lastBackupFileName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ExpenseTrackerTheme.extendedColors.textMuted,
                                modifier = Modifier.testTag("last_backup_file_name_info")
                            )
                        }
                    }

                    // Backup Card
                    ExpenseTrackerCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpenseTrackerRadius.card,
                        containerColor = ExpenseTrackerTheme.extendedColors.surface,
                        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
                        testTag = "create_backup_card"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                            ) {
                                IconAvatar(
                                    icon = Icons.Default.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    size = 40.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Create Full Backup",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "Includes accounts, categories, transactions, budgets, recurring rules, and preferences.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            // Privacy & Integrity Callout
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                                    .padding(
                                        horizontal = ExpenseTrackerSpacing.md,
                                        vertical = ExpenseTrackerSpacing.sm
                                    )
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "• Backup files contain financial data. Store them somewhere you trust.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )
                                    Text(
                                        text = "• Includes SHA-256 integrity verification to detect file corruption.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ExpenseTrackerTheme.extendedColors.textMuted
                                    )
                                }
                            }

                            PrimaryButton(
                                text = if (uiState.isLoading && uiState.message?.contains("backup", ignoreCase = true) == true) "Preparing Backup..." else "Create Backup",
                                onClick = { viewModel.createBackup() },
                                enabled = !uiState.isLoading,
                                leadingIcon = Icons.Default.Backup,
                                testTag = "create_backup_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Restore Backup Card
                    ExpenseTrackerCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpenseTrackerRadius.card,
                        containerColor = ExpenseTrackerTheme.extendedColors.surface,
                        borderColor = ExpenseTrackerTheme.extendedColors.financialWarning.copy(alpha = 0.4f),
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
                        testTag = "restore_backup_card"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                            ) {
                                IconAvatar(
                                    icon = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = ExpenseTrackerTheme.extendedColors.financialWarning,
                                    size = 40.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Restore From Backup",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "Replace the current local financial database with data from a saved backup file.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ExpenseTrackerTheme.extendedColors.financialWarning.copy(alpha = 0.08f))
                                    .padding(
                                        horizontal = ExpenseTrackerSpacing.md,
                                        vertical = ExpenseTrackerSpacing.sm
                                    )
                            ) {
                                Text(
                                    text = "⚠️ High-risk action: Current local database will be replaced. Any data created since the backup will be lost.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ExpenseTrackerTheme.extendedColors.financialWarning,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            SecondaryButton(
                                text = "Choose Backup File",
                                onClick = { restoreBackupLauncher.launch(arrayOf("application/json")) },
                                enabled = !uiState.isLoading,
                                leadingIcon = Icons.Default.FileOpen,
                                testTag = "restore_backup_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Export CSV Card
                    ExpenseTrackerCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpenseTrackerRadius.card,
                        containerColor = ExpenseTrackerTheme.extendedColors.surface,
                        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
                        testTag = "export_csv_card"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                            ) {
                                IconAvatar(
                                    icon = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    size = 40.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Export Transactions (CSV)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "Create a spreadsheet-compatible CSV file containing all transaction records for spreadsheets or external accounting.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            SecondaryButton(
                                text = if (uiState.isLoading && uiState.message?.contains("CSV", ignoreCase = true) == true) "Preparing CSV..." else "Export CSV",
                                onClick = { viewModel.exportCsv() },
                                enabled = !uiState.isLoading,
                                leadingIcon = Icons.Default.FileDownload,
                                testTag = "export_csv_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Loading Indicator
                if (uiState.isLoading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("loading_indicator")
                        )
                        uiState.message?.let {
                            Text(
                                text = it,
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

    // Pre-restore confirmation and inspection dialog
    uiState.pendingRestoreBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
            shape = ExpenseTrackerRadius.dialog,
            containerColor = ExpenseTrackerTheme.extendedColors.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Restore this backup?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                    Text(
                        text = "Your current local financial database will be replaced by the contents of this backup file.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Any unsaved changes or new records created since this backup will be overwritten.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                    // Inspection Details Card
                    ExpenseTrackerCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpenseTrackerRadius.card,
                        containerColor = ExpenseTrackerTheme.extendedColors.surfaceElevated,
                        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.md),
                        testTag = "restore_inspection_card"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xxs)) {
                            Text(
                                text = "BACKUP SNAPSHOT DETAILS:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "• Created: ${formatLastBackupAt(backup.createdAt)}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "• App Version: ${backup.appVersion}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "• Format Version: v${backup.formatVersion}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "• Accounts: ${backup.accounts.size}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "• Categories: ${backup.categories.size}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "• Transactions: ${backup.transactions.size}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "• Budgets: ${backup.budgets.size}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "• Recurring Rules: ${backup.recurringTransactions.size}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                    SecondaryButton(
                        text = "Back Up Current Data First",
                        onClick = { viewModel.createBackup() },
                        enabled = !uiState.isLoading,
                        leadingIcon = Icons.Default.Backup,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                DangerButton(
                    text = "Restore Database",
                    onClick = { viewModel.confirmAndRestore() },
                    leadingIcon = Icons.Default.CloudDownload,
                    testTag = "confirm_restore_button"
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "Cancel",
                    onClick = { viewModel.cancelRestore() },
                    modifier = Modifier.testTag("cancel_restore_button")
                )
            },
            modifier = Modifier.testTag("restore_confirmation_dialog")
        )
    }
}

private fun formatLastBackupAt(instantStr: String?): String {
    if (instantStr == null) return "Never"
    return try {
        val instant = Instant.parse(instantStr)
        val zonedDateTime = instant.atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a")
        zonedDateTime.format(formatter)
    } catch (e: Exception) {
        instantStr
    }
}

