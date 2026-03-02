package com.example.tricount.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id       : Int    = 0,
    val name     : String,
    val email    : String,
    val password : String,
    val nickname : String? = null,   // stored as TEXT, nullable
    val photoUri : String? = null    // URI string e.g. "content://media/..." NOT the image itself
)