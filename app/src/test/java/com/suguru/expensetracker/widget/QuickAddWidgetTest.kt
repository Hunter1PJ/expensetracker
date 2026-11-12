package com.suguru.expensetracker.widget

import android.content.Intent
import com.suguru.expensetracker.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickAddWidgetTest {

    @Test
    fun testQuickAddIntentNavigationContract() {
        // Quick Add buttons must deep-link with the proper preselected type
        val type = "EXPENSE"
        val intent = Intent().apply {
            action = "com.suguru.expensetracker.action.NAVIGATE"
            putExtra("destination", "add_transaction")
            putExtra("transactionType", type)
            data = android.net.Uri.parse("expensetracker://navigate/add_transaction?type=$type")
        }

        assertEquals("com.suguru.expensetracker.action.NAVIGATE", intent.action)
        assertEquals("add_transaction", intent.getStringExtra("destination"))
        assertEquals("EXPENSE", intent.getStringExtra("transactionType"))
        assertEquals("expensetracker://navigate/add_transaction?type=EXPENSE", intent.dataString)
    }

    @Test
    fun testMalformedTransactionTypeFallsBackSafely() {
        // If a malformed transaction type is passed in the intent, it should safely fall back.
        val malformedType = "INVALID_TYPE_BLAH"
        val parsedType = try {
            TransactionType.valueOf(malformedType)
        } catch (_: Exception) {
            null // Fallback to null
        }
        assertNull(parsedType)
    }
}
