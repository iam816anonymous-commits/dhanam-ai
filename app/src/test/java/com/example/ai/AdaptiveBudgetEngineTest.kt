package com.example.ai

import com.example.data.local.CategoryTotal
import com.example.data.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveBudgetEngineTest {
    @Test
    fun smallBudgetIsNeverInflatedByPerCategoryMinimums() {
        val plan = AdaptiveBudgetEngine.computeAdaptiveBudgets(
            overallBudget = 1_000.0,
            historicalTotals = emptyList(),
            totalExpenseCount = 0
        )

        assertEquals(1_000.0, plan.categoryBudgets.sumOf { it.monthlyLimit }, 0.001)
        assertTrue(plan.categoryBudgets.all { it.monthlyLimit >= 0.0 && it.monthlyLimit.isFinite() })
    }

    @Test
    fun normalBudgetIsAllocatedExactlyAndEveryCategoryGetsTheFloor() {
        val plan = AdaptiveBudgetEngine.computeAdaptiveBudgets(
            overallBudget = 35_000.0,
            historicalTotals = emptyList(),
            totalExpenseCount = 0
        )

        assertEquals(35_000.0, plan.categoryBudgets.sumOf { it.monthlyLimit }, 0.001)
        assertTrue(plan.categoryBudgets.all { it.monthlyLimit >= 300.0 })
    }

    @Test
    fun historicalShortCategoryLabelsAffectCanonicalCategoryWeights() {
        val plan = AdaptiveBudgetEngine.computeAdaptiveBudgets(
            overallBudget = 35_000.0,
            historicalTotals = listOf(
                CategoryTotal(category = "Food", total = 600.0),
                CategoryTotal(category = "Transport", total = 300.0)
            ),
            totalExpenseCount = 3
        )

        assertTrue(plan.isLearnedFromHabits)
        assertEquals(ExpenseCategory.FOOD.displayNameEn, plan.primaryExpenseCategory)
        val foodLimit = plan.categoryBudgets.single { it.category == ExpenseCategory.FOOD.displayNameEn }.monthlyLimit
        val transportLimit = plan.categoryBudgets.single { it.category == ExpenseCategory.TRANSPORT.displayNameEn }.monthlyLimit
        assertTrue(foodLimit > transportLimit)
        assertEquals(35_000.0, plan.categoryBudgets.sumOf { it.monthlyLimit }, 0.001)
    }
}
