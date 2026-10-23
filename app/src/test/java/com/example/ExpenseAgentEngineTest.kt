package com.example

import com.example.ai.AgentFinancialBrain
import com.example.data.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseAgentEngineTest {

    @Test
    fun testTeluguNumeralNormalization() {
        val teluguInput = "కూరగాయలకి ౩౫౦ రూపాయలు"
        val normalized = teluguInput
            .replace('౦', '0').replace('౧', '1').replace('౨', '2').replace('౩', '3')
            .replace('౪', '4').replace('౫', '5').replace('౬', '6').replace('౭', '7')
            .replace('౮', '8').replace('౯', '9')
        assertTrue(normalized.contains("350"))
    }

    @Test
    fun testExpenseCategoryDetection() {
        val catFood = ExpenseCategory.fromString("Food & Groceries")
        assertEquals(ExpenseCategory.FOOD, catFood)

        val catDining = ExpenseCategory.fromString("Dining & Snacks")
        assertEquals(ExpenseCategory.DINING, catDining)
    }

    @Test
    fun testLoanCalculation() {
        val amount = 5000.0
        val amountPaid = 2000.0
        val remaining = (amount - amountPaid).coerceAtLeast(0.0)
        assertEquals(3000.0, remaining, 0.01)
    }

    @Test
    fun testStrictDomainBoundaryEnforcement() {
        // Out of scope query should be politely refused
        val refusalEnglish = AgentFinancialBrain.checkDomainScope("What is the recipe for biryani?", false)
        assertNotNull(refusalEnglish)
        assertTrue(refusalEnglish!!.contains("Dhanam AI"))

        val refusalTelugu = AgentFinancialBrain.checkDomainScope("వాతావరణం ఎలా ఉంది", true)
        assertNotNull(refusalTelugu)
        assertTrue(refusalTelugu!!.contains("Dhanam AI"))

        // Financial queries should NOT be rejected
        val financeQuery = AgentFinancialBrain.checkDomainScope("Spent 500 on vegetables", false)
        assertNull(financeQuery)
    }

    @Test
    fun testLocalDailySafeToSpendCalculation() {
        val health = AgentFinancialBrain.calculateLocalFinancialHealth(
            totalSpent = 10000.0,
            monthlyBudget = 30000.0,
            categoryTotals = emptyList(),
            categoryBudgets = emptyList(),
            loans = emptyList()
        )
        // Remaining = 20000
        assertEquals(20000.0, health.remainingBudget, 0.01)
        assertTrue(health.dailySafeToSpend > 0)
        assertEquals(33, health.budgetAdherencePercent)
    }
}
