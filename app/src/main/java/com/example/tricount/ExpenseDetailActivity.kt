package com.example.tricount

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.data.entity.ExpenseSplitWithUser
import com.example.tricount.data.entity.ExpenseWithDetails
import com.example.tricount.data.entity.MemberWithDetails
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.TricountViewModel
import java.text.SimpleDateFormat
import java.util.*

// =============================================================================
// Activity
// =============================================================================

class ExpenseDetailActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val expenseId   = intent.getIntExtra(EXTRA_EXPENSE_ID, -1)
        val tricountId  = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        val sessionManager = SessionManager(this)

        AppTheme.isDark.value = sessionManager.getDarkMode()

        if (expenseId == -1 || tricountId == -1) { finish(); return }

        setContent {
            TriCountTheme() {
                LaunchedEffect(tricountId) {
                    viewModel.loadExpenses(tricountId)
                    viewModel.loadTricountDetails(tricountId)
                }

                val expenses      by viewModel.expenses.collectAsStateWithLifecycle()
                val expenseSplits by viewModel.expenseSplits.collectAsStateWithLifecycle()
                val members       by viewModel.tricountMembers.collectAsStateWithLifecycle()
                val currentUserId  = sessionManager.getUserId() ?: -1

                // Current index in the sorted list for prev/next navigation
                val sortedExpenses = remember(expenses) {
                    expenses.sortedByDescending { it.createdAt }
                }
                var currentIndex by remember(sortedExpenses, expenseId) {
                    mutableStateOf(sortedExpenses.indexOfFirst { it.id == expenseId }
                        .coerceAtLeast(0))
                }
                // Track swipe direction so AnimatedContent slides correctly
                var swipeDir by remember { mutableStateOf(1) }  // 1 = forward, -1 = back

                val expense = sortedExpenses.getOrNull(currentIndex)

                if (expense != null) {
                    AnimatedContent(
                        targetState   = currentIndex,
                        transitionSpec = {
                            val dir = swipeDir
                            slideIntoContainer(
                                towards       = if (dir > 0)
                                    AnimatedContentTransitionScope.SlideDirection.Start
                                else
                                    AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(280)
                            ) togetherWith slideOutOfContainer(
                                towards       = if (dir > 0)
                                    AnimatedContentTransitionScope.SlideDirection.Start
                                else
                                    AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(280)
                            )
                        },
                        label = "expense_page"
                    ) { idx ->
                        val exp    = sortedExpenses.getOrNull(idx) ?: return@AnimatedContent
                        val splits = expenseSplits[exp.id] ?: emptyList()
                        ExpenseDetailScreen(
                            expense       = exp,
                            splits        = splits,
                            members       = members,
                            currentUserId = currentUserId,
                            hasPrev       = idx > 0,
                            hasNext       = idx < sortedExpenses.lastIndex,
                            currentIndex  = idx,
                            totalCount    = sortedExpenses.size,
                            onPrev        = { swipeDir = -1; currentIndex-- },
                            onNext        = { swipeDir =  1; currentIndex++ },
                            onDelete      = {
                                viewModel.deleteExpense(exp.id, tricountId)
                                finish()
                            },
                            onEdit        = { /* handled inside screen via dialog */ },
                            onBackClick   = { finish() },
                            viewModel     = viewModel,
                            tricountId    = tricountId,
                            onSwipeLeft   = {
                                if (idx < sortedExpenses.lastIndex) {
                                    swipeDir = 1; currentIndex++
                                }
                            },
                            onSwipeRight  = {
                                if (idx > 0) {
                                    swipeDir = -1; currentIndex--
                                }
                            }
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_EXPENSE_ID  = "expense_detail_id"
        const val EXTRA_TRICOUNT_ID = "expense_detail_tricount_id"
    }
}

// =============================================================================
// Screen
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    expense       : ExpenseWithDetails,
    splits        : List<ExpenseSplitWithUser>,
    members       : List<MemberWithDetails>,
    currentUserId : Int,
    hasPrev       : Boolean,
    hasNext       : Boolean,
    currentIndex  : Int = 0,
    totalCount    : Int = 1,
    onPrev        : () -> Unit,
    onNext        : () -> Unit,
    onDelete      : () -> Unit,
    onEdit        : () -> Unit,
    onBackClick   : () -> Unit,
    viewModel     : TricountViewModel,
    tricountId    : Int,
    onSwipeLeft   : () -> Unit = {},
    onSwipeRight  : () -> Unit = {}
) {
    val context = LocalContext.current

    var showMenu        by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog  by remember { mutableStateOf(false) }

    val categoryEmoji = mapOf(
        "Food & Drinks" to "🍔", "Transport"    to "🚕", "Accommodation" to "🏨",
        "Entertainment" to "🎬", "Shopping"     to "🛍️", "Health"        to "💊",
        "Groceries"     to "🛒", "Utilities"    to "⚡", "Travel"        to "✈️",
        "Education"     to "📚", "General"      to "📌"
    )
    val emoji   = categoryEmoji[expense.category] ?: "📌"
    val isMe    = expense.paidBy == currentUserId
    val dateStr = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        .format(Date(expense.createdAt))

    val totalShares = splits.sumOf { it.shares }.coerceAtLeast(1)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // ← prev
                    IconButton(onClick = onPrev, enabled = hasPrev) {
                        Icon(
                            Icons.Filled.ChevronLeft, "Previous",
                            tint = if (hasPrev) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    // → next
                    IconButton(onClick = onNext, enabled = hasNext) {
                        Icon(
                            Icons.Filled.ChevronRight, "Next",
                            tint = if (hasNext) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    // ⋮ three-dot menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "Options")
                        }
                        DropdownMenu(
                            expanded         = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Filled.Edit, null,
                                        modifier = Modifier.size(20.dp))
                                },
                                text    = { Text("Edit", fontSize = 15.sp) },
                                onClick = { showMenu = false; showEditDialog = true }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, null,
                                        tint     = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp))
                                },
                                text    = {
                                    Text("Delete", fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.error)
                                },
                                onClick = { showMenu = false; showDeleteDialog = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor     = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->

        // ── Swipe gesture accumulator ─────────────────────────────────────────
        var swipeAccum by remember { mutableStateOf(0f) }
        val swipeThreshold = 80f   // px needed to trigger page change

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pointerInput(hasPrev, hasNext) {
                    detectHorizontalDragGestures(
                        onDragEnd = { swipeAccum = 0f },
                        onDragCancel = { swipeAccum = 0f }
                    ) { _, dragAmount ->
                        swipeAccum += dragAmount
                        when {
                            swipeAccum < -swipeThreshold -> { onSwipeLeft();  swipeAccum = 0f }
                            swipeAccum >  swipeThreshold -> { onSwipeRight(); swipeAccum = 0f }
                        }
                    }
                }
        ) {

            // ── Page indicator dots ───────────────────────────────────────────────
            if (totalCount > 1) {
                Row(
                    modifier              = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    val visibleStart = (currentIndex - 2).coerceAtLeast(0)
                    val visibleEnd   = (currentIndex + 3).coerceAtMost(totalCount)
                    (visibleStart until visibleEnd).forEach { i ->
                        val active = i == currentIndex
                        Box(
                            modifier = Modifier
                                .size(if (active) 8.dp else 5.dp)
                                .background(
                                    color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }
                }
            }

            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = if (totalCount > 1) 24.dp else 0.dp, bottom = 32.dp)
            ) {

                // ── Hero: emoji + name + date ─────────────────────────────────
                item {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(emoji, fontSize = 56.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            expense.name,
                            fontSize   = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            dateStr,
                            fontSize = 14.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── "Paid By" section ─────────────────────────────────────────
                item {
                    DetailSectionHeader("Paid By")
                    Spacer(Modifier.height(8.dp))
                    DetailPersonRow(
                        name          = expense.paidByName,
                        subtitle      = if (isMe) "Me" else null,
                        amount        = expense.amount,
                        isCurrentUser = isMe,
                        amountColor   = Color(0xFFE65100)   // orange like screenshot
                    )
                    Spacer(Modifier.height(24.dp))
                }

                // ── "Participants" section ────────────────────────────────────
                item {
                    DetailSectionHeader("Participants")
                    Spacer(Modifier.height(8.dp))
                }

                if (splits.isEmpty()) {
                    item {
                        // No split data — show all members equally
                        val equalAmount = expense.amount / members.size.coerceAtLeast(1)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape    = RoundedCornerShape(12.dp),
                            color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column {
                                members.forEachIndexed { index, member ->
                                    val isCurrent = member.userId == currentUserId
                                    DetailPersonRow(
                                        name          = member.name,
                                        subtitle      = if (isCurrent) "Me" else null,
                                        amount        = equalAmount,
                                        isCurrentUser = isCurrent,
                                        amountColor   = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (index < members.lastIndex) {
                                        HorizontalDivider(
                                            modifier  = Modifier.padding(start = 72.dp, end = 16.dp),
                                            thickness = 0.5.dp,
                                            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape    = RoundedCornerShape(12.dp),
                            color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column {
                                splits.forEachIndexed { index, split ->
                                    val owedAmount    = expense.amount * split.shares / totalShares
                                    val isCurrent     = split.userId == currentUserId
                                    DetailPersonRow(
                                        name          = split.userName,
                                        subtitle      = if (isCurrent) "Me" else null,
                                        amount        = owedAmount,
                                        isCurrentUser = isCurrent,
                                        amountColor   = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (index < splits.lastIndex) {
                                        HorizontalDivider(
                                            modifier  = Modifier.padding(start = 72.dp, end = 16.dp),
                                            thickness = 0.5.dp,
                                            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Description note ──────────────────────────────────────────
                if (expense.description.isNotBlank()) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        DetailSectionHeader("Note")
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape    = RoundedCornerShape(12.dp),
                            color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                expense.description,
                                modifier = Modifier.padding(16.dp),
                                fontSize = 14.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

    } // end Box (swipe gesture)

    // ── Delete confirmation dialog ────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon  = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Expense?") },
            text  = { Text("Delete \"${expense.name}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; onDelete() },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Edit dialog ───────────────────────────────────────────────────────────
    if (showEditDialog) {
        ExpenseEditDialog(
            expense       = expense,
            tricountId    = tricountId,
            currentUserId = currentUserId,
            members       = members,
            viewModel     = viewModel,
            onDismiss     = { showEditDialog = false }
        )
    }
}

// =============================================================================
// Section header — bold left-aligned like screenshot
// =============================================================================

@Composable
private fun DetailSectionHeader(title: String) {
    Text(
        title,
        fontSize   = 18.sp,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.onBackground,
        modifier   = Modifier.padding(horizontal = 16.dp)
    )
}

// =============================================================================
// Person row — avatar circle + name + subtitle + amount
// Matches the Paid By / Participants rows in the screenshot exactly
// =============================================================================

@Composable
private fun DetailPersonRow(
    name          : String,
    subtitle      : String?,
    amount        : Double,
    isCurrentUser : Boolean,
    amountColor   : androidx.compose.ui.graphics.Color
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle — gray with initial letter, matches screenshot
        Surface(
            shape    = CircleShape,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isCurrentUser) {
                    // Filled person icon for "Me" — like screenshot
                    Icon(
                        Icons.Filled.Person, null,
                        tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Text(
                        name.first().uppercase(),
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        // Name + subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Amount
        Text(
            "₹${"%.2f".format(amount)}",
            fontSize   = 17.sp,
            fontWeight = FontWeight.Bold,
            color      = amountColor
        )
    }
}