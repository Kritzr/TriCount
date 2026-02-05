package com.example.tricount.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tricounts")
data class TricountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,
    val description: String,

    // Unique code for joining this tricount
    val joinCode: String,

    // Creator's user ID
    val creatorId: Int,

    val createdAt: Long = System.currentTimeMillis()
)