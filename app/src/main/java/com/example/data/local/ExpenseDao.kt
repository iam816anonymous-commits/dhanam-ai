package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpensesFlow(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getExpensesInRangeFlow(startTime: Long, endTime: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getExpensesInRange(startTime: Long, endTime: Long): List<Expense>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentExpenses(limit: Int = 20): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime")
    fun getTotalSpentInRangeFlow(startTime: Long, endTime: Long): Flow<Double?>

    @Query("SELECT category, SUM(amount) as total FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime GROUP BY category")
    suspend fun getCategoryTotalsInRange(startTime: Long, endTime: Long): List<CategoryTotal>

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestExpense(): Expense?

    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestExpenseByCategory(category: String): Expense?
}

data class CategoryTotal(
    val category: String,
    val total: Double
)
