package com.example.tricount.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tricount.data.entity.TricountEntity

@Dao
interface TricountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTricount(tricount: TricountEntity)

    @Query("SELECT * FROM tricounts")
    suspend fun getAllTricounts(): List<TricountEntity>

    @Query("DELETE FROM tricounts")
    suspend fun deleteAllTricounts()
}
