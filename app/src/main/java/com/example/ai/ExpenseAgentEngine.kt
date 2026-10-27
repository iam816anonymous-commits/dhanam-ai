package com.example.ai

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.BudgetDao
import com.example.data.local.ExpenseDao
import com.example.data.local.LoanDao
import com.example.data.model.CategoryBudget
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Loan
import com.example.data.preferences.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AgentResult(
    val success: Boolean,
    val type: ResultType,
    val message: String,
    val isOverspent: Boolean = false,
    val overspentWarning: String? = null,
    val recordedExpense: Expense? = null,
    val recordedExpenses: List<Expense> = emptyList(),
    val recordedLoan: Loan? = null,
    val recordedLoans: List<Loan> = emptyList(),
    val totalRecordedAmount: Double = 0.0,
    val navigationAction: String? = null, // "KHATA", "EXPENSES", "INSIGHTS", "SETTINGS", "AGENT", "LOCK", "REBALANCE"
    val suggestions: List<String> = emptyList(),
    val spokenSummary: String? = null,
    val spokenSummaryTe: String? = null,
    val spokenSummaryEn: String? = null
)

enum class ResultType {
    EXPENSE_RECORDED,
    MULTI_EXPENSE_RECORDED,
    LOAN_RECORDED,
    NAVIGATION_ACTION,
    INSIGHTS,
    OUT_OF_SCOPE,
    ERROR
}

data class ParsedItem(
    val type: String, // "EXPENSE" or "LOAN_GIVEN"
    val title: String,
    val amount: Double,
    val category: String,
    val personName: String = "",
    val dueDateDays: Int = 7,
    val paymentMode: String = "UPI",
    val notes: String = ""
)

class ExpenseAgentEngine(
    private val expenseDao: ExpenseDao,
    private val loanDao: LoanDao,
    private val budgetDao: BudgetDao,
    private val preferenceManager: PreferenceManager,
    private val merchantCache: AdaptiveMerchantCache? = null,
    private val appDatabase: AppDatabase? = null
) {

    suspend fun processUserInput(input: String, forceLanguage: String? = null): AgentResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return AgentResult(
                success = false,
                type = ResultType.ERROR,
                message = "దయచేసి మీ ఖర్చు లేదా అప్పు వివరాలను చెప్పండి / Please enter or speak your expense."
            )
        }

        val isTelugu = forceLanguage == "te" || containsTelugu(trimmed)

        // 1. In-App Jarvis Operational & Navigation Commands
        val navResult = checkInAppNavigationCommand(trimmed, isTelugu)
        if (navResult != null) {
            return navResult
        }

        // 1.5 Historical spending profile: compute from local Room records, never fabricated data.
        if (isHistoricalAnalysisRequest(trimmed)) {
            return handleHistoricalSpendingAnalysis(isTelugu)
        }

        // 2. Strict Domain Boundary Check: Refuse non-financial inquiries
        val outOfScopeMessage = AgentFinancialBrain.checkDomainScope(trimmed, isTelugu)
        if (outOfScopeMessage != null) {
            return AgentResult(
                success = false,
                type = ResultType.OUT_OF_SCOPE,
                message = outOfScopeMessage,
                spokenSummary = outOfScopeMessage
            )
        }

        // 2.5 Advanced Intent Classification & Conversational Undo / Correction Handlers
        val classifiedIntent = ConversationalFuzzyExtractor.classifyIntent(trimmed)

        // A. Conversational Undo: "Undo last transaction" or "Delete last expense"
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.UNDO_LAST) {
            val latestExpense = expenseDao.getLatestExpense()
            if (latestExpense != null) {
                expenseDao.deleteExpense(latestExpense)
                val msg = if (isTelugu) {
                    "చివరిగా నమోదైన '${latestExpense.title}' (₹${latestExpense.amount.toInt()}) ఖర్చు రద్దు చేయబడింది."
                } else {
                    "Undid last transaction: removed '${latestExpense.title}' (₹${latestExpense.amount.toInt()})."
                }
                val spokenTe = "చివరి ఖర్చు రద్దు చేశాను."
                val spokenEn = "Undid last expense."
                return AgentResult(
                    success = true,
                    type = ResultType.EXPENSE_RECORDED,
                    message = msg,
                    recordedExpenses = emptyList(),
                    totalRecordedAmount = 0.0,
                    spokenSummary = if (isTelugu) spokenTe else spokenEn,
                    spokenSummaryTe = spokenTe,
                    spokenSummaryEn = spokenEn
                )
            } else {
                val noExpMsg = if (isTelugu) "రద్దు చేయడానికి ఎలాంటి ఖర్చులు కనుగొనబడలేదు." else "No recent expense found to undo."
                return AgentResult(
                    success = false,
                    type = ResultType.ERROR,
                    message = noExpMsg,
                    spokenSummary = noExpMsg
                )
            }
        }

        // B. Conversational Correction: "Change previous [category] expense to [amount]"
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.MODIFY_LAST) {
            val mod = ConversationalFuzzyExtractor.parseModificationRequest(trimmed, isTelugu)
            if (mod != null && mod.newAmount > 0.0) {
                val targetExpense = if (mod.targetCategory != null) {
                    expenseDao.getLatestExpenseByCategory(mod.targetCategory) ?: expenseDao.getLatestExpense()
                } else {
                    expenseDao.getLatestExpense()
                }

                if (targetExpense != null) {
                    val updated = targetExpense.copy(amount = mod.newAmount)
                    expenseDao.updateExpense(updated)
                    val oldAmt = targetExpense.amount.toInt()
                    val newAmt = mod.newAmount.toInt()
                    val msg = if (isTelugu) {
                        "'${updated.title}' ఖర్చును ₹$oldAmt నుండి ₹$newAmt కి సవరించాను."
                    } else {
                        "Updated previous '${updated.title}' (${updated.category}) expense from ₹$oldAmt to ₹$newAmt."
                    }
                    val spokenTe = "ఖర్చును ${newAmt} రూపాయలకు మార్చాను."
                    val spokenEn = "Updated expense to $newAmt rupees."
                    return AgentResult(
                        success = true,
                        type = ResultType.EXPENSE_RECORDED,
                        message = msg,
                        recordedExpense = updated,
                        recordedExpenses = listOf(updated),
                        totalRecordedAmount = mod.newAmount,
                        spokenSummary = if (isTelugu) spokenTe else spokenEn,
                        spokenSummaryTe = spokenTe,
                        spokenSummaryEn = spokenEn
                    )
                }
            }
        }

        // B.2 Set Monthly Budget ("Set budget to 25000", "నెల బడ్జెట్ 30000 గా పెట్టు")
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.SET_BUDGET) {
            return handleSetBudget(trimmed, isTelugu)
        }

        // C. Daily Briefing / Greeting ("Hi Jarvis", "హలో జార్విస్", "నమస్కారం")
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.DAILY_BRIEFING) {
            return handleDailyBriefing(isTelugu)
        }

        // D. Spending Today Inquiry ("How much did I spend today?", "ఈ రోజు ఖర్చు ఎంత?")
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.QUERY_SPENDING_TODAY) {
            return handleQuerySpendingToday(isTelugu)
        }

        // E. Spending Month Inquiry ("How much this month?", "ఈ నెల మొత్తం ఖర్చు ఎంత?")
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.QUERY_SPENDING_MONTH) {
            return handleQuerySpendingMonth(isTelugu)
        }

        // F. Budget Remaining Inquiry ("What is my budget left?", "నా బడ్జెట్ ఎంత మిగిలింది?")
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.QUERY_BUDGET_REMAINING) {
            return handleQueryBudgetRemaining(isTelugu)
        }

        // G. Pending Loans / Khata Inquiry ("Who owes me money?", "ఎవరు బాకీ ఉన్నారు?")
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.QUERY_PENDING_LOANS) {
            return handleQueryPendingLoans(isTelugu)
        }

        // H. Category Spending Inquiry ("How much on food?", "పెట్రోల్ కి ఎంత ఖర్చు చేశాను?")
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.QUERY_CATEGORY_SPENDING) {
            return handleQueryCategorySpending(trimmed, isTelugu)
        }

        // I. Savings Advice ("Give me financial advice", "పొదుపు చిట్కాలు చెప్పు")
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.SAVINGS_ADVICE) {
            return handleSavingsAdvice(isTelugu)
        }

        // J. Loan Due Date Setting Follow-up ("Tomorrow", "1 week", "రేపు", "వచ్చే వారం", "15 days", "1 month")
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.SET_LOAN_DUE_DATE) {
            return handleSetLoanDueDate(trimmed, isTelugu)
        }

        // K. User says "i gave this" / "ఇచ్చాను" / "అప్పు ఇచ్చాను" without amount:
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.LOAN_GIVEN && extractAmount(trimmed) <= 0.0) {
            return handleLoanInquiryWithoutAmount(isTelugu)
        }

        // L. Loan Repayment ("Ramesh returned 500", "రమేష్ తిరిగి ఇచ్చాడు", "రమేష్ బాకీ చెల్లించాడు")
        if (classifiedIntent == ConversationalFuzzyExtractor.DetectedIntent.LOAN_REPAYMENT) {
            return handleLoanRepayment(trimmed, isTelugu)
        }

        // 3. Multi-Item Splitting & Advanced Fuzzy Extraction (Telugu, English, Hindi, Kannada)
        val itemClauses = ConversationalFuzzyExtractor.splitIntoClauses(trimmed).ifEmpty { splitMultiItemText(trimmed) }
        val parsedItems = mutableListOf<ParsedItem>()

        for (clause in itemClauses) {
            val fuzzyExtracted = ConversationalFuzzyExtractor.extractClause(clause, isTelugu)
            if (fuzzyExtracted != null && fuzzyExtracted.amount > 0.0) {
                parsedItems.add(
                    ParsedItem(
                        type = if (fuzzyExtracted.isLoan) "LOAN_GIVEN" else "EXPENSE",
                        title = fuzzyExtracted.title,
                        amount = fuzzyExtracted.amount,
                        category = fuzzyExtracted.category.displayNameEn,
                        personName = fuzzyExtracted.personName,
                        dueDateDays = fuzzyExtracted.dueDateDays,
                        paymentMode = fuzzyExtracted.paymentMode
                    )
                )
            } else {
                val item = parseSingleItem(clause, isTelugu)
                if (item != null && item.amount > 0.0) {
                    parsedItems.add(item)
                }
            }
        }

        // If multiple items were recognized, process them in batch!
        if (parsedItems.size >= 2) {
            return executeBatchItems(parsedItems, isTelugu)
        }

        // If 1 item recognized:
        if (parsedItems.size == 1) {
            return executeSingleItem(parsedItems[0], isTelugu)
        }

        // All parsing and financial operations must remain on-device.
        // Do not invoke a remote model when local parsing is uncertain.
        // The user receives a concrete format example so they can rephrase safely.

        // If amount couldn't be detected at all
        return AgentResult(
            success = false,
            type = ResultType.ERROR,
            message = if (isTelugu) "ఖర్చు మొత్తం (Amount) అర్థం కాలేదు. ఉదాహరణకి: 'కూరగాయలు 250, పెట్రోల్ 500' అని చెప్పండి."
            else "Could not detect expense amounts. Try saying: 'Groceries 250, Petrol 500'."
        )
    }

    private fun checkInAppNavigationCommand(input: String, isTelugu: Boolean): AgentResult? {
        val lower = input.lowercase(Locale.ROOT).trim()

        // Guard: If input contains a monetary amount, it is an expense or loan, NOT a pure navigation command!
        if (extractAmount(lower) > 0.0) return null

        // 1. Lending Khata Screen
        if (lower.contains("khata") || lower.contains("lending") || lower.contains("loan") ||
            lower.contains("borrow") || lower.contains("అప్పుల ఖాతా") || lower.contains("ఖాతా తెరువు") ||
            lower.contains("బాకీలు") || lower.contains("సಾಲ")
        ) {
            val msg = if (isTelugu) "అప్పుల ఖాతా (Lending Khata) స్క్రీన్‌కి మారుతున్నాను."
            else "Navigating to Lending Khata screen."
            return AgentResult(
                success = true,
                type = ResultType.NAVIGATION_ACTION,
                message = msg,
                spokenSummary = msg,
                spokenSummaryTe = "అప్పుల ఖాతా తెరుస్తున్నాను.",
                spokenSummaryEn = "Opening Lending Khata.",
                navigationAction = "KHATA"
            )
        }

        // 2. All Expenses Screen
        if (lower.contains("show expenses") || lower.contains("all expenses") || lower.contains("history") ||
            lower.contains("transactions") || lower.contains("ఖర్చులు చూపించు") || lower.contains("ఖర్చుల జాబితా") ||
            lower.contains("వ్యయాల చరిత్ర")
        ) {
            val msg = if (isTelugu) "ఖర్చుల జాబితా (Expenses) స్క్రీన్‌కి మారుతున్నాను."
            else "Navigating to Expenses screen."
            return AgentResult(
                success = true,
                type = ResultType.NAVIGATION_ACTION,
                message = msg,
                spokenSummary = msg,
                spokenSummaryTe = "ఖర్చుల జాబితా తెరుస్తున్నాను.",
                spokenSummaryEn = "Opening Expenses screen.",
                navigationAction = "EXPENSES"
            )
        }

        // 3. Analytics & PDF Report Screen
        if (lower.contains("insight") || lower.contains("analytic") || lower.contains("report") ||
            lower.contains("pdf") || lower.contains("రిపోర్ట్") || lower.contains("అనలిటిక్స్") ||
            lower.contains("విశ్లేషణ")
        ) {
            val msg = if (isTelugu) "ఆర్థిక విశ్లేషణ & PDF రిపోర్ట్ స్క్రీన్‌కి మారుతున్నాను."
            else "Navigating to Insights & PDF Report."
            return AgentResult(
                success = true,
                type = ResultType.NAVIGATION_ACTION,
                message = msg,
                spokenSummary = msg,
                spokenSummaryTe = "పీడీఎఫ్ రిపోర్ట్ స్క్రీన్ తెరుస్తున్నాను.",
                spokenSummaryEn = "Opening Insights and PDF Report.",
                navigationAction = "INSIGHTS"
            )
        }

        // 4. Settings Screen
        if (lower.contains("settings") || lower.contains("preference") || lower.contains("సెట్టింగ్స్") ||
            lower.contains("బడ్జెట్ మార్చు")
        ) {
            val msg = if (isTelugu) "సెట్టింగ్స్ & భద్రత స్క్రీన్‌కి మారుతున్నాను."
            else "Navigating to Settings screen."
            return AgentResult(
                success = true,
                type = ResultType.NAVIGATION_ACTION,
                message = msg,
                spokenSummary = msg,
                spokenSummaryTe = "సెట్టింగ్స్ తెరుస్తున్నాను.",
                spokenSummaryEn = "Opening Settings.",
                navigationAction = "SETTINGS"
            )
        }

        // 5. Lock App Action
        if (lower.contains("lock app") || lower.contains("lock screen") || lower.contains("యాప్ లాక్ చేయి") ||
            lower.contains("స్క్రీన్ లాక్")
        ) {
            val msg = if (isTelugu) "జార్విస్ యాప్‌ను లాక్ చేసింది." else "Jarvis locked the app."
            return AgentResult(
                success = true,
                type = ResultType.NAVIGATION_ACTION,
                message = msg,
                spokenSummary = msg,
                spokenSummaryTe = "యాప్ లాక్ చేయబడింది.",
                spokenSummaryEn = "App locked.",
                navigationAction = "LOCK"
            )
        }

        // 6. Rebalance Budget Action
        if (lower.contains("rebalance budget") || lower.contains("adapt budget") ||
            lower.contains("బడ్జెట్ సర్దుబాటు చేయి") || lower.contains("రీ-బ్యాలెన్స్")
        ) {
            val msg = if (isTelugu) "మీ ఖర్చుల అలవాట్ల ప్రకారం బడ్జెట్ రీ-బ్యాలెన్స్ చేయబడుతోంది."
            else "Re-balancing budgets adaptively to your spending habits."
            return AgentResult(
                success = true,
                type = ResultType.NAVIGATION_ACTION,
                message = msg,
                spokenSummary = msg,
                spokenSummaryTe = "అలవాట్ల ప్రకారం బడ్జెట్ సర్దుబాటు చేస్తున్నాను.",
                spokenSummaryEn = "Re-balancing budget with learned habits.",
                navigationAction = "REBALANCE"
            )
        }

        // 7. Jarvis Chat Screen Navigation
        if (lower.contains("agent") || lower.contains("chat") || lower.contains("home") ||
            lower.contains("jarvis") || lower.contains("చాట్") || lower.contains("హోమ్") ||
            lower.contains("జార్విస్")
        ) {
            val msg = if (isTelugu) "జార్విస్ అసిస్టెంట్ స్క్రీన్‌కి మారుతున్నాను."
            else "Navigating to Jarvis AI Assistant screen."
            return AgentResult(
                success = true,
                type = ResultType.NAVIGATION_ACTION,
                message = msg,
                spokenSummary = msg,
                spokenSummaryTe = "జార్విస్ చాట్ తెరుస్తున్నాను.",
                spokenSummaryEn = "Opening Jarvis AI screen.",
                navigationAction = "AGENT"
            )
        }

        return null
    }

    private fun containsTelugu(text: String): Boolean {
        for (char in text) {
            if (char.code in 0x0C00..0x0C7F) return true
        }
        return false
    }

    /**
     * Splits a compound input into individual item clauses using punctuation,
     * conjunctions, or distinct number boundaries.
     */
    private fun splitMultiItemText(text: String): List<String> {
        val listSeparated = text.replace(Regex("""(?<=\s|^)(?:\d+[\.\)]|\-|\*|•)\s+"""), "|")
        val sanitized = listSeparated
            .replace("\n", "|")
            .replace(";", "|")
            .replace(",", "|")
            .replace(Regex("""\b(?:and also|and then|and|also|plus|then)\b""", RegexOption.IGNORE_CASE), "|")
            .replace(" మరియు ", "|")
            .replace(" ఇంకా ", "|")
            .replace(" అలాగే ", "|")
            .replace(" తో పాటు ", "|")
            .replace(" తర్వాత ", "|")
            .replace(" మరియు", "|")
            .replace(" ಮತ್ತು ", "|")
            .replace(" ಹಾಗು ", "|")
            .replace(" और ", "|")
            .replace(" तथा ", "|")
            .replace(" एवं ", "|")

        var parts = sanitized.split("|")
            .map { it.trim().replace(Regex("""^(?:\d+[\.\)]|\-|\*|•)\s*"""), "") }
            .filter { it.isNotBlank() }

        // If the user didn't use commas or conjunctions (e.g. "కూరగాయలు 200 పెట్రోల్ 500 భోజనం 350")
        if (parts.size == 1) {
            val numRegex = Regex("""(\d+(?:\.\d+)?\s*(?:k|rs\.?|₹|రూ\.?|రూపాయలు|వేలు|వేల|లక్ష)?)""", RegexOption.IGNORE_CASE)
            val matches = numRegex.findAll(parts[0]).toList()
            if (matches.size >= 2) {
                val subParts = mutableListOf<String>()
                var lastIdx = 0
                for (m in matches) {
                    val end = m.range.last + 1
                    val chunk = parts[0].substring(lastIdx, end).trim()
                    if (chunk.isNotBlank()) {
                        subParts.add(chunk)
                    }
                    lastIdx = end
                }
                if (lastIdx < parts[0].length) {
                    val rest = parts[0].substring(lastIdx).trim()
                    if (rest.isNotBlank()) {
                        subParts.add(rest)
                    }
                }
                if (subParts.size >= 2) {
                    parts = subParts
                }
            }
        }

        return parts
    }

    private fun parseSingleItem(text: String, isTelugu: Boolean): ParsedItem? {
        val lower = text.lowercase(Locale.ROOT)
        val amount = extractAmount(lower)
        if (amount <= 0.0) return null

        val isLoan = ConversationalFuzzyExtractor.isLoanPhrase(text)

        if (isLoan) {
            val person = ConversationalFuzzyExtractor.extractPerson(text, isTelugu)
            val dueDays = ConversationalFuzzyExtractor.parseDueDaysFromText(text)
            return ParsedItem(
                type = "LOAN_GIVEN",
                title = if (isTelugu) "$person కి ఇచ్చిన అప్పు" else "Money lent to $person",
                amount = amount,
                category = ExpenseCategory.LENDING.displayNameEn,
                personName = person,
                dueDateDays = dueDays,
                paymentMode = if (lower.contains("cash") || lower.contains("నగదు")) "Cash" else "UPI",
                notes = text
            )
        }

        val cachedMerchant = merchantCache?.resolveMerchant(lower)
        val category = cachedMerchant?.first ?: determineCategory(lower)
        val title = extractTitle(text, category, isTelugu)
        val paymentMode = when {
            lower.contains("cash") || lower.contains("నగదు") -> "Cash"
            lower.contains("card") || lower.contains("కార్డ్") -> "Card"
            lower.contains("netbanking") || lower.contains("బ్యాంక్") -> "NetBanking"
            cachedMerchant != null -> cachedMerchant.second
            else -> "UPI"
        }

        return ParsedItem(
            type = "EXPENSE",
            title = title,
            amount = amount,
            category = category.displayNameEn,
            paymentMode = paymentMode,
            notes = text
        )
    }

    private fun extractAmount(text: String): Double {
        val wordNormalized = ConversationalFuzzyExtractor.normalizeTeluguNumberWords(text)
        val stripped = wordNormalized.replace(Regex("""^\s*\d+[\.\)]\s*"""), "")
        val normalized = stripped
            .replace('౦', '0').replace('౧', '1').replace('౨', '2').replace('౩', '3')
            .replace('౪', '4').replace('౫', '5').replace('౬', '6').replace('౭', '7')
            .replace('౮', '8').replace('౯', '9')
            .replace(",", "")

        // 1. Shorthand thousands (5k, 5 k)
        val kMatch = Regex("""(\d+(?:\.\d+)?)\s*k\b""", RegexOption.IGNORE_CASE).find(normalized)
        if (kMatch != null) {
            val num = kMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            return num * 1000
        }

        // 2. Telugu thousands (వేలు / వేల)
        val veluMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:వేలు|వేల|velu)\b""", RegexOption.IGNORE_CASE).find(normalized)
        if (veluMatch != null) {
            val num = veluMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            return num * 1000
        }

        // 3. Lakh multipliers (1 లక్ష / 1.5 lakhs)
        val lakhMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:లక్ష|లక్షలు|lakh|lakhs)\b""", RegexOption.IGNORE_CASE).find(normalized)
        if (lakhMatch != null) {
            val num = lakhMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            return num * 100000
        }

        // 4. Explicit currency-attached numbers (e.g. ₹500, 500rs, 500 రూపాయలు)
        val currencyRegex = Regex("""(?:₹|rs\.?|inr|రూ\.?|రూపాయలు)\s*(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)\s*(?:₹|rs\.?|inr|రూ\.?|రూపాయలు|/-)""", RegexOption.IGNORE_CASE)
        val currMatch = currencyRegex.find(normalized)
        if (currMatch != null) {
            val amtStr = if (currMatch.groupValues[1].isNotEmpty()) currMatch.groupValues[1] else currMatch.groupValues[2]
            val amt = amtStr.toDoubleOrNull() ?: 0.0
            if (amt > 0) return amt
        }

        // 5. Standalone numbers: prioritize realistic expenditure amounts
        val allNumbers = Regex("""\b(\d+(?:\.\d+)?)\b""").findAll(normalized)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .toList()
        if (allNumbers.isNotEmpty()) {
            return allNumbers.lastOrNull { it >= 1.0 } ?: allNumbers.first()
        }

        return 0.0
    }

    private fun extractPersonName(text: String): String {
        val teluguPattern = Regex("""([A-Za-z\u0C00-\u0C7F]+)\s*(?:కి|కు|గారికి)""")
        val teMatch = teluguPattern.find(text)
        if (teMatch != null) {
            val name = teMatch.groupValues[1].trim()
            if (name.length > 1 && !name.contains("ఎవరి") && !name.contains("అప్పు")) {
                return name
            }
        }

        val engPattern = Regex("""(?:to|gave|lent)\s+([A-Za-z]+)""", RegexOption.IGNORE_CASE)
        val enMatch = engPattern.find(text)
        if (enMatch != null) {
            return enMatch.groupValues[1].replaceFirstChar { it.uppercase() }
        }

        return if (containsTelugu(text)) "స్నేహితుడు" else "Friend"
    }

    private fun determineCategory(text: String): ExpenseCategory {
        val cached = merchantCache?.resolveMerchant(text)
        if (cached != null) {
            return cached.first
        }
        return when {
            text.contains("కూరగాయ") || text.contains("పాలు") || text.contains("కిరాణా") ||
                    text.contains("grocery") || text.contains("vegetable") || text.contains("milk") ||
                    text.contains("supermarket") || text.contains("fruits") || text.contains("పండ్లు") ||
                    text.contains("ತರಕಾರಿ") || text.contains("ಹಾಲು") || text.contains("ಸಬ್ಜಿ") ||
                    text.contains("दूध") || text.contains("सब्जी") || text.contains("राशन") -> ExpenseCategory.FOOD

            text.contains("బిర్యానీ") || text.contains("హోటల్") || text.contains("భోజనం") ||
                    text.contains("టిఫిన్") || text.contains("టీ") || text.contains("కాఫీ") ||
                    text.contains("restaurant") || text.contains("food") || text.contains("dinner") ||
                    text.contains("lunch") || text.contains("swiggy") || text.contains("zomato") ||
                    text.contains("snacks") || text.contains("coffee") || text.contains("pizza") ||
                    text.contains("ಹೋಟೆಲ್") || text.contains("ಊಟ") || text.contains("ತಿಂಡಿ") ||
                    text.contains("खाना") || text.contains("नाश्ता") || text.contains("चाय") -> ExpenseCategory.DINING

            text.contains("పెట్రోల్") || text.contains("డీజిల్") || text.contains("ఆటో") ||
                    text.contains("బస్సు") || text.contains("క్యాబ్") || text.contains("రైలు") ||
                    text.contains("petrol") || text.contains("diesel") || text.contains("fuel") ||
                    text.contains("uber") || text.contains("ola") || text.contains("rapido") ||
                    text.contains("metro") || text.contains("travel") || text.contains("bus") ||
                    text.contains("ಪೆಟ್ರೋಲ್") || text.contains("ಬಸ್ಸು") || text.contains("ऑटो") ||
                    text.contains("किराया") || text.contains("गाड़ी") -> ExpenseCategory.TRANSPORT

            text.contains("షాపింగ్") || text.contains("బట్టలు") || text.contains("డ్రెస్") ||
                    text.contains("shopping") || text.contains("clothes") || text.contains("amazon") ||
                    text.contains("flipkart") || text.contains("myntra") || text.contains("shoes") ||
                    text.contains("ಬಟ್ಟೆ") || text.contains("कपड़े") -> ExpenseCategory.SHOPPING

            text.contains("కరెంట్") || text.contains("రీఛార్జ్") || text.contains("వైఫై") ||
                    text.contains("బిల్") || text.contains("గ్యాస్") || text.contains("rent") ||
                    text.contains("electricity") || text.contains("recharge") || text.contains("wifi") ||
                    text.contains("cylinder") || text.contains("water") || text.contains("bill") ||
                    text.contains("ಕರೆಂಟ್") || text.contains("ರೀಚಾರ್ಜ್") || text.contains("बिजली") ||
                    text.contains("रिचार्ज") || text.contains("बिल") -> ExpenseCategory.BILLS

            text.contains("సినిమా") || text.contains("మూవీ") || text.contains("థియేటర్") ||
                    text.contains("movie") || text.contains("netflix") || text.contains("prime") ||
                    text.contains("cinema") || text.contains("hotstar") || text.contains("game") ||
                    text.contains("ಸಿನಿಮಾ") || text.contains("फिल्म") -> ExpenseCategory.ENTERTAINMENT

            text.contains("మందులు") || text.contains("డాక్టర్") || text.contains("హాస్పిటల్") ||
                    text.contains("medicine") || text.contains("doctor") || text.contains("hospital") ||
                    text.contains("pharmacy") || text.contains("tablet") || text.contains("clinic") ||
                    text.contains("ಔಷಧ") || text.contains("दवा") || text.contains("इलाज") -> ExpenseCategory.HEALTH

            text.contains("స్కూల్") || text.contains("కాలేజ్") || text.contains("ఫీజు") ||
                    text.contains("school") || text.contains("college") || text.contains("fees") ||
                    text.contains("books") || text.contains("course") || text.contains("exam") ||
                    text.contains("ಶಾಲೆ") || text.contains("स्कूल") || text.contains("फीस") -> ExpenseCategory.EDUCATION

            else -> ExpenseCategory.OTHER
        }
    }

    private fun extractTitle(text: String, category: ExpenseCategory, isTelugu: Boolean): String {
        return ConversationalFuzzyExtractor.extractTitle(text, category, isTelugu)
    }

    /**
     * Executes single parsed item (Expense or Loan).
     */
    private suspend fun executeSingleItem(item: ParsedItem, isTelugu: Boolean): AgentResult {
        if (item.type == "LOAN_GIVEN") {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, item.dueDateDays)
            val loan = Loan(
                personName = item.personName,
                amount = item.amount,
                amountPaid = 0.0,
                dateGivenMillis = System.currentTimeMillis(),
                dueDateMillis = cal.timeInMillis,
                notes = item.notes,
                isSettled = false
            )
            val id = loanDao.insertLoan(loan)
            val savedLoan = loan.copy(id = id)

            val confirmMsg = if (isTelugu) {
                "✅ ${item.personName} కి ఇచ్చిన ₹${item.amount.toInt()} అప్పుగా నమోదు చేశాను. వారు ఎప్పుడు తిరిగి ఇస్తారు? (ఉదా: రేపు, 1 వారం, 1 నెలలో)"
            } else {
                "✅ Recorded ₹${item.amount.toInt()} lent to ${item.personName}. When will they give it back? (e.g., Tomorrow, 1 Week, 1 Month)"
            }
            val teSpoken = "${item.personName} కి ఇచ్చిన ${item.amount.toInt()} రూపాయలు అప్పుగా నమోదు చేశాను. వారు ఎప్పుడు తిరిగి ఇస్తారు?"
            val enSpoken = "Recorded ${item.amount.toInt()} rupees lent to ${item.personName}. When will they return it?"
            val loanSuggestions = if (isTelugu) {
                listOf("రేపు (Tomorrow)", "1 వారం (1 Week)", "15 రోజులు", "1 నెల (1 Month)")
            } else {
                listOf("Tomorrow", "1 Week", "15 Days", "1 Month")
            }

            return AgentResult(
                success = true,
                type = ResultType.LOAN_RECORDED,
                message = confirmMsg,
                spokenSummary = if (isTelugu) teSpoken else enSpoken,
                spokenSummaryTe = teSpoken,
                spokenSummaryEn = enSpoken,
                recordedLoan = savedLoan,
                recordedLoans = listOf(savedLoan),
                totalRecordedAmount = item.amount,
                suggestions = loanSuggestions
            )
        }

        // Expense
        val expense = Expense(
            title = item.title,
            amount = item.amount,
            category = item.category,
            timestamp = System.currentTimeMillis(),
            notes = item.notes,
            paymentMode = item.paymentMode,
            detectedLanguage = if (isTelugu) "te" else "en"
        )
        val id = expenseDao.insertExpense(expense)
        val savedExpense = expense.copy(id = id)
        merchantCache?.recordMerchantUsage(item.title, item.category, item.paymentMode)

        val health = calculateCurrentMonthHealth()
        // Never invent spending or a category limit when the persisted aggregate is unavailable.
        val catSpent = health.categorySpent[item.category] ?: 0.0
        val catBudget = health.categoryBudgets[item.category] ?: 0.0
        val percentUsed = if (catBudget > 0.0) (catSpent / catBudget) * 100.0 else 0.0
        val isOverspent = catBudget > 0.0 && percentUsed >= 80.0

        val warningMsg = if (isOverspent) {
            if (isTelugu) "⚠️ హెచ్చరిక! ${toTeluguSpokenCategory(item.category)} లో బడ్జెట్ ${percentUsed.toInt()}% పూర్తయింది (₹${catSpent.toInt()} / ₹${catBudget.toInt()})."
            else "⚠️ Overspending Alert! You've used ${percentUsed.toInt()}% of your ${item.category} budget (₹${catSpent.toInt()} / ₹${catBudget.toInt()})."
        } else null

        val confirmationMsg = if (isTelugu) {
            "✅ ₹${item.amount.toInt()} ${item.category} కోసం నమోదు చేశాను. రోజువారీ సురక్షిత వ్యయం: ₹${health.dailySafeToSpend}."
        } else {
            "✅ Recorded ₹${item.amount.toInt()} for ${item.category}. Daily safe-to-spend: ₹${health.dailySafeToSpend}."
        }

        val teCatName = toTeluguSpokenCategory(item.category)
        val teSpoken = if (isOverspent) {
            "హెచ్చరిక! $teCatName లో బడ్జెట్ పరిమితి దాటింది. ${item.amount.toInt()} రూపాయలు రాశాను."
        } else {
            "$teCatName కోసం ${item.amount.toInt()} రూపాయలు రాశాను. రోజువారీ సురక్షిత వ్యయం ${health.dailySafeToSpend} రూపాయలు."
        }
        val enSpoken = "Recorded ${item.amount.toInt()} rupees for ${item.category}. Daily allowance is ${health.dailySafeToSpend} rupees."

        return AgentResult(
            success = true,
            type = ResultType.EXPENSE_RECORDED,
            message = confirmationMsg,
            isOverspent = isOverspent,
            overspentWarning = warningMsg,
            recordedExpense = savedExpense,
            recordedExpenses = listOf(savedExpense),
            totalRecordedAmount = item.amount,
            spokenSummary = if (isTelugu) teSpoken else enSpoken,
            spokenSummaryTe = teSpoken,
            spokenSummaryEn = enSpoken
        )
    }

    /**
     * Executes batch multi-item input (e.g. "Vegetables 200, Petrol 500, Lunch 350").
     */
    private suspend fun executeBatchItems(items: List<ParsedItem>, isTelugu: Boolean): AgentResult {
        val database = appDatabase
        return if (database != null) {
            // A multi-item command is one logical operation: either every record is saved or none is.
            database.withTransaction { executeBatchItemsInternal(items, isTelugu) }
        } else {
            executeBatchItemsInternal(items, isTelugu)
        }
    }

    private suspend fun executeBatchItemsInternal(items: List<ParsedItem>, isTelugu: Boolean): AgentResult {
        val savedExpenses = mutableListOf<Expense>()
        val savedLoans = mutableListOf<Loan>()
        var totalAmount = 0.0

        val lines = mutableListOf<String>()

        for (item in items) {
            totalAmount += item.amount
            if (item.type == "LOAN_GIVEN") {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, item.dueDateDays)
                val loan = Loan(
                    personName = item.personName,
                    amount = item.amount,
                    amountPaid = 0.0,
                    dateGivenMillis = System.currentTimeMillis(),
                    dueDateMillis = cal.timeInMillis,
                    notes = item.notes,
                    isSettled = false
                )
                val id = loanDao.insertLoan(loan)
                val saved = loan.copy(id = id)
                savedLoans.add(saved)

                val line = if (isTelugu) "• ${item.personName} కి అప్పు: ₹${item.amount.toInt()}"
                else "• Lent to ${item.personName}: ₹${item.amount.toInt()}"
                lines.add(line)
            } else {
                val exp = Expense(
                    title = item.title,
                    amount = item.amount,
                    category = item.category,
                    timestamp = System.currentTimeMillis(),
                    notes = item.notes,
                    paymentMode = item.paymentMode,
                    detectedLanguage = if (isTelugu) "te" else "en"
                )
                val id = expenseDao.insertExpense(exp)
                val saved = exp.copy(id = id)
                savedExpenses.add(saved)
                merchantCache?.recordMerchantUsage(item.title, item.category, item.paymentMode)

                val catDisplay = if (isTelugu) toTeluguSpokenCategory(item.category) else item.category
                val line = if (isTelugu) "• ${item.title}: ₹${item.amount.toInt()} ($catDisplay)"
                else "• ${item.title}: ₹${item.amount.toInt()} ($catDisplay)"
                lines.add(line)
            }
        }

        val health = calculateCurrentMonthHealth()
        val header = if (isTelugu) {
            "⚡ జార్విస్ ఒకేసారి ${items.size} అంశాలను నమోదు చేశాడు (మొత్తం: ₹${totalAmount.toInt()}):"
        } else {
            "⚡ Jarvis recorded ${items.size} items (Total: ₹${totalAmount.toInt()}):"
        }

        val footer = if (isTelugu) {
            "మిగిలిన రోజులకు సురక్షిత రోజువారీ వ్యయం: ₹${health.dailySafeToSpend}."
        } else {
            "Safe-to-spend allowance for rest of month: ₹${health.dailySafeToSpend}/day."
        }

        val fullMessage = "$header\n${lines.joinToString("\n")}\n\n$footer"

        val teSpoken = "${items.size} ఖర్చులు నమోదు చేశాను. మొత్తం ${totalAmount.toInt()} రూపాయలు."
        val enSpoken = "Recorded ${items.size} items totaling ${totalAmount.toInt()} rupees."

        return AgentResult(
            success = true,
            type = ResultType.MULTI_EXPENSE_RECORDED,
            message = fullMessage,
            recordedExpenses = savedExpenses,
            recordedExpense = savedExpenses.firstOrNull(),
            recordedLoans = savedLoans,
            recordedLoan = savedLoans.firstOrNull(),
            totalRecordedAmount = totalAmount,
            spokenSummary = if (isTelugu) teSpoken else enSpoken,
            spokenSummaryTe = teSpoken,
            spokenSummaryEn = enSpoken
        )
    }

    private data class MonthHealth(
        val overallBudget: Double,
        val totalSpent: Double,
        val remainingBudget: Double,
        val dailySafeToSpend: Int,
        val categorySpent: Map<String, Double>,
        val categoryBudgets: Map<String, Double>
    )

    private suspend fun calculateCurrentMonthHealth(): MonthHealth {
        val calendar = Calendar.getInstance()
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemaining = (daysInMonth - dayOfMonth + 1).coerceAtLeast(1)

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfMonth = calendar.timeInMillis
        val now = System.currentTimeMillis()

        val categoryTotals = expenseDao.getCategoryTotalsInRange(startOfMonth, now)
        val spendMap = categoryTotals.associate { it.category to it.total }
        val totalSpent = categoryTotals.sumOf { it.total }

        val overallBudget = preferenceManager.getOverallBudget()
        val remaining = (overallBudget - totalSpent).coerceAtLeast(0.0)
        val dailySafe = (remaining / daysRemaining).toInt()

        val activeCategories = ExpenseCategory.entries.filter { it != ExpenseCategory.LENDING }
        val budgetMap = mutableMapOf<String, Double>()
        for (cat in activeCategories) {
            val b = budgetDao.getBudgetForCategory(cat.displayNameEn)
            budgetMap[cat.displayNameEn] = b?.monthlyLimit ?: 0.0
        }

        return MonthHealth(
            overallBudget = overallBudget,
            totalSpent = totalSpent,
            remainingBudget = remaining,
            dailySafeToSpend = dailySafe,
            categorySpent = spendMap,
            categoryBudgets = budgetMap
        )
    }

    private fun toTeluguSpokenCategory(catName: String): String {
        val lower = catName.lowercase(Locale.ROOT)
        return when {
            lower.contains("food") || lower.contains("grocer") -> "కూరగాయలు మరియు నిత్యావసరాలు"
            lower.contains("dining") || lower.contains("hotel") || lower.contains("snack") -> "హోటల్ భోజనం మరియు తినుబండారాలు"
            lower.contains("transport") || lower.contains("fuel") || lower.contains("petrol") -> "పెట్రోల్ మరియు ప్రయాణం"
            lower.contains("shopping") || lower.contains("cloth") -> "షాపింగ్ మరియు దుస్తులు"
            lower.contains("bill") || lower.contains("util") || lower.contains("recharge") -> "కరెంట్ మరియు బిల్లులు"
            lower.contains("entertainment") || lower.contains("movie") -> "సినిమా మరియు వినోదం"
            lower.contains("health") || lower.contains("medic") -> "మందులు మరియు వైద్యం"
            lower.contains("education") || lower.contains("school") || lower.contains("fee") -> "చదువు మరియు ఫీజులు"
            else -> "ఇతర అవసరాలు"
        }
    }

    suspend fun generateMonthlySavingsTips(isTelugu: Boolean): List<String> {
        val calendar = Calendar.getInstance()
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemaining = (daysInMonth - dayOfMonth + 1).coerceAtLeast(1)

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startOfMonth = calendar.timeInMillis
        val now = System.currentTimeMillis()

        val categoryTotals = expenseDao.getCategoryTotalsInRange(startOfMonth, now)
        val totalSpent = categoryTotals.sumOf { it.total }
        val overallBudget = preferenceManager.getOverallBudget()
        val remainingBudget = (overallBudget - totalSpent).coerceAtLeast(0.0)
        val dailySafeToSpend = (remainingBudget / daysRemaining).toInt()

        if (totalSpent <= 0) {
            return if (isTelugu) listOf(
                "💡 ఈ నెల ఇంకా ఖర్చులు ప్రారంభం కాలేదు. రోజువారీ ఖర్చులను జార్విస్ AI తో నమోదు చేయండి.",
                "💡 సురక్షిత రోజువారీ వ్యయం: ఈ నెలలో రోజుకు సుమారు ₹${(overallBudget / daysInMonth).toInt()} ఖర్చు చేసుకోవచ్చు.",
                "💡 మీ నెలవారీ ఆదాయంలో కనీసం 20% పొదుపు ఖాతాలో ముందుగానే దాచుకోండి."
            ) else listOf(
                "💡 No expenses recorded yet this month. Start tracking daily with Jarvis AI.",
                "💡 Safe Daily Allowance: You can spend ~₹${(overallBudget / daysInMonth).toInt()}/day to stay within budget.",
                "💡 Follow the 50/30/20 rule: 50% Needs, 30% Wants, and 20% Savings."
            )
        }

        val adherence = ((totalSpent / overallBudget) * 100).toInt()
        val topCategory = categoryTotals.maxByOrNull { it.total }

        val tips = mutableListOf<String>()

        if (adherence >= 80) {
            tips.add(
                if (isTelugu) "⚠️ హెచ్చరిక: మీ నెలవారీ బడ్జెట్‌లో $adherence% ఇప్పటికే ఖర్చు అయింది! మిగిలిన రోజుల్లో రోజుకు ₹$dailySafeToSpend మించకుండా ఉండండి."
                else "⚠️ Budget Alert: You have used $adherence% of your monthly budget! Try to limit daily spending to ₹$dailySafeToSpend."
            )
        } else {
            tips.add(
                if (isTelugu) "✅ అద్భుతం! బడ్జెట్ నియంత్రణలో ఉంది ($adherence% వినియోగం). మిగిలిన రోజులకు సురక్షిత రోజువారీ వ్యయం: ₹$dailySafeToSpend."
                else "✅ On Track: Budget adherence is healthy at $adherence%. Safe allowance for remaining days: ₹$dailySafeToSpend/day."
            )
        }

        topCategory?.let { top ->
            val catTe = toTeluguSpokenCategory(top.category)
            tips.add(
                if (isTelugu) "📊 అత్యధిక వ్యయం: ఈ నెలలో $catTe కోసం ₹${top.total.toInt()} ఖర్చు చేశారు."
                else "📊 Top Spend Category: You spent ₹${top.total.toInt()} on ${top.category} this month."
            )
        }

        tips.add(
            if (isTelugu) "💡 జార్విస్ తో ఒకేసారి ఎక్కువ ఖర్చులు లేదా అప్పులు మాట్లాడి నమోదు చేసుకోవచ్చు."
            else "💡 Tip: Speak or type multiple expenses in one breath to Jarvis."
        )

        return tips
    }

    private suspend fun handleDailyBriefing(isTelugu: Boolean): AgentResult {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        val todayExps = expenseDao.getExpensesInRange(startOfDay, now)
        val todaySpent = todayExps.sumOf { it.amount }
        val count = todayExps.size

        val health = calculateCurrentMonthHealth()
        val budgetLeft = (health.overallBudget - health.totalSpent).coerceAtLeast(0.0)

        val pendingLoans = loanDao.getAllLoans().filter { !it.isSettled }
        val totalPendingLent = pendingLoans.sumOf { it.amount - it.amountPaid }

        val greetingText = if (isTelugu) {
            buildString {
                append("నమస్కారం! నేను మీ జార్విస్ AI అసిస్టెంట్.\n")
                if (count == 0) {
                    append("ఈ రోజు ఇంకా ఎలాంటి ఖర్చులు నమోదు చేయలేదు. ")
                } else {
                    append("ఈ రోజు $count లావాదేవీలలో మొత్తం ₹${todaySpent.toInt()} ఖర్చు చేశారు. ")
                }
                append("ఈ నెల మిగిలిన బడ్జెట్: ₹${budgetLeft.toInt()} (రోజువారీ సురక్షిత వ్యయం: ₹${health.dailySafeToSpend}).")
                if (totalPendingLent > 0) {
                    append("\n⚠️ ఇతరుల వద్ద వసూలు కావాల్సిన బాకీలు: ₹${totalPendingLent.toInt()} (${pendingLoans.size} వ్యక్తులు).")
                }
            }
        } else {
            buildString {
                append("Hello! Jarvis AI at your service.\n")
                if (count == 0) {
                    append("No expenses logged yet today. ")
                } else {
                    append("You've spent ₹${todaySpent.toInt()} across $count item${if (count > 1) "s" else ""} today. ")
                }
                append("Remaining monthly budget: ₹${budgetLeft.toInt()} (Daily allowance: ₹${health.dailySafeToSpend}).")
                if (totalPendingLent > 0) {
                    append("\n⚠️ Pending loans to collect: ₹${totalPendingLent.toInt()} from ${pendingLoans.size} people.")
                }
            }
        }

        val spokenTe = if (count == 0) "నమస్కారం! ఈ రోజు ఇంకా ఖర్చులు లేవు. మిగిలిన బడ్జెట్ ${budgetLeft.toInt()} రూపాయలు."
        else "ఈ రోజు ఖర్చులు ${todaySpent.toInt()} రూపాయలు. మిగిలిన బడ్జెట్ ${budgetLeft.toInt()} రూపాయలు."
        val spokenEn = if (count == 0) "Hello! No expenses logged today. Remaining budget is ${budgetLeft.toInt()} rupees."
        else "You spent ${todaySpent.toInt()} rupees today. Remaining monthly budget is ${budgetLeft.toInt()} rupees."

        return AgentResult(
            success = true,
            type = ResultType.INSIGHTS,
            message = greetingText,
            spokenSummary = if (isTelugu) spokenTe else spokenEn,
            spokenSummaryTe = spokenTe,
            spokenSummaryEn = spokenEn,
            suggestions = listOf("Add expense", "Remaining budget", "Pending loans")
        )
    }

    private suspend fun handleQuerySpendingToday(isTelugu: Boolean): AgentResult {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        val todayExps = expenseDao.getExpensesInRange(startOfDay, now)
        val todaySpent = todayExps.sumOf { it.amount }
        val count = todayExps.size

        if (count == 0) {
            val msg = if (isTelugu) "ఈ రోజు మీరు ఇంకా ఎలాంటి ఖర్చులు నమోదు చేయలేదు." else "You haven't recorded any expenses today."
            return AgentResult(
                success = true,
                type = ResultType.INSIGHTS,
                message = msg,
                spokenSummary = msg,
                spokenSummaryTe = "ఈ రోజు ఎలాంటి ఖర్చులు లేవు.",
                spokenSummaryEn = "No expenses logged today."
            )
        }

        val topCategory = todayExps.groupBy { it.category }
            .maxByOrNull { entry -> entry.value.sumOf { it.amount } }

        val msg = if (isTelugu) {
            val topName = toTeluguSpokenCategory(topCategory?.key ?: "")
            "ఈ రోజు మీరు $count లావాదేవీలలో మొత్తం ₹${todaySpent.toInt()} ఖర్చు చేశారు. అత్యధిక ఖర్చు: $topName (₹${topCategory?.value?.sumOf { it.amount }?.toInt() ?: 0})."
        } else {
            "Today you spent ₹${todaySpent.toInt()} across $count transaction${if (count > 1) "s" else ""}. Highest category: ${topCategory?.key ?: "Other"} (₹${topCategory?.value?.sumOf { it.amount }?.toInt() ?: 0})."
        }

        val spokenTe = "ఈ రోజు మొత్తం ${todaySpent.toInt()} రూపాయలు ఖర్చు చేశారు."
        val spokenEn = "You spent ${todaySpent.toInt()} rupees today."

        return AgentResult(
            success = true,
            type = ResultType.INSIGHTS,
            message = msg,
            spokenSummary = if (isTelugu) spokenTe else spokenEn,
            spokenSummaryTe = spokenTe,
            spokenSummaryEn = spokenEn
        )
    }

    private suspend fun handleQuerySpendingMonth(isTelugu: Boolean): AgentResult {
        val health = calculateCurrentMonthHealth()
        val totalSpent = health.totalSpent
        val budget = health.overallBudget
        val percent = if (budget > 0) (totalSpent / budget * 100).toInt() else 0

        val topCat = health.categorySpent.maxByOrNull { it.value }

        val msg = if (isTelugu) {
            val topName = toTeluguSpokenCategory(topCat?.key ?: "")
            "ఈ నెల మొత్తం ఖర్చు: ₹${totalSpent.toInt()} (బడ్జెట్‌లో $percent% పూర్తయింది). అత్యధిక వ్యయం: $topName (₹${topCat?.value?.toInt() ?: 0})."
        } else {
            "This month's total spending is ₹${totalSpent.toInt()} ($percent% of your ₹${budget.toInt()} budget used). Top category: ${topCat?.key ?: "Other"} (₹${topCat?.value?.toInt() ?: 0})."
        }

        val spokenTe = "ఈ నెల మొత్తం ${totalSpent.toInt()} రూపాయలు ఖర్చు చేశారు."
        val spokenEn = "This month's total spending is ${totalSpent.toInt()} rupees."

        return AgentResult(
            success = true,
            type = ResultType.INSIGHTS,
            message = msg,
            spokenSummary = if (isTelugu) spokenTe else spokenEn,
            spokenSummaryTe = spokenTe,
            spokenSummaryEn = spokenEn
        )
    }

    private suspend fun handleQueryBudgetRemaining(isTelugu: Boolean): AgentResult {
        val health = calculateCurrentMonthHealth()
        val budget = health.overallBudget
        val spent = health.totalSpent
        val remaining = (budget - spent).coerceAtLeast(0.0)
        val dailySafe = health.dailySafeToSpend

        val msg = if (isTelugu) {
            "మీ మొత్తం నెల బడ్జెట్: ₹${budget.toInt()}.\nఖర్చు అయింది: ₹${spent.toInt()}.\nమిగిలిన మొత్తం: ₹${remaining.toInt()}.\nరాబోయే రోజులకు సురక్షిత రోజువారీ వ్యయం: ₹$dailySafe."
        } else {
            "Monthly Budget: ₹${budget.toInt()}.\nTotal Spent: ₹${spent.toInt()}.\nRemaining Balance: ₹${remaining.toInt()}.\nSafe daily allowance for remaining days: ₹$dailySafe/day."
        }

        val spokenTe = "మిగిలిన బడ్జెట్ ${remaining.toInt()} రూపాయలు. రోజుకు ${dailySafe} రూపాయలు ఖర్చు చేసుకోవచ్చు."
        val spokenEn = "Remaining budget is ${remaining.toInt()} rupees. Daily safe allowance is $dailySafe rupees."

        return AgentResult(
            success = true,
            type = ResultType.INSIGHTS,
            message = msg,
            spokenSummary = if (isTelugu) spokenTe else spokenEn,
            spokenSummaryTe = spokenTe,
            spokenSummaryEn = spokenEn
        )
    }

    private suspend fun handleQueryPendingLoans(isTelugu: Boolean): AgentResult {
        val pending = loanDao.getAllLoans().filter { !it.isSettled }
        if (pending.isEmpty()) {
            val msg = if (isTelugu) "ప్రస్తుతం ఎలాంటి బాకీలు లేవు. ఎవరూ మీకు డబ్బు బాకీ లేరు." else "No pending loans found. Nobody owes you money currently."
            return AgentResult(
                success = true,
                type = ResultType.INSIGHTS,
                message = msg,
                spokenSummary = msg
            )
        }

        val totalPending = pending.sumOf { it.amount - it.amountPaid }
        val namesList = pending.take(5).joinToString(", ") { "${it.personName} (₹${(it.amount - it.amountPaid).toInt()})" }

        val msg = if (isTelugu) {
            "మీ వద్ద మొత్తం ${pending.size} పెండింగ్ బాకీలు ఉన్నాయి. వసూలు కావాల్సిన మొత్తం: ₹${totalPending.toInt()}.\nముఖ్యమైనవి: $namesList."
        } else {
            "You have ${pending.size} pending loan${if (pending.size > 1) "s" else ""} totaling ₹${totalPending.toInt()} to collect.\nDetails: $namesList."
        }

        val spokenTe = "మొత్తం ${totalPending.toInt()} రూపాయల బాకీలు వసూలు కావాలి."
        val spokenEn = "Total ${totalPending.toInt()} rupees in pending loans to collect."

        return AgentResult(
            success = true,
            type = ResultType.INSIGHTS,
            message = msg,
            spokenSummary = if (isTelugu) spokenTe else spokenEn,
            spokenSummaryTe = spokenTe,
            spokenSummaryEn = spokenEn
        )
    }

    private fun isHistoricalAnalysisRequest(input: String): Boolean {
        val normalized = input.lowercase(Locale.ROOT)
        val explicitRequests = listOf(
            "last year", "past year", "last 12 months", "past 12 months",
            "spending profile", "expense history", "spending history",
            "yearly spending", "annual spending", "analyze my expenses",
            "analyse my expenses", "analyze my spending", "analyse my spending",
            "analyze expenses", "analyse expenses", "12-month spending",
            "గత సంవత్సరం", "గత ఏడాది ఖర్చులు", "గత 12 నెలలు",
            "ఖర్చుల విశ్లేషణ", "నా ఖర్చులను విశ్లేషించు", "నా ఖర్చుల వివరాలు"
        )
        return explicitRequests.any(normalized::contains)
    }

    private suspend fun handleHistoricalSpendingAnalysis(isTelugu: Boolean): AgentResult {
        val endMillis = System.currentTimeMillis()
        val startCalendar = Calendar.getInstance().apply {
            timeInMillis = endMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -11)
        }
        val expenses = expenseDao.getExpensesInRange(startCalendar.timeInMillis, endMillis)
        val profile = SpendingProfileAnalyzer.analyze(
            expenses = expenses,
            startMillis = startCalendar.timeInMillis,
            endMillis = endMillis,
            calendarMonthCount = 12
        )
        val message = profile.toMessage(isTelugu)
        val spokenSummary = if (isTelugu) {
            "గత 12 నెలల ఖర్చుల విశ్లేషణ సిద్ధంగా ఉంది. మొత్తం ఖర్చు ${profile.totalSpent.toInt()} రూపాయలు."
        } else {
            "Your 12-month spending profile is ready. Total recorded spending is ${profile.totalSpent.toInt()} rupees."
        }
        return AgentResult(
            success = true,
            type = ResultType.INSIGHTS,
            message = message,
            spokenSummary = spokenSummary,
            spokenSummaryTe = "గత 12 నెలల ఖర్చుల విశ్లేషణ సిద్ధంగా ఉంది.",
            spokenSummaryEn = "Your 12-month spending profile is ready."
        )
    }

    private suspend fun handleQueryCategorySpending(input: String, isTelugu: Boolean): AgentResult {
        val cat = determineCategory(input.lowercase(Locale.ROOT))
        val health = calculateCurrentMonthHealth()
        val catSpent = health.categorySpent[cat.displayNameEn] ?: 0.0
        val catBudget = health.categoryBudgets[cat.displayNameEn] ?: 5000.0

        val msg = if (isTelugu) {
            val teName = toTeluguSpokenCategory(cat.displayNameEn)
            "ఈ నెల $teName కోసం మీరు ₹${catSpent.toInt()} ఖర్చు చేశారు (బడ్జెట్ పరిమితి: ₹${catBudget.toInt()})."
        } else {
            "This month you spent ₹${catSpent.toInt()} on ${cat.displayNameEn} (Budget: ₹${catBudget.toInt()})."
        }

        val spokenTe = "${toTeluguSpokenCategory(cat.displayNameEn)} కోసం ${catSpent.toInt()} రూపాయలు ఖర్చు చేశారు."
        val spokenEn = "You spent ${catSpent.toInt()} rupees on ${cat.displayNameEn} this month."

        return AgentResult(
            success = true,
            type = ResultType.INSIGHTS,
            message = msg,
            spokenSummary = if (isTelugu) spokenTe else spokenEn,
            spokenSummaryTe = spokenTe,
            spokenSummaryEn = spokenEn
        )
    }

    private suspend fun handleSavingsAdvice(isTelugu: Boolean): AgentResult {
        val tips = generateMonthlySavingsTips(isTelugu)
        val selectedTip = tips.firstOrNull() ?: if (isTelugu) "రోజువారీ ఖర్చులను జార్విస్‌తో క్రమం తప్పకుండా నమోదు చేయండి." else "Track your daily expenses regularly with Jarvis."

        val msg = if (isTelugu) "💡 జార్విస్ పొదుపు సలహా:\n$selectedTip" else "💡 Jarvis Savings Advice:\n$selectedTip"
        val spokenTe = "పొదుపు సలహా: $selectedTip"
        val spokenEn = "Savings tip: $selectedTip"

        return AgentResult(
            success = true,
            type = ResultType.INSIGHTS,
            message = msg,
            spokenSummary = if (isTelugu) spokenTe else spokenEn,
            spokenSummaryTe = spokenTe,
            spokenSummaryEn = spokenEn
        )
    }

    private suspend fun handleSetBudget(input: String, isTelugu: Boolean): AgentResult {
        val amount = extractAmount(input)
        if (amount <= 0.0) {
            val err = if (isTelugu) "బడ్జెట్ మొత్తం స్పష్టంగా చెప్పండి (ఉదా: 'నెల బడ్జెట్ 30000 గా పెట్టు')."
            else "Please specify a budget amount (e.g. 'Set budget to 30000')."
            return AgentResult(success = false, type = ResultType.ERROR, message = err, spokenSummary = err)
        }

        preferenceManager.setOverallBudget(amount)
        val health = calculateCurrentMonthHealth()
        val msg = if (isTelugu) {
            "✅ నెలవారీ బడ్జెట్‌ను ₹${amount.toInt()} గా సెట్ చేశాను. రాబోయే రోజులకు సురక్షిత రోజువారీ వ్యయం: ₹${health.dailySafeToSpend}."
        } else {
            "✅ Updated monthly budget to ₹${amount.toInt()}. Safe-to-spend allowance is now ₹${health.dailySafeToSpend}/day."
        }
        val spokenTe = "నెలవారీ బడ్జెట్‌ను ${amount.toInt()} రూపాయలుగా సెట్ చేశాను."
        val spokenEn = "Updated monthly budget to ${amount.toInt()} rupees."

        return AgentResult(
            success = true,
            type = ResultType.INSIGHTS,
            message = msg,
            spokenSummary = if (isTelugu) spokenTe else spokenEn,
            spokenSummaryTe = spokenTe,
            spokenSummaryEn = spokenEn
        )
    }

    private suspend fun handleSetLoanDueDate(text: String, isTelugu: Boolean): AgentResult {
        val latestLoan = loanDao.getLatestPendingLoan() ?: loanDao.getAllLoans().firstOrNull { !it.isSettled }
        if (latestLoan == null) {
            val noLoanMsg = if (isTelugu) {
                "గడువు తేదీని సెట్ చేయడానికి ఎలాంటి పెండింగ్ అప్పులు కనుగొనబడలేదు. కొత్త అప్పు నమోదు చేయడానికి: 'రమేష్ కి 1000 ఇచ్చాను' అని చెప్పండి."
            } else {
                "No pending loan found to update due date. To record a new loan, say: 'Gave 1000 to Suresh'."
            }
            return AgentResult(
                success = false,
                type = ResultType.ERROR,
                message = noLoanMsg,
                spokenSummary = noLoanMsg
            )
        }

        val days = ConversationalFuzzyExtractor.parseDueDaysFromText(text)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        val newDueDateMillis = cal.timeInMillis

        val updatedLoan = latestLoan.copy(dueDateMillis = newDueDateMillis)
        loanDao.updateLoan(updatedLoan)

        val dateFormatted = SimpleDateFormat("dd MMMM yyyy", if (isTelugu) Locale("te", "IN") else Locale.ENGLISH)
            .format(Date(newDueDateMillis))

        val confirmMsg = if (isTelugu) {
            "📅 ${latestLoan.personName} కి ఇచ్చిన ₹${latestLoan.amount.toInt()} అప్పుకు తిరిగి చెల్లించే గడువు తేదీని **$dateFormatted** గా సెట్ చేశాను."
        } else {
            "📅 Set repayment due date for ₹${latestLoan.amount.toInt()} lent to ${latestLoan.personName} to **$dateFormatted**."
        }

        val spokenTe = "${latestLoan.personName} అప్పుకు గడువు తేదీని సెట్ చేశాను."
        val spokenEn = "Updated repayment due date for ${latestLoan.personName}."

        return AgentResult(
            success = true,
            type = ResultType.LOAN_RECORDED,
            message = confirmMsg,
            spokenSummary = if (isTelugu) spokenTe else spokenEn,
            spokenSummaryTe = spokenTe,
            spokenSummaryEn = spokenEn,
            recordedLoan = updatedLoan,
            recordedLoans = listOf(updatedLoan),
            totalRecordedAmount = latestLoan.amount
        )
    }

    private fun handleLoanInquiryWithoutAmount(isTelugu: Boolean): AgentResult {
        val msg = if (isTelugu) {
            "ఎవరికి ఎంత అప్పు ఇచ్చారు మరియు వారు ఎప్పుడు తిరిగి ఇస్తారు? దయచేసి వివరాలు చెప్పండి. (ఉదా: 'రమేష్ కి 1000 ఇచ్చాను, 1 వారంలో ఇస్తాడు')"
        } else {
            "Who did you lend money to, what amount, and when will they return it? (e.g., 'Gave 1000 to Suresh, will return in 1 week')"
        }
        val spoken = if (isTelugu) {
            "ఎవరికి ఎంత అప్పు ఇచ్చారు? వివరాలు చెప్పండి."
        } else {
            "Who did you lend money to and what amount?"
        }
        val suggestions = if (isTelugu) {
            listOf("రమేష్ కి 500 ఇచ్చాను", "సురేష్ కి 1000 ఇచ్చాను", "స్నేహితుడికి 2000 ఇచ్చాను రేపు ఇస్తాడు")
        } else {
            listOf("Gave 500 to Ramesh", "Gave 1000 to Suresh", "Lent 2000 to friend return tomorrow")
        }
        return AgentResult(
            success = true,
            type = ResultType.INSIGHTS,
            message = msg,
            spokenSummary = spoken,
            spokenSummaryTe = if (isTelugu) spoken else null,
            spokenSummaryEn = if (!isTelugu) spoken else null,
            suggestions = suggestions
        )
    }

    private suspend fun handleLoanRepayment(text: String, isTelugu: Boolean): AgentResult {
        val lower = text.lowercase(Locale.ROOT)
        val amount = extractAmount(lower)

        var person = ConversationalFuzzyExtractor.extractPerson(text, isTelugu)
        if (person == "Borrower" || person == "Friend" || person == "స్నేహితుడు") {
            val leadingMatch = Regex("""^([A-Za-z\u0C00-\u0C7F]+)""").find(text.trim())
            if (leadingMatch != null) {
                val candidate = leadingMatch.groupValues[1]
                if (candidate !in listOf("i", "he", "she", "they", "నా", "అతను", "ఆమె", "వారు", "అప్పు", "బాకీ")) {
                    person = candidate
                }
            }
        }

        val allLoans = loanDao.getAllLoans().filter { !it.isSettled }
        if (allLoans.isEmpty()) {
            val noLoanMsg = if (isTelugu) {
                "ప్రస్తుతం ఎలాంటి పెండింగ్ అప్పులు లేవు. ఎవరూ మీకు డబ్బు బాకీ లేరు."
            } else {
                "No pending loans found to settle."
            }
            return AgentResult(
                success = true,
                type = ResultType.INSIGHTS,
                message = noLoanMsg,
                spokenSummary = noLoanMsg
            )
        }

        val targetLoan = if (person.isNotBlank() && person !in listOf("Borrower", "Friend", "స్నేహితుడు")) {
            allLoans.firstOrNull {
                it.personName.contains(person, ignoreCase = true) || person.contains(it.personName, ignoreCase = true)
            }
        } else if (allLoans.size == 1) {
            allLoans.first()
        } else {
            null
        }

        if (targetLoan == null) {
            val activeBorrowers = allLoans.joinToString { it.personName }
            val clarifyMsg = if (isTelugu) {
                if (person.isNotBlank() && person !in listOf("Borrower", "Friend", "స్నేహితుడు")) {
                    "ఖాతాలో '${person}' పేరుతో ఎలాంటి పెండింగ్ అప్పు కనుగొనబడలేదు. ఎవరి అప్పును చెల్లించాలో దయచేసి స్పష్టం చేయండి (ప్రస్తుతం బాకీ ఉన్నవారు: $activeBorrowers)."
                } else {
                    "ఎవరి అప్పు జమ చేయాలో దయచేసి పేరు చెప్పండి (ఉదా: రమేష్ 500 తిరిగి ఇచ్చాడు). ప్రస్తుతం బాకీ ఉన్నవారు: $activeBorrowers."
                }
            } else {
                if (person.isNotBlank() && person !in listOf("Borrower", "Friend", "స్నేహితుడు")) {
                    "No active loan found for '$person'. Please specify who repaid (Active borrowers: $activeBorrowers)."
                } else {
                    "Multiple pending loans found. Please specify who repaid (Active borrowers: $activeBorrowers)."
                }
            }
            val suggestions = allLoans.take(3).map {
                val amtInt = if (amount > 0.0) amount.toInt() else (it.amount - it.amountPaid).toInt()
                if (isTelugu) "${it.personName} $amtInt తిరిగి ఇచ్చాడు"
                else "${it.personName} returned $amtInt"
            }
            return AgentResult(
                success = false,
                type = ResultType.ERROR,
                message = clarifyMsg,
                spokenSummary = clarifyMsg,
                suggestions = suggestions
            )
        }

        val remaining = targetLoan.amount - targetLoan.amountPaid
        val repayAmount = if (amount > 0.0) amount.coerceAtMost(remaining) else remaining
        val newPaid = (targetLoan.amountPaid + repayAmount).coerceAtMost(targetLoan.amount)
        val isSettledNow = newPaid >= targetLoan.amount

        val updated = targetLoan.copy(
            amountPaid = newPaid,
            isSettled = isSettledNow
        )
        loanDao.updateLoan(updated)

        val confirmMsg = if (isTelugu) {
            if (isSettledNow) {
                "🎉 ${targetLoan.personName} నుండి ₹${repayAmount.toInt()} జమ చేయబడింది. అప్పు పూర్తిగా తీరిపోయింది (Settled)!"
            } else {
                "✅ ${targetLoan.personName} నుండి ₹${repayAmount.toInt()} జమ చేయబడింది. ఇంకా రావాల్సిన మిగిలిన బాకీ: ₹${(targetLoan.amount - newPaid).toInt()}."
            }
        } else {
            if (isSettledNow) {
                "🎉 Recorded ₹${repayAmount.toInt()} repayment from ${targetLoan.personName}. Loan is fully settled!"
            } else {
                "✅ Recorded ₹${repayAmount.toInt()} repayment from ${targetLoan.personName}. Remaining balance: ₹${(targetLoan.amount - newPaid).toInt()}."
            }
        }

        val spokenTe = if (isSettledNow) {
            "${targetLoan.personName} అప్పు పూర్తిగా తీరిపోయింది."
        } else {
            "${targetLoan.personName} నుండి ${repayAmount.toInt()} రూపాయలు జమ చేశాను."
        }
        val spokenEn = if (isSettledNow) {
            "Loan for ${targetLoan.personName} is fully settled."
        } else {
            "Recorded ${repayAmount.toInt()} rupees repayment from ${targetLoan.personName}."
        }

        return AgentResult(
            success = true,
            type = ResultType.LOAN_RECORDED,
            message = confirmMsg,
            spokenSummary = if (isTelugu) spokenTe else spokenEn,
            spokenSummaryTe = spokenTe,
            spokenSummaryEn = spokenEn,
            recordedLoan = updated,
            recordedLoans = listOf(updated),
            totalRecordedAmount = repayAmount
        )
    }
}
