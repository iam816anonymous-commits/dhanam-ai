package com.example.ai

import com.example.data.local.CategoryTotal
import com.example.data.model.CategoryBudget
import com.example.data.model.ExpenseCategory
import kotlin.math.roundToInt

/**
 * AdaptiveBudgetEngine:
 * Proactive learning engine for Dhanam AI that:
 * 1. Automatically distributes overall monthly budget across categories at the beginning using financial baseline weights.
 * 2. Continuously learns user spending patterns from historical expenses.
 * 3. Proactively adapts and re-balances category limits to reflect the user's real living habits.
 */
object AdaptiveBudgetEngine {

    // Initial baseline allocation weights when no spending history exists (sums to 1.0)
    private val baselineWeights = mapOf(
        ExpenseCategory.FOOD to 0.28,          // 28% (~₹9,800 of ₹35k) - Groceries, vegetables, milk
        ExpenseCategory.TRANSPORT to 0.15,     // 15% (~₹5,250 of ₹35k) - Petrol, fuel, commute
        ExpenseCategory.BILLS to 0.18,         // 18% (~₹6,300 of ₹35k) - Electricity, mobile recharge, wifi
        ExpenseCategory.DINING to 0.10,        // 10% (~₹3,500 of ₹35k) - Dining out, snacks, tea
        ExpenseCategory.HEALTH to 0.09,        // 9%  (~₹3,150 of ₹35k) - Medicines, healthcare
        ExpenseCategory.SHOPPING to 0.08,      // 8%  (~₹2,800 of ₹35k) - Clothes, personal shopping
        ExpenseCategory.EDUCATION to 0.06,     // 6%  (~₹2,100 of ₹35k) - Fees, books, learning
        ExpenseCategory.OTHER to 0.06          // 6%  (~₹2,100 of ₹35k) - Miscellaneous emergencies
    )

    data class AdaptiveBudgetPlan(
        val totalBudget: Double,
        val categoryBudgets: List<CategoryBudget>,
        val isLearnedFromHabits: Boolean,
        val totalTransactionsAnalyzed: Int,
        val primaryExpenseCategory: String?,
        val proactiveInsightTe: String,
        val proactiveInsightEn: String
    )

    /**
     * Calculates category budgets by learning from historical spending.
     * Blends baseline weights with real user behavior as spending history builds up.
     */
    fun computeAdaptiveBudgets(
        overallBudget: Double,
        historicalTotals: List<CategoryTotal>,
        totalExpenseCount: Int = 0
    ): AdaptiveBudgetPlan {
        val activeCategories = ExpenseCategory.entries.filter { it != ExpenseCategory.LENDING }
        val totalSpent = historicalTotals.sumOf { it.total }

        // Category spend lookup
        val spendMap = mutableMapOf<String, Double>()
        for (ct in historicalTotals) {
            spendMap[ct.category.lowercase()] = ct.total
        }

        val hasSignificantHistory = totalSpent > 200.0 && totalExpenseCount >= 3
        val learnedWeights = mutableMapOf<ExpenseCategory, Double>()
        var totalWeight = 0.0

        for (cat in activeCategories) {
            val baseWeight = baselineWeights[cat] ?: 0.08
            if (hasSignificantHistory) {
                val catSpent = spendMap[cat.displayNameEn.lowercase()]
                    ?: spendMap[cat.name.lowercase()]
                    ?: 0.0
                val actualRatio = (catSpent / totalSpent).coerceIn(0.02, 0.55)

                // Adaptive blending: As history grows, user actual habits take stronger priority (up to 75%)
                val historyFactor = (totalExpenseCount / 15.0).coerceIn(0.3, 0.75)
                val blended = (baseWeight * (1.0 - historyFactor)) + (actualRatio * historyFactor)
                learnedWeights[cat] = blended
                totalWeight += blended
            } else {
                learnedWeights[cat] = baseWeight
                totalWeight += baseWeight
            }
        }

        // Allocate budget rounded to clean 50-rupee units
        var remainingBudget = overallBudget
        val budgets = mutableListOf<CategoryBudget>()

        activeCategories.forEachIndexed { index, cat ->
            val normWeight = (learnedWeights[cat] ?: 0.10) / totalWeight
            if (index == activeCategories.lastIndex) {
                // Ensure exact penny-perfect total match
                val finalLimit = remainingBudget.coerceAtLeast(300.0)
                budgets.add(CategoryBudget(cat.displayNameEn, finalLimit))
            } else {
                val rawLimit = overallBudget * normWeight
                val roundedLimit = ((rawLimit / 50.0).roundToInt() * 50).toDouble().coerceAtLeast(300.0)
                remainingBudget -= roundedLimit
                budgets.add(CategoryBudget(cat.displayNameEn, roundedLimit))
            }
        }

        // Identify primary spending category
        val topCategory = historicalTotals.maxByOrNull { it.total }?.category

        val insightTe = if (hasSignificantHistory) {
            "మీ గత ఖర్చుల అలవాట్లను బట్టి బడ్జెట్ సర్దుబాటు చేయబడింది. ఎక్కువగా ఖర్చు అవుతున్న విభాగాలకు తగిన నిధులు కేటాయించాను."
        } else {
            "మొత్తం ₹${overallBudget.toInt()} బడ్జెట్ ప్రారంభ నిష్పత్తి ప్రకారం విభజించబడింది. మీరు ఖర్చులు నమోదు చేసే కొద్దీ మీ అలవాట్లకు అనుగుణంగా మారుతుంది."
        }

        val insightEn = if (hasSignificantHistory) {
            "Budget adaptively allocated based on your personal spending habits. Heavily used categories received higher limits."
        } else {
            "Budget of ₹${overallBudget.toInt()} distributed using smart financial baseline. Limits will dynamically adapt as you log expenses."
        }

        return AdaptiveBudgetPlan(
            totalBudget = overallBudget,
            categoryBudgets = budgets,
            isLearnedFromHabits = hasSignificantHistory,
            totalTransactionsAnalyzed = totalExpenseCount,
            primaryExpenseCategory = topCategory,
            proactiveInsightTe = insightTe,
            proactiveInsightEn = insightEn
        )
    }
}
