package com.example.tricount.viewModel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tricount.data.AppNotification
import com.example.tricount.data.ChatMessage
import com.example.tricount.data.FirebaseSyncRepository
import com.example.tricount.data.SessionManager
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// =============================================================================
// Currency API layer
// =============================================================================

data class ErApiResponse(
    val result               : String?,
    val base_code            : String?,
    val time_last_update_utc : String?,
    val rates                : Map<String, Double>?
)

interface ErApi {
    @GET("latest/USD")
    suspend fun getLatestRates(): ErApiResponse
}

data class FrankfurterResponse(
    val base  : String?,
    val date  : String?,
    val rates : Map<String, Double>?
)

interface FrankfurterApi {
    @GET("latest")
    suspend fun getLatestRates(): FrankfurterResponse
}

private val FALLBACK_RATES_USD_BASED: Map<String, Double> = mapOf(
    "USD" to 1.0, "EUR" to 0.922, "GBP" to 0.789, "INR" to 83.50,
    "JPY" to 149.50, "CAD" to 1.360, "AUD" to 1.528,
    "CHF" to 0.888, "SGD" to 1.344, "AED" to 3.673
)

private val sharedOkHttp by lazy {
    OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
}

private fun makeRetrofit(baseUrl: String): Retrofit =
    Retrofit.Builder().baseUrl(baseUrl).client(sharedOkHttp)
        .addConverterFactory(GsonConverterFactory.create()).build()

object ErApiClient {
    val api: ErApi by lazy { makeRetrofit("https://open.er-api.com/v6/").create(ErApi::class.java) }
}

object FrankfurterClient {
    val api: FrankfurterApi by lazy { makeRetrofit("https://api.frankfurter.dev/v1/").create(FrankfurterApi::class.java) }
}

class CurrencyRepository {
    private var cachedRates   : Map<String, Double>? = null
    private var cacheDate     : String = ""
    private var cacheRateDate : String = ""
    private var usingFallback : Boolean = false

    private val todayStr: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    suspend fun getRates(): Map<String, Double> {
        val today = todayStr
        cachedRates?.takeIf { cacheDate == today }?.let { return it }
        try {
            val resp = ErApiClient.api.getLatestRates()
            if (resp.result == "success" && resp.rates != null) {
                val rates = resp.rates.toMutableMap().apply { put("USD", 1.0) }
                cachedRates = rates; cacheDate = today
                cacheRateDate = resp.time_last_update_utc?.take(16) ?: today
                usingFallback = false
                return rates
            }
        } catch (e: Exception) { Log.w("CurrencyRepo", "open.er-api.com failed: ${e.message}") }
        try {
            val resp = FrankfurterClient.api.getLatestRates()
            if (resp.rates != null) {
                val eurBased = resp.rates.toMutableMap().apply { put("EUR", 1.0) }
                val eurToUsd = eurBased["USD"] ?: 1.085
                val usdBased = eurBased.mapValues { (_, r) -> r / eurToUsd }
                    .toMutableMap().apply { put("USD", 1.0) }
                cachedRates = usdBased; cacheDate = today
                cacheRateDate = resp.date ?: today; usingFallback = false
                return usdBased
            }
        } catch (e: Exception) { Log.w("CurrencyRepo", "frankfurter.dev failed: ${e.message}") }
        cachedRates?.let { return it }
        return useFallback()
    }

    private fun useFallback(): Map<String, Double> {
        usingFallback = true; cacheRateDate = "approx."
        return FALLBACK_RATES_USD_BASED
    }

    fun getRateDate(): String = when {
        usingFallback           -> "approx. (offline)"
        cacheRateDate.isBlank() -> todayStr
        else                    -> cacheRateDate
    }

    suspend fun convert(amount: Double, from: String, to: String): Double? {
        if (from == to) return amount
        val rates = getRates()
        val fromRate = rates[from] ?: return null
        val toRate   = rates[to]   ?: return null
        return amount * toRate / fromRate
    }

    fun isUsingFallback(): Boolean = usingFallback
}

// =============================================================================
// ViewModel
// =============================================================================

class TricountViewModel(application: Application) : AndroidViewModel(application) {

    private val db             = TricountDatabase.getDatabase(application)
    private val tricountDao    = db.tricountDao()
    private val userDao        = db.userDao()
    private val paymentDao     = db.paymentDao()
    private val sessionManager = SessionManager(application)
    private val syncRepo       = FirebaseSyncRepository(db, sessionManager)

    val currencyRepository = CurrencyRepository()

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

    // Messaging
    private val _messages          = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    // Notifications
    private val _notifications     = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications

    // Pending join requests (for tricount creators)
    private val _pendingRequests   = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val pendingRequests: StateFlow<List<Map<String, Any>>> = _pendingRequests

    // FIX: moved inside the class (was incorrectly at file level)
    private var notifListener: com.google.firebase.firestore.ListenerRegistration? = null

    // ── Currency StateFlows ───────────────────────────────────────────────────

    private val _convertedAmount = MutableStateFlow<Double?>(null)
    val convertedAmount: StateFlow<Double?> = _convertedAmount

    private val _rateLoading     = MutableStateFlow(false)
    val rateLoading: StateFlow<Boolean> = _rateLoading

    private val _rateError       = MutableStateFlow<String?>(null)
    val rateError: StateFlow<String?> = _rateError

    private val _rateDate        = MutableStateFlow<String?>(null)
    val rateDate: StateFlow<String?> = _rateDate

    private val _rateIsFallback  = MutableStateFlow(false)
    val rateIsFallback: StateFlow<Boolean> = _rateIsFallback

    fun convertCurrency(amount: Double, from: String, to: String = "INR") {
        if (from == to) {
            _convertedAmount.value = amount
            _rateDate.value        = currencyRepository.getRateDate()
            _rateIsFallback.value  = false
            return
        }
        viewModelScope.launch {
            _rateLoading.value = true; _rateError.value = null
            try {
                val result = currencyRepository.convert(amount, from, to)
                _convertedAmount.value = result
                _rateDate.value        = currencyRepository.getRateDate()
                _rateIsFallback.value  = currencyRepository.isUsingFallback()
                if (result == null) _rateError.value = "Rate for $from or $to not available"
            } catch (e: Exception) {
                val fallback = currencyRepository.convert(amount, from, to)
                _convertedAmount.value = fallback
                _rateIsFallback.value  = true
                _rateDate.value        = currencyRepository.getRateDate()
                if (fallback == null) _rateError.value = "Rate unavailable for $from"
            } finally { _rateLoading.value = false }
        }
    }

    fun prefetchExchangeRates() {
        viewModelScope.launch {
            try {
                currencyRepository.getRates()
                _rateDate.value       = currencyRepository.getRateDate()
                _rateIsFallback.value = currencyRepository.isUsingFallback()
            } catch (e: Exception) { Log.w("TricountVM", "prefetchExchangeRates error: ${e.message}") }
        }
    }

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
                    name        = name,
                    description = description,
                    creatorId   = userId,
                    joinCode    = generateJoinCode(),
                    emoji       = emoji,
                    category    = "created"              // FIX: mark as created
                )
                val tricountId = tricountDao.insertTricount(tricount).toInt()
                tricountDao.addMember(TricountMemberCrossRef(userId = userId, tricountId = tricountId))

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

    /**
     * Duplicate does NOT push to Firestore — it's a local-only copy
     * (prevents the "duplicate = new Firestore doc" bug).
     */
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
                // Do NOT push duplicate to Firestore
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
                val user = userDao.getUserByEmail(trimmed) ?: createPlaceholderUser(trimmed)
                if (tricountDao.isMember(tricountId, user.id) > 0) {
                    onResult(AddMemberResult.Error("${user.name} is already a member")); return@launch
                }
                tricountDao.addMember(TricountMemberCrossRef(userId = user.id, tricountId = tricountId))

                // FIX: update Firestore members[] with the new member's Firebase UID
                if (user.firebaseUid.isNotEmpty()) {
                    syncRepo.updateTricountMembers(tricountId, user.firebaseUid)
                }

                val tricount = tricountDao.getTricountById(tricountId)
                if (tricount != null) {
                    syncRepo.notifyMembersAdded(tricountId, tricount.name, trimmed)
                }

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

    // ── Join by code — requires creator approval ──────────────────────────────

    /**
     * Submit a join request via Firestore. The tricount creator must approve it.
     * JoinResult.Pending is emitted immediately; Success only after approval.
     */
    fun joinTricountByCode(joinCode: String) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: run {
                    _joinResult.value = JoinResult.Error("Not logged in"); return@launch
                }

                // First check Room (offline / already joined)
                val localTricount = tricountDao.getTricountByJoinCode(joinCode)
                if (localTricount != null && tricountDao.isMember(localTricount.id, userId) > 0) {
                    _joinResult.value = JoinResult.Error("You are already a member"); return@launch
                }

                // Submit request to Firestore — creator must approve
                val result = syncRepo.submitJoinRequest(joinCode)
                when {
                    result == null                       -> _joinResult.value = JoinResult.Error("No tricount found with code: $joinCode")
                    result.startsWith("ALREADY_MEMBER:") -> _joinResult.value = JoinResult.Error("You are already a member of ${result.substringAfter(":")}")
                    else                                 -> _joinResult.value = JoinResult.Pending(result)
                }
            } catch (e: Exception) {
                _joinResult.value = JoinResult.Error("Failed to submit request: ${e.message}")
            }
        }
    }

    /**
     * Creator approves a pending join request.
     * Adds member in Firestore and locally in Room.
     */
    fun approveJoinRequest(tricountId: String, requesterUid: String, requesterEmail: String) {
        viewModelScope.launch {
            try {
                // FIX: approveJoinRequest now atomically updates members[] in Firestore
                val approved = syncRepo.approveJoinRequest(tricountId, requesterUid)
                if (approved) {
                    // Reflect the change locally in Room too
                    val localUser = userDao.getUserByEmail(requesterEmail)
                        ?: createPlaceholderUser(requesterEmail)
                    // FIX: save the firebase UID on the local user record if missing
                    if (localUser.firebaseUid.isEmpty()) {
                        userDao.updateFirebaseUid(localUser.id, requesterUid)
                    }
                    val localTricountId = tricountId.toIntOrNull() ?: return@launch
                    if (tricountDao.isMember(localTricountId, localUser.id) == 0) {
                        tricountDao.addMember(TricountMemberCrossRef(userId = localUser.id, tricountId = localTricountId))
                    }
                    loadPendingJoinRequests()
                    loadTricountMembers(localTricountId)
                }
            } catch (e: Exception) { Log.e("TricountVM", "approveJoinRequest error", e) }
        }
    }

    fun rejectJoinRequest(tricountId: String, requesterUid: String) {
        viewModelScope.launch {
            try {
                syncRepo.rejectJoinRequest(tricountId, requesterUid)
                loadPendingJoinRequests()
            } catch (e: Exception) { Log.e("TricountVM", "rejectJoinRequest error", e) }
        }
    }

    fun loadPendingJoinRequests() {
        viewModelScope.launch {
            try {
                _pendingRequests.value = syncRepo.getPendingJoinRequests()
            } catch (e: Exception) { _pendingRequests.value = emptyList() }
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
                if (name.isBlank())                   { onResult(AddExpenseResult.Error("Expense name is required")); return@launch }
                if (amount <= 0)                      { onResult(AddExpenseResult.Error("Amount must be greater than 0")); return@launch }
                if (sharesMap.values.all { it == 0 }) { onResult(AddExpenseResult.Error("At least one member must have shares > 0")); return@launch }

                val expense   = ExpenseEntity(tricountId = tricountId, name = name, description = description, amount = amount, paidBy = paidBy, category = category)
                val expenseId = tricountDao.insertExpense(expense).toInt()
                val splits    = sharesMap.filter { it.value > 0 }.map { (uid, shares) -> ExpenseSplitEntity(expenseId = expenseId, userId = uid, shares = shares) }
                if (splits.isNotEmpty()) tricountDao.insertExpenseSplits(splits)

                // Push to top-level tricounts/{id}/expenses collection
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

    // ── Messaging ─────────────────────────────────────────────────────────────

    fun loadMessages(tricountId: Int) {
        viewModelScope.launch {
            try {
                _messages.value = syncRepo.getMessages(tricountId.toString())
            } catch (e: Exception) {
                Log.e("TricountVM", "loadMessages error", e)
                _messages.value = emptyList()
            }
        }
    }

    fun sendMessage(tricountId: Int, text: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val ok = syncRepo.sendMessage(tricountId.toString(), text)
                if (ok) loadMessages(tricountId)
                onResult(ok)
            } catch (e: Exception) {
                Log.e("TricountVM", "sendMessage error", e)
                onResult(false)
            }
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    fun loadNotifications() {
        viewModelScope.launch {
            try {
                _notifications.value = syncRepo.getNotifications()
            } catch (e: Exception) {
                Log.e("TricountVM", "loadNotifications error", e)
                _notifications.value = emptyList()
            }
        }
    }

    /**
     * Starts a real-time Firestore listener that keeps _notifications up to date.
     * Call this from your Activity/Fragment in onStart() after the user is logged in.
     * Cleans up automatically when the ViewModel is destroyed.
     */
    fun startNotificationListener() {
        notifListener?.remove()
        notifListener = syncRepo.listenForNotifications { list ->
            _notifications.value = list
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            try {
                syncRepo.markNotificationRead(notificationId)
                loadNotifications()
            } catch (e: Exception) { Log.e("TricountVM", "markNotificationRead error", e) }
        }
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    fun uploadProfilePhoto(uri: android.net.Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val base64 = syncRepo.uploadProfileImageToFirestore(getApplication(), uri)
                onDone(base64)
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
                // FIX: sync the new state to Firestore
                val isFav = tricountDao.isFavorite(userId, tricountId) > 0
                syncRepo.updateTricountFavorite(tricountId, isFav)
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
        return userDao.getUserById(newId)!!
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    override fun onCleared() {
        super.onCleared()
        notifListener?.remove()   // FIX: stop the Firestore listener when VM is destroyed
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
    data class Pending(val tricountName: String)     : JoinResult()  // request submitted, awaiting creator approval
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