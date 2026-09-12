package com.suguru.expensetracker.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.suguru.expensetracker.ExpenseTrackerApplication
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker for periodically processing due recurring transactions.
 */
class RecurringTransactionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val appContainer = (applicationContext as? ExpenseTrackerApplication)?.appContainer
                ?: return Result.failure()

            appContainer.processDueRecurringTransactionsUseCase(asOfDate = LocalDate.now())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "expense_tracker_recurring_transactions"

        fun enqueuePeriodicWork(context: Context) {
            try {
                val workRequest = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(
                    1, TimeUnit.DAYS
                ).build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                // Safely ignore WorkManager initialization failures in non-Android environments like Robolectric tests
            }
        }
    }
}
