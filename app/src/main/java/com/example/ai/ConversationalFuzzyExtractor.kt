package com.example.ai

import com.example.data.model.ExpenseCategory
import java.util.Locale

/**
 * ConversationalFuzzyExtractor:
 * High-performance, on-device lightweight conversational NLP & fuzzy entity extractor.
 * Specially designed for Android 8.1 (API 27) and resource-constrained MediaTek Helio P23 chips.
 *
 * Capabilities:
 * 1. Multilingual Slang & Phrase Classification:
 *    - Telugu ("ఖర్చు", "కొన్నాను", "అప్పు ఇచ్చాను", "రద్దు చేయి", "మార్చు", "రూపాయలు")
 *    - Hindi ("kharcha", "samosa khaya", "udhar diya", "pichla hatao", "badal do")
 *    - Kannada ("kharchu", "kotte", "thagonde", "raddhu madi")
 *    - English ("spent on dinner", "lent 3000 to Suresh", "undo last transaction", "change last expense to 400")
 * 2. Conversational Intent Identification (Undo, Correction, Loan, Multi-Item).
 * 3. Robust Entity Parsing: Title normalization, Indian number format & Telugu numeral normalization,
 *    phonetic category matching, and honorific stripping.
 */
object ConversationalFuzzyExtractor {

    enum class DetectedIntent {
        UNDO_LAST,
        MODIFY_LAST,
        SET_BUDGET,
        LOAN_GIVEN,
        LOAN_REPAYMENT,
        SET_LOAN_DUE_DATE,
        QUERY_SPENDING_TODAY,
        QUERY_SPENDING_MONTH,
        QUERY_BUDGET_REMAINING,
        QUERY_CATEGORY_SPENDING,
        QUERY_PENDING_LOANS,
        DAILY_BRIEFING,
        SAVINGS_ADVICE,
        EXPENSE_TRANSACTIONS,
        UNKNOWN
    }

    data class ExtractedClause(
        val rawClause: String,
        val title: String,
        val amount: Double,
        val category: ExpenseCategory,
        val isLoan: Boolean = false,
        val personName: String = "",
        val paymentMode: String = "Unknown",
        val dueDateDays: Int = 7
    )

    data class ModificationRequest(
        val targetCategory: String?,
        val newAmount: Double
    )

    // --- Intent Detection ---

    fun classifyIntent(text: String): DetectedIntent {
        val lower = text.lowercase(Locale.ROOT).trim()

        // 1. Undo Commands
        val undoPatterns = listOf(
            "undo last", "delete last", "remove last", "cancel last", "undo expense",
            "రద్దు చేయి", "డిలీట్ చేయి", "చివరి ఖర్చు రద్దు", "చివరి లావాదేవీ రద్దు", "చివరిది తీసేయి",
            "pichla kharcha hatao", "pichla delete", "undo karo", "aakhri kharcha cancel",
            "kadeya kharchu raddhu"
        )
        if (undoPatterns.any { lower.contains(it) }) {
            return DetectedIntent.UNDO_LAST
        }

        // 2. Correction / Modification Commands
        val modifyPatterns = listOf(
            "change previous", "change last", "update previous", "update last", "correct previous",
            "మార్చు", "సవరించు", "ఖర్చు మార్చు", "చివరి ఖర్చును",
            "pichla badlo", "kharcha badlo", "update karo",
            "badalisu", "thiddu"
        )
        if (modifyPatterns.any { lower.contains(it) } && extractNumber(lower) > 0.0) {
            return DetectedIntent.MODIFY_LAST
        }

        // 2.2 Set Monthly Budget command (e.g. "set budget to 25000", "నెల బడ్జెట్ 30000 గా పెట్టు")
        val setBudgetPatterns = listOf(
            "set budget", "update budget", "change budget", "monthly budget", "fix budget",
            "నెల బడ్జెట్", "బడ్జెట్ సెట్", "బడ్జెట్ పెట్టు",
            "budget set karo", "budget badlo"
        )
        val budgetNum = extractNumber(lower)
        if (budgetNum >= 1000.0 && setBudgetPatterns.any { lower.contains(it) }) {
            return DetectedIntent.SET_BUDGET
        }

        // 2.5 Loan Repayment Due Date Setting ("Tomorrow", "1 week", "రేపు", "వచ్చే వారం", "15 days", "1 month")
        if (isDueDatePhrase(lower)) {
            val num = extractNumber(lower)
            if (num <= 0.0 || num in listOf(1.0, 2.0, 3.0, 7.0, 10.0, 14.0, 15.0, 30.0, 60.0)) {
                return DetectedIntent.SET_LOAN_DUE_DATE
            }
        }

        // 3. Lending Khata Commands
        val loanGivenPatterns = listOf(
            "lent", "gave loan", "given to", "loan to", "borrowed by",
            "i gave this", "gave this", "i gave", "gave",
            "అప్పు ఇచ్చాను", "చేబదులు ఇచ్చాను", "బాకీ ఇచ్చాను", "ఇచ్చాను", "అప్పుగా", "చేబదులుగా", "ఇచ్చా", "ఇచ్చాము",
            "udhar diya", "karz diya", "paise diye",
            "saala kotte", "udhari kotte"
        )
        val loanRepayPatterns = listOf(
            "returned loan", "repaid", "received back", "paid back", "returned", "gave back",
            "settled loan", "settled in full", "fully settled", "fully repaid", "paid in full",
            "cleared the loan", "loan settled",
            "అప్పు తిరిగి ఇచ్చారు", "బాకీ చెల్లించారు", "తిరిగి వచ్చింది", "తిరిగి ఇచ్చాడు", "తిరిగి ఇచ్చింది",
            "ఇచ్చేశాడు", "చెల్లించాడు", "వాపస్ ఇచ్చాడు", "బాకీ తీరింది", "అప్పు తీరింది",
            "udhar wapas mila", "karz chukaya", "wapas diya"
        )

        if (loanRepayPatterns.any { lower.contains(it) }) return DetectedIntent.LOAN_REPAYMENT
        if (loanGivenPatterns.any { lower.contains(it) }) return DetectedIntent.LOAN_GIVEN

        // 4. Greetings / Daily Briefing
        val greetingPatterns = listOf(
            "hi jarvis", "hello jarvis", "hey jarvis", "hi", "hello", "good morning", "good evening",
            "హలో", "నమస్కారం", "నమస్తే", "నమస్తే జార్విస్", "హలో జార్విస్", "హాయ్",
            "namaste", "kem cho", "shubh prabhat"
        )
        if (extractNumber(lower) <= 0.0 && greetingPatterns.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }) {
            return DetectedIntent.DAILY_BRIEFING
        }

        // 5. Inquiries: Spending Today
        val todaySpendingPatterns = listOf(
            "spend today", "spent today", "today expense", "today's expense", "today total", "today's total", "how much today",
            "ఈ రోజు ఖర్చు", "ఈరోజు ఖర్చు", "ఈ రోజు ఎంత", "ఈరోజు ఎంత", "ఈ రోజు మొత్తం", "నేడు ఖర్చు",
            "aaj kitna", "aaj ka kharcha", "aaj kharch"
        )
        if (extractNumber(lower) <= 0.0 && todaySpendingPatterns.any { lower.contains(it) }) {
            return DetectedIntent.QUERY_SPENDING_TODAY
        }

        // 6. Inquiries: Spending Month
        val monthSpendingPatterns = listOf(
            "spend this month", "spent this month", "this month expense", "this month total", "monthly expense", "month total",
            "how much this month", "total spending", "total expenses",
            "ఈ నెల ఖర్చు", "ఈ నెల ఎంత", "ఈ నెల మొత్తం", "మొత్తం ఖర్చు ఎంత", "ఈ నెల్లో ఎంత",
            "is mahine ka kharcha", "mahine ka kharch"
        )
        if (extractNumber(lower) <= 0.0 && monthSpendingPatterns.any { lower.contains(it) }) {
            return DetectedIntent.QUERY_SPENDING_MONTH
        }

        // 7. Inquiries: Budget Remaining
        val budgetPatterns = listOf(
            "budget left", "remaining budget", "balance budget", "how much budget", "my budget", "budget balance",
            "బడ్జెట్ ఎంత మిగిలింది", "మిగిలిన బడ్జెట్", "నా బడ్జెట్ ఎంత", "బడ్జెట్ బ్యాలెన్స్", "ఇంకా ఎంత ఖర్చు",
            "kitna budget", "budget kitna bacha"
        )
        if (extractNumber(lower) <= 0.0 && budgetPatterns.any { lower.contains(it) }) {
            return DetectedIntent.QUERY_BUDGET_REMAINING
        }

        // 8. Inquiries: Pending Loans / Khata
        val pendingLoansPatterns = listOf(
            "who owes me", "pending loans", "pending loan", "khata balance", "lending balance", "money lent", "loan list",
            "ఎవరు బాకీ", "బాకీలు ఎంత", "అప్పులు ఎంత రావాలి", "ఎవరికి ఎంత ఇచ్చాను", "ఖాటా బ్యాలెన్స్", "ఎంత బాకీ",
            "udhar kitna", "kisko kitna diya"
        )
        if (extractNumber(lower) <= 0.0 && pendingLoansPatterns.any { lower.contains(it) }) {
            return DetectedIntent.QUERY_PENDING_LOANS
        }

        // 9. Inquiries: Category Spending
        val categoryQueryPatterns = listOf(
            "spend on food", "spent on food", "food expense", "dining expense", "petrol expense", "spend on petrol",
            "groceries expense", "bills expense", "shopping expense", "health expense", "medicine expense",
            "ఆహారం ఖర్చు", "భోజనం ఖర్చు", "పెట్రోల్ ఖర్చు", "కిరాణా ఖర్చు", "కూరగాయల ఖర్చు", "షాపింగ్ ఖర్చు", "కరెంట్ బిల్ ఖర్చు",
            "khana pe kitna", "petrol me kitna"
        )
        val hasCategoryQuestion = (lower.contains("ఎంత") || lower.contains("how much") || lower.contains("kitna")) &&
                (detectCategory(lower) != ExpenseCategory.OTHER || lower.contains("petrol") || lower.contains("పెట్రోల్") || lower.contains("food") || lower.contains("కూరగాయలు") || lower.contains("భోజనం")) &&
                !lower.contains("ఈ రోజు") && !lower.contains("ఈరోజు") && !lower.contains("ఈ నెల") && !lower.contains("today") && !lower.contains("month") && !lower.contains("budget") && !lower.contains("బడ్జెట్") && !lower.contains("బాకీ") && !lower.contains("loan")

        if (extractNumber(lower) <= 0.0 && (hasCategoryQuestion || categoryQueryPatterns.any { lower.contains(it) })) {
            return DetectedIntent.QUERY_CATEGORY_SPENDING
        }

        // 10. Savings Advice
        val advicePatterns = listOf(
            "savings tip", "save money", "how to save", "financial advice", "financial tip", "advice",
            "పొదుపు చిట్కా", "పొదుపు చిట్కాలు", "డబ్బు ఎలా ఆదా చేయాలి", "సలహా ఇవ్వు", "ఆర్థిక సలహా",
            "bachat kaise kare", "paisa kaise bachaye"
        )
        if (advicePatterns.any { lower.contains(it) }) {
            return DetectedIntent.SAVINGS_ADVICE
        }

        return DetectedIntent.EXPENSE_TRANSACTIONS
    }

    // --- Conversational Clause Splitting ---

    fun splitIntoClauses(text: String): List<String> {
        // Step 1: Normalize newlines, semicolons, and list markers (1. 2. - * •)
        val withListDelimiters = text.replace(Regex("""(?<=\s|^)(?:\d+[\.\)]|\-|\*|•)\s+"""), "|")
        val sanitized = withListDelimiters
            .replace("\n", "|")
            .replace(";", "|")
            .replace(",", "|")
            // English conjunctions
            .replace(Regex("""\b(?:and also|and then|and|also|plus)\b""", RegexOption.IGNORE_CASE), "|")
            // Telugu conjunctions
            .replace("మరియు", "|")
            .replace("కూడా", "|")
            // Hindi conjunctions
            .replace(Regex("""\b(?:aur|tatha|or)\b""", RegexOption.IGNORE_CASE), "|")
            // Kannada conjunctions
            .replace(Regex("""\b(?:mathu|haagu)\b""", RegexOption.IGNORE_CASE), "|")

        var clauses = sanitized.split("|")
            .map { it.trim().replace(Regex("""^(?:\d+[\.\)]|\-|\*|•)\s*"""), "") }
            .filter { it.isNotBlank() }

        // If user didn't use punctuation or conjunctions (e.g. "milk 50 tea 20 petrol 500")
        if (clauses.size <= 1) {
            val spaceTokens = text.trim().split(Regex("""\s+"""))
            val boundaryIndices = mutableListOf<Int>()
            for (i in 0 until spaceTokens.size - 1) {
                val currentTokenHasNumber = containsNumericAmount(spaceTokens[i])
                val nextTokenIsWord = !containsNumericAmount(spaceTokens[i + 1])
                if (currentTokenHasNumber && nextTokenIsWord) {
                    val remainderHasNumber = spaceTokens.subList(i + 1, spaceTokens.size).any { containsNumericAmount(it) }
                    if (remainderHasNumber) {
                        boundaryIndices.add(i + 1)
                    }
                }
            }

            if (boundaryIndices.isNotEmpty()) {
                val reconstructed = mutableListOf<String>()
                var startIndex = 0
                for (bIndex in boundaryIndices) {
                    reconstructed.add(spaceTokens.subList(startIndex, bIndex).joinToString(" "))
                    startIndex = bIndex
                }
                if (startIndex < spaceTokens.size) {
                    reconstructed.add(spaceTokens.subList(startIndex, spaceTokens.size).joinToString(" "))
                }
                if (reconstructed.size > 1) {
                    clauses = reconstructed.map { it.trim() }.filter { it.isNotBlank() }
                }
            }
        }

        return clauses
    }

    // --- Entity Extraction for a Single Clause ---

    fun extractClause(clause: String, isTelugu: Boolean): ExtractedClause? {
        val amount = extractNumber(clause)
        if (amount <= 0.0) return null

        val isLoan = isLoanPhrase(clause)
        val category = if (isLoan) ExpenseCategory.LENDING else detectCategory(clause)
        val personName = if (isLoan) extractPerson(clause, isTelugu) else ""
        val title = if (isLoan) {
            if (isTelugu) "$personName కి అప్పు" else "Loan to $personName"
        } else {
            extractTitle(clause, category, isTelugu)
        }
        val paymentMode = detectPaymentMode(clause)

        return ExtractedClause(
            rawClause = clause,
            title = title,
            amount = amount,
            category = category,
            isLoan = isLoan,
            personName = personName,
            paymentMode = paymentMode
        )
    }

    // --- Number & Currency Parsing ---

    fun normalizeTeluguNumberWords(text: String): String {
        var s = text
        // Compound thousands
        s = s.replace(Regex("""పది\s*వేలు|పది\s*వేల"""), "10000")
        s = s.replace(Regex("""ఐదు\s*వేలు|ఐదు\s*వేల"""), "5000")
        s = s.replace(Regex("""నాలుగు\s*వేలు|నాలుగు\s*వేల"""), "4000")
        s = s.replace(Regex("""మూడు\s*వేలు|మూడు\s*వేల"""), "3000")
        s = s.replace(Regex("""రెండు\s*వేలు|రెండు\s*వేల"""), "2000")
        s = s.replace(Regex("""వెయ్యి|వేయి|ఒక\s*వెయ్యి|ఒక\s*వేయి"""), "1000")

        // Hundreds with tens
        s = s.replace(Regex("""రెండు\s*వందల\s*యాభై"""), "250")
        s = s.replace(Regex("""వంద\s*యాభై"""), "150")
        s = s.replace(Regex("""ఐదు\s*వందల\s*యాభై"""), "550")

        // Hundreds
        s = s.replace(Regex("""తొమ్మిది\s*వందలు|తొమ్మిది\s*వందల"""), "900")
        s = s.replace(Regex("""ఎనిమిది\s*వందలు|ఎనిమిది\s*వందల"""), "800")
        s = s.replace(Regex("""ఏడు\s*వందలు|ఏడు\s*వందల"""), "700")
        s = s.replace(Regex("""ఆరు\s*వందలు|ఆరు\s*వందల"""), "600")
        s = s.replace(Regex("""ఐదు\s*వందలు|ఐదు\s*వందల"""), "500")
        s = s.replace(Regex("""నాలుగు\s*వందలు|నాలుగు\s*వందల"""), "400")
        s = s.replace(Regex("""మూడు\s*వందలు|మూడు\s*వందల"""), "300")
        s = s.replace(Regex("""రెండు\s*వందలు|రెండు\s*వందల"""), "200")
        s = s.replace(Regex("""వంద|వందలు|వందల|ఒక\s*వంద"""), "100")

        // Tens & Units
        s = s.replace(Regex("""తొంభై"""), "90")
        s = s.replace(Regex("""ఎనభై"""), "80")
        s = s.replace(Regex("""డెబ్బై"""), "70")
        s = s.replace(Regex("""అరవై"""), "60")
        s = s.replace(Regex("""యాభై"""), "50")
        s = s.replace(Regex("""నలభై"""), "40")
        s = s.replace(Regex("""ముప్పై"""), "30")
        s = s.replace(Regex("""ఇరవై"""), "20")
        s = s.replace(Regex("""పాతిక"""), "25")
        s = s.replace(Regex("""పది"""), "10")

        return s
    }

    fun extractNumber(text: String): Double {
        val wordNormalized = normalizeTeluguNumberWords(text)
        val normalized = wordNormalized
            // Telugu numerals normalization
            .replace('౦', '0').replace('౧', '1').replace('౨', '2').replace('౩', '3')
            .replace('౪', '4').replace('౫', '5').replace('౬', '6').replace('౭', '7')
            .replace('౮', '8').replace('౯', '9')
            .lowercase(Locale.ROOT)

        // 1. Check for multiplier words: Lakh / వేలు (Thousands) / k
        val lakhMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:lakh|lakhs|లక్ష|లక్షలు)""").find(normalized)
        if (lakhMatch != null) {
            val base = lakhMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            return base * 100000.0
        }

        val thousandMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:thousand|thousands|వేలు|వేల|hazaar|hazar)""").find(normalized)
        if (thousandMatch != null) {
            val base = thousandMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            return base * 1000.0
        }

        val kMatch = Regex("""\b(\d+(?:\.\d+)?)\s*k\b""").find(normalized)
        if (kMatch != null) {
            val base = kMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            return base * 1000.0
        }

        // 2. Attached to currency symbol: ₹500, rs. 450, 450/-
        val currencyAttached = Regex("""(?:₹|rs\.?|inr)\s*(\d+(?:\.\d+)?)""").find(normalized)
        if (currencyAttached != null) {
            return currencyAttached.groupValues[1].toDoubleOrNull() ?: 0.0
        }

        val reverseCurrency = Regex("""(\d+(?:\.\d+)?)\s*(?:₹|rs\.?|inr|/-|రూపాయలు|రూ\.|rupaye|rupees|rūpāyalu)""").find(normalized)
        if (reverseCurrency != null) {
            return reverseCurrency.groupValues[1].toDoubleOrNull() ?: 0.0
        }

        // 3. Fallback: Last standalone number in the clause (avoids leading list index "1. milk 50")
        val strippedLeadingIndex = normalized.replace(Regex("""^\s*\d+[\.\)]\s*"""), "")
        val allNumbers = Regex("""\b\d+(?:\.\d+)?\b""").findAll(strippedLeadingIndex).toList()
        return allNumbers.lastOrNull()?.value?.toDoubleOrNull() ?: 0.0
    }

    private fun containsNumericAmount(token: String): Boolean {
        return token.any { it.isDigit() || it in '౦'..'౯' }
    }

    // --- Category Detection via Multilingual Lexicon ---

    fun detectCategory(text: String): ExpenseCategory {
        val lower = text.lowercase(Locale.ROOT)

        val foodKeywords = listOf(
            "grocery", "groceries", "milk", "vegetables", "fruits", "rice", "dal", "oil",
            "bread", "eggs", "chicken", "meat", "fish", "supermarket", "kirana", "d-mart", "ration",
            "కూరగాయలు", "పాలు", "బియ్యం", "పప్పు", "నూనె", "గుడ్లు", "చికెన్", "కిరాణా", "పండ్లు",
            "sabzi", "doodh", "chawal", "atta", "daal", "tel", "machli", "anda",
            "tharakari", "haalu", "akki", "bele"
        )
        if (foodKeywords.any { lower.contains(it) }) return ExpenseCategory.FOOD

        val transportKeywords = listOf(
            "petrol", "diesel", "fuel", "auto", "cab", "uber", "ola", "rapido", "bus", "train", "metro",
            "toll", "puncture", "mechanic", "parking", "bike service", "car service",
            "పెట్రోల్", "డీజిల్", "ఆటో", "బస్సు", "రైలు", "సర్వీసింగ్", "పార్కింగ్",
            "gaadi", "chalan", "sawari",
            "vaahana", "prayāna"
        )
        if (transportKeywords.any { lower.contains(it) }) return ExpenseCategory.TRANSPORT

        val diningKeywords = listOf(
            "hotel", "restaurant", "swiggy", "zomato", "biryani", "tiffins", "tiffin", "tea", "chai",
            "coffee", "snacks", "dosa", "idli", "bakery", "cake", "ice cream", "lunch", "dinner", "breakfast",
            "భోజనం", "హోటల్", "టిఫిన్", "టీ", "కాఫీ", "స్నాక్స్", "బిర్యానీ", "దోశ", "ఇడ్లీ", "స్వీట్స్",
            "nashta", "khana", "dhaba", "bhojana", "thindi"
        )
        if (diningKeywords.any { lower.contains(it) }) return ExpenseCategory.DINING

        val billsKeywords = listOf(
            "current bill", "electricity", "power bill", "wifi", "broadband", "mobile recharge",
            "cylinder", "gas", "water bill", "rent", "maintenance", "dth", "recharge",
            "కరెంట్ బిల్లు", "గ్యాస్", "సిలిండర్", "రీఛార్జ్", "అద్దె", "నీళ్ల బిల్లు", "ఇంటి అద్దె",
            "bijli", "kiraya", "paani bill"
        )
        if (billsKeywords.any { lower.contains(it) }) return ExpenseCategory.BILLS

        val healthKeywords = listOf(
            "medicine", "medicines", "medical", "doctor", "hospital", "pharmacy", "clinic",
            "tablet", "syrup", "test", "lab", "dental", "apollo", "medplus",
            "మందులు", "డాక్టర్", "ఆసుపత్రి", "హాస్పిటల్", "మెడికల్", "మాత్రలు",
            "dawa", "ilaj", "aushadhi", "vaidhya"
        )
        if (healthKeywords.any { lower.contains(it) }) return ExpenseCategory.HEALTH

        val shoppingKeywords = listOf(
            "clothes", "shirt", "pant", "saree", "dress", "shoes", "chappal", "amazon", "flipkart",
            "myntra", "meesho", "bag", "cosmetics", "jewellery", "gold",
            "బట్టలు", "చొక్కా", "చీర", "చెప్పులు", "షాపింగ్", "బంగారం",
            "kapde", "joota", "bhatta", "vasthra"
        )
        if (shoppingKeywords.any { lower.contains(it) }) return ExpenseCategory.SHOPPING

        val educationKeywords = listOf(
            "school fee", "college fee", "books", "pen", "exam fee", "tuition", "uniform",
            "స్కూల్ ఫీజు", "కాలేజీ ఫీజు", "పుస్తకాలు", "ట్యూషన్",
            "padhai", "kitab", "shala", "pusthaka"
        )
        if (educationKeywords.any { lower.contains(it) }) return ExpenseCategory.EDUCATION

        val entertainmentKeywords = listOf(
            "movie", "cinema", "theatre", "netflix", "prime", "hotstar", "game", "resort",
            "సినిమా", "మూవీ", "థియేటర్", "ఆటలు"
        )
        if (entertainmentKeywords.any { lower.contains(it) }) return ExpenseCategory.ENTERTAINMENT

        return ExpenseCategory.OTHER
    }

    // --- Title Extraction & Noise Removal ---

    fun extractTitle(clause: String, category: ExpenseCategory, isTelugu: Boolean): String {
        var cleaned = clause

        // 1. Remove leading list numbers/bullets
        cleaned = cleaned.replace(Regex("""^\s*(?:\d+[\.\)]|\-|\*|•)\s*"""), "")

        // 2. Remove amounts with currency affixes
        cleaned = cleaned
            .replace(Regex("""(?:₹|rs\.?|inr|/-)\s*\d+(?:\.\d+)?""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\d+(?:\.\d+)?\s*(?:k|/-)?""", RegexOption.IGNORE_CASE), " ")

        // 3. Remove standalone numbers
        cleaned = cleaned.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])\d+(?:\.\d+)?(?=$|[^\p{L}\p{M}\p{N}])"""), " ")

        // 4. Remove all variations of currency words in Telugu, English, Hindi, Kannada
        val currencyWords = listOf(
            "రూపాయలు", "రూపాయల", "రూపాయిలు", "రూపాయి", "రూ\\.", "రూ", "పైసలు",
            "వేలు", "వేల", "లక్షలు", "లక్ష",
            "rupayalu", "rupayala", "rupayilu", "rupayi", "rupaye", "rupees", "rupee", "rs", "inr",
            "rupeelu", "rupai", "paisalu", "paisa", "paise", "lakh", "lakhs", "thousand", "thousands"
        )
        for (cw in currencyWords) {
            cleaned = cleaned.replace(Regex("""(?<=^|\s|[^\p{L}\p{M}\p{N}])$cw(?=$|\s|[^\p{L}\p{M}\p{N}])""", RegexOption.IGNORE_CASE), " ")
        }

        // 5. Remove conversational verbs and filler words
        val fillerWords = listOf(
            "కోసం", "కొరకు", "ఖర్చు", "ఖర్చయింది", "ఖర్చుచేశాను", "ఖర్చుపెట్టాను", "కొన్నాను", "కొన్నాము",
            "తీసుకున్నాను", "తిన్నాను", "తాగిన", "చెల్లించాను", "కొట్టించాను", "చేసాను", "వేశాను", "ఇచ్చాను", "ఇచ్చా",
            "spent on", "bought for", "paid for", "purchased", "expense of", "given for", "spent", "bought", "paid", "for",
            "via", "by", "through", "upi", "cash", "card", "gpay", "phonepe", "paytm",
            "kharcha", "kharidha", "diya", "liya", "bhara", "kotte", "thagonde"
        )
        for (fw in fillerWords) {
            cleaned = cleaned.replace(Regex("""(?<=^|\s|[^\p{L}\p{M}\p{N}])$fw(?=$|\s|[^\p{L}\p{M}\p{N}])""", RegexOption.IGNORE_CASE), " ")
        }

        // 6. Strip non-word characters (keep Unicode letters, marks, digits, and hyphen)
        cleaned = cleaned.replace(Regex("""[^\p{L}\p{M}\p{N}\s&-]"""), " ").trim()
        cleaned = cleaned.replace(Regex("""\s+"""), " ")

        // 7. Strip trailing Telugu particles like "చాక్లెట్ కి" -> "చాక్లెట్"
        cleaned = cleaned.replace(Regex("""\s+(?:కి|కు|గా|లో)\s*$"""), "").trim()

        if (cleaned.length >= 2) {
            val words = cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() && it.length > 1 }
            if (words.isNotEmpty()) {
                val candidate = words.take(3).joinToString(" ")
                return candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }

        return if (isTelugu) category.displayNameTe.split("&")[0].trim() else category.displayNameEn.split("&")[0].trim()
    }

    // --- Person & Loan Extraction ---

    fun containsExplicitLoanKeywords(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        val explicitLoanWords = listOf(
            "lent", "loan", "borrowed", "lending",
            "అప్పు", "చేబదులు", "బాకీ", "అప్పుగా", "చేబదులుగా",
            "udhar", "karz", "saala"
        )
        return explicitLoanWords.any { lower.contains(it) }
    }

    fun isLoanPhrase(clause: String): Boolean {
        val lower = clause.lowercase(Locale.ROOT)
        if (containsExplicitLoanKeywords(lower)) return true

        // Check for general lending action verbs
        val generalGiveWords = listOf(
            "i gave this", "gave this", "i gave", "gave to", "given to", "gave",
            "ఇచ్చాను", "ఇచ్చా", "ఇచ్చాము", "ఇచ్చాం",
            "udhar diya", "paise diye", "diya",
            "kotte", "saala kotte"
        )
        val hasGiveWord = generalGiveWords.any { lower.contains(it) }
        if (!hasGiveWord) return false

        // Distinguish from common expenses like "పెట్రోల్ కి 500 ఇచ్చాను"
        val detectedCat = detectCategory(lower)
        val hasPersonDative = Regex("""([A-Za-z\u0C00-\u0C7F]+)\s*(?:కి|కు|గారికి|to)(?=$|[^\p{L}\p{M}\p{N}])""").find(clause)?.let { match ->
            val name = match.groupValues[1].trim()
            name !in listOf("పెట్రోల్", "డీజిల్", "కరెంట్", "బిల్లు", "రూం", "ఇంటి", "నా", "గ్యాస్", "షాప్")
        } ?: false

        return detectedCat == ExpenseCategory.OTHER || hasPersonDative || lower.contains("i gave") || lower.contains("gave this")
    }

    fun extractPerson(clause: String, isTelugu: Boolean = false): String {
        val lower = clause.lowercase(Locale.ROOT)
        val toMatch = Regex("""(?:lent to|gave to|given to|loan to|lent|gave)\s+([A-Za-z]+)""", RegexOption.IGNORE_CASE).find(lower)
        if (toMatch != null) {
            val name = toMatch.groupValues[1].trim()
            val stopWords = listOf("the", "my", "rs", "inr", "upi", "cash", "this", "him", "her", "them", "money", "loan")
            if (name !in stopWords && name.length > 1) {
                return name.replaceFirstChar { it.uppercase(Locale.ROOT) }
            }
        }

        // Telugu dative suffix: "రమేశ్ కి", "సురేష్ కు", "రవి గారికి"
        val teMatch = Regex("""([A-Za-z\u0C00-\u0C7F]+)\s*(?:కి|కు|గారికి)""").find(clause)
        if (teMatch != null) {
            val name = teMatch.groupValues[1].trim()
            val invalidTeluguNames = listOf("నా", "ఇంటి", "అప్పు", "చేబదులు", "బాకీ", "షాప్", "రూం", "గది", "పెట్రోల్", "కరెంట్")
            if (name !in invalidTeluguNames && name.length > 1) {
                return name
            }
        }

        return if (isTelugu) "స్నేహితుడు" else "Friend"
    }

    // --- Loan Repayment Due Date Helpers ---

    fun isDueDatePhrase(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        val dueDateKeywords = listOf(
            "tomorrow", "next week", "1 week", "one week", "in a week", "in 1 week",
            "1 month", "one month", "next month", "in 1 month",
            "15 days", "in 15 days", "10 days", "in 10 days", "3 days", "2 days", "2 weeks",
            "రేపు", "ఎల్లుండి", "వచ్చే వారం", "1 వారం", "ఒక వారం", "వారం రోజుల్లో",
            "1 నెల", "ఒక నెల", "వచ్చే నెల", "నెల రోజుల్లో", "15 రోజులు", "15 రోజుల్లో", "10 రోజులు",
            "3 రోజులు", "రెండు రోజులు", "మూడు రోజులు",
            "kal", "parso", "agle hafte", "ek hafte me", "ek mahine me", "15 din me", "10 din me"
        )
        return dueDateKeywords.any { lower == it || lower.contains(it) }
    }

    fun parseDueDaysFromText(text: String): Int {
        val lower = text.lowercase(Locale.ROOT).trim()
        return when {
            lower.contains("tomorrow") || lower.contains("రేపు") || lower.contains("kal") -> 1
            lower.contains("day after") || lower.contains("ఎల్లుండి") || lower.contains("parso") -> 2
            lower.contains("3 days") || lower.contains("3 రోజులు") || lower.contains("మూడు రోజులు") -> 3
            lower.contains("2 weeks") || lower.contains("2 వారాలు") || lower.contains("14 days") -> 14
            lower.contains("1 week") || lower.contains("one week") || lower.contains("వారం") || lower.contains("next week") || lower.contains("hafte") -> 7
            lower.contains("10 days") || lower.contains("10 రోజులు") || lower.contains("10 din") -> 10
            lower.contains("15 days") || lower.contains("15 రోజులు") || lower.contains("15 din") -> 15
            lower.contains("2 months") || lower.contains("2 నెలలు") -> 60
            lower.contains("1 month") || lower.contains("one month") || lower.contains("నెల") || lower.contains("next month") || lower.contains("mahine") -> 30
            else -> 7
        }
    }

    // --- Payment Mode Detection ---

    fun detectPaymentMode(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("cash") || lower.contains("నగదు") || lower.contains("roked") -> "Cash"
            lower.contains("card") || lower.contains("credit") || lower.contains("debit") || lower.contains("కార్డు") -> "Card"
            lower.contains("netbanking") || lower.contains("net banking") ||
                lower.contains("bank transfer") || lower.contains("నెట్ బ్యాంకింగ్") ||
                lower.contains("బ్యాంక్ ట్రాన్స్‌ఫర్") -> "NetBanking"
            lower.contains("upi") || lower.contains("gpay") || lower.contains("google pay") ||
                lower.contains("phonepe") || lower.contains("paytm") -> "UPI"
            else -> "Unknown"
        }
    }

    // --- Modification / Correction Request Parsing ---

    fun parseModificationRequest(text: String, isTelugu: Boolean): ModificationRequest? {
        val amount = extractNumber(text)
        if (amount <= 0.0) return null

        val cat = detectCategory(text)
        val categoryName = if (cat != ExpenseCategory.OTHER) cat.displayNameEn else null

        return ModificationRequest(
            targetCategory = categoryName,
            newAmount = amount
        )
    }
}
