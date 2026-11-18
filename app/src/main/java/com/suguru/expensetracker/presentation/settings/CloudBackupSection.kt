package com.suguru.expensetracker.presentation.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.suguru.expensetracker.domain.model.CloudBackupManifest
import com.suguru.expensetracker.domain.model.CloudBackupManifestStatus
import com.suguru.expensetracker.presentation.auth.CloudAuthUser
import com.suguru.expensetracker.presentation.auth.GoogleSignInButton
import com.suguru.expensetracker.presentation.auth.authStateFlow
import com.suguru.expensetracker.presentation.auth.signOutFromGoogle
import com.suguru.expensetracker.presentation.auth.toCloudAuthUser
import com.suguru.expensetracker.presentation.components.DangerButton
import com.suguru.expensetracker.presentation.components.ErrorBanner
import com.suguru.expensetracker.presentation.components.ExpenseTrackerCard
import com.suguru.expensetracker.presentation.components.IconAvatar
import com.suguru.expensetracker.presentation.components.PrimaryButton
import com.suguru.expensetracker.presentation.components.SecondaryButton
import com.suguru.expensetracker.presentation.components.SectionHeader
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CloudBackupSection(
    createCloudBackupViewModel: (uid: String) -> CloudBackupViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isFirebaseInitialized = remember(context) {
        com.suguru.expensetracker.presentation.auth.isFirebaseInitialized(context)
    }

    if (!isFirebaseInitialized) {
        CloudBackupUnavailableCard(modifier = modifier)
        return
    }

    val firebaseAuth = remember { Firebase.auth }
    val currentUser by firebaseAuth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = firebaseAuth.currentUser)

    var authError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
    ) {
        SectionHeader(
            title = "Cloud Backup (Google & Firestore)",
            testTag = "section_cloud_backup"
        )

        val user = currentUser
        if (user == null) {
            CloudBackupSignInCard(
                authError = authError,
                onAuthSuccess = { authError = null },
                onAuthError = { msg -> authError = msg }
            )
        } else {
            val authUser = user.toCloudAuthUser()
            key(authUser.uid) {
                val vm: CloudBackupViewModel = viewModel(
                    key = authUser.uid,
                    factory = viewModelFactory {
                        initializer {
                            createCloudBackupViewModel(authUser.uid)
                        }
                    }
                )
                AuthenticatedCloudBackupCard(
                    user = authUser,
                    viewModel = vm,
                    onShowMessage = onShowMessage
                )
            }
        }
    }
}

@Composable
private fun CloudBackupSignInCard(
    authError: String?,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    ExpenseTrackerCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.surface,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
        testTag = "cloud_backup_signin_card"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
            ) {
                IconAvatar(
                    icon = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 40.dp
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Connect Google Account",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Status: Not connected (Google login is optional).",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                    Text(
                        text = "Sign in with Google to save compressed backups privately to your Firebase Firestore account and restore across reinstalls.",
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
                    .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                    .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.sm)
            ) {
                Text(
                    text = "⚠️ Warning: Without cloud backup, local data will be lost if you uninstall the app or replace your device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseTrackerTheme.extendedColors.financialWarning,
                    fontWeight = FontWeight.Medium
                )
            }

            if (authError != null) {
                ErrorBanner(message = authError)
            }

            GoogleSignInButton(
                onAuthSuccess = onAuthSuccess,
                onAuthError = onAuthError,
                modifier = Modifier.fillMaxWidth(),
                testTag = "cloud_backup_google_signin_button"
            )
        }
    }
}

@Composable
private fun AuthenticatedCloudBackupCard(
    user: CloudAuthUser,
    viewModel: CloudBackupViewModel,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val manifestsState by viewModel.manifestsState.collectAsStateWithLifecycle()
    val localCloudState by viewModel.localCloudState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    LaunchedEffect(actionState.successMessage) {
        actionState.successMessage?.let { msg ->
            onShowMessage(msg)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(actionState.errorMessage) {
        actionState.errorMessage?.let { err ->
            onShowMessage(err)
            viewModel.clearMessages()
        }
    }

    ExpenseTrackerCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.surface,
        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
        testTag = "cloud_backup_authenticated_card"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
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
                    IconAvatar(
                        icon = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = ExpenseTrackerTheme.extendedColors.financialPositive,
                        size = 40.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = user.displayName?.takeIf { it.isNotBlank() } ?: "Google Account Connected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = user.email ?: "UID: ${user.uid.take(10)}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(
                    onClick = {
                        signOutFromGoogle(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    },
                    enabled = !actionState.isBusy,
                    modifier = Modifier.testTag("cloud_backup_signout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign out",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Sign out")
                }
            }

            val lastCloudTime = localCloudState.lastSuccessfulBackupAtEpochMillis
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                    .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.sm)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (lastCloudTime != null) {
                            "Last cloud backup: ${formatInstant(Instant.ofEpochMilli(lastCloudTime))}"
                        } else {
                            "No cloud backup recorded on this device yet."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                    localCloudState.lastFailureCode?.let { failureCode ->
                        Text(
                            text = "Last failure code: $failureCode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (actionState.isBusy && actionState.busyMessage != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = actionState.busyMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
            }

            PrimaryButton(
                text = if (actionState.isBusy) "Working..." else "Back Up to Cloud",
                onClick = { viewModel.backUpNow() },
                enabled = !actionState.isBusy,
                leadingIcon = Icons.Default.CloudUpload,
                testTag = "cloud_backup_now_button",
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = ExpenseTrackerTheme.extendedColors.borderSubtle)

            Text(
                text = "Cloud Snapshots",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            when (val state = manifestsState) {
                is CloudSnapshotsUiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = ExpenseTrackerSpacing.sm),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                is CloudSnapshotsUiState.Error -> {
                    ErrorBanner(message = state.message)
                }
                is CloudSnapshotsUiState.Success -> {
                    if (state.manifests.isEmpty()) {
                        Text(
                            text = "No cloud snapshots stored yet. Tap Back Up to Cloud to create your first snapshot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseTrackerTheme.extendedColors.textMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                            state.manifests.take(5).forEach { manifest ->
                                CloudManifestRow(
                                    manifest = manifest,
                                    isBusy = actionState.isBusy,
                                    onRestore = { viewModel.requestRestoreSnapshot(manifest) },
                                    onDelete = { viewModel.deleteSnapshot(manifest.snapshotId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (syncStatus is com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus.Conflict) {
        val conflict = syncStatus as com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus.Conflict
        AlertDialog(
            onDismissRequest = {},
            shape = ExpenseTrackerRadius.dialog,
            containerColor = ExpenseTrackerTheme.extendedColors.surface,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Data Conflict Detected",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                    Text(
                        text = "This device has local edits, but your Google account already has a different cloud backup. Please choose how to proceed:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Cloud snapshot from ${formatInstant(conflict.remoteManifest.completedAt ?: conflict.remoteManifest.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Restore Cloud Snapshot",
                    onClick = { viewModel.resolveConflictRestore(conflict.remoteManifest) },
                    testTag = "conflict_restore_cloud_button"
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "Keep Local & Back Up",
                    onClick = { viewModel.resolveConflictKeepLocal() },
                    testTag = "conflict_keep_local_button"
                )
            }
        )
    }

    if (actionState.isBusy && actionState.busyMessage != null) {
        AlertDialog(
            onDismissRequest = {},
            shape = ExpenseTrackerRadius.dialog,
            containerColor = ExpenseTrackerTheme.extendedColors.surface,
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            },
            title = {
                Text(
                    text = "Restoring Cloud Backup",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = actionState.busyMessage ?: "Restoring data to this device...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
            },
            confirmButton = {}
        )
    }

    actionState.pendingRestoreCandidate?.let { manifest ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingRestore() },
            shape = ExpenseTrackerRadius.dialog,
            containerColor = ExpenseTrackerTheme.extendedColors.surface,
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Restore cloud snapshot?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                    Text(
                        text = "Cloud backup data will be restored to this device. Any existing local data will be replaced by the snapshot.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Snapshot from ${formatInstant(manifest.completedAt ?: manifest.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Restore",
                    onClick = { viewModel.confirmPendingRestore() },
                    leadingIcon = Icons.Default.CloudDownload,
                    testTag = "confirm_cloud_restore_button"
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "Cancel",
                    onClick = { viewModel.cancelPendingRestore() },
                    testTag = "cancel_cloud_restore_button"
                )
            }
        )
    }

    actionState.restoreResult?.let { result ->
        when (result) {
            is CloudRestoreResult.Success -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissRestoreResult() },
                    shape = ExpenseTrackerRadius.dialog,
                    containerColor = ExpenseTrackerTheme.extendedColors.surface,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = ExpenseTrackerTheme.extendedColors.financialPositive,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Data successfully restored to this device.",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "All accounts, transactions, and categories have been updated from your cloud backup snapshot (${formatInstant(result.manifest.completedAt ?: result.manifest.createdAt)}).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        PrimaryButton(
                            text = "Next",
                            onClick = { viewModel.dismissRestoreResult() },
                            testTag = "restore_success_next_button"
                        )
                    }
                )
            }
            is CloudRestoreResult.Failure -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissRestoreResult() },
                    shape = ExpenseTrackerRadius.dialog,
                    containerColor = ExpenseTrackerTheme.extendedColors.surface,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Restoration failed",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                            Text(
                                text = "Unable to restore data from cloud backup to this device:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = result.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    confirmButton = {
                        PrimaryButton(
                            text = "Next",
                            onClick = { viewModel.dismissRestoreResult() },
                            testTag = "restore_failure_next_button"
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun CloudManifestRow(
    manifest: CloudBackupManifest,
    isBusy: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val sizeText = remember(manifest.compressedBytes) {
        Formatter.formatShortFileSize(context, manifest.compressedBytes)
    }
    val statusColor = when (manifest.status) {
        CloudBackupManifestStatus.COMPLETE -> ExpenseTrackerTheme.extendedColors.financialPositive
        CloudBackupManifestStatus.UPLOADING -> MaterialTheme.colorScheme.primary
        CloudBackupManifestStatus.FAILED -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ExpenseTrackerTheme.extendedColors.surfaceElevated)
            .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
        ) {
            Icon(
                imageVector = when (manifest.status) {
                    CloudBackupManifestStatus.COMPLETE -> Icons.Default.CloudDone
                    CloudBackupManifestStatus.UPLOADING -> Icons.Default.Sync
                    CloudBackupManifestStatus.FAILED -> Icons.Default.ErrorOutline
                },
                contentDescription = manifest.status.name,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = formatInstant(manifest.completedAt ?: manifest.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${manifest.status.name} • $sizeText • ${manifest.chunkCount} chunk(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (manifest.status == CloudBackupManifestStatus.COMPLETE) {
                TextButton(
                    onClick = onRestore,
                    enabled = !isBusy,
                    modifier = Modifier.testTag("restore_cloud_snapshot_${manifest.snapshotId}")
                ) {
                    Text("Restore")
                }
            }
            IconButton(
                onClick = onDelete,
                enabled = !isBusy,
                modifier = Modifier.testTag("delete_cloud_snapshot_${manifest.snapshotId}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete cloud snapshot",
                    tint = ExpenseTrackerTheme.extendedColors.textMuted
                )
            }
        }
    }
}

@Composable
private fun CloudBackupUnavailableCard(modifier: Modifier = Modifier) {
    ExpenseTrackerCard(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.surface,
        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
    ) {
        Text(
            text = "Cloud backup is unavailable in this test environment.",
            style = MaterialTheme.typography.bodySmall,
            color = ExpenseTrackerTheme.extendedColors.textSecondary
        )
    }
}

private fun formatInstant(instant: Instant): String {
    return try {
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a")
        instant.atZone(ZoneId.systemDefault()).format(formatter)
    } catch (_: Exception) {
        instant.toString()
    }
}
