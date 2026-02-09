package com.example.tricount.data.dao
//dao - data access object
import androidx.room.Dao // annotation from the room
import androidx.room.Insert// this is to insert data in the database
import androidx.room.OnConflictStrategy //for handling the conflicts that occur when we insert data
import androidx.room.Query //to write sql queries inside kotlin
import com.example.tricount.data.entity.ExpenseEntity //this handles the expense table

@Dao //marks this interface as dao - room will autogenerate during the implementation at compile time
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE) // basically creates a new entry if already existing, replace the key
    suspend fun insertExpense(expense: ExpenseEntity) // gets off the main thread and runs this to inhibit the ui freezing

    @Query("SELECT * FROM expenses WHERE tricountId = :tricountId") //query to fetch columns from expenses table
    suspend fun getExpensesForTricount(tricountId: Int): List<ExpenseEntity>

    @Query("DELETE FROM expenses") //query to delete all the expenses
    suspend fun deleteAllExpenses()
}
