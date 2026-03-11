package com.example.tricount.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records a real-world payment between two members of a tricount.
 * When fromUserId pays toUserId [amount], a row is inserted here.
 * The settlement engine subtracts these from outstanding debts before
 * computing the final "Settle Up" list.
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity        = TricountEntity::class,
            parentColumns = ["id"],
            childColumns  = ["tricountId"],
            onDelete      = ForeignKey.CASCADE,
            onUpdate      = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity        = UserEntity::class,
            parentColumns = ["id"],
            childColumns  = ["fromUserId"],
            onDelete      = ForeignKey.CASCADE,
            onUpdate      = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity        = UserEntity::class,
            parentColumns = ["id"],
            childColumns  = ["toUserId"],
            onDelete      = ForeignKey.CASCADE,
            onUpdate      = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index("tricountId"),
        Index("fromUserId"),
        Index("toUserId")
    ]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id          : Int    = 0,
    val tricountId  : Int,
    val fromUserId  : Int,    // who paid
    val fromUserName: String,
    val toUserId    : Int,    // who received
    val toUserName  : String,
    val amount      : Double,
    val note        : String  = "Settlement payment",
    val paidAt      : Long    = System.currentTimeMillis()
)