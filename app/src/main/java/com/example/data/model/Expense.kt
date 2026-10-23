package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String, // e.g. Food, Dining, Transport, Shopping, Bills, Entertainment, Health, Other
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val paymentMode: String = "UPI", // UPI, Cash, Card, NetBanking
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
        fun fromString(name: String): ExpenseCategory {
            return entries.firstOrNull { 
                it.name.equals(name, ignoreCase = true) || 
                it.displayNameEn.contains(name, ignoreCase = true) ||
                it.displayNameTe.contains(name, ignoreCase = true)
            } ?: OTHER
        }
    }
}
