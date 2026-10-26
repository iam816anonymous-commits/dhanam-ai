package com.example.data.local

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CategoryBudget
import com.example.data.model.Expense
import com.example.data.model.Loan
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClearDataTransactionTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testClearAllDataDeletesExpensesLoansAndCategoryBudgetsTransactionally() = runTest {
        val expenseDao = db.expenseDao()
        val loanDao = db.loanDao()
        val budgetDao = db.budgetDao()

        expenseDao.insertExpense(
            Expense(title = "Lunch", amount = 150.0, category = "Dining", timestamp = System.currentTimeMillis())
        )
        loanDao.insertLoan(
            Loan(personName = "Ramesh", amount = 1000.0)
        )
        budgetDao.insertOrUpdateBudget(
            CategoryBudget(category = "Dining", monthlyLimit = 4000.0)
        )

        assertEquals(1, expenseDao.getAllExpenses().size)
        assertEquals(1, loanDao.getAllLoans().size)
        assertEquals(1, budgetDao.getAllCategoryBudgets().size)

        db.withTransaction {
            expenseDao.deleteAllExpenses()
            loanDao.deleteAllLoans()
            budgetDao.deleteAllCategoryBudgets()
        }

        assertTrue(expenseDao.getAllExpenses().isEmpty())
        assertTrue(loanDao.getAllLoans().isEmpty())
        assertTrue(budgetDao.getAllCategoryBudgets().isEmpty())
    }
}
