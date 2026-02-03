package com.example.tricount.data

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface TricountDao {

    @Insert
    suspend fun insertTricount(tricount: TricountEntity)
}
