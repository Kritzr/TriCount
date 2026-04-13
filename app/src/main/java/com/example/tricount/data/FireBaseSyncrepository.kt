package com.example.tricount.data

import android.util.Log
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage

class FirebaseSyncRepository(
    private val db             : TricountDatabase,
    private val sessionManager : SessionManager
) {
    private val tricountDao = db.tricountDao()
    private val firestore   = FirebaseFirestore.getInstance()
    private val storage     = FirebaseStorage.getInstance()

    private val uid: String?
        get() = sessionManager.getFirebaseUid()
            ?: FirebaseAuth.getInstance().currentUser?.uid

    suspend fun uploadProfileImage(localUri: Uri): String? {
        val uid = uid ?: return null
        val storageRef = storage.reference.child("profile_pics/$uid.jpg")
        return try {
            storageRef.putFile(localUri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            firestore.collection("users").document(uid)
                .update("photoUri", downloadUrl)
                .await()
            downloadUrl
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Upload failed: ${e.message}")
            null
        }
    }

    suspend fun pullFromFirebase(localUserId: Int) {
        val uid = uid
        if (uid.isNullOrEmpty()) {
            Log.w("FirebaseSync", "pullFromFirebase: no uid, skipping")
            return
        }
        Log.d("FirebaseSync", "pullFromFirebase uid=$uid userId=$localUserId")
        try {
            val userRoot = firestore.collection("users").document(uid)

            // ── 1. Pull profile (nickname + photoUri) ─────────────────────
            val userSnap = userRoot.get().await()
            if (userSnap.exists()) {
                val remotePhotoUri = userSnap.getString("photoUri") ?: ""
                val remoteNickname = userSnap.getString("nickname") ?: ""
                Log.d("FirebaseSync", "profile: nickname=$remoteNickname photoUri=$remotePhotoUri")

                // FIX: only overwrite Room + SessionManager if Firestore has a value
                if (remotePhotoUri.isNotEmpty()) {
                    db.userDao().updatePhotoUri(localUserId, remotePhotoUri)
                    sessionManager.setProfilePhotoUri(remotePhotoUri)
                }
                if (remoteNickname.isNotEmpty()) {
                    db.userDao().updateNickname(localUserId, remoteNickname)
                    sessionManager.setNickname(remoteNickname)
                }
            }

            // ── 2. Pull tricounts ─────────────────────────────────────────
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
                        emoji       = doc.getString("emoji")       ?: "⛺"
                    ))
                    tricountDao.addMember(
                        TricountMemberCrossRef(userId = localUserId, tricountId = firestoreId)
                    )
                }

                // ── 3. Pull expenses for this tricount ────────────────────
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
                            name        = eDoc.getString("name")          ?: "",
                            description = eDoc.getString("description")   ?: "",
                            amount      = eDoc.getDouble("amount")        ?: 0.0,
                            paidBy      = eDoc.getLong("paidBy")?.toInt() ?: localUserId,
                            category    = eDoc.getString("category")      ?: "General",
                            createdAt   = eDoc.getLong("createdAt")       ?: System.currentTimeMillis(),
                            isArchived  = eDoc.getBoolean("isArchived")   ?: false
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

            Log.d("FirebaseSync", "pullFromFirebase: done")
        } catch (e: Exception) {
            Log.e("FirebaseSync", "pullFromFirebase failed: ${e.message}", e)
        }
    }

    // ── Tricount writes ───────────────────────────────────────────────────────

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
        val uid    = uid ?: run { Log.w("FirebaseSync", "pushExpense: no uid"); return }
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
            .addOnFailureListener { e -> Log.e("FirebaseSync", "deleteTricount: ${e.message}") }
    }

    fun deleteExpense(tricountId: Int, expenseId: Int) {
        val uid = uid ?: return
        firestore.collection("users").document(uid)
            .collection("tricounts").document(tricountId.toString())
            .collection("expenses").document(expenseId.toString()).delete()
            .addOnFailureListener { e -> Log.e("FirebaseSync", "deleteExpense: ${e.message}") }
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

    // ── Profile writes ────────────────────────────────────────────────────────

    fun pushProfilePhoto(photoUri: String) {
        val uid = uid ?: run { Log.w("FirebaseSync", "pushProfilePhoto: no uid"); return }
        firestore.collection("users").document(uid)
            .set(mapOf("photoUri" to photoUri), SetOptions.merge())
            .addOnSuccessListener { Log.d("FirebaseSync", "pushProfilePhoto: saved") }
            .addOnFailureListener { e -> Log.e("FirebaseSync", "pushProfilePhoto failed: ${e.message}") }
    }

    fun pushNickname(nickname: String) {
        val uid = uid ?: run { Log.w("FirebaseSync", "pushNickname: no uid"); return }
        firestore.collection("users").document(uid)
            .set(mapOf("nickname" to nickname), SetOptions.merge())
            .addOnSuccessListener { Log.d("FirebaseSync", "pushNickname: saved") }
            .addOnFailureListener { e -> Log.e("FirebaseSync", "pushNickname failed: ${e.message}") }
    }
}