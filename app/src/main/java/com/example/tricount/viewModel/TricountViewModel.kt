package com.example.tricount.viewModel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tricount.data.SessionManager
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TricountViewModel(application: Application) : AndroidViewModel(application) {

    private val tricountDao    = TricountDatabase.getDatabase(application).tricountDao()
    private val userDao        = TricountDatabase.getDatabase(application).userDao()
    private val sessionManager = SessionManager(application)

    private val _tricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val tricounts: StateFlow<List<TricountEntity>> = _tricounts

    private val _currentTricount = MutableStateFlow<TricountEntity?>(null)
    val currentTricount: StateFlow<TricountEntity?> = _currentTricount

    private val _tricountMembers = MutableStateFlow<List<MemberWithDetails>>(emptyList())
    val tricountMembers: StateFlow<List<MemberWithDetails>> = _tricountMembers

    private val _expenses = MutableStateFlow<List<ExpenseWithDetails>>(emptyList())
    val expenses: StateFlow<List<ExpenseWithDetails>> = _expenses

    private val _expenseSplits = MutableStateFlow<Map<Int, List<ExpenseSplitWithUser>>>(emptyMap())
    val expenseSplits: StateFlow<Map<Int, List<ExpenseSplitWithUser>>> = _expenseSplits

    private val _settlements = MutableStateFlow<List<Settlement>>(emptyList())
    val settlements: StateFlow<List<Settlement>> = _settlements

    private val _joinResult = MutableStateFlow<JoinResult?>(null)
    val joinResult: StateFlow<JoinResult?> = _joinResult

    private val _favoriteTricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val favoriteTricounts: StateFlow<List<TricountEntity>> = _favoriteTricounts

    // ===============================
    // TRICOUNT OPERATIONS
    // ===============================

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

    fun insertTricount(name: String, description: String) {
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
                // Auto-add creator as first member
                tricountDao.addMember(TricountMemberCrossRef(userId = userId, tricountId = tricountId))
                loadTricounts()
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
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Delete error", e)
            }
        }
    }

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

    // ===============================
    // MEMBERS
    // ===============================

    fun addMemberByEmail(
        tricountId : Int,
        email      : String,
        onResult   : (AddMemberResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val user = userDao.getUserByEmail(email)
                if (user == null) {
                    onResult(AddMemberResult.Error("No account found with that email."))
                    return@launch
                }
                if (tricountDao.isMember(tricountId, user.id) > 0) {
                    onResult(AddMemberResult.Error("${user.name} is already a member."))
                    return@launch
                }
                tricountDao.addMember(TricountMemberCrossRef(userId = user.id, tricountId = tricountId))
                loadTricountMembers(tricountId)
                onResult(AddMemberResult.Success(user.name))
            } catch (e: Exception) {
                Log.e("TricountViewModel", "addMemberByEmail error", e)
                onResult(AddMemberResult.Error("Something went wrong. Please try again."))
            }
        }
    }

    fun removeMember(userId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.removeMember(tricountId, userId)
                loadTricountMembers(tricountId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "removeMember error", e)
            }
        }
    }

    // ===============================
    // EXPENSES
    // ===============================

    fun loadExpenses(tricountId: Int) {
        viewModelScope.launch {
            try {
                val expensesList = tricountDao.getExpensesWithDetails(tricountId)
                _expenses.value  = expensesList
                loadAllSplits(expensesList)
            } catch (e: Exception) {
                _expenses.value = emptyList()
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

    // ===============================
    // SETTLEMENT LOGIC
    // ===============================

    private fun recomputeSettlements() {
        val netBalance = mutableMapOf<Int, Double>()
        val nameMap    = mutableMapOf<Int, String>()

        for (expense in _expenses.value) {
            netBalance[expense.paidBy] = (netBalance[expense.paidBy] ?: 0.0) + expense.amount
            nameMap[expense.paidBy]    = expense.paidByName
            val splits = _expenseSplits.value[expense.id] ?: continue
            for (split in splits) {
                netBalance[split.userId] = (netBalance[split.userId] ?: 0.0) - split.amount
                nameMap[split.userId]    = split.userName
            }
        }

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

    // ===============================
    // FAVORITES
    // ===============================

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

    // ===============================
    // JOIN BY CODE
    // ===============================

    fun joinTricountByCode(joinCode: String) {
        viewModelScope.launch {
            try {
                val userId   = sessionManager.getUserId() ?: return@launch
                val tricount = tricountDao.getTricountByJoinCode(joinCode.uppercase().trim())
                if (tricount == null) {
                    _joinResult.value = JoinResult.Error("No Tricount found with that code.")
                    return@launch
                }
                if (tricountDao.isMember(tricount.id, userId) > 0) {
                    _joinResult.value = JoinResult.Error("You are already a member of \"${tricount.name}\".")
                    return@launch
                }
                tricountDao.addMember(TricountMemberCrossRef(userId = userId, tricountId = tricount.id))
                loadTricounts()
                _joinResult.value = JoinResult.Success(tricount)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "joinTricountByCode error", e)
                _joinResult.value = JoinResult.Error("Something went wrong. Please try again.")
            }
        }
    }

    fun resetJoinResult() { _joinResult.value = null }

    // ===============================
    // PROFILE — nickname & photo (DB-backed)
    // ===============================

    fun saveNickname(nickname: String, onDone: () -> Unit = {}) {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                userDao.updateNickname(userId, nickname)
                sessionManager.setNickname(nickname)
                onDone()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "saveNickname error", e)
            }
        }
    }

    fun savePhotoUri(photoUri: String, onDone: () -> Unit = {}) {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                userDao.updatePhotoUri(userId, photoUri)
                sessionManager.setProfilePhotoUri(photoUri)
                onDone()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "savePhotoUri error", e)
            }
        }
    }

    fun syncProfileFromDb() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                val user = userDao.getUserById(userId) ?: return@launch
                user.nickname?.let { sessionManager.setNickname(it) }
                user.photoUri?.let { sessionManager.setProfilePhotoUri(it) }
            } catch (e: Exception) {
                Log.e("TricountViewModel", "syncProfileFromDb error", e)
            }
        }
    }

    // ===============================
    // HELPERS
    // ===============================

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}

// ===============================
// DATA / RESULT CLASSES
// ===============================

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