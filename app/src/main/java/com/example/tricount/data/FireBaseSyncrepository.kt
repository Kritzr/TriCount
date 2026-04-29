package com.example.tricount.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

class FirebaseSyncRepository(
    private val db            : TricountDatabase,
    private val sessionManager: SessionManager
) {
    private val tricountDao = db.tricountDao()
    private val firestore   = FirebaseFirestore.getInstance()
    private val auth        = FirebaseAuth.getInstance()

    private val uid: String?
        get() = auth.currentUser?.uid ?: sessionManager.getFirebaseUid()

    // ── FCM token ─────────────────────────────────────────────────────────────

    suspend fun registerFcmToken() {
        val uid = uid ?: run {
            Log.w("FirebaseSync", "registerFcmToken: no uid"); return
        }
        try {
            val token = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .token.await()
            firestore.collection("users").document(uid)
                .update("fcmToken", token).await()
            Log.d("FirebaseSync", "FCM token registered: $token")
        } catch (e: Exception) {
            Log.e("FirebaseSync", "registerFcmToken failed: ${e.message}")
        }
    }

    // ── Profile photo — Base64 stored in Firestore ────────────────────────────

    suspend fun uploadProfileImageToFirestore(context: Context, uri: Uri): String? {
        val uid = uid ?: return null
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original    = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val maxDim = 200
            val scale  = minOf(
                maxDim.toFloat() / original.width,
                maxDim.toFloat() / original.height,
                1f
            )
            val scaled = Bitmap.createScaledBitmap(
                original,
                (original.width  * scale).toInt(),
                (original.height * scale).toInt(),
                true
            )

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            firestore.collection("users").document(uid)
                .set(mapOf("photoBase64" to base64), SetOptions.merge()).await()

            sessionManager.getUserId()?.let { db.userDao().updatePhotoUri(it, base64) }
            sessionManager.setProfilePhotoUri(base64)

            Log.d("FirebaseSync", "Photo saved as Base64 (${baos.size()} bytes)")
            base64
        } catch (e: Exception) {
            Log.e("FirebaseSync", "uploadProfileImageToFirestore failed: ${e.message}", e)
            null
        }
    }

    // ── Pull from Firestore on login ──────────────────────────────────────────

    suspend fun pullFromFirebase(localUserId: Int) {
        val uid = uid
        if (uid.isNullOrEmpty()) {
            Log.w("FirebaseSync", "pullFromFirebase: no uid"); return
        }
        try {
            // 1. Pull and restore user profile
            val userSnap = firestore.collection("users").document(uid).get().await()
            if (userSnap.exists()) {
                val remotePhoto    = userSnap.getString("photoBase64") ?: ""
                val remoteNickname = userSnap.getString("nickname")    ?: ""
                if (remotePhoto.isNotEmpty()) {
                    db.userDao().updatePhotoUri(localUserId, remotePhoto)
                    sessionManager.setProfilePhotoUri(remotePhoto)
                }
                if (remoteNickname.isNotEmpty()) {
                    db.userDao().updateNickname(localUserId, remoteNickname)
                    sessionManager.setNickname(remoteNickname)
                }
            }

            // Keep Firestore user doc fresh
            db.userDao().getUserById(localUserId)?.let { user ->
                val profileData = mutableMapOf<String, Any>(
                    "uid"   to uid,
                    "name"  to user.name,
                    "email" to user.email
                )
                if (!user.nickname.isNullOrEmpty())  profileData["nickname"]    = user.nickname
                if (!user.photoUri.isNullOrEmpty())  profileData["photoBase64"] = user.photoUri
                firestore.collection("users").document(uid)
                    .set(profileData, SetOptions.merge()).await()
            }

            // 2. Tricounts where uid is a member
            val tricountSnaps = firestore.collection("tricounts")
                .whereArrayContains("members", uid).get().await()

            Log.d("FirebaseSync", "pullFromFirebase: ${tricountSnaps.size()} tricounts")

            for (doc in tricountSnaps.documents) {
                val firestoreId = doc.id.toIntOrNull() ?: continue
                val existing    = runCatching { tricountDao.getTricountById(firestoreId) }.getOrNull()

                val isCreator = doc.getString("creatorUid") == uid
                val category  = if (isCreator) "created" else "joined"

                if (existing == null) {
                    tricountDao.insertTricount(TricountEntity(
                        id          = firestoreId,
                        name        = doc.getString("name")        ?: "",
                        description = doc.getString("description") ?: "",
                        creatorId   = localUserId,
                        joinCode    = doc.getString("joinCode")    ?: "",
                        createdAt   = doc.getLong("createdAt")     ?: System.currentTimeMillis(),
                        isArchived  = doc.getBoolean("isArchived") ?: false,
                        emoji       = doc.getString("emoji")       ?: "⛺",
                        category    = category
                    ))
                    tricountDao.addMember(
                        TricountMemberCrossRef(userId = localUserId, tricountId = firestoreId)
                    )
                } else {
                    tricountDao.updateTricountFull(
                        firestoreId,
                        doc.getString("name")        ?: existing.name,
                        doc.getString("description") ?: existing.description,
                        doc.getString("emoji")       ?: existing.emoji
                    )
                }

                try {
                    val metaSnap = firestore.collection("tricounts").document(doc.id)
                        .collection("userMeta").document(uid).get().await()
                    if (metaSnap.exists()) {
                        val isFav = metaSnap.getBoolean("isFavorite") ?: false
                        if (isFav) {
                            if (tricountDao.isFavorite(localUserId, firestoreId) == 0) {
                                tricountDao.toggleFavorite(localUserId, firestoreId)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FirebaseSync", "pullFavorite failed for $firestoreId: ${e.message}")
                }

                // 3. Expenses
                val expenseSnaps = firestore.collection("tricounts")
                    .document(doc.id).collection("expenses").get().await()

                for (eDoc in expenseSnaps.documents) {
                    val expenseId = eDoc.id.toIntOrNull() ?: eDoc.getLong("id")?.toInt() ?: continue
                    if (runCatching { tricountDao.getExpenseById(expenseId) }.getOrNull() != null) continue

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

                    val splitSnaps = firestore.collection("tricounts")
                        .document(doc.id).collection("expenses")
                        .document(eDoc.id).collection("splits").get().await()

                    val splits = splitSnaps.documents.mapNotNull { sDoc ->
                        ExpenseSplitEntity(
                            expenseId = expenseId,
                            userId    = sDoc.getLong("userId")?.toInt() ?: return@mapNotNull null,
                            shares    = sDoc.getLong("shares")?.toInt() ?: 1
                        )
                    }
                    if (splits.isNotEmpty()) tricountDao.insertExpenseSplits(splits)
                }
            }
            Log.d("FirebaseSync", "pullFromFirebase: done")
        } catch (e: Exception) {
            Log.e("FirebaseSync", "pullFromFirebase failed: ${e.message}", e)
        }
    }

    // ── Tricount writes ───────────────────────────────────────────────────────

    suspend fun pushTricount(tricount: TricountEntity) {
        val uid = uid ?: run { Log.w("FirebaseSync", "pushTricount: no uid"); return }

        val memberUids = tricountDao.getMemberFirebaseUids(tricount.id)
            .toMutableList()
            .also { if (!it.contains(uid)) it.add(0, uid) }

        try {
            firestore.collection("tricounts").document(tricount.id.toString())
                .set(mapOf(
                    "id"          to tricount.id,
                    "name"        to tricount.name,
                    "description" to tricount.description,
                    "joinCode"    to tricount.joinCode,
                    "createdAt"   to tricount.createdAt,
                    "isArchived"  to tricount.isArchived,
                    "emoji"       to tricount.emoji,
                    "creatorUid"  to uid,
                    "members"     to memberUids,
                    "category"    to tricount.category
                ), SetOptions.merge()).await()
            Log.d("FirebaseSync", "pushTricount ok id=${tricount.id}")
        } catch (e: Exception) {
            Log.e("FirebaseSync", "pushTricount failed: ${e.message}")
        }
    }

    fun pushExpense(
        tricountId : Int,
        expense    : ExpenseEntity,
        splits     : List<ExpenseSplitEntity> = emptyList()
    ) {
        val expRef = firestore.collection("tricounts")
            .document(tricountId.toString())
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
        firestore.collection("tricounts").document(tricountId.toString()).delete()
            .addOnFailureListener { e -> Log.e("FirebaseSync", "deleteTricount: ${e.message}") }
    }

    fun deleteExpense(tricountId: Int, expenseId: Int) {
        firestore.collection("tricounts").document(tricountId.toString())
            .collection("expenses").document(expenseId.toString()).delete()
            .addOnFailureListener { e -> Log.e("FirebaseSync", "deleteExpense: ${e.message}") }
    }

    fun updateTricountArchived(tricountId: Int, isArchived: Boolean) {
        firestore.collection("tricounts").document(tricountId.toString())
            .update("isArchived", isArchived)
            .addOnFailureListener { e -> Log.e("FirebaseSync", "updateTricountArchived: ${e.message}") }
    }

    fun updateExpenseArchived(tricountId: Int, expenseId: Int, isArchived: Boolean) {
        firestore.collection("tricounts").document(tricountId.toString())
            .collection("expenses").document(expenseId.toString())
            .update("isArchived", isArchived)
            .addOnFailureListener { e -> Log.e("FirebaseSync", "updateExpenseArchived: ${e.message}") }
    }

    fun updateTricountFields(tricountId: Int, name: String, description: String, emoji: String) {
        firestore.collection("tricounts").document(tricountId.toString())
            .update(mapOf("name" to name, "description" to description, "emoji" to emoji))
            .addOnFailureListener { e -> Log.e("FirebaseSync", "updateTricountFields: ${e.message}") }
    }

    fun updateTricountMembers(tricountId: Int, memberUid: String) {
        firestore.collection("tricounts").document(tricountId.toString())
            .update("members", FieldValue.arrayUnion(memberUid))
            .addOnFailureListener { e -> Log.e("FirebaseSync", "updateTricountMembers: ${e.message}") }
    }

    fun updateTricountFavorite(tricountId: Int, isFavorite: Boolean) {
        val uid = uid ?: return
        firestore.collection("tricounts").document(tricountId.toString())
            .collection("userMeta").document(uid)
            .set(mapOf("isFavorite" to isFavorite), SetOptions.merge())
            .addOnFailureListener { e -> Log.e("FirebaseSync", "updateTricountFavorite: ${e.message}") }
    }

    // ── Profile writes ────────────────────────────────────────────────────────

    fun pushProfilePhoto(base64OrUrl: String) {
        val uid = uid ?: return
        val field = if (base64OrUrl.startsWith("http")) "photoUri" else "photoBase64"
        firestore.collection("users").document(uid)
            .set(mapOf(field to base64OrUrl), SetOptions.merge())
            .addOnFailureListener { e -> Log.e("FirebaseSync", "pushProfilePhoto failed: ${e.message}") }
    }

    fun pushNickname(nickname: String) {
        val uid = uid ?: return
        firestore.collection("users").document(uid)
            .set(mapOf("nickname" to nickname), SetOptions.merge())
            .addOnFailureListener { e -> Log.e("FirebaseSync", "pushNickname failed: ${e.message}") }
    }

    suspend fun pushFullUserProfile(name: String, email: String) {
        val uid = uid ?: return
        try {
            val data = mutableMapOf<String, Any>(
                "uid"       to uid,
                "name"      to name,
                "email"     to email,
                "createdAt" to System.currentTimeMillis()
            )
            val existing = firestore.collection("users").document(uid).get().await()
            if (!existing.exists()) {
                data["nickname"]    = ""
                data["photoBase64"] = ""
            }
            firestore.collection("users").document(uid)
                .set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e("FirebaseSync", "pushFullUserProfile failed: ${e.message}")
        }
    }

    // ── Email verification ────────────────────────────────────────────────────

    /**
     * Sends Firebase's built-in email verification link to the currently
     * signed-in user. Returns true on success, false on failure.
     *
     * Call this from EmailVerificationActivity (or AuthViewModel) after
     * FirebaseAuth.createUserWithEmailAndPassword() / signInWithEmailAndPassword()
     * has succeeded but before allowing access to the app.
     */
    suspend fun sendVerificationEmail(): Boolean {
        return try {
            auth.currentUser?.sendEmailVerification()?.await()
            Log.d("FirebaseSync", "sendVerificationEmail: sent")
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "sendVerificationEmail failed: ${e.message}")
            false
        }
    }

    /**
     * Reloads the currently signed-in Firebase user to get the latest
     * server-side [emailVerified] flag, then returns it.
     *
     * Always call reload() first — the local cached value is only updated
     * when the user object is refreshed from the server.
     *
     * @return true if the user's email is verified, false otherwise.
     */
    suspend fun isEmailVerified(): Boolean {
        return try {
            val user = auth.currentUser ?: return false
            user.reload().await()
            val verified = user.isEmailVerified
            Log.d("FirebaseSync", "isEmailVerified: $verified")
            verified
        } catch (e: Exception) {
            Log.e("FirebaseSync", "isEmailVerified failed: ${e.message}")
            false
        }
    }

    // ── Join request / approval ───────────────────────────────────────────────

    suspend fun submitJoinRequest(joinCode: String): String? {
        var currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            var waited = 0
            while (currentUid == null && waited < 10) {
                kotlinx.coroutines.delay(500)
                currentUid = auth.currentUser?.uid
                waited++
            }
        }
        val uid = currentUid ?: sessionManager.getFirebaseUid() ?: run {
            Log.e("FirebaseSync", "submitJoinRequest: no authenticated uid")
            return null
        }
        Log.d("FirebaseSync", "submitJoinRequest: uid=$uid joinCode=$joinCode")
        return try {
            val snap = firestore.collection("tricounts")
                .whereEqualTo("joinCode", joinCode.uppercase().trim()).get().await()
            Log.d("FirebaseSync", "submitJoinRequest: query returned ${snap.size()} docs")
            if (snap.isEmpty) {
                val snap2 = firestore.collection("tricounts")
                    .whereEqualTo("joinCode", joinCode.lowercase().trim()).get().await()
                if (snap2.isEmpty) {
                    Log.w("FirebaseSync", "submitJoinRequest: no tricount with joinCode=$joinCode")
                    return null
                }
                return processJoinRequest(snap2.documents.first(), uid)
            }

            processJoinRequest(snap.documents.first(), uid)
        } catch (e: Exception) {
            Log.e("FirebaseSync", "submitJoinRequest failed: ${e.message}", e); null
        }
    }

    private suspend fun processJoinRequest(tricountDoc: com.google.firebase.firestore.DocumentSnapshot, uid: String): String? {
        val tricountId   = tricountDoc.id
        val tricountName = tricountDoc.getString("name")       ?: ""
        val creatorUid   = tricountDoc.getString("creatorUid") ?: ""

        Log.d("FirebaseSync", "processJoinRequest: tricountId=$tricountId name=$tricountName creatorUid=$creatorUid")

        @Suppress("UNCHECKED_CAST")
        if ((tricountDoc.get("members") as? List<String>)?.contains(uid) == true)
            return "ALREADY_MEMBER:$tricountName"

        val existingReq = firestore.collection("joinRequests").document(tricountId)
            .collection("pending").document(uid).get().await()
        if (existingReq.exists()) {
            Log.d("FirebaseSync", "processJoinRequest: already pending")
            return tricountName
        }

        val userSnap       = firestore.collection("users").document(uid).get().await()
        val requesterName  = userSnap.getString("name")  ?: sessionManager.getUserName()  ?: "Unknown"
        val requesterEmail = userSnap.getString("email") ?: sessionManager.getUserEmail() ?: ""

        Log.d("FirebaseSync", "processJoinRequest: requester=$requesterName email=$requesterEmail")

        val batch = firestore.batch()

        val pendingRef = firestore.collection("joinRequests").document(tricountId)
            .collection("pending").document(uid)
        batch.set(pendingRef, mapOf(
            "uid"          to uid,
            "name"         to requesterName,
            "email"        to requesterEmail,
            "requestedAt"  to System.currentTimeMillis(),
            "tricountId"   to tricountId,
            "tricountName" to tricountName,
            "status"       to "pending"
        ))

        if (creatorUid.isNotEmpty()) {
            val notifRef = firestore.collection("notifications").document()
            batch.set(notifRef, mapOf(
                "toUid"        to creatorUid,
                "fromUid"      to uid,
                "fromName"     to requesterName,
                "type"         to "JOIN_REQUEST",
                "tricountId"   to tricountId,
                "tricountName" to tricountName,
                "message"      to "$requesterName wants to join $tricountName",
                "createdAt"    to System.currentTimeMillis(),
                "read"         to false
            ))
        }

        batch.commit().await()
        Log.d("FirebaseSync", "processJoinRequest: batch committed ok")
        return tricountName
    }

    suspend fun approveJoinRequest(tricountId: String, requesterUid: String): Boolean {
        return try {
            val tricountRef = firestore.collection("tricounts").document(tricountId)
            val tricountDoc = tricountRef.get().await()
            val name = tricountDoc.getString("name") ?: ""

            val batch = firestore.batch()

            batch.update(tricountRef, "members", FieldValue.arrayUnion(requesterUid))

            val pendingRef = firestore.collection("joinRequests").document(tricountId)
                .collection("pending").document(requesterUid)
            batch.delete(pendingRef)

            val notifRef = firestore.collection("notifications").document()
            batch.set(notifRef, mapOf(
                "toUid"        to requesterUid,
                "fromUid"      to (uid ?: ""),
                "type"         to "JOIN_APPROVED",
                "tricountId"   to tricountId,
                "tricountName" to name,
                "message"      to "Your request to join $name was approved!",
                "createdAt"    to System.currentTimeMillis(),
                "read"         to false
            ))

            batch.commit().await()
            Log.d("FirebaseSync", "approveJoinRequest ok tricountId=$tricountId uid=$requesterUid")
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "approveJoinRequest failed: ${e.message}"); false
        }
    }

    suspend fun rejectJoinRequest(tricountId: String, requesterUid: String): Boolean {
        return try {
            val batch = firestore.batch()

            val pendingRef = firestore.collection("joinRequests").document(tricountId)
                .collection("pending").document(requesterUid)
            batch.delete(pendingRef)

            val name = firestore.collection("tricounts").document(tricountId)
                .get().await().getString("name") ?: ""
            val notifRef = firestore.collection("notifications").document()
            batch.set(notifRef, mapOf(
                "toUid"        to requesterUid,
                "fromUid"      to (uid ?: ""),
                "type"         to "JOIN_REJECTED",
                "tricountId"   to tricountId,
                "tricountName" to name,
                "message"      to "Your request to join $name was declined.",
                "createdAt"    to System.currentTimeMillis(),
                "read"         to false
            ))

            batch.commit().await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "rejectJoinRequest failed: ${e.message}"); false
        }
    }

    suspend fun getPendingJoinRequests(): List<Map<String, Any>> {
        val uid = uid ?: return emptyList()
        return try {
            val myTricounts = firestore.collection("tricounts")
                .whereEqualTo("creatorUid", uid).get().await()
            val requests = mutableListOf<Map<String, Any>>()
            for (doc in myTricounts.documents) {
                firestore.collection("joinRequests").document(doc.id)
                    .collection("pending").get().await()
                    .documents.forEach { req ->
                        val data = (req.data ?: return@forEach).toMutableMap()
                        data["docId"] = "${doc.id}_${req.id}"
                        requests.add(data)
                    }
            }
            requests
        } catch (e: Exception) {
            Log.e("FirebaseSync", "getPendingJoinRequests failed: ${e.message}"); emptyList()
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    suspend fun getNotifications(): List<AppNotification> {
        val uid = auth.currentUser?.uid ?: sessionManager.getFirebaseUid() ?: return emptyList()
        return try {
            firestore.collection("notifications")
                .whereEqualTo("toUid", uid)
                .limit(50).get().await()
                .documents.mapNotNull { doc -> doc.toAppNotification() }
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
                .take(20)
        } catch (e: Exception) {
            Log.e("FirebaseSync", "getNotifications failed: ${e.message}", e); emptyList()
        }
    }

    fun listenForNotifications(onUpdate: (List<AppNotification>) -> Unit): ListenerRegistration {
        val uid = auth.currentUser?.uid ?: sessionManager.getFirebaseUid()
        if (uid == null) {
            Log.w("FirebaseSync", "listenForNotifications: no uid, returning no-op listener")
            return firestore.collection("notifications").limit(1).addSnapshotListener { _, _ -> }
        }
        return firestore.collection("notifications")
            .whereEqualTo("toUid", uid)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("FirebaseSync", "listenForNotifications error: ${err.message}", err)
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toAppNotification() }
                    ?.distinctBy { it.id }
                    ?.sortedByDescending { it.createdAt }
                    ?.take(20)
                    ?: emptyList()
                onUpdate(list)
            }
    }

    suspend fun markNotificationRead(notificationId: String) {
        try {
            firestore.collection("notifications").document(notificationId)
                .update("read", true).await()
        } catch (e: Exception) {
            Log.e("FirebaseSync", "markNotificationRead failed: ${e.message}")
        }
    }

    fun notifyMembersAdded(tricountId: Int, tricountName: String, newMemberEmail: String) {
        val uid = uid ?: return
        firestore.collection("tricounts").document(tricountId.toString()).get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val members = (doc.get("members") as? List<String>) ?: return@addOnSuccessListener
                val batch = firestore.batch()
                members.filter { it != uid }.forEach { memberUid ->
                    batch.set(firestore.collection("notifications").document(), mapOf(
                        "toUid"        to memberUid,
                        "fromUid"      to uid,
                        "type"         to "MEMBER_ADDED",
                        "tricountId"   to tricountId.toString(),
                        "tricountName" to tricountName,
                        "message"      to "$newMemberEmail has been added to $tricountName",
                        "createdAt"    to System.currentTimeMillis(),
                        "read"         to false
                    ))
                }
                batch.commit()
                    .addOnFailureListener { e -> Log.e("FirebaseSync", "notifyMembersAdded: ${e.message}") }
            }
    }

    suspend fun lookupFirebaseUidByEmail(email: String): String? {
        return try {
            val snap = firestore.collection("users")
                .whereEqualTo("email", email.trim().lowercase())
                .limit(1).get().await()
            snap.documents.firstOrNull()?.getString("uid")
        } catch (e: Exception) {
            Log.w("FirebaseSync", "lookupFirebaseUidByEmail failed: ${e.message}")
            null
        }
    }

    fun addMemberToTricount(tricountId: Int, memberUid: String) {
        firestore.collection("tricounts").document(tricountId.toString())
            .update("members", FieldValue.arrayUnion(memberUid))
            .addOnFailureListener { e -> Log.e("FirebaseSync", "addMemberToTricount: ${e.message}") }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun com.google.firebase.firestore.DocumentSnapshot.toAppNotification(): AppNotification? {
        return try {
            AppNotification(
                id           = id,
                type         = getString("type")         ?: "",
                message      = getString("message")      ?: "",
                tricountId   = getString("tricountId")   ?: "",
                tricountName = getString("tricountName") ?: "",
                fromUid      = getString("fromUid")      ?: "",
                fromName     = getString("fromName")     ?: "",
                createdAt    = getLong("createdAt")      ?: 0L,
                read         = getBoolean("read")        ?: false
            )
        } catch (e: Exception) { null }
    }
}

// ── Data classes ──────────────────────────────────────────────────────────────

data class ChatMessage(
    val id         : String,
    val senderUid  : String,
    val senderName : String,
    val text       : String,
    val timestamp  : Long
)

data class AppNotification(
    val id           : String,
    val type         : String,
    val message      : String,
    val tricountId   : String,
    val tricountName : String,
    val fromUid      : String,
    val fromName     : String,
    val createdAt    : Long,
    val read         : Boolean
)

// ── LoginResult — returned by AuthViewModel.login() ───────────────────────────

sealed class LoginResult {
    /** Sign-in succeeded and email is verified. */
    data class Success(val firebaseUid: String) : LoginResult()
    /** Sign-in succeeded but the email is NOT yet verified. */
    object NeedsVerification                    : LoginResult()
    /** Sign-in failed (wrong password, network, etc.). */
    data class Failure(val message: String)     : LoginResult()
}