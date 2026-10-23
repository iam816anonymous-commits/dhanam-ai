package com.example

import com.example.ai.AdaptiveBudgetEngine
import com.example.ai.AgentFinancialBrain
import com.example.data.local.CategoryTotal
import com.example.data.model.CategoryBudget
import com.example.data.model.ExpenseCategory
import com.example.data.model.Loan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testFinancialHealthCalculations() {
        val categoryTotals = listOf(
            CategoryTotal(ExpenseCategory.FOOD.name, 4500.0),
            CategoryTotal(ExpenseCategory.TRANSPORT.name, 3000.0)
        )
        val categoryBudgets = listOf(
            CategoryBudget(ExpenseCategory.FOOD.name, 5000.0),
            CategoryBudget(ExpenseCategory.TRANSPORT.name, 6000.0)
        )
        val loans = listOf(
            Loan(personName = "Ramesh", amount = 5000.0, amountPaid = 2000.0, dueDateMillis = System.currentTimeMillis() + 86400000L)
        )

        val health = AgentFinancialBrain.calculateLocalFinancialHealth(
            totalSpent = 7500.0,
            monthlyBudget = 30000.0,
            categoryTotals = categoryTotals,
            categoryBudgets = categoryBudgets,
            loans = loans
        )

        assertEquals(7500.0, health.totalSpentThisMonth, 0.01)
        assertEquals(22500.0, health.remainingBudget, 0.01)
        assertEquals(3000.0, health.totalOutstandingLent, 0.01)
        assertEquals(40, health.khataRecoveryPercent)
        assertEquals(25, health.budgetAdherencePercent)
        assertFalse(health.isAtRiskOfOverspending)
    }

    @Test
    fun testCategoryOverspendingDetection() {
        val categoryTotals = listOf(
            CategoryTotal(ExpenseCategory.FOOD.name, 6200.0)
        )
        val categoryBudgets = listOf(
            CategoryBudget(ExpenseCategory.FOOD.name, 5000.0)
        )

        val health = AgentFinancialBrain.calculateLocalFinancialHealth(
            totalSpent = 6200.0,
            monthlyBudget = 10000.0,
            categoryTotals = categoryTotals,
            categoryBudgets = categoryBudgets,
            loans = emptyList()
        )

        val foodHealth = health.categoryBurnSummaries.find { 
            it.category == ExpenseCategory.FOOD.displayNameEn || it.category == ExpenseCategory.FOOD.name 
        }
        assertTrue(foodHealth != null && foodHealth.isOverspent)
        assertEquals(124, foodHealth?.percentUsed)
    }

    @Test
    fun testAdaptiveBudgetInitialAllocation() {
        // Initial budget 35,000 without history should allocate baseline weights summing to exactly 35,000
        val plan = AdaptiveBudgetEngine.computeAdaptiveBudgets(
            overallBudget = 35000.0,
            historicalTotals = emptyList(),
            totalExpenseCount = 0
        )

        assertFalse(plan.isLearnedFromHabits)
        val totalAllocated = plan.categoryBudgets.sumOf { it.monthlyLimit }
        assertEquals(35000.0, totalAllocated, 0.01)

        // Food should have highest allocation in baseline (~28%)
        val foodBudget = plan.categoryBudgets.find { it.category == ExpenseCategory.FOOD.displayNameEn }
        assertTrue(foodBudget != null && foodBudget.monthlyLimit >= 9000.0)
    }

    @Test
    fun testAdaptiveBudgetLearningFromUserHabits() {
        // Given historical spending where user spent heavily on Transport & Petrol (₹10,000 out of ₹15,000)
        val totals = listOf(
            CategoryTotal(ExpenseCategory.TRANSPORT.displayNameEn, 10000.0),
            CategoryTotal(ExpenseCategory.FOOD.displayNameEn, 3000.0),
            CategoryTotal(ExpenseCategory.DINING.displayNameEn, 2000.0)
        )

        val plan = AdaptiveBudgetEngine.computeAdaptiveBudgets(
            overallBudget = 40000.0,
            historicalTotals = totals,
            totalExpenseCount = 8
        )

        assertTrue(plan.isLearnedFromHabits)
        val transportBudget = plan.categoryBudgets.find { it.category == ExpenseCategory.TRANSPORT.displayNameEn }
        val diningBudget = plan.categoryBudgets.find { it.category == ExpenseCategory.DINING.displayNameEn }

        // Transport should adaptively get a higher limit due to learned user habit
        assertTrue(transportBudget != null && transportBudget.monthlyLimit > (diningBudget?.monthlyLimit ?: 0.0))
        val totalAllocated = plan.categoryBudgets.sumOf { it.monthlyLimit }
        assertEquals(40000.0, totalAllocated, 0.01)
    }
}
