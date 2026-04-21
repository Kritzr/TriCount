package com.example.tricount

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.data.entity.ExpenseWithDetails
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.TricountViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Activity
// ─────────────────────────────────────────────────────────────────────────────

class SearchExpensesActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tricountId   = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        val tricountName = intent.getStringExtra(EXTRA_TRICOUNT_NAME) ?: "Expenses"
        val sessionManager = SessionManager(this)

        // Honour the user's dark-mode preference, exactly as TricountDetailActivity does
        AppTheme.isDark.value = sessionManager.getDarkMode()

        if (tricountId == -1) { finish(); return }

        setContent {
            TriCountTheme() {
                LaunchedEffect(tricountId) {
                    viewModel.loadExpenses(tricountId)
                }

                val expenses      by viewModel.expenses.collectAsStateWithLifecycle()
                val currentUserId = sessionManager.getUserId() ?: -1

                SearchExpensesScreen(
                    tricountId    = tricountId,
                    tricountName  = tricountName,
                    expenses      = expenses,
                    currentUserId = currentUserId,
                    onBackClick   = { finish() },
                    onExpenseClick = { expense ->
                        val detailIntent = Intent(this, ExpenseDetailActivity::class.java).apply {
                            putExtra(ExpenseDetailActivity.EXTRA_EXPENSE_ID,  expense.id)
                            putExtra(ExpenseDetailActivity.EXTRA_TRICOUNT_ID, expense.tricountId)
                        }
                        startActivity(detailIntent)
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TRICOUNT_ID   = "extra_tricount_id"
        const val EXTRA_TRICOUNT_NAME = "extra_tricount_name"

        // Maximum number of recent searches to keep
        private const val MAX_RECENT_SEARCHES = 8
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchExpensesScreen(
    tricountId    : Int,
    tricountName  : String,
    expenses      : List<ExpenseWithDetails>,
    currentUserId : Int,
    onBackClick   : () -> Unit,
    onExpenseClick: (ExpenseWithDetails) -> Unit
) {
    var searchQuery     by remember { mutableStateOf("") }
    var recentSearches  by remember { mutableStateOf<List<String>>(emptyList()) }
    val focusRequester  = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus the search field when the screen opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Handle hardware/gesture back
    BackHandler { onBackClick() }

    // Filtered results — searches name, description, paidByName, and category
    val searchResults = remember(expenses, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else expenses.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true) ||
                    it.paidByName.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    fun commitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        // Add to recent searches (deduplicated, most-recent-first, capped)
        recentSearches = (listOf(trimmed) + recentSearches.filter { it != trimmed })
            .take(8)
        keyboardController?.hide()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                shadowElevation = 2.dp,
                color           = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Search field
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder   = {
                            Text(
                                "Search in $tricountName…",
                                fontSize = 14.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        leadingIcon   = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon  = {
                            AnimatedVisibility(
                                visible = searchQuery.isNotEmpty(),
                                enter   = fadeIn(),
                                exit    = fadeOut()
                            ) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, "Clear search")
                                }
                            }
                        },
                        singleLine      = true,
                        modifier        = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        shape           = RoundedCornerShape(50),
                        colors          = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { commitSearch(searchQuery) }
                        )
                    )

                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {

            // ── Results count ─────────────────────────────────────────────────
            if (searchQuery.isNotBlank()) {
                item(key = "count") {
                    Text(
                        text     = "${searchResults.size} result${if (searchResults.size == 1) "" else "s"} for \"$searchQuery\"",
                        fontSize = 12.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

            // ── Search results ────────────────────────────────────────────────
            if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                items(searchResults, key = { "result_${it.id}" }) { expense ->
                    SearchExpenseRow(
                        expense        = expense,
                        query          = searchQuery,
                        currentUserId  = currentUserId,
                        onClick        = {
                            commitSearch(searchQuery)
                            onExpenseClick(expense)
                        }
                    )
                    HorizontalDivider(
                        modifier  = Modifier.padding(start = 76.dp, end = 16.dp),
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }

            // ── Empty results state ───────────────────────────────────────────
            if (searchQuery.isNotBlank() && searchResults.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp, start = 32.dp, end = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape    = CircleShape,
                            color    = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "No results found",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "No expenses match \"$searchQuery\"",
                            fontSize  = 14.sp,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── Recent searches (only shown when query is blank) ──────────────
            if (searchQuery.isBlank() && recentSearches.isNotEmpty()) {
                item(key = "recent_header") {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent Searches",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { recentSearches = emptyList() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "Clear all",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                items(recentSearches, key = { "recent_$it" }) { term ->
                    RecentSearchRow(
                        term    = term,
                        onClick = { searchQuery = term },
                        onRemove = {
                            recentSearches = recentSearches.filter { it != term }
                        }
                    )
                }
            }

            // ── Idle state (no query, no recents) ─────────────────────────────
            if (searchQuery.isBlank() && recentSearches.isEmpty()) {
                item(key = "idle") {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp, start = 32.dp, end = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape    = CircleShape,
                            color    = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Search expenses",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Find expenses by name, description,\npaid-by, or category",
                            fontSize  = 14.sp,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recent search row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentSearchRow(
    term    : String,
    onClick : () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text     = term,
                fontSize = 15.sp,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick  = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove",
                modifier           = Modifier.size(16.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Expense row in search results
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchExpenseRow(
    expense       : ExpenseWithDetails,
    query         : String,
    currentUserId : Int,
    onClick       : () -> Unit
) {
    val categoryEmoji = mapOf(
        "Food & Drinks" to "🍔", "Transport"     to "🚕", "Accommodation" to "🏨",
        "Entertainment" to "🎬", "Shopping"      to "🛍️", "Health"        to "💊",
        "Groceries"     to "🛒", "Utilities"     to "⚡", "Travel"        to "✈️",
        "Education"     to "📚", "General"       to "📌"
    )
    val emoji = categoryEmoji[expense.category] ?: "📌"
    val isMe  = expense.paidBy == currentUserId

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category emoji circle
        Surface(
            shape    = CircleShape,
            color    = if (isMe)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 22.sp)
            }
        }

        Spacer(Modifier.width(14.dp))

        // Name + paid-by
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = expense.name,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append("Paid by ")
                    }
                    withStyle(SpanStyle(
                        fontWeight = FontWeight.SemiBold,
                        color      = if (isMe) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )) {
                        append(expense.paidByName)
                    }
                    if (isMe) {
                        withStyle(SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )) { append(" (me)") }
                    }
                },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Show category chip if it matched the query
            if (expense.category.contains(query, ignoreCase = true) && expense.category != "General") {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        expense.category,
                        fontSize   = 10.sp,
                        color      = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        // Amount + "you" badge
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text       = "₹${"%.2f".format(expense.amount)}",
                fontSize   = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            if (isMe) {
                Spacer(Modifier.height(3.dp))
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "you",
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}