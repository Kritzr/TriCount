package com.example.tricount.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// Junction table for user's favorite tricounts
@Entity(
    tableName = "tricount_favorites",
    primaryKeys = ["userId", "tricountId"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TricountEntity::class,
            parentColumns = ["id"],
            childColumns = ["tricountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId"), Index("tricountId")]
)
data class TricountFavorite(
    val userId: Int,
    val tricountId: Int,
    val favoritedAt: Long = System.currentTimeMillis()
)