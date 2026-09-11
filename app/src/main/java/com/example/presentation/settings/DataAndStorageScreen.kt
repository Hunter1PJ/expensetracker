package com.example.presentation.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.presentation.components.ExpenseTrackerCard
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
                title = { Text("Data & Storage", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("data_and_storage_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
            ) {
                SectionHeader(
                    title = "Database & File Storage",
                    testTag = "section_data_storage_info"
                )

                ExpenseTrackerCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpenseTrackerRadius.card,
                    containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                    borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                    contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
                    testTag = "local_data_card"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xxs)
                        ) {
                            Text(
                                text = "Offline-First Storage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Your data remains 100% private and stored locally on your device in an encrypted/safe application sandbox. Backups contain sensitive financial history and should be stored securely.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ExpenseTrackerTheme.extendedColors.textSecondary
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
            ) {
                SectionHeader(
                    title = "Data Operations",
                    testTag = "section_backup_export_info"
                )

                // Last Backup Metadata
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ExpenseTrackerSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last Backup:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                    Text(
                        text = uiState.lastBackupAt?.let { formatLastBackupAt(it) } ?: "Never",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("last_backup_info")
                    )
                }

                if (uiState.lastBackupFileName != null) {
                    Text(
                        text = "File: ${uiState.lastBackupFileName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary,
                        modifier = Modifier
                            .padding(horizontal = ExpenseTrackerSpacing.sm)
                            .testTag("last_backup_file_name_info")
                    )
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                // Create Backup Button/Card
                ExpenseTrackerCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpenseTrackerRadius.card,
                    containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                    borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                    contentPadding = PaddingValues(ExpenseTrackerSpacing.md),
                    testTag = "create_backup_card"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.sm)
                            )
                            Column {
                                Text(
                                    text = "Create Full Backup",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Save a secure local snapshot of all budgets, accounts, categories, and transactions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.createBackup() },
                            enabled = !uiState.isLoading,
                            shape = ExpenseTrackerRadius.button,
                            modifier = Modifier
                                .padding(start = ExpenseTrackerSpacing.sm)
                                .testTag("create_backup_button")
                        ) {
                            Text("Backup")
                        }
                    }
                }

                // Restore Backup Button/Card
                ExpenseTrackerCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpenseTrackerRadius.card,
                    containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                    borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                    contentPadding = PaddingValues(ExpenseTrackerSpacing.md),
                    testTag = "restore_backup_card"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.sm)
                            )
                            Column {
                                Text(
                                    text = "Restore From Backup",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Replace all current application database records with data from an existing backup file.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                            }
                        }
                        Button(
                            onClick = { restoreBackupLauncher.launch(arrayOf("application/json")) },
                            enabled = !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = ExpenseTrackerRadius.button,
                            modifier = Modifier
                                .padding(start = ExpenseTrackerSpacing.sm)
                                .testTag("restore_backup_button")
                        ) {
                            Text("Restore")
                        }
                    }
                }

                // Export CSV Button/Card
                ExpenseTrackerCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpenseTrackerRadius.card,
                    containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                    borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                    contentPadding = PaddingValues(ExpenseTrackerSpacing.md),
                    testTag = "export_csv_card"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.sm)
                            )
                            Column {
                                Text(
                                    text = "Export as CSV",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Export complete transaction logs into a human-readable spreadsheet-compatible CSV file.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.exportCsv() },
                            enabled = !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            shape = ExpenseTrackerRadius.button,
                            modifier = Modifier
                                .padding(start = ExpenseTrackerSpacing.sm)
                                .testTag("export_csv_button")
                        ) {
                            Text("Export")
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                ) {
                    CircularProgressIndicator(modifier = Modifier.testTag("loading_indicator"))
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

    // Pre-restore confirmation and inspection dialog
    uiState.pendingRestoreBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
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
                    text = "Restore backup?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                    Text(
                        text = "Your current local financial data will be replaced by the selected backup.",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "This cannot be undone unless you create a backup of the current data first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                    // Inspection Details
                    ExpenseTrackerCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpenseTrackerRadius.card,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.sm),
                        testTag = "restore_inspection_card"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xxs)) {
                            Text(
                                text = "BACKUP SNAPSHOT DETAILS:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
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
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAndRestore() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = ExpenseTrackerRadius.button,
                    modifier = Modifier.testTag("confirm_restore_button")
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelRestore() },
                    modifier = Modifier.testTag("cancel_restore_button")
                ) {
                    Text("Cancel")
                }
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
