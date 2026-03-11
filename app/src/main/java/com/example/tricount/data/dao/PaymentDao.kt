package com.example.tricount.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tricount.data.entity.PaymentEntity

@Dao
interface PaymentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    /** All payments for a tricount, newest first. */
    @Query("SELECT * FROM payments WHERE tricountId = :tricountId ORDER BY paidAt DESC")
    suspend fun getPaymentsForTricount(tricountId: Int): List<PaymentEntity>

    /** Payments where this user is the payer. */
    @Query("SELECT * FROM payments WHERE tricountId = :tricountId AND fromUserId = :userId ORDER BY paidAt DESC")
    suspend fun getPaymentsByUser(tricountId: Int, userId: Int): List<PaymentEntity>

    /** Net amount already paid from [fromUserId] to [toUserId] in this tricount. */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM payments
        WHERE tricountId = :tricountId
          AND fromUserId = :fromUserId
          AND toUserId   = :toUserId
    """)
    suspend fun getPaidAmount(tricountId: Int, fromUserId: Int, toUserId: Int): Double

    @Query("DELETE FROM payments WHERE tricountId = :tricountId")
    suspend fun clearPaymentsForTricount(tricountId: Int)
}