package com.example.tricount.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Stores the share ratio each member has for a given expense.
 * e.g. Holly: 2 shares, Krithika: 3 shares on a $500 expense
 * → Holly owes $200, Krithika owes $300
 */
@Entity(
    tableName = "expense_splits",
    primaryKeys = ["expenseId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("expenseId"), Index("userId")]
)
data class ExpenseSplitEntity(
    val expenseId: Int,
    val userId: Int,
    val shares: Int = 1  // number of parts this user owes
)

/** Convenience data class returned from DAO joins */
data class ExpenseSplitWithUser(
    val userId: Int,
    val userName: String,
    val userEmail: String,
    val shares: Int,
    // Computed in ViewModel, not from DB
    val amount: Double = 0.0,
    val isPayerToo: Boolean = false
)