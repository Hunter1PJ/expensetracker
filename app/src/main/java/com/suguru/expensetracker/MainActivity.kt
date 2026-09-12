package com.suguru.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.presentation.navigation.ExpenseTrackerApp
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val appContainer = (application as ExpenseTrackerApplication).appContainer
    setContent {
      val settingsState = appContainer.settingsRepository.settings.collectAsState(initial = null).value
      val darkTheme = when (settingsState?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
      }
      ExpenseTrackerTheme(darkTheme = darkTheme) {
        ExpenseTrackerApp(appContainer = appContainer)
      }
    }
  }
}
