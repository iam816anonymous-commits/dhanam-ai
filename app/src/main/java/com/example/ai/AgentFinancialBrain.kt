package com.example.ai

import com.example.data.local.CategoryTotal
import com.example.data.model.CategoryBudget
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Loan
import java.util.Calendar

/**
 * AgentFinancialBrain: Specialized local financial intelligence and strict domain guardrails.
 * 
 * Bounded exclusively to:
 * 1. Expense parsing & auto-categorization in Telugu & English
 * 2. Overspending detection & daily burn rate calculations
 * 3. Daily safe-to-spend allowances & monthly projection
 * 4. Lending Khata repayment tracking, overdue alerts & reminder generation
 * 5. Bounded scope enforcement (strictly rejects out-of-scope queries)
 */
object AgentFinancialBrain {

    // Financial formulas computed 100% locally on-device
    data class LocalFinancialHealth(
        val totalSpentThisMonth: Double,
        val monthlyBudget: Double,
        val remainingBudget: Double,
        val daysInMonth: Int,
        val dayOfMonth: Int,
        val daysRemainingInMonth: Int,
        val dailyBurnRate: Double,          // Average spent per day so far
        val dailySafeToSpend: Double,       // Allowed spend per day for rest of month
        val projectedMonthEndSpend: Double, // Extrapolated total at current pace
        val budgetAdherencePercent: Int,    // Total spent / Budget * 100
        val isAtRiskOfOverspending: Boolean,
        val categoryBurnSummaries: List<CategoryHealth>,
        val khataRecoveryPercent: Int,
        val totalOutstandingLent: Double
    )

    data class CategoryHealth(
        val category: String,
        val spent: Double,
        val budget: Double,
        val percentUsed: Int,
        val isOverspent: Boolean,
        val isNearLimit: Boolean,
        val hasBudget: Boolean = budget > 0.0
    )

    fun calculateLocalFinancialHealth(
        totalSpent: Double,
        monthlyBudget: Double,
        categoryTotals: List<CategoryTotal>,
        categoryBudgets: List<CategoryBudget>,
        loans: List<Loan>
    ): LocalFinancialHealth {
        val cal = Calendar.getInstance()
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemaining = (daysInMonth - dayOfMonth + 1).coerceAtLeast(1)

        val remainingBudget = (monthlyBudget - totalSpent).coerceAtLeast(0.0)
        val dailyBurnRate = if (dayOfMonth > 0) totalSpent / dayOfMonth else totalSpent
        val dailySafeToSpend = if (daysRemaining > 0) remainingBudget / daysRemaining else 0.0
        val projectedMonthEndSpend = dailyBurnRate * daysInMonth
        val adherencePct = if (monthlyBudget > 0) ((totalSpent / monthlyBudget) * 100).toInt() else 0
        val isAtRisk = monthlyBudget > 0.0 && (projectedMonthEndSpend > monthlyBudget || adherencePct >= 80)

        val categorySummaries = ExpenseCategory.entries
            .filter { it != ExpenseCategory.LENDING }
            .map { cat ->
                val spent = categoryTotals
                    .filter { ExpenseCategory.fromString(it.category) == cat }
                    .sumOf { it.total }
                val configuredBudget = categoryBudgets.firstOrNull {
                    ExpenseCategory.fromString(it.category) == cat
                }?.monthlyLimit?.takeIf { it.isFinite() && it > 0.0 }
                val budget = configuredBudget ?: 0.0
                val pct = configuredBudget?.let { ((spent / it) * 100).toInt() } ?: 0
                CategoryHealth(
                    category = cat.displayNameEn,
                    spent = spent,
                    budget = budget,
                    percentUsed = pct,
                    isOverspent = configuredBudget != null && spent > budget,
                    isNearLimit = configuredBudget != null && pct in 80..100,
                    hasBudget = configuredBudget != null
                )
            }

        val totalLent = loans.sumOf { it.amount }
        val totalCollected = loans.sumOf { it.amountPaid }
        val recoveryPct = if (totalLent > 0) ((totalCollected / totalLent) * 100).toInt() else 100
        val totalOutstanding = loans.filter { !it.isSettled }.sumOf { it.remainingAmount }

        return LocalFinancialHealth(
            totalSpentThisMonth = totalSpent,
            monthlyBudget = monthlyBudget,
            remainingBudget = remainingBudget,
            daysInMonth = daysInMonth,
            dayOfMonth = dayOfMonth,
            daysRemainingInMonth = daysRemaining,
            dailyBurnRate = dailyBurnRate,
            dailySafeToSpend = dailySafeToSpend,
            projectedMonthEndSpend = projectedMonthEndSpend,
            budgetAdherencePercent = adherencePct,
            isAtRiskOfOverspending = isAtRisk,
            categoryBurnSummaries = categorySummaries,
            khataRecoveryPercent = recoveryPct,
            totalOutstandingLent = totalOutstanding
        )
    }

    /**
     * Strict Domain Guardrail: Checks if user input is asking about non-finance topics
     * and returns a polite bounded response if out-of-scope.
     */
    fun checkDomainScope(input: String, isTelugu: Boolean): String? {
        val lower = input.lowercase()

        val outOfScopeKeywords = listOf(
            "who are you", "who made you", "write code", "recipe", "cook", "weather",
            "cricket", "movie review", "tell me a joke", "general knowledge", "who is the prime minister",
            "translate to french", "write an essay", "homework", "python", "javascript",
            "నువ్వు ఎవరు", "వంట", "వాతావరణం", "జోక్", "కవిత", "పాట"
        )

        val isOutOfScope = outOfScopeKeywords.any { lower.contains(it) }
        if (isOutOfScope) {
            return if (isTelugu) {
                "నేను మీ ఫోన్‌లో నివసించే చిన్న ఆర్థిక సహాయకుడిని (Dhanam AI). మీ ప్రతి పైసా ఖర్చును లెక్కించడం, మీ బడ్జెట్‌ను కాపాడడం మరియు అప్పుల ఖాటాను గుర్తుపెట్టుకోవడమే నా ఏకైక కర్తవ్యం. నేను ఈ ఒక్క పని మాత్రమే శ్రద్ధగా చేస్తాను. దయచేసి మీ ఖర్చు లేదా అప్పు వివరాలను చెప్పండి."
            } else {
                "I am Dhanam AI, your dedicated little on-device financial entity. My entire existence is devoted exclusively to watching your spending, guarding your budget, and tracking your khata. I do only this one thing. Please tell me your expense or loan details."
            }
        }
        return null
    }

    fun getStrictSystemPrompt(isTelugu: Boolean): String {
        return """
        You are Dhanam AI, a living, humble on-device financial companion entity for Telugu and English.
        You care deeply about the user's hard-earned money and learning their spending habits.
        YOU DEVOTE YOURSELF EXCLUSIVELY TO ONE SINGLE CRAFT:
        1. Daily expense logging and automatic categorization.
        2. Proactive budget allocation and overspending prevention.
        3. Money lent to others (Lending Khata) and repayment tracking.
        4. Learning user spending behavior adaptively over time.
        
        STRICT BOUNDARY:
        Never pretend to be a generic assistant or chatbot. If the user asks about unrelated topics, set "type": "OUT_OF_SCOPE".
        
        CATEGORIES:
        - Food & Groceries (కూరగాయలు, కిరాణా)
        - Dining & Snacks (హోటల్, తినుబండారాలు)
        - Transport & Fuel (పెట్రోల్, ప్రయాణం)
        - Shopping & Clothes (షాపింగ్, బట్టలు)
        - Bills & Utilities (కరెంట్, రీఛార్జ్)
        - Entertainment & Movies (సినిమాలు)
        - Health & Medical (మందులు, వైద్యం)
        - Education & Fees (స్కూల్, కాలేజ్)
        - Money Lent / Debt (అప్పు, బాకీ)
        - Other Miscellaneous
        
        OUTPUT FORMAT (Strict JSON only):
        {
          "type": "EXPENSE" | "LOAN_GIVEN" | "OUT_OF_SCOPE" | "QUERY",
          "title": "Clean title",
          "amount": 0.0,
          "category": "category name",
          "paymentMode": "UPI" | "Cash" | "Card" | "NetBanking",
          "personName": "Person name if loan, else empty",
          "dueDateDays": 7 or null,
          "notes": "context",
          "summary": "1-line confirmation in ${if (isTelugu) "Telugu" else "English"}"
        }
        """.trimIndent()
    }
}
