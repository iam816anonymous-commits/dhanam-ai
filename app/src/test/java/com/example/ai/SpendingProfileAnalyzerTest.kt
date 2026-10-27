package com.example.ai

import com.example.data.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SpendingProfileAnalyzerTest {
    @Test
    fun aggregatesTotalsByMonthCategoryAndPaymentMethod() {
        val start = Calendar.getInstance().apply {
            set(2025, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(2025, Calendar.DECEMBER, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val jan = Calendar.getInstance().apply {
            set(2025, Calendar.JANUARY, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val feb = Calendar.getInstance().apply {
            set(2025, Calendar.FEBRUARY, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val profile = SpendingProfileAnalyzer.analyze(
            listOf(
                Expense(id = 1, title = "Groceries", amount = 100.0, category = "Food", timestamp = jan, paymentMode = "UPI"),
                Expense(id = 2, title = "Groceries", amount = 150.0, category = "Food", timestamp = feb, paymentMode = "UPI"),
                Expense(id = 3, title = "Fuel", amount = 300.0, category = "Transport", timestamp = feb, paymentMode = "Cash")
            ),
            start,
            end,
            calendarMonthCount = 12
        )

        assertEquals(3, profile.transactionCount)
        assertEquals(550.0, profile.totalSpent, 0.001)
        assertEquals(45.833333, profile.averageMonthlySpend, 0.001)
        assertEquals("Food", profile.categorySpending.first().category)
        assertEquals(250.0, profile.categorySpending.first().total, 0.001)
        assertEquals(250.0, profile.paymentModeTotals["UPI"]!!, 0.001)
        assertEquals(300.0, profile.paymentModeTotals["Cash"]!!, 0.001)
        assertEquals(12, profile.monthlySpending.size)
    }

    @Test
    fun excludesRecordsOutsideRangeAndInvalidAmounts() {
        val start = 100L
        val end = 200L
        val profile = SpendingProfileAnalyzer.analyze(
            listOf(
                Expense(id = 1, title = "inside", amount = 10.0, category = "Other", timestamp = 150L),
                Expense(id = 2, title = "outside", amount = 20.0, category = "Other", timestamp = 201L),
                Expense(id = 3, title = "invalid", amount = Double.NaN, category = "Other", timestamp = 160L)
            ),
            start,
            end
        )

        assertEquals(1, profile.transactionCount)
        assertEquals(10.0, profile.totalSpent, 0.001)
    }

    @Test
    fun detectsRepeatedExpenseTitlesAndLargestTransactions() {
        val expenses = listOf(
            Expense(id = 1, title = "Milk", amount = 30.0, category = "Food", timestamp = 10L),
            Expense(id = 2, title = " milk ", amount = 35.0, category = "Food", timestamp = 20L),
            Expense(id = 3, title = "MILK", amount = 40.0, category = "Food", timestamp = 30L),
            Expense(id = 4, title = "Laptop", amount = 500.0, category = "Shopping", timestamp = 40L)
        )
        val profile = SpendingProfileAnalyzer.analyze(expenses, 0L, 100L)

        assertEquals("Milk", profile.frequentExpenses.first().title)
        assertEquals(3, profile.frequentExpenses.first().occurrences)
        assertEquals(105.0, profile.frequentExpenses.first().total, 0.001)
        assertEquals("Laptop", profile.largestExpenses.first().title)
        assertTrue(profile.toMessage(false).contains("12-MONTH SPENDING PROFILE"))
    }
}
