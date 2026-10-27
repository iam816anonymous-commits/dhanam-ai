package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String, // e.g. Food, Dining, Transport, Shopping, Bills, Entertainment, Health, Other
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val paymentMode: String = "Unknown", // UPI, Cash, Card, NetBanking, Unknown
    val detectedLanguage: String = "en" // "te" (Telugu) or "en" (English)
)

enum class ExpenseCategory(val displayNameEn: String, val displayNameTe: String, val iconName: String) {
    FOOD("Food & Groceries", "కూరగాయలు & నిత్యావసరాలు", "grocery"),
    DINING("Dining & Snacks", "రెస్టారెంట్ & తినుబండారాలు", "restaurant"),
    TRANSPORT("Transport & Fuel", "ప్రయాణం & పెట్రోల్", "commute"),
    SHOPPING("Shopping & Clothes", "షాపింగ్ & దుస్తులు", "shopping_bag"),
    BILLS("Bills & Utilities", "కరెంట్, రీఛార్జ్ & బిల్లులు", "receipt_long"),
    ENTERTAINMENT("Entertainment & Movies", "వినోదం & సినిమాలు", "movie"),
    HEALTH("Health & Medical", "వైద్యం & మందులు", "medical_services"),
    EDUCATION("Education & Fees", "చదువు & ఫీజులు", "school"),
    LENDING("Money Lent / Debt", "ఇచ్చిన అప్పు / ఖాటా", "handshake"),
    OTHER("Other Miscellaneous", "ఇతర ఖర్చులు", "category");

    companion object {
        /**
         * Matches canonical labels and known CSV/import aliases without treating arbitrary custom
         * categories as OTHER. This keeps imported short labels aligned with in-app categories.
         */
        fun matchesLabel(label: String, category: ExpenseCategory): Boolean {
            val normalized = label.trim().lowercase(Locale.ROOT)
            if (normalized.isBlank()) return false
            if (normalized == category.name.lowercase(Locale.ROOT) ||
                normalized == category.displayNameEn.lowercase(Locale.ROOT) ||
                normalized == category.displayNameTe.lowercase(Locale.ROOT)
            ) return true

            return when (category) {
                ExpenseCategory.FOOD -> normalized in setOf("food", "groceries", "grocery")
                ExpenseCategory.DINING -> normalized in setOf("dining", "snacks", "restaurant", "restaurants")
                ExpenseCategory.TRANSPORT -> normalized in setOf("transport", "fuel", "petrol", "travel")
                ExpenseCategory.SHOPPING -> normalized in setOf("shopping", "clothes", "clothing")
                ExpenseCategory.BILLS -> normalized in setOf("bills", "bill", "utilities", "utility")
                ExpenseCategory.ENTERTAINMENT -> normalized in setOf("entertainment", "movies", "movie")
                ExpenseCategory.HEALTH -> normalized in setOf("health", "medical", "medicine")
                ExpenseCategory.EDUCATION -> normalized in setOf("education", "fees", "fee")
                ExpenseCategory.LENDING -> normalized in setOf("lending", "money lent", "debt")
                ExpenseCategory.OTHER -> normalized in setOf("other", "uncategorized", "misc", "miscellaneous")
            }
        }

        fun fromString(name: String): ExpenseCategory =
            entries.firstOrNull { matchesLabel(name, it) } ?: OTHER
    }
}
