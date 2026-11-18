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
        val uid = inputData.getString(KEY_UID)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val appContainer = (applicationContext as? ExpenseTrackerApplication)?.appContainer
            ?: return Result.failure()

        val orchestrator = appContainer.cloudBackupOrchestrator
        val authUid = orchestrator.currentAuthenticatedUid()
        if (authUid == null || authUid != uid) {
            return Result.failure()
        }

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
        private const val WORK_PREFIX = "expense_tracker_auto_backup_"

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
