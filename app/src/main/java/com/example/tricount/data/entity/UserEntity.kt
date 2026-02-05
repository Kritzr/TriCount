package com.example.tricount.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val email: String,
    val password: String,  // In production, this should be hashed
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)