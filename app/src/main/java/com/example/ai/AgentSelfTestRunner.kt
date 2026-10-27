package com.example.ai

import com.example.data.local.AppDatabase
import com.example.data.model.Expense
import com.example.export.CsvExpenseImporter
import com.example.ui.navigation.Screen
import java.io.StringReader

data class AgentSelfTestResult(
    val name: String,
    val passed: Boolean,
    val details: String
)

/**
 * Read-only diagnostic suite for the app's core agent/data contracts.
 * It never creates, edits, or deletes a user's stored financial records.
 */
object AgentSelfTestRunner {
    suspend fun run(database: AppDatabase): List<AgentSelfTestResult> {
        val results = mutableListOf<AgentSelfTestResult>()
        fun check(name: String, block: () -> String) {
            try {
                results += AgentSelfTestResult(name, true, block())
            } catch (error: Exception) {
                results += AgentSelfTestResult(name, false, error.message ?: error.javaClass.simpleName)
            }
        }

        check("Capability registry and destructive safeguards") {
            val errors = AgentCapabilityRegistry.validate()
            check(errors.isEmpty()) { errors.joinToString("; ") }
            "All screens mapped; destructive actions require confirmation."
        }

        check("Navigation alias resolution") {
            val navigation = AgentCapabilityRegistry.all.filter { it.action == AgentActionKind.NAVIGATION }
            check(navigation.map { it.screen }.toSet() == Screen.entries.toSet()) {
                "Navigation registry does not cover every screen."
            }
            navigation.forEach { capability ->
                val alias = capability.aliases.firstOrNull()
                    ?: error("Missing navigation alias for ${capability.id}")
                check(AgentCapabilityRegistry.resolveNavigation(alias)?.screen == capability.screen) {
                    "Alias '$alias' did not resolve to ${capability.screen.route}."
                }
            }
            "${navigation.size} screen navigation routes resolved."
        }

        try {
            val count = database.expenseDao().getAllExpenses().size
            results += AgentSelfTestResult("Expense database read", true, "Room query succeeded; $count stored expense(s).")
        } catch (error: Exception) {
            results += AgentSelfTestResult("Expense database read", false, error.message ?: error.javaClass.simpleName)
        }
        try {
            val count = database.loanDao().getAllLoans().size
            results += AgentSelfTestResult("Lending Khata database read", true, "Room query succeeded; $count loan record(s).")
        } catch (error: Exception) {
            results += AgentSelfTestResult("Lending Khata database read", false, error.message ?: error.javaClass.simpleName)
        }
        try {
            val count = database.budgetDao().getAllCategoryBudgets().size
            results += AgentSelfTestResult("Budget/settings database read", true, "Room query succeeded; $count category budget(s).")
        } catch (error: Exception) {
            results += AgentSelfTestResult("Budget/settings database read", false, error.message ?: error.javaClass.simpleName)
        }

        check("CSV validation and duplicate key") {
            val parsed = CsvExpenseImporter.parse(
                StringReader("Date,Time,Title,Category,Amount,Payment Mode,Notes\n27/10/2026,09:30 AM,Self-test,Other,1,UPI,diagnostic")
            )
            check(parsed.headerFound && parsed.expenses.size == 1 && parsed.issues.isEmpty()) {
                "Valid CSV smoke-test row was not parsed cleanly."
            }
            check(CsvExpenseImporter.fingerprint(parsed.expenses.single()) ==
                CsvExpenseImporter.fingerprint(parsed.expenses.single().copy(id = 42L))) {
                "Duplicate fingerprint changed when the database id changed."
            }
            "Valid row parsed and duplicate fingerprint is stable."
        }

        check("Spending profile calculation") {
            val now = System.currentTimeMillis()
            val fixture = listOf(
                Expense(title = "Self-test A", amount = 10.0, category = "Test", timestamp = now),
                Expense(title = "Self-test B", amount = 15.0, category = "Test", timestamp = now)
            )
            val profile = SpendingProfileAnalyzer.analyze(fixture, now - 1_000L, now + 1_000L, calendarMonthCount = 1)
            check(profile.transactionCount == 2 && profile.totalSpent == 25.0) {
                "Expected 2 transactions totalling 25.00; got ${profile.transactionCount} and ${profile.totalSpent}."
            }
            "Two local fixture rows aggregated to ₹25.00; no user data was modified."
        }

        return results
    }
}
