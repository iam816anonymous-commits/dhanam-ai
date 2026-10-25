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

@Database(
    entities = [Expense::class, Loan::class, CategoryBudget::class],
    version = 1,
    exportSchema = true
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
                        // Atomic, non-reentrant seed of default category budgets inside database transaction
                        val defaultBudgets = listOf(
                            Pair(ExpenseCategory.FOOD.displayNameEn, 8000.0),
                            Pair(ExpenseCategory.DINING.displayNameEn, 4000.0),
                            Pair(ExpenseCategory.TRANSPORT.displayNameEn, 3500.0),
                            Pair(ExpenseCategory.SHOPPING.displayNameEn, 5000.0),
                            Pair(ExpenseCategory.BILLS.displayNameEn, 4500.0),
                            Pair(ExpenseCategory.ENTERTAINMENT.displayNameEn, 2500.0),
                            Pair(ExpenseCategory.HEALTH.displayNameEn, 3000.0),
                            Pair(ExpenseCategory.EDUCATION.displayNameEn, 5000.0),
                            Pair(ExpenseCategory.OTHER.displayNameEn, 3000.0)
                        )
                        for ((category, limit) in defaultBudgets) {
                            db.execSQL("INSERT OR REPLACE INTO category_budgets (category, monthlyLimit) VALUES ('$category', $limit)")
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
