package com.dhanam.next.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ExpenseEntity::class], version = 1, exportSchema = true)
abstract class DhanamDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    companion object {
        @Volatile private var instance: DhanamDatabase? = null
        fun getInstance(context: Context): DhanamDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, DhanamDatabase::class.java, "dhanam_next.db"
                ).build().also { instance = it }
            }
    }
}
