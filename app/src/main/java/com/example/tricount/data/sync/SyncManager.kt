package com.example.tricount.data.sync

import android.util.Log
import com.example.tricount.data.dao.PaymentDao
import com.example.tricount.data.dao.TricountDao
import com.example.tricount.util.ConnectivityObserver
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * SyncManager — call this ONCE after a successful Google Sign-In.
 *
 * It reads all existing Room data for the local user and pushes it to
 * Firestore so the data is available in the cloud going forward.
 *
 * After the initial sync, new writes are handled by the repositories
 * directly (Room first, then Firestore if online).
 */
class SyncManager(
    private val tricountDao: TricountDao,
    private val paymentDao: PaymentDao,
    private val connectivity: ConnectivityObserver
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "SyncManager"

    /**
     * @param localUserId  the Room integer user ID
     * @param firebaseUid  the Firebase Auth UID string
     */
    suspend fun syncLocalDataToFirestore(localUserId: Int, firebaseUid: String) {
        if (!connectivity.isCurrentlyOnline()) {
            Log.d(TAG, "Offline — skipping initial sync, will retry next login")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting sync for user $localUserId → Firebase $firebaseUid")

                val tricounts = tricountDao.getTricountsForUser(localUserId)
                Log.d(TAG, "Found ${tricounts.size} tricounts to sync")

                for (tricount in tricounts) {
                    val tricountRef = firestore.collection("tricounts")
                        .document(tricount.id.toString())

                    // Upload tricount document
                    tricountRef.set(
                        mapOf(
                            "localId"     to tricount.id.toString(),
                            "name"        to tricount.name,
                            "description" to tricount.description,
                            "creatorId"   to firebaseUid,
                            "joinCode"    to tricount.joinCode,
                            "emoji"       to tricount.emoji,
                            "createdAt"   to tricount.createdAt,
                            "isArchived"  to tricount.isArchived,
                            "members"     to listOf(firebaseUid)
                        )
                    ).await()

                    // Upload expenses for this tricount
                    val expenses = tricountDao.getExpensesWithDetails(tricount.id)
                    for (expense in expenses) {
                        val splits = tricountDao.getExpenseSplitsWithAmounts(
                            expense.id, expense.amount, expense.paidBy
                        )
                        tricountRef.collection("expenses")
                            .document(expense.id.toString())
                            .set(
                                mapOf(
                                    "localId"     to expense.id.toString(),
                                    "name"        to expense.name,
                                    "description" to expense.description,
                                    "amount"      to expense.amount,
                                    "paidBy"      to firebaseUid,
                                    "category"    to expense.category,
                                    "createdAt"   to expense.createdAt,
                                    "isArchived"  to expense.isArchived,
                                    "splits"      to splits.map {
                                        mapOf(
                                            "userId" to it.userId.toString(),
                                            "shares" to it.shares,
                                            "amount" to it.amount
                                        )
                                    }
                                )
                            ).await()
                    }

                    // Upload payments for this tricount
                    val payments = paymentDao.getPaymentsForTricount(tricount.id)
                    for (payment in payments) {
                        tricountRef.collection("payments")
                            .document(payment.id.toString())
                            .set(
                                mapOf(
                                    "localId"      to payment.id.toString(),
                                    "fromUserId"   to payment.fromUserId.toString(),
                                    "fromUserName" to payment.fromUserName,
                                    "toUserId"     to payment.toUserId.toString(),
                                    "toUserName"   to payment.toUserName,
                                    "amount"       to payment.amount,
                                    "note"         to payment.note,
                                    "paidAt"       to payment.paidAt
                                )
                            ).await()
                    }

                    Log.d(TAG, "Synced tricount '${tricount.name}' with ${expenses.size} expenses, ${payments.size} payments")
                }

                Log.d(TAG, "Sync complete")

            } catch (e: Exception) {
                // Sync failure is non-fatal — app still works offline via Room
                Log.e(TAG, "Sync failed: ${e.message}", e)
            }
        }
    }
}