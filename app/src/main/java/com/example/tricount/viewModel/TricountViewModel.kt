package com.example.tricount.viewModel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tricount.data.FirebaseSyncRepository
import com.example.tricount.data.SessionManager
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TricountViewModel(application: Application) : AndroidViewModel(application) {

    private val db             = TricountDatabase.getDatabase(application)
    private val tricountDao    = db.tricountDao()
    private val userDao        = db.userDao()
    private val paymentDao     = db.paymentDao()
    private val sessionManager = SessionManager(application)
    private val syncRepo       = FirebaseSyncRepository(db, sessionManager)

    // NOTE: pullFromFirebase is NOT called here.
    // It is called once in AuthViewModel.handleGoogleSignIn after login.
    // Calling it here caused a race condition that wiped freshly created local data.

    // ── StateFlows ────────────────────────────────────────────────────────────

    private val _tricounts         = MutableStateFlow<List<TricountEntity>>(emptyList())
    val tricounts: StateFlow<List<TricountEntity>> = _tricounts

    private val _archivedTricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val archivedTricounts: StateFlow<List<TricountEntity>> = _archivedTricounts

    private val _currentTricount   = MutableStateFlow<TricountEntity?>(null)
    val currentTricount: StateFlow<TricountEntity?> = _currentTricount

    private val _tricountMembers   = MutableStateFlow<List<MemberWithDetails>>(emptyList())
    val tricountMembers: StateFlow<List<MemberWithDetails>> = _tricountMembers

    private val _expenses          = MutableStateFlow<List<ExpenseWithDetails>>(emptyList())
    val expenses: StateFlow<List<ExpenseWithDetails>> = _expenses

    private val _archivedExpenses  = MutableStateFlow<List<ExpenseWithDetails>>(emptyList())
    val archivedExpenses: StateFlow<List<ExpenseWithDetails>> = _archivedExpenses

    private val _expenseSplits     = MutableStateFlow<Map<Int, List<ExpenseSplitWithUser>>>(emptyMap())
    val expenseSplits: StateFlow<Map<Int, List<ExpenseSplitWithUser>>> = _expenseSplits

    private val _settlements       = MutableStateFlow<List<Settlement>>(emptyList())
    val settlements: StateFlow<List<Settlement>> = _settlements

    private val _joinResult        = MutableStateFlow<JoinResult?>(null)
    val joinResult: StateFlow<JoinResult?> = _joinResult

    private val _favoriteTricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val favoriteTricounts: StateFlow<List<TricountEntity>> = _favoriteTricounts

    private val _payments          = MutableStateFlow<List<PaymentEntity>>(emptyList())
    val payments: StateFlow<List<PaymentEntity>> = _payments

    // ── Tricount CRUD ─────────────────────────────────────────────────────────

    fun loadTricounts() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                _tricounts.value = if (userId != null) tricountDao.getTricountsForUser(userId) else emptyList()
            } catch (e: Exception) {
                Log.e("TricountVM", "loadTricounts error", e)
                _tricounts.value = emptyList()
            }
        }
    }

    fun insertTricount(
        name         : String,
        description  : String,
        emoji        : String = "⛺",
        memberEmails : List<String> = emptyList(),
        onComplete   : (tricountId: Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: return@launch
                val tricount = TricountEntity(
                    name = name, description = description,
                    creatorId = userId, joinCode = generateJoinCode(), emoji = emoji
                )
                val tricountId = tricountDao.insertTricount(tricount).toInt()
                tricountDao.addMember(TricountMemberCrossRef(userId = userId, tricountId = tricountId))

                // Add pre-invited members — create placeholder if not in Room yet
                for (email in memberEmails) {
                    try {
                        val trimmed = email.trim().lowercase()
                        val member  = userDao.getUserByEmail(trimmed) ?: createPlaceholderUser(trimmed)
                        if (tricountDao.isMember(tricountId, member.id) == 0) {
                            tricountDao.addMember(TricountMemberCrossRef(userId = member.id, tricountId = tricountId))
                        }
                    } catch (e: Exception) { Log.e("TricountVM", "insertTricount: failed adding $email", e) }
                }

                val saved = tricountDao.getTricountById(tricountId)
                if (saved != null) syncRepo.pushTricount(saved)
                loadTricounts()
                onComplete(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "insertTricount error", e) }
        }
    }

    fun deleteTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.deleteTricountById(tricountId)
                syncRepo.deleteTricount(tricountId)
                loadTricounts(); loadArchivedTricounts()
            } catch (e: Exception) { Log.e("TricountVM", "deleteTricount error", e) }
        }
    }

    fun archiveTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.archiveTricount(tricountId)
                syncRepo.updateTricountArchived(tricountId, true)
                loadTricounts()
            } catch (e: Exception) { Log.e("TricountVM", "archiveTricount error", e) }
        }
    }

    fun unarchiveTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.unarchiveTricount(tricountId)
                syncRepo.updateTricountArchived(tricountId, false)
                loadArchivedTricounts(); loadTricounts()
            } catch (e: Exception) { Log.e("TricountVM", "unarchiveTricount error", e) }
        }
    }

    fun editTricount(tricountId: Int, name: String, description: String) {
        viewModelScope.launch {
            try {
                tricountDao.updateTricount(tricountId, name, description)
                syncRepo.updateTricountFields(tricountId, name, description, "⛺")
                loadTricounts()
                _currentTricount.value = tricountDao.getTricountById(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "editTricount error", e) }
        }
    }

    fun editTricountFull(tricountId: Int, name: String, description: String, emoji: String) {
        viewModelScope.launch {
            try {
                tricountDao.updateTricountFull(tricountId, name, description, emoji)
                syncRepo.updateTricountFields(tricountId, name, description, emoji)
                loadTricounts()
                _currentTricount.value = tricountDao.getTricountById(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "editTricountFull error", e) }
        }
    }

    fun duplicateTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                val userId   = sessionManager.getUserId() ?: return@launch
                val original = tricountDao.getTricountById(tricountId) ?: return@launch
                val copy = original.copy(id = 0, name = "${original.name} (copy)",
                    joinCode = generateJoinCode(), createdAt = System.currentTimeMillis(), isArchived = false)
                val newId = tricountDao.insertTricount(copy).toInt()
                tricountDao.addMember(TricountMemberCrossRef(userId = userId, tricountId = newId))
                loadTricounts()
            } catch (e: Exception) { Log.e("TricountVM", "duplicateTricount error", e) }
        }
    }

    fun loadArchivedTricounts() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: return@launch
                _archivedTricounts.value = tricountDao.getArchivedTricountsForUser(userId)
            } catch (e: Exception) { _archivedTricounts.value = emptyList() }
        }
    }

    // ── Tricount Details ──────────────────────────────────────────────────────

    fun loadTricountDetails(tricountId: Int) {
        viewModelScope.launch {
            try {
                _currentTricount.value = tricountDao.getTricountById(tricountId)
                loadTricountMembers(tricountId)
                loadExpenses(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "loadTricountDetails error", e) }
        }
    }

    fun loadTricountMembers(tricountId: Int) {
        viewModelScope.launch {
            try {
                _tricountMembers.value = tricountDao.getTricountMembersWithDetails(tricountId)
            } catch (e: Exception) { _tricountMembers.value = emptyList() }
        }
    }

    // ── Members ───────────────────────────────────────────────────────────────

    fun addMemberByEmail(tricountId: Int, email: String, onResult: (AddMemberResult) -> Unit) {
        viewModelScope.launch {
            try {
                val trimmed = email.trim().lowercase()
                if (trimmed.isEmpty()) {
                    onResult(AddMemberResult.Error("Please enter an email address")); return@launch
                }
                // Find existing Room user OR create a placeholder so they show in splits immediately.
                // When they actually sign in, AuthViewModel.findOrCreateRoomUser matches by email
                // and reuses this same row — their real name/photo will sync then.
                val user = userDao.getUserByEmail(trimmed) ?: createPlaceholderUser(trimmed)

                if (tricountDao.isMember(tricountId, user.id) > 0) {
                    onResult(AddMemberResult.Error("${user.name} is already a member")); return@launch
                }
                tricountDao.addMember(TricountMemberCrossRef(userId = user.id, tricountId = tricountId))
                loadTricountMembers(tricountId)
                onResult(AddMemberResult.Success(user.name))
            } catch (e: Exception) {
                Log.e("TricountVM", "addMemberByEmail error", e)
                onResult(AddMemberResult.Error("Failed to add member: ${e.message}"))
            }
        }
    }

    fun removeMember(userId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.removeMember(userId, tricountId)
                loadTricountMembers(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "removeMember error", e) }
        }
    }

    // ── Join by code ──────────────────────────────────────────────────────────

    fun joinTricountByCode(joinCode: String) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: run {
                    _joinResult.value = JoinResult.Error("Not logged in"); return@launch
                }
                val tricount = tricountDao.getTricountByJoinCode(joinCode)
                if (tricount == null) { _joinResult.value = JoinResult.Error("No tricount found with code: $joinCode"); return@launch }
                if (tricountDao.isMember(tricount.id, userId) > 0) {
                    _joinResult.value = JoinResult.Error("You are already a member"); return@launch
                }
                tricountDao.addMember(TricountMemberCrossRef(userId = userId, tricountId = tricount.id))
                loadTricounts()
                _joinResult.value = JoinResult.Success(tricount)
            } catch (e: Exception) { _joinResult.value = JoinResult.Error("Failed to join: ${e.message}") }
        }
    }

    fun resetJoinResult() { _joinResult.value = null }

    // ── Expenses ──────────────────────────────────────────────────────────────

    fun loadExpenses(tricountId: Int) {
        viewModelScope.launch {
            try {
                val expensesList = tricountDao.getExpensesWithDetails(tricountId)
                _expenses.value  = expensesList
                _payments.value  = paymentDao.getPaymentsForTricount(tricountId)
                loadAllSplits(expensesList)
                _archivedExpenses.value = try {
                    tricountDao.getArchivedExpensesWithDetails(tricountId)
                } catch (e: Exception) { emptyList() }
            } catch (e: Exception) {
                _expenses.value = emptyList(); _archivedExpenses.value = emptyList()
            }
        }
    }

    private suspend fun loadAllSplits(expenses: List<ExpenseWithDetails>) {
        val map = mutableMapOf<Int, List<ExpenseSplitWithUser>>()
        for (expense in expenses) map[expense.id] = tricountDao.getExpenseSplitsWithAmounts(expense.id, expense.amount, expense.paidBy)
        _expenseSplits.value = map
        recomputeSettlements()
    }

    fun addExpense(
        tricountId  : Int,
        name        : String,
        description : String,
        amount      : Double,
        paidBy      : Int,
        category    : String = "General",
        sharesMap   : Map<Int, Int>,
        onResult    : (AddExpenseResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (name.isBlank())               { onResult(AddExpenseResult.Error("Expense name is required")); return@launch }
                if (amount <= 0)                  { onResult(AddExpenseResult.Error("Amount must be greater than 0")); return@launch }
                if (sharesMap.values.all { it == 0 }) { onResult(AddExpenseResult.Error("At least one member must have shares > 0")); return@launch }

                val expense   = ExpenseEntity(tricountId = tricountId, name = name, description = description, amount = amount, paidBy = paidBy, category = category)
                val expenseId = tricountDao.insertExpense(expense).toInt()
                val splits    = sharesMap.filter { it.value > 0 }.map { (uid, shares) -> ExpenseSplitEntity(expenseId = expenseId, userId = uid, shares = shares) }
                if (splits.isNotEmpty()) tricountDao.insertExpenseSplits(splits)
                syncRepo.pushExpense(tricountId, expense.copy(id = expenseId), splits)
                loadExpenses(tricountId)
                onResult(AddExpenseResult.Success)
            } catch (e: Exception) {
                Log.e("TricountVM", "addExpense error", e)
                onResult(AddExpenseResult.Error("Failed to add expense: ${e.message}"))
            }
        }
    }

    fun deleteExpense(expenseId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.deleteExpenseSplits(expenseId)
                tricountDao.deleteExpense(expenseId)
                syncRepo.deleteExpense(tricountId, expenseId)
                loadExpenses(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "deleteExpense error", e) }
        }
    }

    fun archiveExpense(expenseId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.archiveExpense(expenseId)
                syncRepo.updateExpenseArchived(tricountId, expenseId, true)
                loadExpenses(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "archiveExpense error", e) }
        }
    }

    fun unarchiveExpense(expenseId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.unarchiveExpense(expenseId)
                syncRepo.updateExpenseArchived(tricountId, expenseId, false)
                loadExpenses(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "unarchiveExpense error", e) }
        }
    }

    // ── Settlement logic ──────────────────────────────────────────────────────

    private fun recomputeSettlements() {
        val netBalance = mutableMapOf<Int, Double>()
        val nameMap    = mutableMapOf<Int, String>()
        for (member in _tricountMembers.value) nameMap[member.userId] = member.name
        for (expense in _expenses.value) {
            netBalance[expense.paidBy] = (netBalance[expense.paidBy] ?: 0.0) + expense.amount
            nameMap[expense.paidBy]    = expense.paidByName
            val splits = _expenseSplits.value[expense.id]
            if (!splits.isNullOrEmpty()) {
                for (split in splits) {
                    netBalance[split.userId] = (netBalance[split.userId] ?: 0.0) - split.amount
                    nameMap[split.userId]    = split.userName
                }
            } else {
                netBalance[expense.paidBy] = (netBalance[expense.paidBy] ?: 0.0) - expense.amount
            }
        }
        for (payment in _payments.value) {
            netBalance[payment.fromUserId] = (netBalance[payment.fromUserId] ?: 0.0) + payment.amount
            netBalance[payment.toUserId]   = (netBalance[payment.toUserId]   ?: 0.0) - payment.amount
            nameMap[payment.fromUserId]    = payment.fromUserName
            nameMap[payment.toUserId]      = payment.toUserName
        }
        val creditors = netBalance.filter { it.value >  0.01 }.map { it.key to  it.value }.toMutableList()
        val debtors   = netBalance.filter { it.value < -0.01 }.map { it.key to -it.value }.toMutableList()
        val result    = mutableListOf<Settlement>()
        var ci = 0; var di = 0
        while (ci < creditors.size && di < debtors.size) {
            val (creditorId, creditAmt) = creditors[ci]
            val (debtorId,   debtAmt)   = debtors[di]
            val settled = minOf(creditAmt, debtAmt)
            result.add(Settlement(debtorId, nameMap[debtorId] ?: "", creditorId, nameMap[creditorId] ?: "", settled))
            creditors[ci] = creditorId to (creditAmt - settled)
            debtors[di]   = debtorId   to (debtAmt   - settled)
            if (creditors[ci].second <= 0.01) ci++
            if (debtors[di].second   <= 0.01) di++
        }
        _settlements.value = result
    }

    fun markSettlementPaid(
        tricountId: Int, fromUserId: Int, fromUserName: String,
        toUserId: Int, toUserName: String, amount: Double, onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val payment = PaymentEntity(tricountId = tricountId, fromUserId = fromUserId,
                    fromUserName = fromUserName, toUserId = toUserId, toUserName = toUserName, amount = amount)
                paymentDao.insertPayment(payment)
                _payments.value = paymentDao.getPaymentsForTricount(tricountId)
                recomputeSettlements()
                onDone()
            } catch (e: Exception) { Log.e("TricountVM", "markSettlementPaid error", e) }
        }
    }

    fun loadPayments(tricountId: Int) {
        viewModelScope.launch {
            try {
                _payments.value = paymentDao.getPaymentsForTricount(tricountId)
                recomputeSettlements()
            } catch (e: Exception) { _payments.value = emptyList() }
        }
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    /**
     * Uploads the photo to Firebase Storage → gets an https:// URL that never expires
     * → saves it to Room (permanent per-user), SessionManager (session cache),
     * and Firestore (syncs across devices/logins).
     *
     * Using Firebase Storage means the URL works after logout/login on any device.
     * A local content:// URI only works on the device that picked the file and
     * is invalidated when the app's data is cleared or permissions change.
     */
    fun uploadProfilePhoto(uri: android.net.Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: run { onDone(null); return@launch }
                val uid    = sessionManager.getFirebaseUid()
                    ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    ?: run { onDone(null); return@launch }

                Log.d("TricountVM", "uploadProfilePhoto: uploading for uid=$uid userId=$userId")

                val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance()
                    .reference
                    .child("profile_photos/$uid.jpg")

                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                Log.d("TricountVM", "uploadProfilePhoto: got downloadUrl=$downloadUrl")

                // 1. Room — permanent, survives logout/login for this user
                userDao.updatePhotoUri(userId, downloadUrl)
                // 2. SessionManager — fast cache for current session
                sessionManager.setProfilePhotoUri(downloadUrl)
                // 3. Firestore — syncs to other devices on next login
                syncRepo.pushProfilePhoto(downloadUrl)

                onDone(downloadUrl)
            } catch (e: Exception) {
                Log.e("TricountVM", "uploadProfilePhoto error: ${e.message}", e)
                onDone(null)
            }
        }
    }

    fun saveNickname(nickname: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: return@launch
                userDao.updateNickname(userId, nickname)
                sessionManager.setNickname(nickname)
                syncRepo.pushNickname(nickname)
                onDone()
            } catch (e: Exception) { Log.e("TricountVM", "saveNickname error", e) }
        }
    }

    /**
     * Used only for REMOVING the photo (pass empty string).
     * For setting a photo, always use uploadProfilePhoto() instead.
     */
    fun savePhotoUri(uri: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: return@launch
                userDao.updatePhotoUri(userId, uri)
                if (uri.isEmpty()) sessionManager.clearProfilePhotoUri()
                else               sessionManager.setProfilePhotoUri(uri)
                syncRepo.pushProfilePhoto(uri)
                onDone()
            } catch (e: Exception) { Log.e("TricountVM", "savePhotoUri error", e) }
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    fun toggleFavorite(userId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.toggleFavorite(userId, tricountId)
                loadFavoriteTricounts(userId)
            } catch (e: Exception) { Log.e("TricountVM", "toggleFavorite error", e) }
        }
    }

    fun loadFavoriteTricounts(userId: Int) {
        viewModelScope.launch {
            try {
                _favoriteTricounts.value = tricountDao.getFavoriteTricounts(userId)
            } catch (e: Exception) { _favoriteTricounts.value = emptyList() }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun createPlaceholderUser(email: String): UserEntity {
        val displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        val newId = userDao.insertUser(
            UserEntity(email = email, password = "", name = displayName)
        ).toInt()
        Log.d("TricountVM", "createPlaceholderUser: id=$newId for $email")
        return userDao.getUserById(newId)!!
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}

// ── Sealed classes & data classes ─────────────────────────────────────────────

data class Settlement(
    val fromUserId   : Int,
    val fromUserName : String,
    val toUserId     : Int,
    val toUserName   : String,
    val amount       : Double
)

sealed class JoinResult {
    data class Success(val tricount: TricountEntity) : JoinResult()
    data class Error(val message: String)            : JoinResult()
}

sealed class AddExpenseResult {
    object Success                        : AddExpenseResult()
    data class Error(val message: String) : AddExpenseResult()
}

sealed class AddMemberResult {
    data class Success(val memberName: String) : AddMemberResult()
    data class Error(val message: String)      : AddMemberResult()
}