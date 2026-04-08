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

class TricountViewModel(application: Application) : AndroidViewModel(application) {

    private val db             = TricountDatabase.getDatabase(application)
    private val tricountDao    = db.tricountDao()
    private val userDao        = db.userDao()
    private val paymentDao     = db.paymentDao()
    private val sessionManager = SessionManager(application)
    private val syncRepo       = FirebaseSyncRepository(db, sessionManager)

    // ── State ─────────────────────────────────────────────────────────────────

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



    // ── Tricounts ─────────────────────────────────────────────────────────────

    fun loadTricounts() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                Log.d("TricountVM", "loadTricounts userId=$userId")
                _tricounts.value =
                    if (userId != null) tricountDao.getTricountsForUser(userId) else emptyList()
            } catch (e: Exception) {
                Log.e("TricountVM", "loadTricounts: ${e.message}", e)
                _tricounts.value = emptyList()
            }
        }
    }

    fun insertTricount(
        name         : String,
        description  : String,
        emoji        : String = "",
        memberEmails : List<String> = emptyList(),
        onComplete   : (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: run {
                    Log.e("TricountVM", "insertTricount: no userId"); return@launch
                }
                if (userDao.getUserById(userId) == null) {
                    Log.e("TricountVM", "insertTricount: userId=$userId not in users table"); return@launch
                }

                val tricountId = tricountDao.insertTricount(TricountEntity(
                    name = name, description = description,
                    creatorId = userId, joinCode = generateJoinCode(), emoji = emoji
                )).toInt()

                tricountDao.addMember(TricountMemberCrossRef(userId = userId, tricountId = tricountId))

                for (rawEmail in memberEmails) {
                    try {
                        val email  = rawEmail.trim().lowercase()
                        val member = userDao.getUserByEmail(email) ?: createPlaceholderUser(email)
                        if (tricountDao.isMember(tricountId, member.id) == 0) {
                            tricountDao.addMember(TricountMemberCrossRef(userId = member.id, tricountId = tricountId))
                        }
                    } catch (e: Exception) {
                        Log.e("TricountVM", "insertTricount: failed adding member $rawEmail: ${e.message}")
                    }
                }

                val saved = tricountDao.getTricountById(tricountId)
                if (saved != null) syncRepo.pushTricount(saved)
                loadTricounts()
                onComplete(tricountId)
            } catch (e: Exception) {
                Log.e("TricountVM", "insertTricount: ${e.message}", e)
            }
        }
    }

    fun deleteTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.deleteTricountById(tricountId)
                syncRepo.deleteTricount(tricountId)
                loadTricounts(); loadArchivedTricounts()
            } catch (e: Exception) { Log.e("TricountVM", "deleteTricount: ${e.message}", e) }
        }
    }

    fun archiveTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.archiveTricount(tricountId)
                syncRepo.updateTricountArchived(tricountId, true)
                loadTricounts()
            } catch (e: Exception) { Log.e("TricountVM", "archiveTricount: ${e.message}", e) }
        }
    }

    fun unarchiveTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.unarchiveTricount(tricountId)
                loadArchivedTricounts(); loadTricounts()
            } catch (e: Exception) { Log.e("TricountVM", "unarchiveTricount: ${e.message}", e) }
        }
    }

    fun editTricount(tricountId: Int, name: String, description: String) {
        viewModelScope.launch {
            try {
                tricountDao.updateTricount(tricountId, name, description)
                syncRepo.updateTricountFields(tricountId, name, description, "")
                loadTricounts()
                _currentTricount.value = tricountDao.getTricountById(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "editTricount: ${e.message}", e) }
        }
    }

    fun editTricountFull(tricountId: Int, name: String, description: String, emoji: String) {
        viewModelScope.launch {
            try {
                tricountDao.updateTricountFull(tricountId, name, description, emoji)
                syncRepo.updateTricountFields(tricountId, name, description, emoji)
                loadTricounts()
                _currentTricount.value = tricountDao.getTricountById(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "editTricountFull: ${e.message}", e) }
        }
    }

    fun duplicateTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                val userId   = sessionManager.getUserId() ?: return@launch
                val original = tricountDao.getTricountById(tricountId) ?: return@launch
                val newId = tricountDao.insertTricount(original.copy(
                    id = 0, name = "${original.name} (copy)",
                    joinCode = generateJoinCode(),
                    createdAt = System.currentTimeMillis(), isArchived = false
                )).toInt()
                tricountDao.addMember(TricountMemberCrossRef(userId = userId, tricountId = newId))
                loadTricounts()
            } catch (e: Exception) { Log.e("TricountVM", "duplicateTricount: ${e.message}", e) }
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

    // ── Tricount details ──────────────────────────────────────────────────────

    fun loadTricountDetails(tricountId: Int) {
        viewModelScope.launch {
            try {
                _currentTricount.value = tricountDao.getTricountById(tricountId)
                loadTricountMembers(tricountId)
                loadExpenses(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "loadTricountDetails: ${e.message}", e) }
        }
    }

    fun loadTricountMembers(tricountId: Int) {
        viewModelScope.launch {
            try {
                val members = tricountDao.getTricountMembersWithDetails(tricountId)
                Log.d("TricountVM", "loadTricountMembers: $tricountId -> ${members.size} members")
                _tricountMembers.value = members
            } catch (e: Exception) {
                Log.e("TricountVM", "loadTricountMembers: ${e.message}", e)
                _tricountMembers.value = emptyList()
            }
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
                // Find existing Room user or create a placeholder so they show in split UI
                val user = userDao.getUserByEmail(trimmed) ?: createPlaceholderUser(trimmed)

                if (tricountDao.isMember(tricountId, user.id) > 0) {
                    onResult(AddMemberResult.Error("${user.name} is already a member")); return@launch
                }
                tricountDao.addMember(TricountMemberCrossRef(userId = user.id, tricountId = tricountId))
                loadTricountMembers(tricountId)
                Log.d("TricountVM", "addMemberByEmail: added ${user.name} id=${user.id}")
                onResult(AddMemberResult.Success(user.name))
            } catch (e: Exception) {
                Log.e("TricountVM", "addMemberByEmail: ${e.message}", e)
                onResult(AddMemberResult.Error("Failed to add member: ${e.message}"))
            }
        }
    }

    fun removeMember(userId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.removeMember(userId, tricountId)
                loadTricountMembers(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "removeMember: ${e.message}", e) }
        }
    }

    // ── Join by code ──────────────────────────────────────────────────────────

    fun joinTricountByCode(joinCode: String) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: run {
                    _joinResult.value = JoinResult.Error("Not logged in"); return@launch
                }
                val tricount = tricountDao.getTricountByJoinCode(joinCode) ?: run {
                    _joinResult.value = JoinResult.Error("No tricount found with code: $joinCode"); return@launch
                }
                if (tricountDao.isMember(tricount.id, userId) > 0) {
                    _joinResult.value = JoinResult.Error("You are already a member"); return@launch
                }
                tricountDao.addMember(TricountMemberCrossRef(userId = userId, tricountId = tricount.id))
                loadTricounts()
                _joinResult.value = JoinResult.Success(tricount)
            } catch (e: Exception) {
                _joinResult.value = JoinResult.Error("Failed to join: ${e.message}")
            }
        }
    }

    fun resetJoinResult() { _joinResult.value = null }

    // ── Expenses ──────────────────────────────────────────────────────────────

    fun loadExpenses(tricountId: Int) {
        viewModelScope.launch {
            try {
                val list = tricountDao.getExpensesWithDetails(tricountId)
                Log.d("TricountVM", "loadExpenses: $tricountId -> ${list.size}")
                _expenses.value = list
                _payments.value = paymentDao.getPaymentsForTricount(tricountId)
                loadAllSplits(list)
                try { _archivedExpenses.value = tricountDao.getArchivedExpensesWithDetails(tricountId) }
                catch (e: Exception) { _archivedExpenses.value = emptyList() }
            } catch (e: Exception) {
                Log.e("TricountVM", "loadExpenses: ${e.message}", e)
                _expenses.value = emptyList(); _archivedExpenses.value = emptyList()
            }
        }
    }

    private suspend fun loadAllSplits(expenses: List<ExpenseWithDetails>) {
        val map = mutableMapOf<Int, List<ExpenseSplitWithUser>>()
        for (exp in expenses) {
            map[exp.id] = tricountDao.getExpenseSplitsWithAmounts(exp.id, exp.amount, exp.paidBy)
        }
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
                Log.d("TricountVM", "addExpense: name=$name amount=$amount paidBy=$paidBy shares=$sharesMap")
                if (name.isBlank()) { onResult(AddExpenseResult.Error("Expense name is required")); return@launch }
                if (amount <= 0)    { onResult(AddExpenseResult.Error("Amount must be > 0"));       return@launch }
                if (sharesMap.values.all { it == 0 }) {
                    onResult(AddExpenseResult.Error("At least one member must have shares > 0")); return@launch
                }
                if (userDao.getUserById(paidBy) == null) {
                    Log.e("TricountVM", "addExpense: paidBy=$paidBy not found")
                    onResult(AddExpenseResult.Error("Payer not found. Please re-login.")); return@launch
                }
                if (tricountDao.getTricountById(tricountId) == null) {
                    Log.e("TricountVM", "addExpense: tricount $tricountId not found")
                    onResult(AddExpenseResult.Error("Tricount not found.")); return@launch
                }

                val expense = ExpenseEntity(
                    tricountId = tricountId, name = name, description = description,
                    amount = amount, paidBy = paidBy, category = category
                )
                val expenseId = tricountDao.insertExpense(expense).toInt()
                Log.d("TricountVM", "addExpense: inserted expenseId=$expenseId")

                val splits = sharesMap.filter { it.value > 0 }
                    .map { (uid, shares) -> ExpenseSplitEntity(expenseId = expenseId, userId = uid, shares = shares) }
                if (splits.isNotEmpty()) tricountDao.insertExpenseSplits(splits)

                syncRepo.pushExpense(tricountId, expense.copy(id = expenseId), splits)
                loadExpenses(tricountId)
                onResult(AddExpenseResult.Success)
            } catch (e: Exception) {
                Log.e("TricountVM", "addExpense EXCEPTION: ${e.message}", e)
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
            } catch (e: Exception) { Log.e("TricountVM", "deleteExpense: ${e.message}", e) }
        }
    }

    fun archiveExpense(expenseId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.archiveExpense(expenseId)
                syncRepo.updateExpenseArchived(tricountId, expenseId, true)
                loadExpenses(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "archiveExpense: ${e.message}", e) }
        }
    }

    fun unarchiveExpense(expenseId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.unarchiveExpense(expenseId)
                loadExpenses(tricountId)
            } catch (e: Exception) { Log.e("TricountVM", "unarchiveExpense: ${e.message}", e) }
        }
    }

    // ── Settlements ───────────────────────────────────────────────────────────

    private fun recomputeSettlements() {
        val net = mutableMapOf<Int, Double>(); val nameMap = mutableMapOf<Int, String>()
        for (m in _tricountMembers.value) nameMap[m.userId] = m.name
        for (e in _expenses.value) {
            net[e.paidBy] = (net[e.paidBy] ?: 0.0) + e.amount; nameMap[e.paidBy] = e.paidByName
            val splits = _expenseSplits.value[e.id]
            if (!splits.isNullOrEmpty()) {
                for (s in splits) { net[s.userId] = (net[s.userId] ?: 0.0) - s.amount; nameMap[s.userId] = s.userName }
            } else { net[e.paidBy] = (net[e.paidBy] ?: 0.0) - e.amount }
        }
        for (p in _payments.value) {
            net[p.fromUserId] = (net[p.fromUserId] ?: 0.0) + p.amount
            net[p.toUserId]   = (net[p.toUserId]   ?: 0.0) - p.amount
            nameMap[p.fromUserId] = p.fromUserName; nameMap[p.toUserId] = p.toUserName
        }
        val creditors = net.filter { it.value >  0.01 }.map { it.key to  it.value }.toMutableList()
        val debtors   = net.filter { it.value < -0.01 }.map { it.key to -it.value }.toMutableList()
        val result = mutableListOf<Settlement>(); var ci = 0; var di = 0
        while (ci < creditors.size && di < debtors.size) {
            val (cId, cAmt) = creditors[ci]; val (dId, dAmt) = debtors[di]
            val settled = minOf(cAmt, dAmt)
            result.add(Settlement(dId, nameMap[dId] ?: "", cId, nameMap[cId] ?: "", settled))
            creditors[ci] = cId to (cAmt - settled); debtors[di] = dId to (dAmt - settled)
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
                paymentDao.insertPayment(PaymentEntity(
                    tricountId = tricountId, fromUserId = fromUserId, fromUserName = fromUserName,
                    toUserId = toUserId, toUserName = toUserName, amount = amount
                ))
                _payments.value = paymentDao.getPaymentsForTricount(tricountId)
                recomputeSettlements(); onDone()
            } catch (e: Exception) { Log.e("TricountVM", "markSettlementPaid: ${e.message}", e) }
        }
    }

    fun loadPayments(tricountId: Int) {
        viewModelScope.launch {
            try { _payments.value = paymentDao.getPaymentsForTricount(tricountId); recomputeSettlements() }
            catch (e: Exception) { _payments.value = emptyList() }
        }
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    fun saveNickname(nickname: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: return@launch
                // Save to Room (permanent) AND SessionManager (current session cache)
                userDao.updateNickname(userId, nickname)
                sessionManager.setNickname(nickname)
                syncRepo.updateNickname(nickname)   // persist to Firebase
                onDone()
            } catch (e: Exception) { Log.e("TricountVM", "saveNickname: ${e.message}", e) }
        }
    }

    /**
     * Saves the profile photo URI to:
     * 1. Room users table (permanent — survives logout/login for THIS user)
     * 2. SessionManager (session cache — fast access without DB query)
     *
     * Because Room is the source of truth, the photo is restored from Room
     * on every login via AuthViewModel.restoreProfileFromRoom(), so:
     * - Logging out and back in shows the same photo
     * - Switching users shows each user's own photo
     */
    fun savePhotoUri(uri: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: run {
                    Log.e("TricountVM", "savePhotoUri: No userId found in session")
                    return@launch
                }

                // 1. Update Room and check if it actually worked
                // Ensure your UserDao.updatePhotoUri returns an Int
                val rowsAffected = userDao.updatePhotoUri(userId, uri)

                if (rowsAffected > 0) {
                    Log.d("TricountVM", "SUCCESS: Photo URI updated in Room for user $userId")

                    // 2. Update Session Manager cache
                    if (uri.isEmpty()) {
                        sessionManager.clearProfilePhotoUri()
                    } else {
                        sessionManager.setProfilePhotoUri(uri)
                    }

                    // 3. Update Firebase Firestore
                    syncRepo.updateProfilePhoto(uri)

                    // 4. Signal UI that we are done
                    onDone()
                } else {
                    Log.e("TricountVM", "DATABASE ERROR: No user found in Room with ID $userId")
                }

            } catch (e: Exception) {
                Log.e("TricountVM", "savePhotoUri EXCEPTION: ${e.message}", e)
            }
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    fun toggleFavorite(userId: Int, tricountId: Int) {
        viewModelScope.launch {
            try { tricountDao.toggleFavorite(userId, tricountId); loadFavoriteTricounts(userId) }
            catch (e: Exception) { Log.e("TricountVM", "toggleFavorite: ${e.message}", e) }
        }
    }

    fun loadFavoriteTricounts(userId: Int) {
        viewModelScope.launch {
            try { _favoriteTricounts.value = tricountDao.getFavoriteTricounts(userId) }
            catch (e: Exception) { _favoriteTricounts.value = emptyList() }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun createPlaceholderUser(email: String): UserEntity {
        val displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        val newId = userDao.insertUser(
            UserEntity(email = email, password = "", name = displayName)
        ).toInt()
        Log.d("TricountVM", "createPlaceholderUser: id=$newId email=$email")
        return userDao.getUserById(newId)!!
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}

// ── Sealed / data classes ─────────────────────────────────────────────────────

data class Settlement(
    val fromUserId: Int, val fromUserName: String,
    val toUserId: Int,   val toUserName: String,
    val amount: Double
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