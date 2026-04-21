package com.example.tricount.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// Cross-reference table for many-to-many relationship between Users and Tricounts
@Entity(
    tableName = "tricount_members",
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
data class TricountMemberCrossRef(
    val userId: Int,
    val tricountId: Int
)

// Data class to represent a member with their details
data class MemberWithDetails(
    @ColumnInfo(name = "userId")
    val userId: Int,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "isCreator")
    val isCreator: Boolean,
    val photoUri: String
)