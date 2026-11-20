package com.suguru.expensetracker.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.suguru.expensetracker.ExpenseTrackerApplication
import com.suguru.expensetracker.data.backup.BackupExecutionResult
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class AutomaticCloudBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? ExpenseTrackerApplication ?: return Result.failure()
        val uid = inputData.getString(KEY_UID)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val authUid = if (com.suguru.expensetracker.presentation.auth.isFirebaseInitialized(applicationContext)) {
            runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        } else null
        if (authUid != uid) return Result.success()
        if (!application.isWorkspaceActive(uid)) return Result.retry()

        // Resolve the database from the work's UID, never from whichever workspace the UI displays.
        val appContainer = application.containerFor(uid)
        if (appContainer.workspaceId.firebaseUid != uid) return Result.failure()
        val orchestrator = appContainer.cloudBackupOrchestrator

        if (inputData.getBoolean(KEY_AUTOMATIC_RESTORE, false)) {
            val currentUid = orchestrator.currentAuthenticatedUid()
            if (currentUid != uid) return Result.success()
            val enabled = appContainer.autoBackupPreferenceStore.isAutoBackupEnabled(uid)
            if (!shouldRunAutomaticRestore(currentUid, enabled)) return Result.success()
            orchestrator.evaluateOnLaunchOrSignIn(uid)
            return if (orchestrator.shouldRetryAutomaticRestore()) Result.retry() else Result.success()
        }

        val authUidAtExecution = orchestrator.currentAuthenticatedUid()
        if (authUidAtExecution == null || authUidAtExecution != uid) return Result.success()

        val autoPrefStore = appContainer.autoBackupPreferenceStore
        if (!autoPrefStore.isAutoBackupEnabled(uid)) return Result.success()

        val result = orchestrator.performBackup(uid)
        return when (result) {
            is BackupExecutionResult.Success,
            is BackupExecutionResult.SkippedUnchanged,
            is BackupExecutionResult.DeferredConflict -> Result.success()
            is BackupExecutionResult.PermanentFailure -> Result.failure()
            is BackupExecutionResult.RetryableFailure -> Result.retry()
        }
    }

    companion object {
        const val KEY_UID = "key_uid"
        private const val KEY_AUTOMATIC_RESTORE = "key_automatic_restore"
        private const val WORK_PREFIX = "expense_tracker_auto_backup_"
        private const val AUTOMATIC_RESTORE_WORK_PREFIX = "expense_tracker_auto_restore_"

        fun uniqueWorkName(uid: String): String {
            return "$WORK_PREFIX${sha256Hex(uid)}"
        }

        fun scheduleDebounced(context: Context, uid: String) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<AutomaticCloudBackupWorker>()
                    .setInputData(workDataOf(KEY_UID to uid))
                    .setConstraints(constraints)
                    .setInitialDelay(15, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                    .addTag("expense_tracker_auto_backup")
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    uniqueWorkName(uid),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            } catch (e: Exception) {
                // Safely ignore WorkManager failures in non-Android test environments like Robolectric
            }
        }

        internal fun shouldRunAutomaticRestore(currentUid: String?, autoBackupEnabled: Boolean): Boolean =
            !currentUid.isNullOrBlank() && autoBackupEnabled

        internal fun automaticRestoreWorkName(uid: String): String =
            AUTOMATIC_RESTORE_WORK_PREFIX + sha256Hex(uid)

        internal fun createAutomaticRestoreWorkRequest(uid: String) =
            OneTimeWorkRequestBuilder<AutomaticCloudBackupWorker>()
                .setInputData(workDataOf(KEY_AUTOMATIC_RESTORE to true, KEY_UID to uid))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("expense_tracker_auto_restore")
                .build()

        fun scheduleAutomaticRestore(context: Context, uid: String) {
            if (uid.isBlank()) return
            try {
                val request = createAutomaticRestoreWorkRequest(uid)
                WorkManager.getInstance(context).enqueueUniqueWork(
                    automaticRestoreWorkName(uid),
                    ExistingWorkPolicy.KEEP,
                    request
                )
            } catch (_: Exception) {
                // Startup evaluation remains available if WorkManager is unavailable.
            }
        }

        fun scheduleImmediate(context: Context, uid: String) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<AutomaticCloudBackupWorker>()
                    .setInputData(workDataOf(KEY_UID to uid))
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                    .addTag("expense_tracker_auto_backup")
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    uniqueWorkName(uid),
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                // Safely ignore WorkManager failures in non-Android test environments
            }
        }

        fun cancelWork(context: Context, uid: String) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(uid))
                WorkManager.getInstance(context).cancelUniqueWork(automaticRestoreWorkName(uid))
            } catch (e: Exception) {
                // Safely ignore
            }
        }

        private fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
            val hexChars = CharArray(digest.size * 2)
            val digits = "0123456789abcdef"
            for (index in digest.indices) {
                val value = digest[index].toInt() and 0xFF
                hexChars[index * 2] = digits[value ushr 4]
                hexChars[index * 2 + 1] = digits[value and 0x0F]
            }
            return String(hexChars)
        }
    }
}
