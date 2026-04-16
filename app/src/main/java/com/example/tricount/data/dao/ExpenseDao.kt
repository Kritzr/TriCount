package com.example.tricount.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tricount.data.entity.ExpenseEntity

/**
 * Simple single-table DAO for ExpenseEntity.
 * All complex JOIN queries live in TricountDao.
 */
@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Query("SELECT * FROM expenses WHERE tricountId = :tricountId ORDER BY createdAt DESC")
    suspend fun getExpensesForTricount(tricountId: Int): List<ExpenseEntity>

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: Int)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("UPDATE expenses SET isArchived = 1 WHERE id = :expenseId")
    suspend fun archiveExpense(expenseId: Int)


}