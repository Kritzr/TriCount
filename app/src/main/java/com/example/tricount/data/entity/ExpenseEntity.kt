package com.example.tricount.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = TricountEntity::class,
            parentColumns = ["id"],
            childColumns = ["tricountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["paidBy"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tricountId"), Index("paidBy")]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val tricountId: Int,
    val name: String,
    val description: String,
    val amount: Double,
    val paidBy: Int, // User ID who paid
    val createdAt: Long = System.currentTimeMillis(),
    val category: String = "General" // Optional category
)

// Data class for displaying expense with payer details
data class ExpenseWithDetails(
    val id: Int,
    val tricountId: Int,
    val name: String,
    val description: String,
    val amount: Double,
    val paidBy: Int,
    val paidByName: String,
    val paidByEmail: String,
    val createdAt: Long,
    val category: String
)