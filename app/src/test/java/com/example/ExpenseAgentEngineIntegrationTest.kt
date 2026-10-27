package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ExpenseAgentEngine
import com.example.ai.ResultType
import com.example.data.local.AppDatabase
import com.example.data.model.Expense
import com.example.data.preferences.PreferenceManager
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
class ExpenseAgentEngineIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var engine: ExpenseAgentEngine

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val pref = PreferenceManager(context)
        engine = ExpenseAgentEngine(
            expenseDao = db.expenseDao(),
            loanDao = db.loanDao(),
            budgetDao = db.budgetDao(),
            preferenceManager = pref
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testRecordExpenseWithCleanTitle() = runBlocking {
        // "50 రూపాయల చాక్లెట్" should be recorded with title "చాక్లెట్" and amount 50
        val result = engine.processUserInput("50 రూపాయల చాక్లెట్", forceLanguage = "te")
        assertTrue(result.success)
        assertEquals(ResultType.EXPENSE_RECORDED, result.type)
        assertNotNull(result.recordedExpense)
        assertEquals("చాక్లెట్", result.recordedExpense!!.title)
        assertEquals(50.0, result.recordedExpense!!.amount, 0.01)

        // Verify stored in DB
        val fromDb = db.expenseDao().getLatestExpense()
        assertNotNull(fromDb)
        assertEquals("చాక్లెట్", fromDb!!.title)
        assertEquals(50.0, fromDb.amount, 0.01)
    }

    @Test
    fun testRecordLoanAndSetDueDateAndRepayFlow() = runBlocking {
        // Step 1: Record loan given
        val loanResult = engine.processUserInput("రమేష్ కి 500 ఇచ్చాను", forceLanguage = "te")
        assertTrue(loanResult.success)
        assertEquals(ResultType.LOAN_RECORDED, loanResult.type)
        assertNotNull(loanResult.recordedLoan)
        assertEquals("రమేష్", loanResult.recordedLoan!!.personName)
        assertEquals(500.0, loanResult.recordedLoan!!.amount, 0.01)
        assertTrue(loanResult.suggestions.isNotEmpty())

        // Verify in DB
        val pending = db.loanDao().getLatestPendingLoan()
        assertNotNull(pending)
        assertEquals("రమేష్", pending!!.personName)
        val initialDue = pending.dueDateMillis

        // Step 2: Set Due Date ("రేపు" / Tomorrow)
        val dueDateResult = engine.processUserInput("రేపు", forceLanguage = "te")
        assertTrue(dueDateResult.success)
        val updated = db.loanDao().getLatestPendingLoan()
        assertNotNull(updated)
        // Due date updated to approx 1 day ahead
        assertTrue(updated!!.dueDateMillis != null)

        // Step 3: Repayment ("రమేష్ 500 తిరిగి ఇచ్చాడు")
        val repayResult = engine.processUserInput("రమేష్ 500 తిరిగి ఇచ్చాడు", forceLanguage = "te")
        assertTrue(repayResult.success)
        assertEquals(ResultType.LOAN_RECORDED, repayResult.type)

        // Verify loan is settled in DB
        val remainingPending = db.loanDao().getLatestPendingLoan()
        assertEquals(null, remainingPending)

        val allLoans = db.loanDao().getAllLoans()
        assertTrue(allLoans.first().isSettled)
        assertEquals(500.0, allLoans.first().amountPaid, 0.01)
    }

    @Test
    fun testUndoLastExpense() = runBlocking {
        engine.processUserInput("పెట్రోల్ 300", forceLanguage = "te")
        val exp1 = db.expenseDao().getLatestExpense()
        assertNotNull(exp1)

        val undoResult = engine.processUserInput("చివరి ఖర్చు రద్దు చేయి", forceLanguage = "te")
        assertTrue(undoResult.success)
        assertEquals(ResultType.CONFIRMATION_REQUIRED, undoResult.type)
        assertTrue(undoResult.confirmationRequired)
        assertNotNull(undoResult.pendingDeletionExpense)

        // The engine only requests confirmation; it must not delete records by itself.
        val expAfterUndo = db.expenseDao().getLatestExpense()
        assertNotNull(expAfterUndo)
        assertEquals(exp1!!.id, expAfterUndo!!.id)
    }

    @Test
    fun testMultiItemBatchExpenseRecording() = runBlocking {
        // Multi-item simultaneous entry: "milk 50 and petrol 200"
        val batchResult = engine.processUserInput("milk 50 and petrol 200", forceLanguage = "en")
        assertTrue(batchResult.success)
        assertEquals(ResultType.MULTI_EXPENSE_RECORDED, batchResult.type)
        assertEquals(2, batchResult.recordedExpenses.size)
        assertEquals(250.0, batchResult.totalRecordedAmount, 0.01)

        // Verify stored in DB
        val allExpenses = db.expenseDao().getExpensesInRange(0L, Long.MAX_VALUE)
        assertEquals(2, allExpenses.size)
    }

    @Test
    fun repaymentWithoutAmountDoesNotSilentlySettleTheWholeLoan() = runBlocking {
        engine.processUserInput("రమేష్ కి 1000 ఇచ్చాను", forceLanguage = "te")

        val result = engine.processUserInput("రమేష్ తిరిగి ఇచ్చాడు", forceLanguage = "te")

        assertTrue(!result.success)
        val loan = db.loanDao().getLatestPendingLoan()
        assertNotNull(loan)
        assertEquals(0.0, loan!!.amountPaid, 0.001)
        assertEquals(1000.0, loan.remainingAmount, 0.001)
    }

    @Test
    fun explicitFullSettlementPhraseCanCloseLoanWithoutNumericAmount() = runBlocking {
        engine.processUserInput("రమేష్ కి 1000 ఇచ్చాను", forceLanguage = "te")

        val result = engine.processUserInput("రమేష్ అప్పు తీరింది", forceLanguage = "te")

        assertTrue(result.success)
        val loan = db.loanDao().getAllLoans().single()
        assertTrue(loan.isSettled)
        assertEquals(1000.0, loan.amountPaid, 0.001)
    }

    @Test
    fun repaymentGreaterThanOutstandingBalanceIsRejected() = runBlocking {
        engine.processUserInput("రమేష్ కి 1000 ఇచ్చాను", forceLanguage = "te")

        val result = engine.processUserInput("రమేష్ 1500 తిరిగి ఇచ్చాడు", forceLanguage = "te")

        assertTrue(!result.success)
        val loan = db.loanDao().getLatestPendingLoan()
        assertNotNull(loan)
        assertEquals(0.0, loan!!.amountPaid, 0.001)
        assertEquals(1000.0, loan.remainingAmount, 0.001)
    }

    @Test
    fun testPartialLoanRepaymentFlow() = runBlocking {
        // Step 1: Lend 1000 to Suresh
        val lendResult = engine.processUserInput("సురేష్ కి 1000 అప్పు ఇచ్చాను", forceLanguage = "te")
        assertTrue(lendResult.success)
        assertEquals(ResultType.LOAN_RECORDED, lendResult.type)

        // Step 2: Partial repayment of 400
        val partialRepay = engine.processUserInput("సురేష్ 400 తిరిగి ఇచ్చాడు", forceLanguage = "te")
        assertTrue(partialRepay.success)
        val pendingLoan = db.loanDao().getLatestPendingLoan()
        assertNotNull(pendingLoan)
        assertEquals(false, pendingLoan!!.isSettled)
        assertEquals(400.0, pendingLoan.amountPaid, 0.01)
        assertEquals(600.0, pendingLoan.remainingAmount, 0.01)

        // Step 3: Remaining settlement of 600
        val finalRepay = engine.processUserInput("సురేష్ 600 బాకీ చెల్లించాడు", forceLanguage = "te")
        assertTrue(finalRepay.success)
        val afterSettlement = db.loanDao().getLatestPendingLoan()
        assertEquals(null, afterSettlement)
    }

    @Test
    fun testLoanWithoutAmountPrompting() = runBlocking {
        // User says "అప్పు ఇచ్చాను" without specifying amount or person
        val result = engine.processUserInput("అప్పు ఇచ్చాను", forceLanguage = "te")
        assertTrue(result.success)
        assertEquals(ResultType.INSIGHTS, result.type)
        assertTrue(result.message.contains("వివరాలు చెప్పండి") || result.message.contains("ఎవరికి"))
        assertTrue(result.suggestions.isNotEmpty())
    }

    @Test
    fun testModifyLastExpense() = runBlocking {
        // Add expense of 200
        engine.processUserInput("టీ 20", forceLanguage = "te")
        val exp1 = db.expenseDao().getLatestExpense()
        assertNotNull(exp1)
        assertEquals(20.0, exp1!!.amount, 0.01)

        // Modify last to 35
        val modResult = engine.processUserInput("చివరి ఖర్చును 35 కి మార్చు", forceLanguage = "te")
        assertTrue(modResult.success)
        assertEquals(ResultType.EXPENSE_RECORDED, modResult.type)

        val updated = db.expenseDao().getLatestExpense()
        assertNotNull(updated)
        assertEquals(35.0, updated!!.amount, 0.01)
    }

    @Test
    fun correctionDoesNotModifyAnUnrelatedCategoryWhenNoMatchExists() = runBlocking {
        db.expenseDao().insertExpense(
            Expense(
                title = "Groceries",
                amount = 50.0,
                category = "Food",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = engine.processUserInput("change previous Transport expense to 100", forceLanguage = "en")

        assertTrue(!result.success)
        assertTrue(result.message.contains("Nothing was changed"))
        val latest = db.expenseDao().getLatestExpense()
        assertNotNull(latest)
        assertEquals("Groceries", latest!!.title)
        assertEquals(50.0, latest.amount, 0.001)
    }

    @Test
    fun testSetMonthlyBudgetCommand() = runBlocking {
        val result = engine.processUserInput("నెల బడ్జెట్ 45000 గా పెట్టు", forceLanguage = "te")
        assertTrue(result.success)
        assertEquals(ResultType.INSIGHTS, result.type)
        assertTrue(result.message.contains("45000"))
    }

    @Test
    fun testSpendingInquiriesFlow() = runBlocking {
        // Log an expense
        engine.processUserInput("బిర్యానీ 250", forceLanguage = "te")

        // Query Today
        val todayResult = engine.processUserInput("ఈ రోజు ఖర్చు ఎంత?", forceLanguage = "te")
        assertTrue(todayResult.success)
        assertTrue(todayResult.message.contains("250"))

        // Query Month
        val monthResult = engine.processUserInput("ఈ నెల మొత్తం ఖర్చు ఎంత?", forceLanguage = "te")
        assertTrue(monthResult.success)
        assertTrue(monthResult.message.contains("250"))

        // Query Remaining Budget
        val budgetResult = engine.processUserInput("నా బడ్జెట్ ఎంత మిగిలింది?", forceLanguage = "te")
        assertTrue(budgetResult.success)
        assertTrue(budgetResult.message.contains("సురక్షిత రోజువారీ వ్యయం") || budgetResult.message.contains("మిగిలిన"))
    }

    @Test
    fun categorySpendingQueryIncludesShortLabelsImportedFromCsv() = runBlocking {
        db.expenseDao().insertExpense(
            Expense(
                title = "Groceries",
                amount = 42.0,
                category = "Food",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = engine.processUserInput("How much did I spend on food?", forceLanguage = "en")

        assertTrue(result.success)
        assertTrue(result.message.contains("42"))
    }

    @Test
    fun categorySpendingQueryDoesNotInventAnUnconfiguredBudget() = runBlocking {
        val result = engine.processUserInput("How much did I spend on food?", forceLanguage = "en")

        assertTrue(result.success)
        assertTrue(result.message.contains("No category budget is set"))
        assertTrue(!result.message.contains("5000"))
    }

    @Test
    fun testPendingLoansKhataInquiry() = runBlocking {
        // Lend money to Rajesh
        engine.processUserInput("రాజేష్ కి 800 ఇచ్చాను", forceLanguage = "te")

        // Query who owes money
        val queryResult = engine.processUserInput("ఎవరు బాకీ ఉన్నారు?", forceLanguage = "te")
        assertTrue(queryResult.success)
        assertTrue(queryResult.message.contains("రాజేష్"))
        assertTrue(queryResult.message.contains("800"))
    }

    @Test
    fun testCategorySpendingInquiry() = runBlocking {
        // Log fuel expense
        engine.processUserInput("పెట్రోల్ 400", forceLanguage = "te")

        // Query petrol spending
        val result = engine.processUserInput("పెట్రోల్ కి ఎంత ఖర్చు చేశాను?", forceLanguage = "te")
        assertTrue(result.success)
        assertTrue(result.message.contains("400"))
    }

    @Test
    fun testDailyBriefingAndAdvice() = runBlocking {
        // Briefing
        val briefing = engine.processUserInput("హలో జార్విస్", forceLanguage = "te")
        assertTrue(briefing.success)
        assertTrue(briefing.message.contains("జార్విస్") || briefing.message.contains("నమస్కారం"))

        // Savings advice
        val advice = engine.processUserInput("పొదుపు చిట్కాలు చెప్పు", forceLanguage = "te")
        assertTrue(advice.success)
        assertTrue(advice.message.contains("పొదుపు") || advice.message.contains("సలహా"))
    }

    @Test
    fun testDomainBoundaryRefusal() = runBlocking {
        val nonFinance = engine.processUserInput("హైదరాబాద్ వాతావరణం ఎలా ఉంది?", forceLanguage = "te")
        assertEquals(false, nonFinance.success)
        assertEquals(ResultType.OUT_OF_SCOPE, nonFinance.type)
        assertTrue(nonFinance.message.contains("Dhanam AI"))
    }
}
