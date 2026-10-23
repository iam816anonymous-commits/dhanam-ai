package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Loan
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY isSettled ASC, dueDateMillis ASC, dateGivenMillis DESC")
    fun getAllLoansFlow(): Flow<List<Loan>>

    @Query("SELECT * FROM loans WHERE isSettled = 0 ORDER BY dueDateMillis ASC, dateGivenMillis DESC")
    fun getPendingLoansFlow(): Flow<List<Loan>>

    @Query("SELECT * FROM loans ORDER BY isSettled ASC, dateGivenMillis DESC")
    suspend fun getAllLoans(): List<Loan>

    @Query("SELECT * FROM loans WHERE isSettled = 0 ORDER BY dateGivenMillis DESC LIMIT 1")
    suspend fun getLatestPendingLoan(): Loan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: Loan): Long

    @Update
    suspend fun updateLoan(loan: Loan)

    @Delete
    suspend fun deleteLoan(loan: Loan)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT SUM(amount - amountPaid) FROM loans WHERE isSettled = 0")
    fun getTotalPendingLentFlow(): Flow<Double?>

    @Query("DELETE FROM loans")
    suspend fun deleteAllLoans()
}
