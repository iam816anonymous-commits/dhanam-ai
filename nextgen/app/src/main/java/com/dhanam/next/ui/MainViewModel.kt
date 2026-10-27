package com.dhanam.next.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dhanam.next.data.ExpenseRepository
import com.dhanam.next.data.local.DhanamDatabase
import com.dhanam.next.data.local.ExpenseEntity
import com.dhanam.next.domain.ExpenseCommandParser
import com.dhanam.next.domain.ParseExpenseResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ExpenseRepository(DhanamDatabase.getInstance(application).expenseDao())
    val expenses: StateFlow<List<ExpenseEntity>> = repository.observeExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addManualExpense(title: String, amountText: String, category: String, paymentMode: String, onResult: (String) -> Unit) {
        val paise = try {
            BigDecimal(amountText.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        } catch (_: Exception) { 0L }
        if (title.isBlank() || paise <= 0L) {
            onResult("Enter a title and a valid amount greater than zero.")
            return
        }
        viewModelScope.launch {
            runCatching { repository.add(title, paise, category, paymentMode) }
                .onSuccess { onResult("Saved " + title.trim() + " — " + formatRupees(paise) + ".") }
                .onFailure { onResult(it.message ?: "Could not save the expense.") }
        }
    }

    fun processCommand(input: String, onResult: (String) -> Unit) {
        when (val result = ExpenseCommandParser.parse(input)) {
            is ParseExpenseResult.Invalid -> onResult(result.message)
            is ParseExpenseResult.NeedsClarification -> onResult(result.message)
            is ParseExpenseResult.Success -> {
                val item = result.expense
                viewModelScope.launch {
                    runCatching { repository.add(item.title, item.amountPaise, item.category, item.paymentMode) }
                        .onSuccess { onResult("Saved " + item.title + " — " + formatRupees(item.amountPaise) + " (" + item.category + ").") }
                        .onFailure { onResult(it.message ?: "Could not save the expense.") }
                }
            }
        }
    }

    fun deleteExpense(id: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onSuccess { deleted -> onResult(if (deleted) "Expense deleted." else "This expense no longer exists.") }
                .onFailure { onResult(it.message ?: "Could not delete the expense.") }
        }
    }

    companion object {
        fun formatRupees(paise: Long): String =
            "₹" + BigDecimal.valueOf(paise, 2).stripTrailingZeros().toPlainString()
    }
}
