package com.example.tricount.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id       : Int    = 0,
    val name     : String,
    val email    : String,
    val password : String,
    val createdAt: Long   = System.currentTimeMillis(),
    val nickname : String? = null,
    val photoUri : String? = null,

    // ── ADDED ──────────────────────────────────────────────────────────────────
    // Firebase Auth UID — populated on login/register, used to write Firestore members[]
    @ColumnInfo(name = "firebaseUid", defaultValue = "")
    val firebaseUid: String = ""
)