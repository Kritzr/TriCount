package com.example.tricount.data.dao

import androidx.room.*
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.data.entity.TricountMemberCrossRef
import com.example.tricount.data.entity.UserEntity
import com.example.tricount.data.entity.ExpenseSplitEntity

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
    suspend fun insertMember(crossRef: TricountMemberCrossRef)

    // Helper function to add member
    suspend fun addMember(userId: Int, tricountId: Int) {
        insertMember(TricountMemberCrossRef(userId = userId, tricountId = tricountId))
    }

    // Check if user is already a member
    @Query("SELECT * FROM tricount_members WHERE userId = :userId AND tricountId = :tricountId")
    suspend fun getMembership(userId: Int, tricountId: Int): TricountMemberCrossRef?

    // Get all members of a tricount (just the cross-ref)
    @Query("SELECT * FROM tricount_members WHERE tricountId = :tricountId")
    suspend fun getMembersOfTricount(tricountId: Int): List<TricountMemberCrossRef>

    // Get user by email (for adding members)
    @Query("SELECT * FROM users WHERE email = :email COLLATE NOCASE")
    suspend fun getUserByEmail(email: String): UserEntity?

    // Remove a member from a tricount
    @Query("DELETE FROM tricount_members WHERE userId = :userId AND tricountId = :tricountId")
    suspend fun removeMember(userId: Int, tricountId: Int)

    // Get members with their details
    @Transaction
    @Query("""
        SELECT 
            u.id as userId,
            u.name as name,
            u.email as email,
            (CASE WHEN t.creatorId = u.id THEN 1 ELSE 0 END) as isCreator
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
    """)
    suspend fun getTricountMembersRaw(tricountId: Int): List<MemberQueryResult>

    suspend fun getTricountMembersWithDetails(tricountId: Int): List<com.example.tricount.data.entity.MemberWithDetails> {
        return getTricountMembersRaw(tricountId).map { result ->
            com.example.tricount.data.entity.MemberWithDetails(
                userId = result.userId,
                name = result.name,
                email = result.email,
                isCreator = result.isCreator == 1
            )
        }.sortedWith(compareByDescending<com.example.tricount.data.entity.MemberWithDetails> { it.isCreator }.thenBy { it.name })
    }

    // ===== EXPENSE OPERATIONS =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: com.example.tricount.data.entity.ExpenseEntity): Long

    @Query("SELECT * FROM expenses WHERE tricountId = :tricountId ORDER BY createdAt DESC")
    suspend fun getExpensesForTricount(tricountId: Int): List<com.example.tricount.data.entity.ExpenseEntity>

    @Query("""
        SELECT 
            e.id as id,
            e.tricountId as tricountId,
            e.name as name,
            e.description as description,
            e.amount as amount,
            e.paidBy as paidBy,
            u.name as paidByName,
            u.email as paidByEmail,
            e.createdAt as createdAt,
            e.category as category
        FROM expenses e
        INNER JOIN users u ON e.paidBy = u.id
        WHERE e.tricountId = :tricountId
        ORDER BY e.createdAt DESC
    """)
    suspend fun getExpensesWithDetails(tricountId: Int): List<com.example.tricount.data.entity.ExpenseWithDetails>

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getExpenseById(expenseId: Int): com.example.tricount.data.entity.ExpenseEntity?

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: Int)

    @Query("SELECT SUM(amount) FROM expenses WHERE tricountId = :tricountId")
    suspend fun getTotalExpenses(tricountId: Int): Double?

    // ===== EXPENSE SPLIT OPERATIONS =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseSplit(split: ExpenseSplitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseSplits(splits: List<ExpenseSplitEntity>)

    @Query("DELETE FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun deleteExpenseSplits(expenseId: Int)

    /** Raw split rows for one expense */
    @Query("""
        SELECT 
            es.userId as userId,
            u.name as userName,
            u.email as userEmail,
            es.shares as shares
        FROM expense_splits es
        INNER JOIN users u ON es.userId = u.id
        WHERE es.expenseId = :expenseId
        ORDER BY u.name ASC
    """)
    suspend fun getExpenseSplitsRaw(expenseId: Int): List<SplitQueryResult>

    /** Returns splits with computed amount per person */
    suspend fun getExpenseSplitsWithAmounts(
        expenseId: Int,
        totalAmount: Double,
        payerId: Int
    ): List<com.example.tricount.data.entity.ExpenseSplitWithUser> {
        val raw = getExpenseSplitsRaw(expenseId)
        val totalShares = raw.sumOf { it.shares }.takeIf { it > 0 } ?: 1
        return raw.map { r ->
            val amount = (r.shares.toDouble() / totalShares) * totalAmount
            com.example.tricount.data.entity.ExpenseSplitWithUser(
                userId = r.userId,
                userName = r.userName,
                userEmail = r.userEmail,
                shares = r.shares,
                amount = amount,
                isPayerToo = r.userId == payerId
            )
        }
    }

    // ===== FAVORITES OPERATIONS =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToFavorites(favorite: com.example.tricount.data.entity.TricountFavorite)

    suspend fun addToFavorites(userId: Int, tricountId: Int) {
        addToFavorites(com.example.tricount.data.entity.TricountFavorite(userId, tricountId))
    }

    @Query("DELETE FROM tricount_favorites WHERE userId = :userId AND tricountId = :tricountId")
    suspend fun removeFromFavorites(userId: Int, tricountId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM tricount_favorites WHERE userId = :userId AND tricountId = :tricountId)")
    suspend fun isFavorite(userId: Int, tricountId: Int): Boolean

    @Query("""
        SELECT DISTINCT t.* FROM tricounts t
        INNER JOIN tricount_favorites tf ON t.id = tf.tricountId
        WHERE tf.userId = :userId
        ORDER BY tf.favoritedAt DESC
    """)
    suspend fun getFavoriteTricounts(userId: Int): List<TricountEntity>

    suspend fun toggleFavorite(userId: Int, tricountId: Int): Boolean {
        return if (isFavorite(userId, tricountId)) {
            removeFromFavorites(userId, tricountId)
            false
        } else {
            addToFavorites(userId, tricountId)
            true
        }
    }
}

data class MemberQueryResult(
    val userId: Int,
    val name: String,
    val email: String,
    val isCreator: Int
)

data class SplitQueryResult(
    val userId: Int,
    val userName: String,
    val userEmail: String,
    val shares: Int
)