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

    // ── State flows ──────────────────────────────────────────────────────────

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

    // ── Tricount operations ──────────────────────────────────────────────────

    /** Used by HomeActivity.onResume and TriCountListScreen */
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

    /** Used by AddTricountActivity */
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
                tricountDao.insertTricount(tricount)
                loadTricounts()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Insert tricount error", e)
            }
        }
    }

    /** Used by HomeActivity (delete dialog) */
    fun deleteTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.deleteTricountById(tricountId)
                loadTricounts()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Delete tricount error", e)
            }
        }
    }

    /**
     * Used by TricountDetailActivity, ExpensesActivity, SummaryActivity.
     * Loads the tricount entity + members + expenses (which also triggers splits & settlements).
     */
    fun loadTricountDetails(tricountId: Int) {
        viewModelScope.launch {
            try {
                _currentTricount.value = tricountDao.getTricountById(tricountId)
                loadTricountMembers(tricountId)
                loadExpenses(tricountId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Load details error", e)
            }
        }
    }

    /** Used by TricountDetailActivity and ExpensesActivity */
    fun loadTricountMembers(tricountId: Int) {
        viewModelScope.launch {
            try {
                _tricountMembers.value = tricountDao.getTricountMembersWithDetails(tricountId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Load members error", e)
                _tricountMembers.value = emptyList()
            }
        }
    }

    // ── Member operations ────────────────────────────────────────────────────

    /**
     * Used by TricountDetailActivity → AddMemberDialog.
     * Looks up a user by email and adds them to the tricount.
     */
    fun addMemberByEmail(tricountId: Int, email: String, onResult: (AddMemberResult) -> Unit) {
        viewModelScope.launch {
            try {
                val user = tricountDao.getUserByEmail(email)
                if (user == null) {
                    onResult(AddMemberResult.Error("No user found with this email"))
                    return@launch
                }
                val tricount = tricountDao.getTricountById(tricountId)
                if (tricount?.creatorId == user.id) {
                    onResult(AddMemberResult.Error("This user is already the creator"))
                    return@launch
                }
                val existing = tricountDao.getMembership(user.id, tricountId)
                if (existing != null) {
                    onResult(AddMemberResult.Error("${user.name} is already a member"))
                    return@launch
                }
                tricountDao.addMember(user.id, tricountId)
                loadTricountMembers(tricountId)
                onResult(AddMemberResult.Success(user.name))
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Add member error", e)
                onResult(AddMemberResult.Error("Failed to add member: ${e.message}"))
            }
        }
    }

    /** Used by TricountDetailActivity → MemberItem */
    fun removeMember(userId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.removeMember(userId, tricountId)
                loadTricountMembers(tricountId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Remove member error", e)
            }
        }
    }

    // ── Expense operations ───────────────────────────────────────────────────

    /** Used by ExpensesActivity and SummaryActivity */
    fun loadExpenses(tricountId: Int) {
        viewModelScope.launch {
            try {
                val expensesList = tricountDao.getExpensesWithDetails(tricountId)
                _expenses.value = expensesList
                loadAllSplits(expensesList)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Load expenses error", e)
                _expenses.value = emptyList()
            }
        }
    }

    private suspend fun loadAllSplits(expenses: List<ExpenseWithDetails>) {
        val map = mutableMapOf<Int, List<ExpenseSplitWithUser>>()
        for (expense in expenses) {
            map[expense.id] = tricountDao.getExpenseSplitsWithAmounts(
                expense.id,
                expense.amount,
                expense.paidBy
            )
        }
        _expenseSplits.value = map
        recomputeSettlements()
    }

    /**
     * Used by ExpensesActivity → ExpenseAddDialog and
     * Expensestabcontent → AddExpenseDialog.
     */
    fun addExpense(
        tricountId  : Int,
        name        : String,
        description : String,
        amount      : Double,
        paidBy      : Int,
        category    : String = "General",
        sharesMap   : Map<Int, Int>,        // userId → number of shares
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
                Log.e("TricountViewModel", "Add expense error", e)
                onResult(AddExpenseResult.Error("Failed to add expense: ${e.message}"))
            }
        }
    }

    /** Used by ExpensesActivity → ExpenseItemCard */
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

    // ── Settlement computation ───────────────────────────────────────────────

    /**
     * Called automatically after every loadAllSplits.
     * Result is exposed via [settlements] StateFlow and consumed by SummaryActivity.
     */
    private fun recomputeSettlements() {
        val netBalance = mutableMapOf<Int, Double>()
        val nameMap    = mutableMapOf<Int, String>()

        for (expense in _expenses.value) {
            // Payer gets credited the full amount
            netBalance[expense.paidBy] = (netBalance[expense.paidBy] ?: 0.0) + expense.amount
            nameMap[expense.paidBy]    = expense.paidByName

            val splits = _expenseSplits.value[expense.id] ?: continue
            for (split in splits) {
                netBalance[split.userId] = (netBalance[split.userId] ?: 0.0) - split.amount
                nameMap[split.userId]    = split.userName
            }
        }

        // Greedy min-transactions algorithm
        val creditors = netBalance.filter { it.value >  0.01 }.map { it.key to  it.value }.toMutableList()
        val debtors   = netBalance.filter { it.value < -0.01 }.map { it.key to -it.value }.toMutableList()

        val result = mutableListOf<Settlement>()
        var ci = 0; var di = 0

        while (ci < creditors.size && di < debtors.size) {
            val (creditorId, creditAmt) = creditors[ci]
            val (debtorId,   debtAmt)   = debtors[di]
            val settled = minOf(creditAmt, debtAmt)

            result.add(
                Settlement(
                    fromUserId   = debtorId,
                    fromUserName = nameMap[debtorId]   ?: "",
                    toUserId     = creditorId,
                    toUserName   = nameMap[creditorId] ?: "",
                    amount       = settled
                )
            )

            creditors[ci] = creditorId to (creditAmt - settled)
            debtors[di]   = debtorId   to (debtAmt   - settled)

            if (creditors[ci].second <= 0.01) ci++
            if (debtors[di].second   <= 0.01) di++
        }

        _settlements.value = result
    }

    // ── Join tricount ────────────────────────────────────────────────────────

    /** Used by JoinTricountActivity */
    fun joinTricountByCode(code: String) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                if (userId == null) {
                    _joinResult.value = JoinResult.Error("You must be logged in")
                    return@launch
                }
                val tricount = tricountDao.getTricountByJoinCode(code)
                if (tricount == null) {
                    _joinResult.value = JoinResult.Error("Invalid code — tricount not found")
                    return@launch
                }
                if (tricount.creatorId == userId) {
                    _joinResult.value = JoinResult.Error("You are already the creator of this Tricount")
                    return@launch
                }
                val existing = tricountDao.getMembership(userId, tricount.id)
                if (existing != null) {
                    _joinResult.value = JoinResult.Error("You are already a member of this Tricount")
                    return@launch
                }
                tricountDao.addMember(userId, tricount.id)
                loadTricounts()
                _joinResult.value = JoinResult.Success(tricount)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Join tricount error", e)
                _joinResult.value = JoinResult.Error("Failed to join: ${e.message}")
            }
        }
    }

    /** Used by JoinTricountActivity after handling the result */
    fun resetJoinResult() {
        _joinResult.value = null
    }

    // ── Favorites ────────────────────────────────────────────────────────────

    /** Used by HomeActivity → AnimatedTricountCard */
    fun toggleFavorite(userId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.toggleFavorite(userId, tricountId)
                loadFavoriteTricounts(userId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Toggle favorite error", e)
            }
        }
    }

    /** Used by HomeActivity → TriCountListScreen (Favorites tab) */
    fun loadFavoriteTricounts(userId: Int) {
        viewModelScope.launch {
            try {
                _favoriteTricounts.value = tricountDao.getFavoriteTricounts(userId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Load favorites error", e)
                _favoriteTricounts.value = emptyList()
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}

// ── Shared data classes & sealed classes ─────────────────────────────────────

/** Represents one payment needed to settle all debts. Consumed by SummaryActivity. */
data class Settlement(
    val fromUserId   : Int,
    val fromUserName : String,
    val toUserId     : Int,
    val toUserName   : String,
    val amount       : Double
)

/** Result of joinTricountByCode. Consumed by JoinTricountActivity. */
sealed class JoinResult {
    data class Success(val tricount: TricountEntity) : JoinResult()
    data class Error(val message: String)            : JoinResult()
}

/** Result of addMemberByEmail. Consumed by TricountDetailActivity → AddMemberDialog. */
sealed class AddMemberResult {
    data class Success(val memberName: String) : AddMemberResult()
    data class Error(val message: String)      : AddMemberResult()
}

/** Result of addExpense. Consumed by ExpensesActivity and Expensestabcontent. */
sealed class AddExpenseResult {
    object Success                             : AddExpenseResult()
    data class Error(val message: String)      : AddExpenseResult()
}