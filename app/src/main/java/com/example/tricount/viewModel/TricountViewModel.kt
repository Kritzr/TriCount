package com.example.tricount.viewModel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tricount.data.SessionManager
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.dao.PaymentDao
import com.example.tricount.data.entity.PaymentEntity
import com.example.tricount.data.entity.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TricountViewModel(application: Application) : AndroidViewModel(application) {

    private val tricountDao    = TricountDatabase.getDatabase(application).tricountDao()
    private val userDao        = TricountDatabase.getDatabase(application).userDao()
    private val paymentDao     = TricountDatabase.getDatabase(application).paymentDao()
    private val sessionManager = SessionManager(application)

    // ── StateFlows ────────────────────────────────────────────────────────────

    private val _tricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val tricounts: StateFlow<List<TricountEntity>> = _tricounts

    private val _archivedTricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val archivedTricounts: StateFlow<List<TricountEntity>> = _archivedTricounts

    private val _currentTricount = MutableStateFlow<TricountEntity?>(null)
    val currentTricount: StateFlow<TricountEntity?> = _currentTricount

    private val _tricountMembers = MutableStateFlow<List<MemberWithDetails>>(emptyList())
    val tricountMembers: StateFlow<List<MemberWithDetails>> = _tricountMembers

    private val _expenses = MutableStateFlow<List<ExpenseWithDetails>>(emptyList())
    val expenses: StateFlow<List<ExpenseWithDetails>> = _expenses

    private val _archivedExpenses = MutableStateFlow<List<ExpenseWithDetails>>(emptyList())
    val archivedExpenses: StateFlow<List<ExpenseWithDetails>> = _archivedExpenses

    private val _expenseSplits = MutableStateFlow<Map<Int, List<ExpenseSplitWithUser>>>(emptyMap())
    val expenseSplits: StateFlow<Map<Int, List<ExpenseSplitWithUser>>> = _expenseSplits

    private val _settlements = MutableStateFlow<List<Settlement>>(emptyList())
    val settlements: StateFlow<List<Settlement>> = _settlements

    private val _joinResult = MutableStateFlow<JoinResult?>(null)
    val joinResult: StateFlow<JoinResult?> = _joinResult

    private val _favoriteTricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val favoriteTricounts: StateFlow<List<TricountEntity>> = _favoriteTricounts

    private val _payments = MutableStateFlow<List<PaymentEntity>>(emptyList())
    val payments: StateFlow<List<PaymentEntity>> = _payments

    // ── Tricount CRUD ─────────────────────────────────────────────────────────

    fun loadTricounts() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                _tricounts.value =
                    if (userId != null) tricountDao.getTricountsForUser(userId)
                    else emptyList()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error loading tricounts", e)
                _tricounts.value = emptyList()
            }
        }
    }

    fun insertTricount(name: String, description: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: return@launch
                val tricount = TricountEntity(
                    name        = name,
                    description = description,
                    creatorId   = userId,
                    joinCode    = generateJoinCode()
                )
                val tricountId = tricountDao.insertTricount(tricount).toInt()
                tricountDao.addMember(TricountMemberCrossRef(userId = userId, tricountId = tricountId))
                loadTricounts()
                onComplete()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Insert error", e)
            }
        }
    }

    fun deleteTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.deleteTricountById(tricountId)
                loadTricounts()
                loadArchivedTricounts()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Delete error", e)
            }
        }
    }

    fun archiveTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.archiveTricount(tricountId)
                loadTricounts()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Archive tricount error", e)
            }
        }
    }

    fun unarchiveTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.unarchiveTricount(tricountId)
                loadArchivedTricounts()
                loadTricounts()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Unarchive tricount error", e)
            }
        }
    }

    fun editTricount(tricountId: Int, name: String, description: String) {
        viewModelScope.launch {
            try {
                tricountDao.updateTricount(tricountId, name, description)
                loadTricounts()
                _currentTricount.value = tricountDao.getTricountById(tricountId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Edit tricount error", e)
            }
        }
    }

    fun editTricountFull(tricountId: Int, name: String, description: String, emoji: String) {
        viewModelScope.launch {
            try {
                tricountDao.updateTricountFull(tricountId, name, description, emoji)
                loadTricounts()
                _currentTricount.value = tricountDao.getTricountById(tricountId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Edit tricount full error", e)
            }
        }
    }

    fun duplicateTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                val userId   = sessionManager.getUserId() ?: return@launch
                val original = tricountDao.getTricountById(tricountId) ?: return@launch
                val copy = original.copy(
                    id         = 0,
                    name       = "${original.name} (copy)",
                    joinCode   = generateJoinCode(),
                    createdAt  = System.currentTimeMillis(),
                    isArchived = false
                )
                val newId = tricountDao.insertTricount(copy).toInt()
                tricountDao.addMember(TricountMemberCrossRef(userId = userId, tricountId = newId))
                loadTricounts()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Duplicate tricount error", e)
            }
        }
    }

    fun loadArchivedTricounts() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: return@launch
                _archivedTricounts.value = tricountDao.getArchivedTricountsForUser(userId)
            } catch (e: Exception) {
                _archivedTricounts.value = emptyList()
            }
        }
    }

    // ── Tricount Details ──────────────────────────────────────────────────────

    fun loadTricountDetails(tricountId: Int) {
        viewModelScope.launch {
            try {
                _currentTricount.value = tricountDao.getTricountById(tricountId)
                loadTricountMembers(tricountId)
                loadExpenses(tricountId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Details error", e)
            }
        }
    }

    fun loadTricountMembers(tricountId: Int) {
        viewModelScope.launch {
            try {
                _tricountMembers.value = tricountDao.getTricountMembersWithDetails(tricountId)
            } catch (e: Exception) {
                _tricountMembers.value = emptyList()
            }
        }
    }

    // ── Members ───────────────────────────────────────────────────────────────

    fun addMemberByEmail(tricountId: Int, email: String, onResult: (AddMemberResult) -> Unit) {
        viewModelScope.launch {
            try {
                val user = userDao.getUserByEmail(email)
                if (user == null) {
                    onResult(AddMemberResult.Error("No user found with email: $email"))
                    return@launch
                }
                val existing = tricountDao.getTricountMembersWithDetails(tricountId)
                if (existing.any { it.userId == user.id }) {
                    onResult(AddMemberResult.Error("${user.name} is already a member"))
                    return@launch
                }
                tricountDao.addMember(TricountMemberCrossRef(userId = user.id, tricountId = tricountId))
                loadTricountMembers(tricountId)
                onResult(AddMemberResult.Success(user.name))
            } catch (e: Exception) {
                onResult(AddMemberResult.Error("Failed to add member: ${e.message}"))
            }
        }
    }

    fun removeMember(userId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.removeMember(tricountId, userId)  // DAO: (tricountId, userId)
                loadTricountMembers(tricountId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Remove member error", e)
            }
        }
    }

    // ── Join by code ──────────────────────────────────────────────────────────

    fun joinTricountByCode(joinCode: String) {
        viewModelScope.launch {
            try {
                val userId   = sessionManager.getUserId() ?: run {
                    _joinResult.value = JoinResult.Error("Not logged in")
                    return@launch
                }
                val tricount = tricountDao.getTricountByJoinCode(joinCode)
                if (tricount == null) {
                    _joinResult.value = JoinResult.Error("No tricount found with code: $joinCode")
                    return@launch
                }
                val existing = tricountDao.getTricountMembersWithDetails(tricount.id)
                if (existing.any { it.userId == userId }) {
                    _joinResult.value = JoinResult.Error("You are already a member of this tricount")
                    return@launch
                }
                tricountDao.addMember(TricountMemberCrossRef(userId = userId, tricountId = tricount.id))
                loadTricounts()
                _joinResult.value = JoinResult.Success(tricount)
            } catch (e: Exception) {
                _joinResult.value = JoinResult.Error("Failed to join: ${e.message}")
            }
        }
    }

    fun resetJoinResult() {
        _joinResult.value = null
    }

    // ── Expenses ──────────────────────────────────────────────────────────────

    fun loadExpenses(tricountId: Int) {
        viewModelScope.launch {
            try {
                val expensesList = tricountDao.getExpensesWithDetails(tricountId)
                _expenses.value = expensesList
                _payments.value = paymentDao.getPaymentsForTricount(tricountId)
                loadAllSplits(expensesList)
                try {
                    _archivedExpenses.value = tricountDao.getArchivedExpensesWithDetails(tricountId)
                } catch (e: Exception) {
                    _archivedExpenses.value = emptyList()
                }
            } catch (e: Exception) {
                _expenses.value = emptyList()
                _archivedExpenses.value = emptyList()
            }
        }
    }

    private suspend fun loadAllSplits(expenses: List<ExpenseWithDetails>) {
        val map = mutableMapOf<Int, List<ExpenseSplitWithUser>>()
        for (expense in expenses) {
            map[expense.id] = tricountDao.getExpenseSplitsWithAmounts(
                expense.id, expense.amount, expense.paidBy
            )
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
                if (name.isBlank()) {
                    onResult(AddExpenseResult.Error("Expense name is required"))
                    return@launch
                }
                if (amount <= 0) {
                    onResult(AddExpenseResult.Error("Amount must be greater than 0"))
                    return@launch
                }
                if (sharesMap.values.all { it == 0 }) {
                    onResult(AddExpenseResult.Error("At least one member must have shares > 0"))
                    return@launch
                }

                val expense = ExpenseEntity(
                    tricountId  = tricountId,
                    name        = name,
                    description = description,
                    amount      = amount,
                    paidBy      = paidBy,
                    category    = category
                )
                val expenseId = tricountDao.insertExpense(expense).toInt()

                val splits = sharesMap
                    .filter { it.value > 0 }
                    .map { (userId, shares) ->
                        ExpenseSplitEntity(expenseId = expenseId, userId = userId, shares = shares)
                    }
                tricountDao.insertExpenseSplits(splits)

                loadExpenses(tricountId)
                onResult(AddExpenseResult.Success)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error adding expense: ${e.message}", e)
                onResult(AddExpenseResult.Error("Failed to add expense: ${e.message}"))
            }
        }
    }

    fun deleteExpense(expenseId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.deleteExpenseSplits(expenseId)
                tricountDao.deleteExpense(expenseId)
                loadExpenses(tricountId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Delete expense error", e)
            }
        }
    }

    fun archiveExpense(expenseId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.archiveExpense(expenseId)
                loadExpenses(tricountId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Archive expense error", e)
            }
        }
    }

    fun unarchiveExpense(expenseId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.unarchiveExpense(expenseId)
                loadExpenses(tricountId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Unarchive expense error", e)
            }
        }
    }

    // ── Settlement logic ──────────────────────────────────────────────────────

    private fun recomputeSettlements() {
        val netBalance = mutableMapOf<Int, Double>()
        val nameMap    = mutableMapOf<Int, String>()

        // Build a global name map from all known sources first
        for (member in _tricountMembers.value) {
            nameMap[member.userId] = member.name
        }

        // Step 1 — accumulate expense debts
        for (expense in _expenses.value) {
            // Payer gets credited the full amount
            netBalance[expense.paidBy] = (netBalance[expense.paidBy] ?: 0.0) + expense.amount
            nameMap[expense.paidBy] = expense.paidByName

            val splits = _expenseSplits.value[expense.id]

            if (!splits.isNullOrEmpty()) {
                // Use recorded splits — each person owes their proportional share
                for (split in splits) {
                    netBalance[split.userId] = (netBalance[split.userId] ?: 0.0) - split.amount
                    nameMap[split.userId] = split.userName
                }
            } else {
                // No splits recorded — debit the payer their own full share
                // (they paid for themselves, no one else owes them)
                netBalance[expense.paidBy] = (netBalance[expense.paidBy] ?: 0.0) - expense.amount
            }
        }

        // Step 2 — subtract already-recorded payments
        // A payment from A→B means A's debt goes down (credit A) and B's credit goes down (debit B)
        for (payment in _payments.value) {
            netBalance[payment.fromUserId] = (netBalance[payment.fromUserId] ?: 0.0) + payment.amount
            netBalance[payment.toUserId]   = (netBalance[payment.toUserId]   ?: 0.0) - payment.amount
            nameMap[payment.fromUserId]    = payment.fromUserName
            nameMap[payment.toUserId]      = payment.toUserName
        }

        // Step 3 — greedy settle-up
        val creditors = netBalance.filter { it.value >  0.01 }.map { it.key to  it.value }.toMutableList()
        val debtors   = netBalance.filter { it.value < -0.01 }.map { it.key to -it.value }.toMutableList()
        val result    = mutableListOf<Settlement>()

        var ci = 0; var di = 0
        while (ci < creditors.size && di < debtors.size) {
            val (creditorId, creditAmt) = creditors[ci]
            val (debtorId,   debtAmt)   = debtors[di]
            val settled = minOf(creditAmt, debtAmt)

            result.add(Settlement(
                fromUserId   = debtorId,
                fromUserName = nameMap[debtorId]   ?: "",
                toUserId     = creditorId,
                toUserName   = nameMap[creditorId] ?: "",
                amount       = settled
            ))

            creditors[ci] = creditorId to (creditAmt - settled)
            debtors[di]   = debtorId   to (debtAmt   - settled)
            if (creditors[ci].second <= 0.01) ci++
            if (debtors[di].second   <= 0.01) di++
        }
        _settlements.value = result
    }

    /** Record that fromUserId paid toUserId [amount] and refresh settlements live. */
    fun markSettlementPaid(
        tricountId   : Int,
        fromUserId   : Int,
        fromUserName : String,
        toUserId     : Int,
        toUserName   : String,
        amount       : Double,
        onDone       : () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val payment = PaymentEntity(
                    tricountId   = tricountId,
                    fromUserId   = fromUserId,
                    fromUserName = fromUserName,
                    toUserId     = toUserId,
                    toUserName   = toUserName,
                    amount       = amount
                )
                paymentDao.insertPayment(payment)
                // Refresh payments then recompute settlements
                _payments.value = paymentDao.getPaymentsForTricount(tricountId)
                recomputeSettlements()
                onDone()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "markSettlementPaid error", e)
            }
        }
    }

    fun loadPayments(tricountId: Int) {
        viewModelScope.launch {
            try {
                _payments.value = paymentDao.getPaymentsForTricount(tricountId)
                recomputeSettlements()
            } catch (e: Exception) {
                _payments.value = emptyList()
            }
        }
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    fun saveNickname(nickname: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: return@launch
                userDao.updateNickname(userId, nickname)
                sessionManager.setNickname(nickname)
                onDone()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Save nickname error", e)
            }
        }
    }

    fun savePhotoUri(uri: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: return@launch
                userDao.updatePhotoUri(userId, uri)
                sessionManager.setProfilePhotoUri(uri)
                onDone()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Save photo URI error", e)
            }
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    fun toggleFavorite(userId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.toggleFavorite(userId, tricountId)
                loadFavoriteTricounts(userId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Favorite error", e)
            }
        }
    }

    fun loadFavoriteTricounts(userId: Int) {
        viewModelScope.launch {
            try {
                _favoriteTricounts.value = tricountDao.getFavoriteTricounts(userId)
            } catch (e: Exception) {
                _favoriteTricounts.value = emptyList()
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
    object Success                               : AddExpenseResult()
    data class Error(val message: String)        : AddExpenseResult()
}

sealed class AddMemberResult {
    data class Success(val memberName: String)   : AddMemberResult()
    data class Error(val message: String)        : AddMemberResult()
}