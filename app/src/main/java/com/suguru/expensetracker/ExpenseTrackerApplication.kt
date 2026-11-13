package com.suguru.expensetracker

import android.app.Application
import android.util.Log
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
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
            when (val result = appContainer.ensureInternalDefaultAccountProvisioning()) {
                is InternalDefaultAccountProvisioningResult.Ready -> {
                    Log.i("ExpenseTrackerApplication", "Internal default account ready: ${result.internalAccount.id}")
                }
                is InternalDefaultAccountProvisioningResult.RequiresCurrencySelection -> {
                    Log.w("ExpenseTrackerApplication", "Internal default account requires a one-time preferred currency selection.")
                }
                is InternalDefaultAccountProvisioningResult.Failure -> {
                    Log.e("ExpenseTrackerApplication", "Internal default account provisioning failed", result.error)
                }
            }

            try {
                appContainer.processDueRecurringTransactionsUseCase()
            } catch (e: Exception) {
                Log.w("ExpenseTrackerApplication", "Recurring transaction processing deferred until provisioning is ready.", e)
            }
        }
    }
}
