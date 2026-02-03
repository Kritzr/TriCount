package com.example.tricount.data.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tricounts")
data class TricountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val description: String
)
