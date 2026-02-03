package com.example.tricount.data.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val tricountId: Int,          // which tricount this expense belongs to
    val title: String,            // e.g. "Dinner"
    val amount: Double,           // e.g. 450.0
    val paidBy: String,           // e.g. "Krithika"
    val timestamp: Long = System.currentTimeMillis()
)
