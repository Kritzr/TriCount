package com.example.tricount.data.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE tricountId = :tricountId")
    suspend fun getExpensesForTricount(tricountId: Int): List<ExpenseEntity>

    @Query("DELETE FROM expenses")
    suspend fun clearAll()
}
