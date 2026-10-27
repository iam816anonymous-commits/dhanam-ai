package com.example.ai

import com.example.ui.navigation.Screen
import java.util.Locale

enum class AgentActionKind {
    NAVIGATION,
    READ,
    CREATE,
    UPDATE,
    DELETE,
    IMPORT_EXPORT
}

data class AgentCapability(
    val id: String,
    val screen: Screen,
    val action: AgentActionKind,
    val implementation: String,
    val destructive: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val aliases: List<String> = emptyList()
)

/**
 * Auditable map of the app's supported agent-facing actions to the real screen or ViewModel entry point.
 * Destructive actions are always marked as requiring confirmation; this registry does not execute them.
 */
object AgentCapabilityRegistry {
    val all: List<AgentCapability> = listOf(
        AgentCapability("navigate.agent", Screen.AGENT, AgentActionKind.NAVIGATION, "MainViewModel.setCurrentScreen", aliases = listOf("agent", "home", "main screen", "open jarvis")),
        AgentCapability("navigate.expenses", Screen.EXPENSES, AgentActionKind.NAVIGATION, "MainViewModel.setCurrentScreen", aliases = listOf("show expenses", "all expenses", "transactions", "expense history", "open expenses")),
        AgentCapability("navigate.khata", Screen.KHATA, AgentActionKind.NAVIGATION, "MainViewModel.setCurrentScreen", aliases = listOf("khata", "lending khata", "open khata", "loans", "borrowers")),
        AgentCapability("navigate.insights", Screen.INSIGHTS, AgentActionKind.NAVIGATION, "MainViewModel.setCurrentScreen", aliases = listOf("insights", "analytics", "report", "pdf", "open report")),
        AgentCapability("navigate.settings", Screen.SETTINGS, AgentActionKind.NAVIGATION, "MainViewModel.setCurrentScreen", aliases = listOf("settings", "preferences", "open settings")),
        AgentCapability("expense.create", Screen.EXPENSES, AgentActionKind.CREATE, "MainViewModel.addManualExpense"),
        AgentCapability("expense.update", Screen.EXPENSES, AgentActionKind.UPDATE, "MainViewModel.updateExpense"),
        AgentCapability("expense.delete", Screen.EXPENSES, AgentActionKind.DELETE, "MainViewModel.deleteExpense", destructive = true, requiresConfirmation = true),
        AgentCapability("expense.import_csv", Screen.SETTINGS, AgentActionKind.IMPORT_EXPORT, "MainViewModel.importExpensesFromCsv"),
        AgentCapability("expense.export_csv", Screen.INSIGHTS, AgentActionKind.IMPORT_EXPORT, "MainViewModel.exportAndShareCsv"),
        AgentCapability("khata.create", Screen.KHATA, AgentActionKind.CREATE, "MainViewModel.addManualLoan"),
        AgentCapability("khata.update", Screen.KHATA, AgentActionKind.UPDATE, "MainViewModel.updateLoan"),
        AgentCapability("khata.repay", Screen.KHATA, AgentActionKind.UPDATE, "MainViewModel.recordRepayment"),
        AgentCapability("khata.delete", Screen.KHATA, AgentActionKind.DELETE, "MainViewModel.deleteLoan", destructive = true, requiresConfirmation = true),
        AgentCapability("budget.update", Screen.SETTINGS, AgentActionKind.UPDATE, "MainViewModel.setOverallMonthlyBudget"),
        AgentCapability("data.clear_all", Screen.SETTINGS, AgentActionKind.DELETE, "MainViewModel.clearAllData", destructive = true, requiresConfirmation = true),
        AgentCapability("insights.spending_profile", Screen.INSIGHTS, AgentActionKind.READ, "SpendingProfileAnalyzer.analyze")
    )

    fun resolveNavigation(input: String): AgentCapability? {
        val normalized = input.lowercase(Locale.ROOT).trim()
        return all.asSequence()
            .filter { it.action == AgentActionKind.NAVIGATION }
            .sortedByDescending { capability -> capability.aliases.maxOfOrNull { it.length } ?: 0 }
            .firstOrNull { capability ->
                capability.aliases.any { alias ->
                    normalized == alias || normalized.contains(alias)
                }
            }
    }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        val navigation = all.filter { it.action == AgentActionKind.NAVIGATION }
        val mappedScreens = navigation.map { it.screen }.toSet()
        Screen.entries.filterNot { it in mappedScreens }.forEach {
            errors += "Screen ${it.route} has no registered navigation capability."
        }
        all.filter { it.destructive && !it.requiresConfirmation }.forEach {
            errors += "Destructive capability ${it.id} is missing confirmation protection."
        }
        all.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            errors += "Duplicate capability id: $it"
        }
        all.filter { it.implementation.isBlank() }.forEach {
            errors += "Capability ${it.id} has no implementation mapping."
        }
        all.filterNot(::hasCallableImplementation).forEach {
            errors += "Capability ${it.id} points to missing callable entry point '${it.implementation}'."
        }
        return errors
    }

    /**
     * Compile-time callable references keep this registry tied to real application entry points.
     * Unlike runtime reflection, this remains valid in minified release builds.
     */
    private val callableImplementations: Map<String, Any> = mapOf(
        "MainViewModel.setCurrentScreen" to com.example.ui.viewmodel.MainViewModel::setCurrentScreen,
        "MainViewModel.addManualExpense" to com.example.ui.viewmodel.MainViewModel::addManualExpense,
        "MainViewModel.updateExpense" to com.example.ui.viewmodel.MainViewModel::updateExpense,
        "MainViewModel.deleteExpense" to com.example.ui.viewmodel.MainViewModel::deleteExpense,
        "MainViewModel.importExpensesFromCsv" to com.example.ui.viewmodel.MainViewModel::importExpensesFromCsv,
        "MainViewModel.exportAndShareCsv" to com.example.ui.viewmodel.MainViewModel::exportAndShareCsv,
        "MainViewModel.addManualLoan" to com.example.ui.viewmodel.MainViewModel::addManualLoan,
        "MainViewModel.updateLoan" to com.example.ui.viewmodel.MainViewModel::updateLoan,
        "MainViewModel.recordRepayment" to com.example.ui.viewmodel.MainViewModel::recordRepayment,
        "MainViewModel.deleteLoan" to com.example.ui.viewmodel.MainViewModel::deleteLoan,
        "MainViewModel.setOverallMonthlyBudget" to com.example.ui.viewmodel.MainViewModel::setOverallMonthlyBudget,
        "MainViewModel.clearAllData" to com.example.ui.viewmodel.MainViewModel::clearAllData,
        "SpendingProfileAnalyzer.analyze" to SpendingProfileAnalyzer::analyze
    )

    private fun hasCallableImplementation(capability: AgentCapability): Boolean =
        capability.implementation in callableImplementations
}
