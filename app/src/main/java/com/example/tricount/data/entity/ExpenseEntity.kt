package com.example.tricount.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val tricountId: Int,      // FK reference (logical)
    val title: String,
    val amount: Double,
    val paidBy: String,
    val timestamp: Long
)
