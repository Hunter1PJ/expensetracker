package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.presentation.navigation.ExpenseTrackerApp
import com.example.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val appContainer = (application as ExpenseTrackerApplication).appContainer
    setContent {
      ExpenseTrackerTheme {
        ExpenseTrackerApp(appContainer = appContainer)
      }
    }
  }
}
