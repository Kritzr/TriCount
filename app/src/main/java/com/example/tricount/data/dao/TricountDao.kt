package com.example.tricount.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tricount.data.entity.ExpenseEntity
import com.example.tricount.data.entity.ExpenseSplitEntity
import com.example.tricount.data.entity.ExpenseSplitWithUser
import com.example.tricount.data.entity.ExpenseWithDetails
import com.example.tricount.data.entity.MemberWithDetails
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.data.entity.TricountFavorite
import com.example.tricount.data.entity.TricountMemberCrossRef

@Dao
interface TricountDao {

    // ── Tricount CRUD ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTricount(tricount: TricountEntity): Long

    @Query("SELECT * FROM tricounts WHERE id = :tricountId")
    suspend fun getTricountById(tricountId: Int): TricountEntity?

    @Query("SELECT * FROM tricounts WHERE joinCode = :joinCode LIMIT 1")
    suspend fun getTricountByJoinCode(joinCode: String): TricountEntity?

    @Query("DELETE FROM tricounts WHERE id = :tricountId")
    suspend fun deleteTricountById(tricountId: Int)

    @Query("UPDATE tricounts SET isArchived = 1 WHERE id = :tricountId")
    suspend fun archiveTricount(tricountId: Int)

    @Query("UPDATE tricounts SET isArchived = 0 WHERE id = :tricountId")
    suspend fun unarchiveTricount(tricountId: Int)

    @Query("UPDATE tricounts SET name = :name, description = :description WHERE id = :tricountId")
    suspend fun updateTricount(tricountId: Int, name: String, description: String)

    @Query("UPDATE tricounts SET name = :name, description = :description, emoji = :emoji WHERE id = :tricountId")
    suspend fun updateTricountFull(tricountId: Int, name: String, description: String, emoji: String)

    @Query("UPDATE tricounts SET emoji = :emoji WHERE id = :tricountId")
    suspend fun updateTricountEmoji(tricountId: Int, emoji: String)

    /** All non-archived tricounts the user belongs to. */
    @Query("""
        SELECT t.* FROM tricounts t
        INNER JOIN tricount_members tm ON t.id = tm.tricountId
        WHERE tm.userId = :userId AND t.isArchived = 0
        ORDER BY t.id DESC
    """)
    suspend fun getTricountsForUser(userId: Int): List<TricountEntity>

    /** Archived tricounts for the user. */
    @Query("""
        SELECT t.* FROM tricounts t
        INNER JOIN tricount_members tm ON t.id = tm.tricountId
        WHERE tm.userId = :userId AND t.isArchived = 1
        ORDER BY t.id DESC
    """)
    suspend fun getArchivedTricountsForUser(userId: Int): List<TricountEntity>

    // ── Members ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMember(crossRef: TricountMemberCrossRef)

    @Query("DELETE FROM tricount_members WHERE tricountId = :tricountId AND userId = :userId")
    suspend fun removeMember(tricountId: Int, userId: Int)

    @Query("""
        SELECT u.id AS userId, u.name, u.email,
               CASE WHEN t.creatorId = u.id THEN 1 ELSE 0 END AS isCreator
        FROM users u
        INNER JOIN (
            SELECT DISTINCT tm.userId FROM tricount_members tm WHERE tm.tricountId = :tricountId
            UNION
            SELECT DISTINCT es.userId FROM expense_splits es
                INNER JOIN expenses e ON es.expenseId = e.id
                WHERE e.tricountId = :tricountId AND e.isArchived = 0
            UNION
            SELECT DISTINCT e2.paidBy AS userId FROM expenses e2
                WHERE e2.tricountId = :tricountId AND e2.isArchived = 0
        ) AS all_members ON u.id = all_members.userId
        INNER JOIN tricounts t ON t.id = :tricountId
        ORDER BY isCreator DESC, u.name ASC
    """)
    suspend fun getTricountMembersWithDetails(tricountId: Int): List<MemberWithDetails>

    @Query("SELECT COUNT(*) FROM tricount_members WHERE tricountId = :tricountId AND userId = :userId")
    suspend fun isMember(tricountId: Int, userId: Int): Int

    // ── Expenses ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Query("SELECT * FROM expenses WHERE id = :expenseId LIMIT 1")
    suspend fun getExpenseById(expenseId: Int): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE tricountId = :tricountId")
    suspend fun getExpensesForTricountOnce(tricountId: Int): List<ExpenseEntity>

    @Query("SELECT * FROM tricounts")
    suspend fun getAllTricountsOnce(): List<TricountEntity>

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: Int)

    // ← isArchived = 1, does NOT delete from DB, just hides from list
    @Query("UPDATE expenses SET isArchived = 1 WHERE id = :expenseId")
    suspend fun archiveExpense(expenseId: Int)

    /** Expenses for a tricount joined with payer name/email. Excludes archived. */
    @Query("""
        SELECT e.id, e.tricountId, e.name, e.description, e.amount,
               e.paidBy, u.name AS paidByName, u.email AS paidByEmail,
               e.createdAt, e.category, e.isArchived
        FROM expenses e
        INNER JOIN users u ON e.paidBy = u.id
        WHERE e.tricountId = :tricountId AND e.isArchived = 0
        ORDER BY e.createdAt DESC
    """)
    suspend fun getExpensesWithDetails(tricountId: Int): List<ExpenseWithDetails>

    /** Archived expenses for a tricount. */
    @Query("""
        SELECT e.id, e.tricountId, e.name, e.description, e.amount,
               e.paidBy, u.name AS paidByName, u.email AS paidByEmail,
               e.createdAt, e.category, e.isArchived
        FROM expenses e
        INNER JOIN users u ON e.paidBy = u.id
        WHERE e.tricountId = :tricountId AND e.isArchived = 1
        ORDER BY e.createdAt DESC
    """)
    suspend fun getArchivedExpensesWithDetails(tricountId: Int): List<ExpenseWithDetails>

    @Query("UPDATE expenses SET isArchived = 0 WHERE id = :expenseId")
    suspend fun unarchiveExpense(expenseId: Int)

    // ── Expense Splits ───────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseSplits(splits: List<ExpenseSplitEntity>)

    @Query("DELETE FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun deleteExpenseSplits(expenseId: Int)

    @Query("""
        SELECT es.userId, u.name AS userName, u.email AS userEmail,
               es.shares,
               0.0 AS amount,
               CASE WHEN es.userId = :payerId THEN 1 ELSE 0 END AS isPayerToo
        FROM expense_splits es
        INNER JOIN users u ON es.userId = u.id
        WHERE es.expenseId = :expenseId
        ORDER BY isPayerToo DESC, u.name ASC
    """)
    suspend fun getExpenseSplitsRaw(expenseId: Int, payerId: Int): List<ExpenseSplitWithUser>

    suspend fun getExpenseSplitsWithAmounts(
        expenseId   : Int,
        totalAmount : Double,
        payerId     : Int
    ): List<ExpenseSplitWithUser> {
        val raw         = getExpenseSplitsRaw(expenseId, payerId)
        val totalShares = raw.sumOf { it.shares }.coerceAtLeast(1)
        return raw.map { split ->
            split.copy(amount = (split.shares.toDouble() / totalShares) * totalAmount)
        }
    }

    // ── Favorites ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: TricountFavorite)

    @Query("DELETE FROM tricount_favorites WHERE userId = :userId AND tricountId = :tricountId")
    suspend fun removeFavorite(userId: Int, tricountId: Int)

    @Query("SELECT COUNT(*) FROM tricount_favorites WHERE userId = :userId AND tricountId = :tricountId")
    suspend fun isFavorite(userId: Int, tricountId: Int): Int

    @Query("""
        SELECT t.* FROM tricounts t
        INNER JOIN tricount_favorites tf ON t.id = tf.tricountId
        WHERE tf.userId = :userId
        ORDER BY tf.favoritedAt DESC
    """)
    suspend fun getFavoriteTricounts(userId: Int): List<TricountEntity>

    suspend fun toggleFavorite(userId: Int, tricountId: Int) {
        if (isFavorite(userId, tricountId) > 0) {
            removeFavorite(userId, tricountId)
        } else {
            addFavorite(TricountFavorite(
                userId      = userId,
                tricountId  = tricountId,
                favoritedAt = System.currentTimeMillis()
            ))
        }
    }
}