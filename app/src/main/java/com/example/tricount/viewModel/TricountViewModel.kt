package com.example.tricount.viewModel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tricount.data.SessionManager
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.ExpenseSplitEntity
import com.example.tricount.data.entity.ExpenseSplitWithUser
import com.example.tricount.data.entity.TricountEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class TricountViewModel(application: Application) : AndroidViewModel(application) {

    private val tricountDao = TricountDatabase.getDatabase(application).tricountDao()
    private val sessionManager = SessionManager(application)

    private val _tricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val tricounts: StateFlow<List<TricountEntity>> = _tricounts

    private val _currentTricount = MutableStateFlow<TricountEntity?>(null)
    val currentTricount: StateFlow<TricountEntity?> = _currentTricount

    private val _tricountMembers = MutableStateFlow<List<com.example.tricount.data.entity.MemberWithDetails>>(emptyList())
    val tricountMembers: StateFlow<List<com.example.tricount.data.entity.MemberWithDetails>> = _tricountMembers

    private val _expenses = MutableStateFlow<List<com.example.tricount.data.entity.ExpenseWithDetails>>(emptyList())
    val expenses: StateFlow<List<com.example.tricount.data.entity.ExpenseWithDetails>> = _expenses

    /** Maps expenseId → list of splits with computed amounts */
    private val _expenseSplits = MutableStateFlow<Map<Int, List<ExpenseSplitWithUser>>>(emptyMap())
    val expenseSplits: StateFlow<Map<Int, List<ExpenseSplitWithUser>>> = _expenseSplits

    /** Computed settlement: who pays whom to settle up */
    private val _settlements = MutableStateFlow<List<Settlement>>(emptyList())
    val settlements: StateFlow<List<Settlement>> = _settlements

    private val _joinResult = MutableStateFlow<JoinResult?>(null)
    val joinResult: StateFlow<JoinResult?> = _joinResult

    // ===== TRICOUNT OPERATIONS =====

    fun loadTricounts() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                if (userId != null) {
                    val userTricounts = tricountDao.getTricountsForUser(userId)
                    _tricounts.value = userTricounts
                } else {
                    _tricounts.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error loading tricounts: ${e.message}", e)
                _tricounts.value = emptyList()
            }
        }
    }

    fun insertTricount(name: String, description: String) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                if (userId != null) {
                    val joinCode = generateJoinCode()
                    val tricount = TricountEntity(
                        name = name,
                        description = description,
                        creatorId = userId,
                        joinCode = joinCode
                    )
                    tricountDao.insertTricount(tricount)
                    loadTricounts()
                }
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error inserting tricount: ${e.message}", e)
            }
        }
    }

    fun deleteTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                tricountDao.deleteTricountById(tricountId)
                loadTricounts()
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error deleting tricount: ${e.message}", e)
            }
        }
    }

    fun loadTricountDetails(tricountId: Int) {
        viewModelScope.launch {
            try {
                val tricount = tricountDao.getTricountById(tricountId)
                _currentTricount.value = tricount
                if (tricount != null) {
                    loadTricountMembers(tricountId)
                }
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error loading tricount details: ${e.message}", e)
                _currentTricount.value = null
            }
        }
    }

    fun loadTricountMembers(tricountId: Int) {
        viewModelScope.launch {
            try {
                val members = tricountDao.getTricountMembersWithDetails(tricountId)
                _tricountMembers.value = members
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error loading members: ${e.message}", e)
                _tricountMembers.value = emptyList()
            }
        }
    }

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
                    onResult(AddMemberResult.Error("This user is the creator of this Tricount"))
                    return@launch
                }
                val existingMember = tricountDao.getMembership(user.id, tricountId)
                if (existingMember != null) {
                    onResult(AddMemberResult.Error("${user.name} is already a member"))
                    return@launch
                }
                tricountDao.addMember(user.id, tricountId)
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
                tricountDao.removeMember(userId, tricountId)
                loadTricountMembers(tricountId)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error removing member: ${e.message}", e)
            }
        }
    }

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
                    _joinResult.value = JoinResult.Error("Invalid code. Tricount not found.")
                    return@launch
                }
                if (tricount.creatorId == userId) {
                    _joinResult.value = JoinResult.Error("You are already the creator of this Tricount")
                    return@launch
                }
                val existingMember = tricountDao.getMembership(userId, tricount.id)
                if (existingMember != null) {
                    _joinResult.value = JoinResult.Error("You are already a member of this Tricount")
                    return@launch
                }
                tricountDao.addMember(userId, tricount.id)
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

    // ===== EXPENSE OPERATIONS =====

    fun loadExpenses(tricountId: Int) {
        viewModelScope.launch {
            try {
                val expensesList = tricountDao.getExpensesWithDetails(tricountId)
                _expenses.value = expensesList
                // Load splits for all expenses
                loadAllSplits(expensesList)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error loading expenses: ${e.message}", e)
                _expenses.value = emptyList()
            }
        }
    }

    private suspend fun loadAllSplits(expenses: List<com.example.tricount.data.entity.ExpenseWithDetails>) {
        val splitsMap = mutableMapOf<Int, List<ExpenseSplitWithUser>>()
        for (expense in expenses) {
            val splits = tricountDao.getExpenseSplitsWithAmounts(
                expenseId = expense.id,
                totalAmount = expense.amount,
                payerId = expense.paidBy
            )
            splitsMap[expense.id] = splits
        }
        _expenseSplits.value = splitsMap
        recomputeSettlements()
    }

    /**
     * Adds a new expense with custom share ratios per member.
     *
     * @param sharesMap  Map of userId → number of shares. Everyone in the tricount
     *                   must be included. Shares can be 0 to exclude someone.
     */
    fun addExpense(
        tricountId: Int,
        name: String,
        description: String,
        amount: Double,
        paidBy: Int,
        category: String = "General",
        sharesMap: Map<Int, Int>,   // userId → shares
        onResult: (AddExpenseResult) -> Unit
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

                val expense = com.example.tricount.data.entity.ExpenseEntity(
                    tricountId = tricountId,
                    name = name,
                    description = description,
                    amount = amount,
                    paidBy = paidBy,
                    category = category
                )
                val expenseId = tricountDao.insertExpense(expense).toInt()

                // Insert splits
                val splits = sharesMap
                    .filter { it.value > 0 }
                    .map { (userId, shares) ->
                        ExpenseSplitEntity(
                            expenseId = expenseId,
                            userId = userId,
                            shares = shares
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
                Log.e("TricountViewModel", "Error deleting expense: ${e.message}", e)
            }
        }
    }

    // ===== SETTLEMENT COMPUTATION =====

    /**
     * Computes the minimum set of transactions to settle all debts.
     * Uses a greedy algorithm on net balances.
     */
    private fun recomputeSettlements() {
        val allSplits = _expenseSplits.value
        val allExpenses = _expenses.value

        // netBalance[userId] = totalPaid - totalOwed
        // Positive = others owe them; Negative = they owe others
        val netBalance = mutableMapOf<Int, Double>()
        val nameMap = mutableMapOf<Int, String>()

        for (expense in allExpenses) {
            // Payer gets credit for full amount
            netBalance[expense.paidBy] = (netBalance[expense.paidBy] ?: 0.0) + expense.amount
            nameMap[expense.paidBy] = expense.paidByName

            // Each person in split owes their share
            val splits = allSplits[expense.id] ?: continue
            for (split in splits) {
                netBalance[split.userId] = (netBalance[split.userId] ?: 0.0) - split.amount
                nameMap[split.userId] = split.userName
            }
        }

        // Greedy min-transactions settlement
        val creditors = netBalance.filter { it.value > 0.005 }
            .map { Pair(it.key, it.value) }.toMutableList()
        val debtors = netBalance.filter { it.value < -0.005 }
            .map { Pair(it.key, -it.value) }.toMutableList()  // store as positive

        val settlements = mutableListOf<Settlement>()

        var ci = 0; var di = 0
        val credAmt = creditors.map { it.second }.toMutableList()
        val debtAmt = debtors.map { it.second }.toMutableList()

        while (ci < creditors.size && di < debtors.size) {
            val settle = minOf(credAmt[ci], debtAmt[di])
            settlements.add(
                Settlement(
                    fromUserId = debtors[di].first,
                    fromUserName = nameMap[debtors[di].first] ?: "?",
                    toUserId = creditors[ci].first,
                    toUserName = nameMap[creditors[ci].first] ?: "?",
                    amount = settle
                )
            )
            credAmt[ci] -= settle
            debtAmt[di] -= settle
            if (credAmt[ci] < 0.005) ci++
            if (debtAmt[di] < 0.005) di++
        }

        _settlements.value = settlements
    }

    // ===== FAVORITES =====

    fun toggleFavorite(userId: Int, tricountId: Int, onToggled: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val isFavorited = tricountDao.toggleFavorite(userId, tricountId)
                loadFavoriteTricounts(userId)
                onToggled(isFavorited)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error toggling favorite: ${e.message}", e)
            }
        }
    }

    suspend fun isFavorite(userId: Int, tricountId: Int): Boolean {
        return try {
            tricountDao.isFavorite(userId, tricountId)
        } catch (e: Exception) {
            false
        }
    }

    private val _favoriteTricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val favoriteTricounts: StateFlow<List<TricountEntity>> = _favoriteTricounts

    fun loadFavoriteTricounts(userId: Int) {
        viewModelScope.launch {
            try {
                val favorites = tricountDao.getFavoriteTricounts(userId)
                _favoriteTricounts.value = favorites
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

// ===== Data classes =====

/** One settlement transaction: fromUser pays toUser this amount */
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

sealed class AddMemberResult {
    data class Success(val memberName: String) : AddMemberResult()
    data class Error(val message: String) : AddMemberResult()
}

sealed class AddExpenseResult {
    object Success : AddExpenseResult()
    data class Error(val message: String) : AddExpenseResult()
}