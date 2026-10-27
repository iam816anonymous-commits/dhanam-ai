package com.example.ai

import com.example.data.local.CategoryTotal
import com.example.data.model.CategoryBudget
import com.example.data.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentFinancialBrainTest {
    @Test
    fun missingCategoryBudgetIsNotInventedOrMarkedOverspent() {
        val health = AgentFinancialBrain.calculateLocalFinancialHealth(
            totalSpent = 100.0,
            monthlyBudget = 35_000.0,
            categoryTotals = listOf(CategoryTotal(category = "Food", total = 100.0)),
            categoryBudgets = emptyList(),
            loans = emptyList()
        )

        val food = health.categoryBurnSummaries.single { it.category == ExpenseCategory.FOOD.displayNameEn }
        assertEquals(100.0, food.spent, 0.001)
        assertEquals(0.0, food.budget, 0.001)
        assertFalse(food.hasBudget)
        assertFalse(food.isOverspent)
        assertFalse(food.isNearLimit)
    }

    @Test
    fun shortImportedCategoryLabelMatchesConfiguredCanonicalBudget() {
        val health = AgentFinancialBrain.calculateLocalFinancialHealth(
            totalSpent = 100.0,
            monthlyBudget = 35_000.0,
            categoryTotals = listOf(CategoryTotal(category = "Food", total = 100.0)),
            categoryBudgets = listOf(CategoryBudget(category = "Food", monthlyLimit = 50.0)),
            loans = emptyList()
        )

        val food = health.categoryBurnSummaries.single { it.category == ExpenseCategory.FOOD.displayNameEn }
        assertTrue(food.hasBudget)
        assertEquals(50.0, food.budget, 0.001)
        assertTrue(food.isOverspent)
        assertEquals(200, food.percentUsed)
    }
}
