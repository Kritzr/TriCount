package com.example.tricount.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tricount.data.entity.TricountEntity

@Dao
interface TricountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTricount(tricount: TricountEntity): Long //tricount row to inserted - retruns the row id

    @Query("SELECT * FROM tricounts") //get all the tricounts
    suspend fun getAllTricounts(): List<TricountEntity>

    // Get tricounts where user is a member
    @Query("""
        SELECT t.* FROM tricounts t
        INNER JOIN tricount_members tm ON t.id = tm.tricountId
        WHERE tm.userId = :userId
        ORDER BY t.createdAt DESC
    """) //lists all the tricounts the user is a member of sorted at creation time
    suspend fun getTricountsForUser(userId: Int): List<TricountEntity>

    @Query("SELECT * FROM tricounts WHERE joinCode = :code") //get tricount by join code
    suspend fun getTricountByCode(code: String): TricountEntity?

    @Query("SELECT * FROM tricounts WHERE id = :tricountId") //lookup with the id for the tricount
    suspend fun getTricountById(tricountId: Int): TricountEntity?

    @Query("DELETE FROM tricounts WHERE id = :tricountId") //delete the tricount
    suspend fun deleteTricount(tricountId: Int)

    @Query("DELETE FROM tricounts")
    suspend fun deleteAllTricounts()
}