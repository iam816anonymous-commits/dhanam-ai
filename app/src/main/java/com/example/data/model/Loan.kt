package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personName: String,
    val amount: Double,
    val amountPaid: Double = 0.0,
    val dateGivenMillis: Long = System.currentTimeMillis(),
    val dueDateMillis: Long? = null,
    val notes: String = "",
    val phoneNumber: String = "",
    val isSettled: Boolean = false
) {
    val remainingAmount: Double
        get() = (amount - amountPaid).coerceAtLeast(0.0)

    val isOverdue: Boolean
        get() = !isSettled && dueDateMillis != null && System.currentTimeMillis() > dueDateMillis
}
