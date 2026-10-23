package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ExpenseDao
import com.example.data.local.LoanDao
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Loan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var loanDao: LoanDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        expenseDao = db.expenseDao()
        loanDao = db.loanDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testExpenseInsertAndRetrieve() = runBlocking {
        val now = System.currentTimeMillis()
        val expense = Expense(
            title = "చాక్లెట్",
            amount = 50.0,
            category = ExpenseCategory.FOOD.displayNameEn,
            timestamp = now,
            paymentMode = "UPI",
            detectedLanguage = "te"
        )
        val id = expenseDao.insertExpense(expense)
        assertTrue(id > 0)

        val latest = expenseDao.getLatestExpense()
        assertNotNull(latest)
        assertEquals("చాక్లెట్", latest!!.title)
        assertEquals(50.0, latest.amount, 0.01)
    }

    @Test
    fun testLoanInsertRepayAndSettle() = runBlocking {
        val now = System.currentTimeMillis()
        val loan = Loan(
            personName = "రమేష్",
            amount = 1000.0,
            amountPaid = 0.0,
            dateGivenMillis = now,
            dueDateMillis = now + 86400000L * 7,
            isSettled = false
        )
        val id = loanDao.insertLoan(loan)
        assertTrue(id > 0)

        val pendingLoan = loanDao.getLatestPendingLoan()
        assertNotNull(pendingLoan)
        assertEquals("రమేష్", pendingLoan!!.personName)
        assertEquals(1000.0, pendingLoan.amount, 0.01)

        // Make partial repayment of 400
        val updated = pendingLoan.copy(amountPaid = 400.0)
        loanDao.updateLoan(updated)

        val afterPartial = loanDao.getLatestPendingLoan()
        assertNotNull(afterPartial)
        assertEquals(400.0, afterPartial!!.amountPaid, 0.01)
        assertEquals(600.0, afterPartial.remainingAmount, 0.01)

        // Settle full remaining amount
        val settled = afterPartial.copy(amountPaid = 1000.0, isSettled = true)
        loanDao.updateLoan(settled)

        val remainingPending = loanDao.getLatestPendingLoan()
        assertEquals(null, remainingPending)
    }
}
