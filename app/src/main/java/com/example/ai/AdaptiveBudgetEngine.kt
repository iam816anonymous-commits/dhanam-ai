package com.example.ai

import com.example.data.local.CategoryTotal
import com.example.data.model.CategoryBudget
import com.example.data.model.ExpenseCategory
import kotlin.math.floor

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
        ExpenseCategory.FOOD to 0.28,          // 28% - Groceries, vegetables, milk
        ExpenseCategory.TRANSPORT to 0.15,     // 15% - Petrol, fuel, commute
        ExpenseCategory.BILLS to 0.18,         // 18% - Electricity, mobile recharge, wifi
        ExpenseCategory.DINING to 0.10,        // 10% - Dining out, snacks, tea
        ExpenseCategory.HEALTH to 0.09,        // 9% - Medicines, healthcare
        ExpenseCategory.SHOPPING to 0.08,      // 8% - Clothes, personal shopping
        ExpenseCategory.EDUCATION to 0.06,     // 6% - Fees, books, learning
        ExpenseCategory.ENTERTAINMENT to 0.04, // 4% - Movies and recreation
        ExpenseCategory.OTHER to 0.02          // 2% - Miscellaneous emergencies
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
        val safeOverallBudget = overallBudget.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val totalSpent = historicalTotals.sumOf { it.total.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0 }

        // Normalize imported short labels and in-app canonical labels to the same category.
        val spendMap = activeCategories.associateWith { category ->
            historicalTotals
                .filter { ExpenseCategory.matchesLabel(it.category, category) }
                .sumOf { it.total.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0 }
        }

        val hasSignificantHistory = totalSpent > 200.0 && totalExpenseCount >= 3
        val learnedWeights = mutableMapOf<ExpenseCategory, Double>()
        var totalWeight = 0.0

        for (cat in activeCategories) {
            val baseWeight = baselineWeights[cat] ?: 0.08
            if (hasSignificantHistory) {
                val catSpent = spendMap[cat] ?: 0.0
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

        // Allocate in ₹50 increments where possible, without creating money or negative budgets.
        // A ₹300 floor is used only when the total budget can afford it for every category.
        val minimumPerCategory = if (safeOverallBudget >= 300.0 * activeCategories.size) 300.0 else 0.0
        val distributableBudget = (safeOverallBudget - minimumPerCategory * activeCategories.size).coerceAtLeast(0.0)
        var remainingBudget = safeOverallBudget
        val budgets = mutableListOf<CategoryBudget>()

        activeCategories.forEachIndexed { index, cat ->
            val normWeight = (learnedWeights[cat] ?: 0.10) / totalWeight
            val limit = if (index == activeCategories.lastIndex) {
                remainingBudget
            } else {
                val weightedShare = distributableBudget * normWeight
                val roundedShare = floor(weightedShare / 50.0) * 50.0
                val allocated = (minimumPerCategory + roundedShare).coerceAtMost(remainingBudget).coerceAtLeast(0.0)
                remainingBudget -= allocated
                allocated
            }
            budgets.add(CategoryBudget(cat.displayNameEn, limit.coerceAtLeast(0.0)))
        }

        // Identify primary spending category and report its canonical label when known.
        val topCategory = historicalTotals.maxByOrNull { it.total }?.category?.let { raw ->
            activeCategories.firstOrNull { ExpenseCategory.matchesLabel(raw, it) }?.displayNameEn ?: raw
        }

        val insightTe = if (hasSignificantHistory) {
            "మీ గత ఖర్చుల అలవాట్లను బట్టి బడ్జెట్ సర్దుబాటు చేయబడింది. ఎక్కువగా ఖర్చు అవుతున్న విభాగాలకు తగిన నిధులు కేటాయించాను."
        } else {
            "మొత్తం ₹${safeOverallBudget.toInt()} బడ్జెట్ ప్రారంభ నిష్పత్తి ప్రకారం విభజించబడింది. మీరు ఖర్చులు నమోదు చేసే కొద్దీ మీ అలవాట్లకు అనుగుణంగా మారుతుంది."
        }

        val insightEn = if (hasSignificantHistory) {
            "Budget adaptively allocated based on your personal spending habits. Heavily used categories received higher limits."
        } else {
            "Budget of ₹${safeOverallBudget.toInt()} distributed using smart financial baseline. Limits will dynamically adapt as you log expenses."
        }

        return AdaptiveBudgetPlan(
            totalBudget = safeOverallBudget,
            categoryBudgets = budgets,
            isLearnedFromHabits = hasSignificantHistory,
            totalTransactionsAnalyzed = totalExpenseCount,
            primaryExpenseCategory = topCategory,
            proactiveInsightTe = insightTe,
            proactiveInsightEn = insightEn
        )
    }
}
