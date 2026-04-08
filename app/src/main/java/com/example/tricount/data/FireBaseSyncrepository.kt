package com.example.tricount.data

import android.util.Log
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirebaseSyncRepository(
    private val db             : TricountDatabase,
    private val sessionManager : SessionManager
) {
    private val tricountDao = db.tricountDao()
    private val paymentDao  = db.paymentDao()
    private val firestore   = FirebaseFirestore.getInstance()

    private val uid: String?
        get() = sessionManager.getFirebaseUid()
            ?: FirebaseAuth.getInstance().currentUser?.uid

    suspend fun pullFromFirebase(localUserId: Int) {
        val uid = uid
        if (uid.isNullOrEmpty()) {
            Log.w("FirebaseSync", "pullFromFirebase: no uid, skipping")
            return
        }
        Log.d("FirebaseSync", "pullFromFirebase uid=$uid userId=$localUserId")
        try {
            val userRoot      = firestore.collection("users").document(uid)
            val tricountSnaps = userRoot.collection("tricounts").get().await()
            Log.d("FirebaseSync", "pullFromFirebase: ${tricountSnaps.size()} tricounts")

            for (doc in tricountSnaps.documents) {
                val firestoreId = doc.getLong("id")?.toInt() ?: continue
                val existing    = runCatching { tricountDao.getTricountById(firestoreId) }.getOrNull()
                if (existing == null) {
                    tricountDao.insertTricount(TricountEntity(
                        id          = firestoreId,
                        name        = doc.getString("name")        ?: "",
                        description = doc.getString("description") ?: "",
                        creatorId   = localUserId,
                        joinCode    = doc.getString("joinCode")    ?: "",
                        createdAt   = doc.getLong("createdAt")     ?: System.currentTimeMillis(),
                        isArchived  = doc.getBoolean("isArchived") ?: false,
                        emoji       = doc.getString("emoji")       ?: ""
                    ))
                    tricountDao.addMember(
                        TricountMemberCrossRef(userId = localUserId, tricountId = firestoreId)
                    )
                }

                val expenseSnaps = userRoot
                    .collection("tricounts").document(doc.id)
                    .collection("expenses").get().await()

                for (eDoc in expenseSnaps.documents) {
                    val expenseId   = eDoc.getLong("id")?.toInt() ?: continue
                    val existingExp = runCatching { tricountDao.getExpenseById(expenseId) }.getOrNull()
                    if (existingExp == null) {
                        tricountDao.insertExpense(ExpenseEntity(
                            id          = expenseId,
                            tricountId  = firestoreId,
                            name        = eDoc.getString("name")        ?: "",
                            description = eDoc.getString("description") ?: "",
                            amount      = eDoc.getDouble("amount")      ?: 0.0,
                            paidBy      = eDoc.getLong("paidBy")?.toInt() ?: localUserId,
                            category    = eDoc.getString("category")    ?: "General",
                            createdAt   = eDoc.getLong("createdAt")     ?: System.currentTimeMillis(),
                            isArchived  = eDoc.getBoolean("isArchived") ?: false
                        ))
                        val splitSnaps = userRoot
                            .collection("tricounts").document(doc.id)
                            .collection("expenses").document(eDoc.id)
                            .collection("splits").get().await()
                        val splits = splitSnaps.documents.mapNotNull { sDoc ->
                            val userId = sDoc.getLong("userId")?.toInt() ?: return@mapNotNull null
                            val shares = sDoc.getLong("shares")?.toInt() ?: 1
                            ExpenseSplitEntity(expenseId = expenseId, userId = userId, shares = shares)
                        }
                        if (splits.isNotEmpty()) tricountDao.insertExpenseSplits(splits)
                    }
                }
            }
            // Also restore profile photo and nickname
            pullProfileFromFirebase(localUserId)

            Log.d("FirebaseSync", "pullFromFirebase: done")
        } catch (e: Exception) {
            Log.e("FirebaseSync", "pullFromFirebase failed: ${e.message}", e)
        }
    }

    fun pushTricount(tricount: TricountEntity) {
        val uid = uid ?: run { Log.w("FirebaseSync", "pushTricount: no uid"); return }
        firestore.collection("users").document(uid)
            .collection("tricounts").document(tricount.id.toString())
            .set(mapOf(
                "id"          to tricount.id,
                "name"        to tricount.name,
                "description" to tricount.description,
                "joinCode"    to tricount.joinCode,
                "createdAt"   to tricount.createdAt,
                "isArchived"  to tricount.isArchived,
                "emoji"       to tricount.emoji
            ), SetOptions.merge())
            .addOnSuccessListener { Log.d("FirebaseSync", "pushTricount ok id=${tricount.id}") }
            .addOnFailureListener { e -> Log.e("FirebaseSync", "pushTricount failed: ${e.message}") }
    }

    fun pushExpense(tricountId: Int, expense: ExpenseEntity, splits: List<ExpenseSplitEntity> = emptyList()) {
        val uid = uid ?: run { Log.w("FirebaseSync", "pushExpense: no uid"); return }
        val expRef = firestore.collection("users").document(uid)
            .collection("tricounts").document(tricountId.toString())
            .collection("expenses").document(expense.id.toString())
        expRef.set(mapOf(
            "id"          to expense.id,
            "tricountId"  to expense.tricountId,
            "name"        to expense.name,
            "description" to expense.description,
            "amount"      to expense.amount,
            "paidBy"      to expense.paidBy,
            "category"    to expense.category,
            "createdAt"   to expense.createdAt,
            "isArchived"  to expense.isArchived
        ), SetOptions.merge())
            .addOnSuccessListener { Log.d("FirebaseSync", "pushExpense ok id=${expense.id}") }
            .addOnFailureListener { e -> Log.e("FirebaseSync", "pushExpense failed: ${e.message}") }
        splits.forEach { split ->
            expRef.collection("splits").document(split.userId.toString())
                .set(mapOf("userId" to split.userId, "shares" to split.shares), SetOptions.merge())
                .addOnFailureListener { e -> Log.e("FirebaseSync", "pushSplit failed: ${e.message}") }
        }
    }

    fun deleteTricount(tricountId: Int) {
        val uid = uid ?: return
        firestore.collection("users").document(uid)
            .collection("tricounts").document(tricountId.toString()).delete()
            .addOnFailureListener { e -> Log.e("FirebaseSync", "deleteTricount failed: ${e.message}") }
    }

    fun deleteExpense(tricountId: Int, expenseId: Int) {
        val uid = uid ?: return
        firestore.collection("users").document(uid)
            .collection("tricounts").document(tricountId.toString())
            .collection("expenses").document(expenseId.toString()).delete()
            .addOnFailureListener { e -> Log.e("FirebaseSync", "deleteExpense failed: ${e.message}") }
    }

    fun updateTricountArchived(tricountId: Int, isArchived: Boolean) {
        val uid = uid ?: return
        firestore.collection("users").document(uid)
            .collection("tricounts").document(tricountId.toString())
            .update("isArchived", isArchived)
            .addOnFailureListener { e -> Log.e("FirebaseSync", "updateTricountArchived: ${e.message}") }
    }

    fun updateExpenseArchived(tricountId: Int, expenseId: Int, isArchived: Boolean) {
        val uid = uid ?: return
        firestore.collection("users").document(uid)
            .collection("tricounts").document(tricountId.toString())
            .collection("expenses").document(expenseId.toString())
            .update("isArchived", isArchived)
            .addOnFailureListener { e -> Log.e("FirebaseSync", "updateExpenseArchived: ${e.message}") }
    }

    fun updateTricountFields(tricountId: Int, name: String, description: String, emoji: String) {
        val uid = uid ?: return
        firestore.collection("users").document(uid)
            .collection("tricounts").document(tricountId.toString())
            .update(mapOf("name" to name, "description" to description, "emoji" to emoji))
            .addOnFailureListener { e -> Log.e("FirebaseSync", "updateTricountFields: ${e.message}") }
    }

    // ── Profile photo ─────────────────────────────────────────────────────────

    fun updateProfilePhoto(photoUri: String) {
        val uid = uid ?: run {
            Log.w("FirebaseSync", "updateProfilePhoto: no uid, skipping")
            return
        }
        firestore.collection("users").document(uid)
            .set(mapOf("photoUri" to photoUri), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { Log.d("FirebaseSync", "updateProfilePhoto: saved to Firestore") }
            .addOnFailureListener { e -> Log.e("FirebaseSync", "updateProfilePhoto failed: ${e.message}") }
    }

    fun updateNickname(nickname: String) {
        val uid = uid ?: return
        firestore.collection("users").document(uid)
            .set(mapOf("nickname" to nickname), com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { e -> Log.e("FirebaseSync", "updateNickname failed: ${e.message}") }
    }

    // ── Pull profile fields from Firestore (called during pullFromFirebase) ───

    suspend fun pullProfileFromFirebase(localUserId: Int) {
        val uid = uid ?: run {
            Log.w("FirebaseSync", "pullProfileFromFirebase: No UID, skipping sync")
            return
        }

        try {
            val userRoot = firestore.collection("users").document(uid)

            // 1. Fetch the user profile once
            val userSnap = userRoot.get().await()

            if (userSnap.exists()) {
                val remotePhotoUri = userSnap.getString("photoUri") ?: ""
                val remoteNickname = userSnap.getString("nickname") ?: ""

                Log.d("FirebaseSync", "Found profile: nickname=$remoteNickname, photoUri=$remotePhotoUri")

                // 2. Update local Room database
                db.userDao().updatePhotoUri(localUserId, remotePhotoUri)
                db.userDao().updateNickname(localUserId, remoteNickname)

                // 3. Update SessionManager (Source of truth for current UI)
                if (remotePhotoUri.isNotEmpty()) {
                    sessionManager.setProfilePhotoUri(remotePhotoUri)
                }
                if (remoteNickname.isNotEmpty()) {
                    sessionManager.setNickname(remoteNickname)
                }
            } else {
                Log.d("FirebaseSync", "No remote profile found for UID: $uid")
            }

            // 4. Continue to pull the rest of the data (Tricounts/Expenses)
            // Note: You must actually call your existing pullFromFirebase logic here
            // or ensure this function is called alongside it.
            pullFromFirebase(localUserId)

        } catch (e: Exception) {
            Log.e("FirebaseSync", "pullProfileFromFirebase failed: ${e.message}", e)
        }
    }
}