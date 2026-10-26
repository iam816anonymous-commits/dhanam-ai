package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.CategoryBudget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM category_budgets")
    fun getAllCategoryBudgetsFlow(): Flow<List<CategoryBudget>>

    @Query("SELECT * FROM category_budgets")
    suspend fun getAllCategoryBudgets(): List<CategoryBudget>

    @Query("SELECT * FROM category_budgets WHERE category = :category LIMIT 1")
    suspend fun getBudgetForCategory(category: String): CategoryBudget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBudget(budget: CategoryBudget)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<CategoryBudget>)

    @Query("DELETE FROM category_budgets")
    suspend fun deleteAllCategoryBudgets()
}
