package com.example.presentation.home

/**
 * Represents the UI state for the minimal placeholder Home screen.
 */
data class HomeUiState(
    val appTitle: String = "ExpenseTracker",
    val subtitle: String = "Project Foundation",
    val balanceFormatted: String = "$0.00",
    val isOfflineFirstActive: Boolean = true,
    val architectureStatusTitle: String = "Architecture Ready",
    val architectureStatusDescription: String = "Clean Architecture, MVVM, and Material 3 are now established. Ready for business logic."
)
