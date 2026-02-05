package com.example.tricount.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tricount.data.entity.TricountMemberEntity

@Dao
interface TricountMemberDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: TricountMemberEntity)

    // Get all tricounts for a specific user
    @Query("SELECT tricountId FROM tricount_members WHERE userId = :userId")
    suspend fun getTricountIdsForUser(userId: Int): List<Int>

    // Get all users in a specific tricount
    @Query("SELECT userId FROM tricount_members WHERE tricountId = :tricountId")
    suspend fun getUserIdsInTricount(tricountId: Int): List<Int>

    // Check if user is already a member of a tricount
    @Query("SELECT COUNT(*) FROM tricount_members WHERE userId = :userId AND tricountId = :tricountId")
    suspend fun isMemberOfTricount(userId: Int, tricountId: Int): Int

    // Remove user from tricount
    @Query("DELETE FROM tricount_members WHERE userId = :userId AND tricountId = :tricountId")
    suspend fun removeMember(userId: Int, tricountId: Int)

    // Get creator of a tricount
    @Query("SELECT userId FROM tricount_members WHERE tricountId = :tricountId AND isCreator = 1")
    suspend fun getCreatorId(tricountId: Int): Int?
}