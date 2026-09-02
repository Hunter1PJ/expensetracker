package com.example

import android.app.Application
import com.example.di.AppContainer

/**
 * Application class for ExpenseTracker, maintaining the application-scoped dependency graph.
 */
class ExpenseTrackerApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
