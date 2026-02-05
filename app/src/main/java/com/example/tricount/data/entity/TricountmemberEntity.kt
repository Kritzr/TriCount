package com.example.tricount.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tricount_members",
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
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tricountId"]), Index(value = ["userId"])]
)
data class TricountMemberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val tricountId: Int,
    val userId: Int,
    val isCreator: Boolean = false,  // Track who created the Tricount
    val joinedAt: Long = System.currentTimeMillis()
)