package com.example.tricount.data.repository

import com.example.tricount.data.dao.PaymentDao
import com.example.tricount.data.entity.PaymentEntity
import com.example.tricount.util.ConnectivityObserver
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Single source of truth for payment/settlement data.
 *
 * READ  → always from Room (works offline)
 * WRITE → Room first, then Firestore if online
 */
class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val connectivity: ConnectivityObserver
) {

    private val firestore = FirebaseFirestore.getInstance()

    // ── Reads (Room only) ─────────────────────────────────────────────────────

    suspend fun getPaymentsForTricount(tricountId: Int): List<PaymentEntity> =
        paymentDao.getPaymentsForTricount(tricountId)

    suspend fun getPaymentsByUser(tricountId: Int, userId: Int): List<PaymentEntity> =
        paymentDao.getPaymentsByUser(tricountId, userId)

    suspend fun getPaidAmount(tricountId: Int, fromUserId: Int, toUserId: Int): Double =
        paymentDao.getPaidAmount(tricountId, fromUserId, toUserId)

    // ── Writes (Room + Firestore when online) ─────────────────────────────────

    suspend fun insertPayment(payment: PaymentEntity): Long {
        val localId = paymentDao.insertPayment(payment)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                firestore.collection("tricounts")
                    .document(payment.tricountId.toString())
                    .collection("payments")
                    .document(localId.toString())
                    .set(payment.toFirestoreMap(localId))
                    .await()
            }
        }
        return localId
    }

    suspend fun clearPaymentsForTricount(tricountId: Int) {
        paymentDao.clearPaymentsForTricount(tricountId)
        if (connectivity.isCurrentlyOnline()) {
            runCatching {
                // Delete all payment documents in Firestore sub-collection
                val docs = firestore.collection("tricounts")
                    .document(tricountId.toString())
                    .collection("payments")
                    .get().await()
                docs.forEach { it.reference.delete() }
            }
        }
    }
}

// ── Firestore mapping helper ──────────────────────────────────────────────────

private fun PaymentEntity.toFirestoreMap(localId: Long) = mapOf(
    "localId"      to localId.toString(),
    "tricountId"   to tricountId.toString(),
    "fromUserId"   to fromUserId.toString(),
    "fromUserName" to fromUserName,
    "toUserId"     to toUserId.toString(),
    "toUserName"   to toUserName,
    "amount"       to amount,
    "note"         to note,
    "paidAt"       to paidAt
)