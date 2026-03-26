package com.example.tricount.data.repository

import com.example.tricount.data.dao.TricountDao
import com.example.tricount.data.entity.ExpenseEntity
import com.example.tricount.data.entity.ExpenseSplitEntity
import com.example.tricount.data.entity.ExpenseWithDetails
import com.example.tricount.data.entity.MemberWithDetails
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.data.entity.TricountMemberCrossRef
import com.example.tricount.util.ConnectivityObserver
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Single source of truth for tricount + member + expense data.
 *
 * READ  → always from Room (works offline)
 * WRITE → Room first, then Firestore if online (fire-and-forget, never blocks UI)
 */
class TricountRepository(
    private val tricountDao: TricountDao,
    private val connectivity: ConnectivityObserver
) {

    private val firestore = FirebaseFirestore.getInstance()

    // ── Tricount reads (Room only) ────────────────────────────────────────────

    suspend fun getTricountsForUser(userId: Int): List<TricountEntity> =
        tricountDao.getTricountsForUser(userId)

    suspend fun getArchivedTricountsForUser(userId: Int): List<TricountEntity> =
        tricountDao.getArchivedTricountsForUser(userId)

    suspend fun getTricountById(tricountId: Int): TricountEntity? =
        tricountDao.getTricountById(tricountId)

    suspend fun getTricountByJoinCode(joinCode: String): TricountEntity? =
        tricountDao.getTricountByJoinCode(joinCode)

    // ── Tricount writes (Room + Firestore when online) ────────────────────────

    suspend fun insertTricount(tricount: TricountEntity): Long {
        val localId = tricountDao.insertTricount(tricount)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                firestore.collection("tricounts")
                    .document(localId.toString())
                    .set(tricount.toFirestoreMap(localId))
                    .await()
            }
        }
        return localId
    }

    suspend fun updateTricount(tricountId: Int, name: String, description: String) {
        tricountDao.updateTricount(tricountId, name, description)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                firestore.collection("tricounts").document(tricountId.toString())
                    .update(mapOf("name" to name, "description" to description))
                    .await()
            }
        }
    }

    suspend fun updateTricountFull(tricountId: Int, name: String, description: String, emoji: String) {
        tricountDao.updateTricountFull(tricountId, name, description, emoji)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                firestore.collection("tricounts").document(tricountId.toString())
                    .update(mapOf("name" to name, "description" to description, "emoji" to emoji))
                    .await()
            }
        }
    }

    suspend fun deleteTricount(tricountId: Int) {
        tricountDao.deleteTricountById(tricountId)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                firestore.collection("tricounts").document(tricountId.toString())
                    .delete().await()
            }
        }
    }

    suspend fun archiveTricount(tricountId: Int) {
        tricountDao.archiveTricount(tricountId)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                firestore.collection("tricounts").document(tricountId.toString())
                    .update("isArchived", true).await()
            }
        }
    }

    suspend fun unarchiveTricount(tricountId: Int) {
        tricountDao.unarchiveTricount(tricountId)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                firestore.collection("tricounts").document(tricountId.toString())
                    .update("isArchived", false).await()
            }
        }
    }

    // ── Member reads ──────────────────────────────────────────────────────────

    suspend fun getTricountMembersWithDetails(tricountId: Int): List<MemberWithDetails> =
        tricountDao.getTricountMembersWithDetails(tricountId)

    suspend fun isMember(tricountId: Int, userId: Int): Boolean =
        tricountDao.isMember(tricountId, userId) > 0

    // ── Member writes ─────────────────────────────────────────────────────────

    suspend fun addMember(crossRef: TricountMemberCrossRef) {
        tricountDao.addMember(crossRef)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                firestore.collection("tricounts").document(crossRef.tricountId.toString())
                    .update("members", com.google.firebase.firestore.FieldValue.arrayUnion(crossRef.userId.toString()))
                    .await()
            }
        }
    }

    suspend fun removeMember(tricountId: Int, userId: Int) {
        tricountDao.removeMember(tricountId, userId)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                firestore.collection("tricounts").document(tricountId.toString())
                    .update("members", com.google.firebase.firestore.FieldValue.arrayRemove(userId.toString()))
                    .await()
            }
        }
    }

    // ── Expense reads ─────────────────────────────────────────────────────────

    suspend fun getExpensesWithDetails(tricountId: Int): List<ExpenseWithDetails> =
        tricountDao.getExpensesWithDetails(tricountId)

    suspend fun getArchivedExpensesWithDetails(tricountId: Int): List<ExpenseWithDetails> =
        tricountDao.getArchivedExpensesWithDetails(tricountId)

    suspend fun getExpenseSplitsWithAmounts(
        expenseId: Int, totalAmount: Double, payerId: Int
    ) = tricountDao.getExpenseSplitsWithAmounts(expenseId, totalAmount, payerId)

    // ── Expense writes ────────────────────────────────────────────────────────

    suspend fun insertExpenseWithSplits(
        expense: ExpenseEntity,
        splits: List<ExpenseSplitEntity>
    ): Long {
        val localId = tricountDao.insertExpense(expense)
        val splitsWithId = splits.map { it.copy(expenseId = localId.toInt()) }
        tricountDao.insertExpenseSplits(splitsWithId)

        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                val expenseMap = expense.toFirestoreMap(localId).toMutableMap()
                expenseMap["splits"] = splitsWithId.map {
                    mapOf("userId" to it.userId.toString(), "shares" to it.shares)
                }
                firestore.collection("tricounts")
                    .document(expense.tricountId.toString())
                    .collection("expenses")
                    .document(localId.toString())
                    .set(expenseMap)
                    .await()
            }
        }
        return localId
    }

    suspend fun deleteExpense(expenseId: Int, tricountId: Int) {
        tricountDao.deleteExpenseSplits(expenseId)
        tricountDao.deleteExpense(expenseId)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                firestore.collection("tricounts").document(tricountId.toString())
                    .collection("expenses").document(expenseId.toString())
                    .delete().await()
            }
        }
    }

    suspend fun archiveExpense(expenseId: Int, tricountId: Int) {
        tricountDao.archiveExpense(expenseId)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                firestore.collection("tricounts").document(tricountId.toString())
                    .collection("expenses").document(expenseId.toString())
                    .update("isArchived", true).await()
            }
        }
    }

    suspend fun unarchiveExpense(expenseId: Int, tricountId: Int) {
        tricountDao.unarchiveExpense(expenseId)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                firestore.collection("tricounts").document(tricountId.toString())
                    .collection("expenses").document(expenseId.toString())
                    .update("isArchived", false).await()
            }
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    suspend fun toggleFavorite(userId: Int, tricountId: Int) =
        tricountDao.toggleFavorite(userId, tricountId)

    suspend fun isFavorite(userId: Int, tricountId: Int): Boolean =
        tricountDao.isFavorite(userId, tricountId) > 0

    suspend fun getFavoriteTricounts(userId: Int): List<TricountEntity> =
        tricountDao.getFavoriteTricounts(userId)
}

// ── Firestore mapping helpers ─────────────────────────────────────────────────

private fun TricountEntity.toFirestoreMap(localId: Long) = mapOf(
    "localId"     to localId.toString(),
    "name"        to name,
    "description" to description,
    "creatorId"   to creatorId.toString(),
    "joinCode"    to joinCode,
    "emoji"       to emoji,
    "createdAt"   to createdAt,
    "isArchived"  to isArchived,
    "members"     to listOf(creatorId.toString())
)

private fun ExpenseEntity.toFirestoreMap(localId: Long) = mapOf(
    "localId"     to localId.toString(),
    "tricountId"  to tricountId.toString(),
    "name"        to name,
    "description" to description,
    "amount"      to amount,
    "paidBy"      to paidBy.toString(),
    "category"    to category,
    "createdAt"   to createdAt,
    "isArchived"  to isArchived
)