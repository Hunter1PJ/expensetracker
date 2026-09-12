package com.suguru.expensetracker

import android.app.Application
import com.suguru.expensetracker.data.worker.RecurringTransactionWorker
import com.suguru.expensetracker.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for ExpenseTracker, maintaining the application-scoped dependency graph.
 */
class ExpenseTrackerApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        
        RecurringTransactionWorker.enqueuePeriodicWork(this)

        applicationScope.launch {
            try {
                appContainer.processDueRecurringTransactionsUseCase()
            } catch (_: Exception) {
            }
        }
    }
}
