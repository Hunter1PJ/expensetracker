package com.suguru.expensetracker.presentation.export

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class PdfReportLocalizationTest {
    private val baseContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `report labels follow the supplied app locale for English Uzbek and Russian`() {
        val cases = listOf(
            Triple("en", "ExpenseTracker Financial Report", "Expense"),
            Triple("uz", "ExpenseTracker moliyaviy hisoboti", "Xarajat"),
            Triple("ru", "Финансовый отчёт ExpenseTracker", "Расход")
        )

        cases.forEach { (languageTag, expectedTitle, expectedExpense) ->
            val configuration = Configuration(baseContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(languageTag))
            }
            val localizedContext = baseContext.createConfigurationContext(configuration)
            val labels = localizedPdfReportLabels(localizedContext)

            assertEquals(languageTag, labels.locale.language)
            assertEquals(expectedTitle, labels.text("title"))
            assertEquals(expectedExpense, labels.text("expense"))
            assertTrue(labels.text("ledgerTitle", 3).contains("3"))
        }
    }
}
