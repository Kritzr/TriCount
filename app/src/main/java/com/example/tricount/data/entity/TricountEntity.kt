package com.example.tricount.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tricounts")
data class TricountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val description: String = "",
    val creatorId: Int,
    val joinCode: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val emoji: String = "⛺"
)