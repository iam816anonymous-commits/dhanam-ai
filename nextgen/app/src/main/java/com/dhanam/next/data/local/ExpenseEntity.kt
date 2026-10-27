package com.dhanam.next.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountPaise: Long,
    val category: String,
    val paymentMode: String,
    val occurredAtEpochMillis: Long,
    val notes: String = ""
)
