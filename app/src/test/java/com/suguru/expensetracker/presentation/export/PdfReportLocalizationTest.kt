package com.suguru.expensetracker.presentation.export

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.suguru.expensetracker.R
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

    @Test
    fun `built in categories are localized for every PDF category label while custom names stay verbatim`() {
        val builtInNames = listOf(
            "Groceries" to R.string.category_groceries,
            "Food" to R.string.category_food,
            "Transport" to R.string.category_transport,
            "Study" to R.string.category_study,
            "Shopping" to R.string.category_shopping,
            "Health" to R.string.category_health,
            "Entertainment" to R.string.category_entertainment,
            "Bills" to R.string.category_bills,
            "Housing" to R.string.category_housing,
            "Personal Care" to R.string.category_personal_care,
            "Subscriptions" to R.string.category_subscriptions,
            "Travel" to R.string.category_travel,
            "Gifts & Donations" to R.string.category_gifts_donations,
            "Salary" to R.string.category_salary,
            "Freelance" to R.string.category_freelance,
            "Business" to R.string.category_business,
            "Gift" to R.string.category_gift,
            "Other" to R.string.category_other
        )
        val localized = listOf("uz", "ru").associateWith { languageTag ->
            val configuration = Configuration(baseContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(languageTag))
            }
            localizedPdfReportLabels(baseContext.createConfigurationContext(configuration))
        }
        val englishConfiguration = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val english = localizedPdfReportLabels(baseContext.createConfigurationContext(englishConfiguration))

        builtInNames.forEach { (name, resourceId) ->
            assertEquals(name, english.categoryName(name, isSystem = true))
            localized.forEach { (languageTag, labels) ->
                val configuration = Configuration(baseContext.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(languageTag))
                }
                val context = baseContext.createConfigurationContext(configuration)
                assertEquals(context.getString(resourceId), labels.categoryName(name, isSystem = true))
            }
        }

        localized.values.forEach { labels ->
            assertEquals("Groceries", labels.categoryName("Groceries", isSystem = false))
            assertEquals("My custom category", labels.categoryName("My custom category", isSystem = false))
        }
    }
}
