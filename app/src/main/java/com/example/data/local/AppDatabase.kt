package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.CategoryBudget
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Loan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Expense::class, Loan::class, CategoryBudget::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun loanDao(): LoanDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dhanam_expense_database"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default budgets
                        CoroutineScope(Dispatchers.IO).launch {
                            val defaultBudgets = listOf(
                                CategoryBudget(ExpenseCategory.FOOD.displayNameEn, 8000.0),
                                CategoryBudget(ExpenseCategory.DINING.displayNameEn, 4000.0),
                                CategoryBudget(ExpenseCategory.TRANSPORT.displayNameEn, 3500.0),
                                CategoryBudget(ExpenseCategory.SHOPPING.displayNameEn, 5000.0),
                                CategoryBudget(ExpenseCategory.BILLS.displayNameEn, 4500.0),
                                CategoryBudget(ExpenseCategory.ENTERTAINMENT.displayNameEn, 2500.0),
                                CategoryBudget(ExpenseCategory.HEALTH.displayNameEn, 3000.0),
                                CategoryBudget(ExpenseCategory.EDUCATION.displayNameEn, 5000.0),
                                CategoryBudget(ExpenseCategory.OTHER.displayNameEn, 3000.0)
                            )
                            getInstance(context).budgetDao().insertAll(defaultBudgets)
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
