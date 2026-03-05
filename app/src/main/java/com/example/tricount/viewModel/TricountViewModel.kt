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

    private val tricountDao = TricountDatabase.getDatabase(application).tricountDao()
    private val sessionManager = SessionManager(application)

    private val _tricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val tricounts: StateFlow<List<TricountEntity>> = _tricounts

    private val _currentTricount = MutableStateFlow<TricountEntity?>(null)
    val currentTricount: StateFlow<TricountEntity?> = _currentTricount

    private val _tricountMembers =
        MutableStateFlow<List<MemberWithDetails>>(emptyList())
    val tricountMembers: StateFlow<List<MemberWithDetails>> = _tricountMembers

    private val _expenses =
        MutableStateFlow<List<ExpenseWithDetails>>(emptyList())
    val expenses: StateFlow<List<ExpenseWithDetails>> = _expenses

    private val _expenseSplits =
        MutableStateFlow<Map<Int, List<ExpenseSplitWithUser>>>(emptyMap())
    val expenseSplits: StateFlow<Map<Int, List<ExpenseSplitWithUser>>> = _expenseSplits

    private val _settlements =
        MutableStateFlow<List<Settlement>>(emptyList())
    val settlements: StateFlow<List<Settlement>> = _settlements

    private val _joinResult = MutableStateFlow<JoinResult?>(null)
    val joinResult: StateFlow<JoinResult?> = _joinResult

    private val _favoriteTricounts =
        MutableStateFlow<List<TricountEntity>>(emptyList())
    val favoriteTricounts: StateFlow<List<TricountEntity>> = _favoriteTricounts

    // ===============================
    // TRICOUNT OPERATIONS
    // ===============================

    fun loadTricounts() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                _tricounts.value =
                    if (userId != null)
                        tricountDao.getTricountsForUser(userId)
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
                    name = name,
                    description = description,
                    creatorId = userId,
                    joinCode = generateJoinCode()
                )
                tricountDao.insertTricount(tricount)
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

    fun editTricount(tricountId: Int, name: String, description: String) {
        viewModelScope.launch {
            try {
                tricountDao.updateTricount(tricountId, name, description)
                loadTricounts()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Edit tricount error", e)
            }
        }
    }

    fun duplicateTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                val userId   = sessionManager.getUserId() ?: return@launch
                val original = tricountDao.getTricountById(tricountId) ?: return@launch
                val copy = original.copy(
                    id        = 0,
                    name      = "${original.name} (copy)",
                    joinCode  = generateJoinCode(),
                    createdAt = System.currentTimeMillis(),
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

    fun loadTricountDetails(tricountId: Int) {
        viewModelScope.launch {
            try {
                _currentTricount.value =
                    tricountDao.getTricountById(tricountId)
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
                _tricountMembers.value =
                    tricountDao.getTricountMembersWithDetails(tricountId)
            } catch (e: Exception) {
                _tricountMembers.value = emptyList()
            }
        }
    }

    // ===============================
    // EXPENSES
    // ===============================

    fun loadExpenses(tricountId: Int) {
        viewModelScope.launch {
            try {
                val expensesList =
                    tricountDao.getExpensesWithDetails(tricountId)
                _expenses.value = expensesList
                loadAllSplits(expensesList)
            } catch (e: Exception) {
                _expenses.value = emptyList()
            }
        }
    }

    private suspend fun loadAllSplits(
        expenses: List<ExpenseWithDetails>
    ) {
        val map = mutableMapOf<Int, List<ExpenseSplitWithUser>>()
        for (expense in expenses) {
            map[expense.id] =
                tricountDao.getExpenseSplitsWithAmounts(
                    expense.id,
                    expense.amount,
                    expense.paidBy
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
        sharesMap   : Map<Int, Int>,          // userId → shares
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
                        ExpenseSplitEntity(
                            expenseId = expenseId,
                            userId    = userId,
                            shares    = shares
                        )
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

    // ===============================
    // SETTLEMENT LOGIC (FIXED)
    // ===============================

    private fun recomputeSettlements() {

        val netBalance = mutableMapOf<Int, Double>()
        val nameMap = mutableMapOf<Int, String>()

        for (expense in _expenses.value) {

            // Payer gets credited full amount
            netBalance[expense.paidBy] =
                (netBalance[expense.paidBy] ?: 0.0) + expense.amount
            nameMap[expense.paidBy] = expense.paidByName

            val splits = _expenseSplits.value[expense.id] ?: continue

            for (split in splits) {
                netBalance[split.userId] =
                    (netBalance[split.userId] ?: 0.0) - split.amount
                nameMap[split.userId] = split.userName
            }
        }

        val creditors = netBalance
            .filter { it.value > 0.01 }
            .map { it.key to it.value }
            .toMutableList()

        val debtors = netBalance
            .filter { it.value < -0.01 }
            .map { it.key to -it.value }
            .toMutableList()

        val settlements = mutableListOf<Settlement>()

        var ci = 0
        var di = 0

        while (ci < creditors.size && di < debtors.size) {

            val (creditorId, creditAmt) = creditors[ci]
            val (debtorId, debtAmt) = debtors[di]

            val settled = minOf(creditAmt, debtAmt)

            settlements.add(
                Settlement(
                    fromUserId = debtorId,
                    fromUserName = nameMap[debtorId] ?: "",
                    toUserId = creditorId,
                    toUserName = nameMap[creditorId] ?: "",
                    amount = settled
                )
            )

            creditors[ci] = creditorId to (creditAmt - settled)
            debtors[di] = debtorId to (debtAmt - settled)

            if (creditors[ci].second <= 0.01) ci++
            if (debtors[di].second <= 0.01) di++
        }

        _settlements.value = settlements
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
                _favoriteTricounts.value =
                    tricountDao.getFavoriteTricounts(userId)
            } catch (e: Exception) {
                _favoriteTricounts.value = emptyList()
            }
        }
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}

// ===============================
// DATA CLASSES
// ===============================

data class Settlement(
    val fromUserId: Int,
    val fromUserName: String,
    val toUserId: Int,
    val toUserName: String,
    val amount: Double
)

sealed class JoinResult {
    data class Success(val tricount: TricountEntity) : JoinResult()
    data class Error(val message: String) : JoinResult()
}

sealed class AddExpenseResult {
    object Success : AddExpenseResult()
    data class Error(val message: String) : AddExpenseResult()
}