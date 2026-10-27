package com.dhanam.next.data

import com.dhanam.next.data.local.ExpenseDao
import com.dhanam.next.data.local.ExpenseEntity
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao) {
    fun observeExpenses(): Flow<List<ExpenseEntity>> = dao.observeAll()

    suspend fun add(title: String, amountPaise: Long, category: String, paymentMode: String): Long {
        require(title.isNotBlank()) { "Expense title is required." }
        require(amountPaise > 0L) { "Amount must be greater than zero." }
        require(category.isNotBlank()) { "Category is required." }
        require(paymentMode in setOf("UPI", "Cash", "Card", "NetBanking", "Unknown")) {
            "Unsupported payment mode."
        }
        return dao.insert(
            ExpenseEntity(
                title = title.trim(),
                amountPaise = amountPaise,
                category = category.trim(),
                paymentMode = paymentMode,
                occurredAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(id: Long): Boolean {
        require(id > 0L) { "Invalid expense identifier." }
        return dao.deleteById(id) == 1
    }
}
