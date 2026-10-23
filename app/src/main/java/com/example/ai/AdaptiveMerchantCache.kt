package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ExpenseCategory
import org.json.JSONObject
import java.util.Locale

/**
 * AdaptiveMerchantCache:
 * An ultra-lightweight, on-device self-improving merchant & category intelligence cache.
 * Designed specifically for 4GB RAM devices (Android 8.1 / MediaTek Helio P23) to keep
 * RAM footprint < 2MB and response latency < 1ms.
 *
 * It combines:
 * 1. Pre-seeded Indian & regional brand/term knowledge (Telugu, Hindi, English).
 * 2. Day-by-day continuous learning: Automatically learns and prioritizes the user's
 *    personal merchants, shops, payment modes, and spending habits as they log transactions.
 */
class AdaptiveMerchantCache(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("dhanam_merchant_brain", Context.MODE_PRIVATE)

    // In-memory lookup map for instant O(1) matching
    private val dynamicMerchantMap = mutableMapOf<String, LearnedMerchant>()

    data class LearnedMerchant(
        val category: ExpenseCategory,
        val defaultPaymentMode: String = "UPI",
        var frequencyCount: Int = 1
    )

    init {
        loadLearnedCache()
    }

    private fun loadLearnedCache() {
        try {
            val jsonStr = prefs.getString(KEY_LEARNED_MERCHANTS, null) ?: return
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = json.getJSONObject(key)
                val catName = obj.optString("category", ExpenseCategory.OTHER.name)
                val cat = ExpenseCategory.fromString(catName)
                val mode = obj.optString("paymentMode", "UPI")
                val freq = obj.optInt("frequency", 1)
                dynamicMerchantMap[key] = LearnedMerchant(cat, mode, freq)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun saveLearnedCache() {
        try {
            val json = JSONObject()
            // Keep top 200 learned items to strictly protect memory and disk I/O on budget storage
            val sorted = dynamicMerchantMap.entries.sortedByDescending { it.value.frequencyCount }.take(200)
            for ((key, item) in sorted) {
                val obj = JSONObject().apply {
                    put("category", item.category.name)
                    put("paymentMode", item.defaultPaymentMode)
                    put("frequency", item.frequencyCount)
                }
                json.put(key, obj)
            }
            prefs.edit().putString(KEY_LEARNED_MERCHANTS, json.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Records a successful user transaction into the adaptive brain.
     * Makes the agent smarter every single day.
     */
    @Synchronized
    fun recordMerchantUsage(title: String, categoryName: String, paymentMode: String = "UPI") {
        val normalized = normalizeKey(title)
        if (normalized.length < 2) return

        val category = ExpenseCategory.fromString(categoryName)
        val existing = dynamicMerchantMap[normalized]
        if (existing != null) {
            existing.frequencyCount += 1
        } else {
            dynamicMerchantMap[normalized] = LearnedMerchant(
                category = category,
                defaultPaymentMode = paymentMode,
                frequencyCount = 1
            )
        }
        saveLearnedCache()
    }

    /**
     * Resolves category and payment mode from learned memory or built-in Indian knowledge base.
     */
    fun resolveMerchant(query: String): Pair<ExpenseCategory, String>? {
        val normalized = normalizeKey(query)

        // 1. Check user's personalized learned memory first (highest priority)
        val learned = dynamicMerchantMap[normalized]
        if (learned != null) {
            return Pair(learned.category, learned.defaultPaymentMode)
        }

        // Substring / word match in learned memory
        for ((key, item) in dynamicMerchantMap) {
            if (normalized.contains(key) || key.contains(normalized)) {
                return Pair(item.category, item.defaultPaymentMode)
            }
        }

        // 2. Pre-seeded Indian & Regional Knowledge Base
        return resolvePreseeded(normalized)
    }

    private fun resolvePreseeded(norm: String): Pair<ExpenseCategory, String>? {
        // Food & Groceries
        if (containsAny(norm, "dmart", "d-mart", "blinkit", "zepto", "bigbasket", "instamart", "spencers",
                "more", "ratnadeep", "kirana", "ration", "grocery", "groceries", "supermarket",
                "కూరగాయలు", "పాలు", "బియ్యం", "నిత్యావసరాలు", "కిరాణా", "సరుకులు", "కూరగాయ",
                "sabzi", "doodh", "chawal", "atta", "dal", "oil", "milk", "curd", "vegetable", "vegetables")) {
            return Pair(ExpenseCategory.FOOD, "UPI")
        }

        // Dining & Snacks
        if (containsAny(norm, "swiggy", "zomato", "mcdonald", "kfc", "domino", "pizza", "burger", "biryani",
                "paradise", "bawarchi", "mehfil", "hotel", "mess", "tiffin", "tiffins", "dosa", "idli", "vada",
                "puri", "tea", "coffee", "chai", "bakery", "sweets", "dhaba", "restaurant", "lunch", "dinner",
                "breakfast", "snacks", "shawarma", "noodles", "ice cream",
                "భోజనం", "టిఫిన్", "తినుబండారాలు", "టీ", "కాఫీ", "రెస్టారెంట్", "హోటల్", "బిర్యానీ",
                "khana", "nashta", "samosa", "chai point")) {
            return Pair(ExpenseCategory.DINING, "UPI")
        }

        // Transport & Fuel
        if (containsAny(norm, "petrol", "diesel", "fuel", "cng", "hp petrol", "bharat petroleum", "indian oil", "shell",
                "uber", "ola", "rapido", "auto", "cab", "taxi", "metro", "bus", "train", "irctc", "flight", "toll",
                "fastag", "parking", "bike service", "car wash", "puncture",
                "పెట్రోల్", "డీజిల్", "బస్సు", "ఆటో", "ప్రయాణం", "రైలు", "టికెట్",
                "gaadi", "chalan")) {
            return Pair(ExpenseCategory.TRANSPORT, "UPI")
        }

        // Bills & Utilities
        if (containsAny(norm, "current bill", "electricity", "bescom", "tsspdcl", "apspdcl", "water bill", "gas cylinder",
                "indane", "bharat gas", "hp gas", "airtel", "jio", "vi", "bsnl", "broadband", "wifi", "act fibernet",
                "recharge", "dth", "tata sky", "sun direct", "rent", "maintenance", "maid",
                "కరెంట్ బిల్లు", "బిల్లు", "రీఛార్జ్", "గ్యాస్", "అద్దె", "మెయింటెనెన్స్",
                "bijli", "kiraya")) {
            return Pair(ExpenseCategory.BILLS, "UPI")
        }

        // Shopping & Clothes
        if (containsAny(norm, "amazon", "flipkart", "myntra", "meesho", "ajio", "zara", "h&m", "trends", "max",
                "zudio", "westside", "pantaloons", "shirt", "pant", "saree", "clothes", "shoes", "bag",
                "దుస్తులు", "బట్టలు", "షాపింగ్", "చీర", "చెప్పులు",
                "kapde", "joote")) {
            return Pair(ExpenseCategory.SHOPPING, "UPI")
        }

        // Health & Medical
        if (containsAny(norm, "apollo", "medplus", "netmeds", "1mg", "pharmeasy", "clinic", "hospital", "doctor",
                "medical", "medicine", "tablets", "syrup", "dentist", "lab test", "scan",
                "మందులు", "వైద్యం", "ఆసుపత్రి", "డాక్టర్", "క్లినిక్",
                "dawai", "ilaaj")) {
            return Pair(ExpenseCategory.HEALTH, "UPI")
        }

        // Education & Fees
        if (containsAny(norm, "school fee", "college fee", "tuition", "course", "udemy", "coursera", "books",
                "stationery", "exam fee", "pen", "notebook",
                "చదువు", "ఫీజు", "పుస్తకాలు", "స్కూల్", "కాలేజ్",
                "padhai", "kitab")) {
            return Pair(ExpenseCategory.EDUCATION, "UPI")
        }

        // Entertainment & Movies
        if (containsAny(norm, "bookmyshow", "pvr", "inox", "cinepolis", "netflix", "prime video", "hotstar",
                "movie", "cinema", "theatre", "game", "gaming", "amusement",
                "సినిమా", "వినోదం", "థియేటర్",
                "film", "natak")) {
            return Pair(ExpenseCategory.ENTERTAINMENT, "UPI")
        }

        return null
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        for (kw in keywords) {
            if (text.contains(kw, ignoreCase = true)) return true
        }
        return false
    }

    private fun normalizeKey(str: String): String {
        return str.lowercase(Locale.ROOT)
            .replace(Regex("""[0-9₹,\.\-\(\)\"\'!]"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    companion object {
        private const val KEY_LEARNED_MERCHANTS = "learned_merchants_map"
    }
}
