package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ExpenseTrackerDatabase
import com.example.data.repository.RoomAccountRepository
import com.example.data.repository.RoomCategoryRepository
import com.example.data.repository.RoomTransactionRepository
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.model.Money
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.transaction.CreateTransactionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: ExpenseTrackerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ExpenseTracker", appName)
    }

    @Test
    fun `persists and queries transaction in real Room database via UseCase`() = runBlocking {
        val accountRepo = RoomAccountRepository(database.accountDao())
        val categoryRepo = RoomCategoryRepository(database.categoryDao())
        val transactionRepo = RoomTransactionRepository(database.transactionDao())

        val accountId = accountRepo.insertAccount(
            Account(name = "Main Bank", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val categoryId = categoryRepo.insertCategory(
            Category(name = "Coffee", type = CategoryType.EXPENSE, iconName = "local_cafe", colorHex = "#795548")
        )

        val useCase = CreateTransactionUseCase(transactionRepo, accountRepo, categoryRepo)
        val txId = useCase(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(475L, "USD"),
                accountId = accountId,
                categoryId = categoryId,
                transactionTime = Instant.now(),
                note = "Latte"
            )
        )

        val retrievedTx = transactionRepo.getTransactionById(txId)
        assertNotNull(retrievedTx)
        assertEquals(475L, retrievedTx?.amount?.amountInMinorUnits)
        assertEquals("USD", retrievedTx?.amount?.currencyCode)
        assertEquals("Latte", retrievedTx?.note)

        val accountTransactions = transactionRepo.observeTransactionsByAccount(accountId).first()
        assertEquals(1, accountTransactions.size)
        assertEquals(txId, accountTransactions.first().id)
    }
}
