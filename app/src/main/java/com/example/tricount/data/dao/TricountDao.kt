package com.example.tricount.data.dao

import androidx.room.*
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.data.entity.TricountMemberCrossRef

@Dao
interface TricountDao {

    // Insert a new tricount
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTricount(tricount: TricountEntity): Long

    // Get tricount by ID
    @Query("SELECT * FROM tricounts WHERE id = :tricountId")
    suspend fun getTricountById(tricountId: Int): TricountEntity?

    // Get tricount by join code
    @Query("SELECT * FROM tricounts WHERE joinCode = :joinCode")
    suspend fun getTricountByJoinCode(joinCode: String): TricountEntity?

    // Get all tricounts where user is creator OR member
    @Query("""
        SELECT DISTINCT t.* FROM tricounts t
        LEFT JOIN tricount_members tm ON t.id = tm.tricountId
        WHERE t.creatorId = :userId OR tm.userId = :userId
        ORDER BY t.id DESC
    """)
    suspend fun getTricountsForUser(userId: Int): List<TricountEntity>

    // Delete tricount by ID
    @Query("DELETE FROM tricounts WHERE id = :tricountId")
    suspend fun deleteTricountById(tricountId: Int)

    // Delete all tricounts (for testing)
    @Query("DELETE FROM tricounts")
    suspend fun deleteAllTricounts()

    // Add a member to a tricount
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMember(crossRef: TricountMemberCrossRef)

    // Helper function to add member
    suspend fun addMember(userId: Int, tricountId: Int) {
        addMember(TricountMemberCrossRef(userId = userId, tricountId = tricountId))
    }

    // Check if user is already a member
    @Query("SELECT * FROM tricount_members WHERE userId = :userId AND tricountId = :tricountId")
    suspend fun getMembership(userId: Int, tricountId: Int): TricountMemberCrossRef?

    // Get all members of a tricount
    @Query("SELECT * FROM tricount_members WHERE tricountId = :tricountId")
    suspend fun getMembersOfTricount(tricountId: Int): List<TricountMemberCrossRef>

    // Get members with their details for a tricount
    @Query("""
        SELECT 
            u.id as userId,
            u.name as name,
            u.email as email,
            CASE WHEN t.creatorId = u.id THEN 1 ELSE 0 END as isCreator
        FROM users u
        INNER JOIN tricount_members tm ON u.id = tm.userId
        INNER JOIN tricounts t ON tm.tricountId = t.id
        WHERE t.id = :tricountId
        UNION
        SELECT 
            u.id as userId,
            u.name as name,
            u.email as email,
            1 as isCreator
        FROM users u
        INNER JOIN tricounts t ON u.id = t.creatorId
        WHERE t.id = :tricountId
        ORDER BY isCreator DESC, name ASC
    """)
    suspend fun getTricountMembersWithDetails(tricountId: Int): List<com.example.tricount.data.entity.MemberWithDetails>

    // Get user by email (for adding members)
    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): com.example.tricount.data.entity.UserEntity?

    // Remove a member from a tricount
    @Query("DELETE FROM tricount_members WHERE userId = :userId AND tricountId = :tricountId")
    suspend fun removeMember(userId: Int, tricountId: Int)
}