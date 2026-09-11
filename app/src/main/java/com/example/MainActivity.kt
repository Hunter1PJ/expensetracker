package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import com.example.domain.model.ThemeMode
import com.example.presentation.navigation.ExpenseTrackerApp
import com.example.ui.theme.ExpenseTrackerTheme

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
