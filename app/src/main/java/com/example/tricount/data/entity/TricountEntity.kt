package com.example.tricount.data.entity

import androidx.room.ColumnInfo
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
    val emoji: String = "⛺",

    // ── ADDED ──────────────────────────────────────────────────────────────────
    // "created" if this user made the tricount, "joined" if they were added/approved
    @ColumnInfo(name = "category", defaultValue = "created")
    val category: String = "created",

    // isFavorite is per-user, stored locally; also synced to Firestore userMeta sub-collection
    @ColumnInfo(name = "isFavorite", defaultValue = "0")
    val isFavorite: Boolean = false
)