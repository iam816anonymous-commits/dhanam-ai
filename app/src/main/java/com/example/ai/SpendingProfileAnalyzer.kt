package com.example.ai

import com.example.data.model.Expense
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class MonthlySpend(val key: String, val total: Double, val transactionCount: Int)
data class CategorySpend(val category: String, val total: Double, val transactionCount: Int)
data class FrequentExpense(val title: String, val total: Double, val occurrences: Int)

data class SpendingProfile(
    val startMillis: Long,
    val endMillis: Long,
    val transactionCount: Int,
    val totalSpent: Double,
    val averageMonthlySpend: Double,
    val averageTransactionAmount: Double,
    val highestSpendMonth: MonthlySpend?,
    val monthlySpending: List<MonthlySpend>,
    val categorySpending: List<CategorySpend>,
    val paymentModeTotals: Map<String, Double>,
    val frequentExpenses: List<FrequentExpense>,
    val largestExpenses: List<Expense>
) {
    fun toMessage(isTelugu: Boolean): String {
        if (transactionCount == 0) {
            return if (isTelugu) {
                "గత 12 క్యాలెండర్ నెలల్లో ఖర్చుల రికార్డులు లేవు. ఖర్చులను నమోదు చేయండి లేదా మీ పాత ఖర్చులను దిగుమతి చేయండి; అప్పుడు నేను మీ ఖర్చుల నమూనాలను విశ్లేషించగలను."
            } else {
                "I couldn't find expense records in the last 12 calendar months. Add expenses or import your historical records, then I can analyze your spending patterns."
            }
        }

        val currency = { value: Double -> "₹" + String.format(Locale.US, "%,.2f", value) }
        val monthFormat = SimpleDateFormat("MMM yyyy", if (isTelugu) Locale("te", "IN") else Locale.ENGLISH)
        val start = monthFormat.format(Date(startMillis))
        val end = monthFormat.format(Date(endMillis))
        val monthlyLines = monthlySpending.joinToString("\n") { "• ${it.key}: ${currency(it.total)} (${it.transactionCount})" }
        val categoryLines = categorySpending.take(5).joinToString("\n") {
            "• ${it.category}: ${currency(it.total)} — ${String.format(Locale.US, "%.1f", it.total / totalSpent * 100.0)}%"
        }
        val paymentLines = paymentModeTotals.entries.joinToString("\n") { "• ${it.key}: ${currency(it.value)}" }
        val frequentLines = frequentExpenses.take(5).joinToString("\n") {
            "• ${it.title}: ${it.occurrences} times, ${currency(it.total)}"
        }.ifBlank { if (isTelugu) "• తరచుగా పునరావృతమయ్యే ఖర్చులు ఇంకా గుర్తించబడలేదు." else "• No repeated expense titles found yet." }
        val largestLines = largestExpenses.take(5).joinToString("\n") {
            "• ${it.title}: ${currency(it.amount)} (${SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(it.timestamp))})"
        }

        return if (isTelugu) {
            "గత 12 క్యాలెండర్ నెలల ఖర్చుల విశ్లేషణ ($start – $end)\n" +
                "మొత్తం ఖర్చు: ${currency(totalSpent)}\n" +
                "లావాదేవీలు: $transactionCount\n" +
                "సగటు నెలవారీ ఖర్చు: ${currency(averageMonthlySpend)}\n" +
                "సగటు ఒక్క ఖర్చు: ${currency(averageTransactionAmount)}\n" +
                "అత్యధిక ఖర్చు నెల: ${highestSpendMonth?.let { "${it.key} — ${currency(it.total)}" } ?: "N/A"}\n\n" +
                "నెలవారీ ఖర్చులు:\n$monthlyLines\n\n" +
                "అత్యధిక ఖర్చు వర్గాలు:\n$categoryLines\n\n" +
                "చెల్లింపు విధానం:\n$paymentLines\n\n" +
                "తరచుగా వచ్చే ఖర్చులు:\n$frequentLines\n\n" +
                "అత్యధిక వ్యక్తిగత ఖర్చులు:\n$largestLines\n\n" +
                "ఈ నివేదిక మీ యాప్‌లో ఉన్న రికార్డుల ఆధారంగా మాత్రమే రూపొందించబడింది."
        } else {
            "12-MONTH SPENDING PROFILE ($start – $end)\n" +
                "Total spent: ${currency(totalSpent)}\n" +
                "Transactions: $transactionCount\n" +
                "Average monthly spend: ${currency(averageMonthlySpend)}\n" +
                "Average transaction: ${currency(averageTransactionAmount)}\n" +
                "Highest-spend month: ${highestSpendMonth?.let { "${it.key} — ${currency(it.total)}" } ?: "N/A"}\n\n" +
                "MONTH-BY-MONTH\n$monthlyLines\n\n" +
                "TOP CATEGORIES\n$categoryLines\n\n" +
                "PAYMENT METHODS\n$paymentLines\n\n" +
                "REPEATED EXPENSES (title-based)\n$frequentLines\n\n" +
                "LARGEST INDIVIDUAL EXPENSES\n$largestLines\n\n" +
                "This report reflects only records currently stored in Dhanam AI; it does not infer missing transactions."
        }
    }
}

object SpendingProfileAnalyzer {
    fun analyze(
        expenses: List<Expense>,
        startMillis: Long,
        endMillis: Long,
        calendarMonthCount: Int = 12
    ): SpendingProfile {
        require(startMillis <= endMillis) { "startMillis must be <= endMillis" }
        require(calendarMonthCount > 0) { "calendarMonthCount must be positive" }

        val selected = expenses.filter { it.timestamp in startMillis..endMillis && it.amount.isFinite() && it.amount >= 0.0 }
        val monthFormatter = SimpleDateFormat("yyyy-MM", Locale.ROOT)
        val monthTotals = linkedMapOf<String, Pair<Double, Int>>()
        val categoryTotals = linkedMapOf<String, Pair<Double, Int>>()
        val paymentTotals = linkedMapOf<String, Double>()
        val repeated = linkedMapOf<String, Pair<Expense, MutableList<Expense>>>()

        val startCal = Calendar.getInstance().apply { timeInMillis = startMillis }
        startCal.set(Calendar.DAY_OF_MONTH, 1)
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)
        val monthDisplay = SimpleDateFormat("MMM yyyy", Locale.ENGLISH)
        val months = mutableListOf<String>()
        repeat(calendarMonthCount) {
            months += monthFormatter.format(startCal.time)
            startCal.add(Calendar.MONTH, 1)
        }

        selected.forEach { expense ->
            val month = monthFormatter.format(Date(expense.timestamp))
            val monthCurrent = monthTotals[month] ?: (0.0 to 0)
            monthTotals[month] = (monthCurrent.first + expense.amount) to (monthCurrent.second + 1)

            val category = expense.category.ifBlank { "Uncategorized" }
            val catCurrent = categoryTotals[category] ?: (0.0 to 0)
            categoryTotals[category] = (catCurrent.first + expense.amount) to (catCurrent.second + 1)

            val mode = expense.paymentMode.ifBlank { "Unknown" }
            paymentTotals[mode] = (paymentTotals[mode] ?: 0.0) + expense.amount

            val normalizedTitle = expense.title.trim().lowercase(Locale.ROOT)
            if (normalizedTitle.isNotBlank()) {
                val group = repeated[normalizedTitle]
                if (group == null) repeated[normalizedTitle] = expense to mutableListOf(expense)
                else group.second += expense
            }
        }

        val monthly = months.map { key ->
            val value = monthTotals[key] ?: (0.0 to 0)
            val parsed = SimpleDateFormat("yyyy-MM", Locale.ROOT).parse(key)
            MonthlySpend(parsed?.let { monthDisplay.format(it) } ?: key, value.first, value.second)
        }
        val categories = categoryTotals.map { (name, value) ->
            CategorySpend(name, value.first, value.second)
        }.sortedByDescending { it.total }
        val frequent = repeated.values.mapNotNull { (first, records) ->
            if (records.size >= 3) FrequentExpense(first.title.trim(), records.sumOf { it.amount }, records.size) else null
        }.sortedWith(compareByDescending<FrequentExpense> { it.occurrences }.thenByDescending { it.total })

        val total = selected.sumOf { it.amount }
        val highest = monthly.maxByOrNull { it.total }?.takeIf { it.transactionCount > 0 }
        return SpendingProfile(
            startMillis = startMillis,
            endMillis = endMillis,
            transactionCount = selected.size,
            totalSpent = total,
            averageMonthlySpend = total / calendarMonthCount,
            averageTransactionAmount = if (selected.isEmpty()) 0.0 else total / selected.size,
            highestSpendMonth = highest,
            monthlySpending = monthly,
            categorySpending = categories,
            paymentModeTotals = paymentTotals.toSortedMap(),
            frequentExpenses = frequent,
            largestExpenses = selected.sortedByDescending { it.amount }.take(5)
        )
    }
}
