package com.dhanam.next.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class ParsedExpenseCommand(
    val title: String, val amountPaise: Long, val category: String, val paymentMode: String
)
sealed interface ParseExpenseResult {
    data class Success(val expense: ParsedExpenseCommand) : ParseExpenseResult
    data class NeedsClarification(val message: String) : ParseExpenseResult
    data class Invalid(val message: String) : ParseExpenseResult
}

/** Bounded deterministic parser. It never invents an amount or payment method. */
object ExpenseCommandParser {
    private val amountPattern = Regex("""(?:₹|rs\.?\s*)?([0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    fun parse(input: String): ParseExpenseResult {
        val text = input.trim()
        if (text.isBlank()) return ParseExpenseResult.Invalid("Enter an expense, for example: tea 30")
        val match = amountPattern.find(text)
            ?: return ParseExpenseResult.NeedsClarification("I couldn't find an amount. Try “tea 30”.")
        val amount = try {
            BigDecimal(match.groupValues[1]).setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2).longValueExact()
        } catch (_: Exception) {
            return ParseExpenseResult.Invalid("Enter an amount with at most two decimal places.")
        }
        if (amount <= 0L) return ParseExpenseResult.Invalid("The amount must be greater than zero.")
        val title = text.removeRange(match.range)
            .replace(Regex("(?i)\\b(spent|expense|on|for|rupees?)\\b"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (title.isBlank()) return ParseExpenseResult.NeedsClarification("What was the expense for?")
        val normalized = title.lowercase(Locale.ROOT)
        val category = when {
            listOf("petrol", "fuel", "bus", "train", "auto", "taxi", "travel").any(normalized::contains) -> "Transport"
            listOf("grocery", "groceries", "vegetable", "milk", "rice", "food").any(normalized::contains) -> "Food & Groceries"
            listOf("restaurant", "snack", "tea", "coffee", "hotel", "lunch", "dinner").any(normalized::contains) -> "Dining & Snacks"
            listOf("electricity", "recharge", "bill", "internet").any(normalized::contains) -> "Bills & Utilities"
            listOf("medicine", "doctor", "hospital", "medical").any(normalized::contains) -> "Health & Medical"
            else -> "Other"
        }
        val payment = when {
            Regex("(?i)\\bupi\\b").containsMatchIn(text) -> "UPI"
            Regex("(?i)\\bcash\\b").containsMatchIn(text) -> "Cash"
            Regex("(?i)\\b(card|credit card|debit card)\\b").containsMatchIn(text) -> "Card"
            Regex("(?i)\\bnet ?banking\\b").containsMatchIn(text) -> "NetBanking"
            else -> "Unknown"
        }
        val cleanTitle = title.replace(Regex("(?i)\\b(upi|cash|card|net ?banking)\\b"), "").trim()
        if (cleanTitle.isBlank()) return ParseExpenseResult.NeedsClarification("What was the expense for?")
        return ParseExpenseResult.Success(ParsedExpenseCommand(cleanTitle, amount, category, payment))
    }
}
