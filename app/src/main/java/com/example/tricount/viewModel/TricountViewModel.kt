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
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// =============================================================================
// Currency API layer — no API key required, fully free, HTTPS
//
// Primary  : https://open.er-api.com/v6/latest/USD   (USD-based rates)
// Secondary: https://api.frankfurter.dev/v1/latest   (EUR-based rates, fallback)
// Offline  : hardcoded approximate rates             (last resort)
// =============================================================================

// ── open.er-api.com response ─────────────────────────────────────────────────

data class ErApiResponse(
    val result          : String?,              // "success" or "error"
    val base_code       : String?,
    val time_last_update_utc : String?,
    val rates           : Map<String, Double>?
)

interface ErApi {
    // Returns all rates with USD as base — free, no key, HTTPS ✓
    @GET("latest/USD")
    suspend fun getLatestRates(): ErApiResponse
}

// ── frankfurter.dev response ─────────────────────────────────────────────────

data class FrankfurterResponse(
    val base  : String?,
    val date  : String?,
    val rates : Map<String, Double>?
)

interface FrankfurterApi {
    // Returns rates with EUR as base — free, no key, HTTPS ✓
    @GET("latest")
    suspend fun getLatestRates(): FrankfurterResponse
}

// ── Hardcoded fallback (USD-based, approximate) ───────────────────────────────

private val FALLBACK_RATES_USD_BASED: Map<String, Double> = mapOf(
    "USD" to 1.0,
    "EUR" to 0.922,
    "GBP" to 0.789,
    "INR" to 83.50,
    "JPY" to 149.50,
    "CAD" to 1.360,
    "AUD" to 1.528,
    "CHF" to 0.888,
    "SGD" to 1.344,
    "AED" to 3.673
)

// ── Shared OkHttp client ──────────────────────────────────────────────────────

private val sharedOkHttp by lazy {
    OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
}

private fun makeRetrofit(baseUrl: String): Retrofit =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(sharedOkHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

object ErApiClient {
    val api: ErApi by lazy {
        makeRetrofit("https://open.er-api.com/v6/").create(ErApi::class.java)
    }
}

object FrankfurterClient {
    val api: FrankfurterApi by lazy {
        makeRetrofit("https://api.frankfurter.dev/v1/").create(FrankfurterApi::class.java)
    }
}

// ── CurrencyRepository ────────────────────────────────────────────────────────

/**
 * Fetches USD-based rates.
 * Priority chain (each is tried only if the previous fails):
 *   1. Today's in-memory cache
 *   2. open.er-api.com  (primary,   USD base)
 *   3. frankfurter.dev  (secondary, EUR base → normalised to USD base)
 *   4. Hardcoded fallback rates
 *
 * All conversions use USD as the pivot:
 *   result = amount * rates[to] / rates[from]
 */
class CurrencyRepository {

    // USD-based rates cache
    private var cachedRates   : Map<String, Double>? = null
    private var cacheDate     : String = ""
    private var cacheRateDate : String = ""
    private var usingFallback : Boolean = false

    private val todayStr: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** Returns a USD-based rates map. Never throws — always returns something usable. */
    suspend fun getRates(): Map<String, Double> {
        val today = todayStr
        cachedRates?.takeIf { cacheDate == today }?.let { return it }

        // 1. Try open.er-api.com
        try {
            val resp = ErApiClient.api.getLatestRates()
            if (resp.result == "success" && resp.rates != null) {
                val rates = resp.rates.toMutableMap().apply { put("USD", 1.0) }
                cachedRates   = rates
                cacheDate     = today
                cacheRateDate = resp.time_last_update_utc
                    ?.take(16) ?: today          // trim to "Fri, 21 Apr 2025"
                usingFallback = false
                Log.d("CurrencyRepo", "Rates from open.er-api.com: ${resp.time_last_update_utc}")
                return rates
            }
        } catch (e: Exception) {
            Log.w("CurrencyRepo", "open.er-api.com failed: ${e.message}")
        }

        // 2. Try frankfurter.dev (EUR-based → convert to USD-based)
        try {
            val resp = FrankfurterClient.api.getLatestRates()
            if (resp.rates != null) {
                // resp.rates is EUR-based. Add EUR itself, then normalise to USD base.
                val eurBased = resp.rates.toMutableMap().apply { put("EUR", 1.0) }
                val eurToUsd = eurBased["USD"] ?: 1.085   // EUR→USD rate
                // Convert every rate: rate_usd = rate_eur / eurToUsd
                val usdBased = eurBased.mapValues { (_, rateVsEur) ->
                    rateVsEur / eurToUsd
                }.toMutableMap().apply { put("USD", 1.0) }
                cachedRates   = usdBased
                cacheDate     = today
                cacheRateDate = resp.date ?: today
                usingFallback = false
                Log.d("CurrencyRepo", "Rates from frankfurter.dev date=${resp.date}")
                return usdBased
            }
        } catch (e: Exception) {
            Log.w("CurrencyRepo", "frankfurter.dev failed: ${e.message}")
        }

        // 3. Stale cache is better than hardcoded fallback
        cachedRates?.let {
            Log.w("CurrencyRepo", "Both APIs failed — using stale cache")
            return it
        }

        // 4. Last resort: hardcoded fallback
        Log.w("CurrencyRepo", "Using hardcoded fallback rates")
        return useFallback()
    }

    private fun useFallback(): Map<String, Double> {
        usingFallback = true
        cacheRateDate = "approx."
        return FALLBACK_RATES_USD_BASED
    }

    /** Label shown in UI: the update timestamp or "approx. (offline)". */
    fun getRateDate(): String = when {
        usingFallback           -> "approx. (offline)"
        cacheRateDate.isBlank() -> todayStr
        else                    -> cacheRateDate
    }

    /**
     * Convert [amount] from [from] to [to] using USD as the pivot currency.
     *   result = amount * rates[to] / rates[from]
     * Returns null only if either currency code is missing from the rates map.
     */
    suspend fun convert(amount: Double, from: String, to: String): Double? {
        if (from == to) return amount
        val rates    = getRates()
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

    // Currency repository — one instance, shared cache across all screens
    val currencyRepository = CurrencyRepository()

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

    // ── Currency conversion StateFlows ────────────────────────────────────────

    /** Live conversion result: amount in the target currency (INR by default). */
    private val _convertedAmount = MutableStateFlow<Double?>(null)
    val convertedAmount: StateFlow<Double?> = _convertedAmount

    /** True while a live rate network call is in flight. */
    private val _rateLoading = MutableStateFlow(false)
    val rateLoading: StateFlow<Boolean> = _rateLoading

    /** Non-null when the last rate fetch failed. */
    private val _rateError = MutableStateFlow<String?>(null)
    val rateError: StateFlow<String?> = _rateError

    /** The date of the rates currently in cache, shown in the UI. */
    private val _rateDate = MutableStateFlow<String?>(null)
    val rateDate: StateFlow<String?> = _rateDate

    /** True when the conversion is using hardcoded fallback rates (APIs unavailable). */
    private val _rateIsFallback = MutableStateFlow(false)
    val rateIsFallback: StateFlow<Boolean> = _rateIsFallback

    /**
     * Convert [amount] from [from] → [to] (defaults to INR) using live rates.
     * Updates [convertedAmount], [rateLoading], [rateError], and [rateDate].
     */
    fun convertCurrency(amount: Double, from: String, to: String = "INR") {
        if (from == to) {
            _convertedAmount.value = amount
            _rateDate.value        = currencyRepository.getRateDate()
            _rateIsFallback.value  = false
            return
        }
        viewModelScope.launch {
            _rateLoading.value = true
            _rateError.value   = null
            try {
                val result = currencyRepository.convert(amount, from, to)
                _convertedAmount.value = result
                _rateDate.value        = currencyRepository.getRateDate()
                _rateIsFallback.value  = currencyRepository.isUsingFallback()
                if (result == null) _rateError.value = "Rate for $from or $to not available"
            } catch (e: Exception) {
                Log.e("TricountVM", "convertCurrency error", e)
                // Even on exception, try to return a fallback-based result
                val fallback = currencyRepository.convert(amount, from, to)
                _convertedAmount.value = fallback
                _rateIsFallback.value  = true
                _rateDate.value        = currencyRepository.getRateDate()
                if (fallback == null) _rateError.value = "Rate unavailable for $from"
            } finally {
                _rateLoading.value = false
            }
        }
    }

    /** Call once when AddExpenseActivity opens to pre-warm the cache. */
    fun prefetchExchangeRates() {
        viewModelScope.launch {
            try {
                currencyRepository.getRates()   // getRates() never throws — uses fallback internally
                _rateDate.value       = currencyRepository.getRateDate()
                _rateIsFallback.value = currencyRepository.isUsingFallback()
                Log.d("CurrencyRepo", "prefetchExchangeRates done, rateDate=${currencyRepository.getRateDate()}")
            } catch (e: Exception) {
                Log.w("TricountVM", "prefetchExchangeRates unexpected error: ${e.message}")
            }
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
                    name = name, description = description,
                    creatorId = userId, joinCode = generateJoinCode(), emoji = emoji
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
                if (name.isBlank())                   { onResult(AddExpenseResult.Error("Expense name is required")); return@launch }
                if (amount <= 0)                      { onResult(AddExpenseResult.Error("Amount must be greater than 0")); return@launch }
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

                userDao.updatePhotoUri(userId, downloadUrl)
                sessionManager.setProfilePhotoUri(downloadUrl)
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