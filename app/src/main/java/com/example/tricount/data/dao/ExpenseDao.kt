package com.example.tricount.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tricount.data.entity.ExpenseEntity

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE tricountId = :tricountId")
    suspend fun getExpensesForTricount(tricountId: Int): List<ExpenseEntity>

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
}
