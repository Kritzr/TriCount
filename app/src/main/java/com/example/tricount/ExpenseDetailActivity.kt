package com.example.tricount

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack  // ✅ fixed deprecated ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.data.entity.ExpenseSplitWithUser
import com.example.tricount.data.entity.ExpenseWithDetails
import com.example.tricount.data.entity.MemberWithDetails
import com.example.tricount.ui.components.DestructiveMenuItem
import com.example.tricount.ui.components.NormalMenuItem
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.TricountViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
// =============================================================================
// Activity
// =============================================================================

class ExpenseDetailActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun finish() {
        super.finish()
        // ✅ Fixed deprecated overridePendingTransition (API 34+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        } else {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val expenseId      = intent.getIntExtra(EXTRA_EXPENSE_ID, -1)
        val tricountId     = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        val tricountName   = intent.getStringExtra("extra_tricount_name") ?: ""
        val sessionManager = SessionManager(this)

        AppTheme.isDark.value = sessionManager.getDarkMode()

        if (expenseId == -1 || tricountId == -1) { finish(); return }

        setContent {
            TriCountTheme {
                LaunchedEffect(tricountId) {
                    viewModel.loadExpenses(tricountId)
                    viewModel.loadTricountDetails(tricountId)
                }

                val expenses      by viewModel.expenses.collectAsStateWithLifecycle()
                val expenseSplits by viewModel.expenseSplits.collectAsStateWithLifecycle()
                val members       by viewModel.tricountMembers.collectAsStateWithLifecycle()
                val currentUserId  = sessionManager.getUserId() ?: -1

                val sortedExpenses = remember(expenses) {
                    expenses.sortedByDescending { it.createdAt }
                }

                if (sortedExpenses.isNotEmpty()) {
                    val initialPage = remember(sortedExpenses, expenseId) {
                        sortedExpenses.indexOfFirst { it.id == expenseId }.coerceAtLeast(0)
                    }
                    val pagerState = rememberPagerState(
                        initialPage = initialPage,
                        pageCount   = { sortedExpenses.size }
                    )
                    val scope = rememberCoroutineScope()

                    ExpenseDetailScreen(
                        sortedExpenses = sortedExpenses,
                        expenseSplits  = expenseSplits,
                        members        = members,
                        currentUserId  = currentUserId,
                        pagerState     = pagerState,
                        onPrev         = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        onNext         = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        onDelete       = { expId ->
                            viewModel.deleteExpense(expId, tricountId)
                            finish()
                        },
                        onBackClick    = { finish() },
                        tricountId     = tricountId,
                        tricountName   = tricountName
                    )
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
// Screen — static TopAppBar + dots row; only HorizontalPager content swipes
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    sortedExpenses : List<ExpenseWithDetails>,
    expenseSplits  : Map<Int, List<ExpenseSplitWithUser>>,
    members        : List<MemberWithDetails>,
    currentUserId  : Int,
    pagerState     : androidx.compose.foundation.pager.PagerState,
    onPrev         : () -> Unit,
    onNext         : () -> Unit,
    onDelete       : (Int) -> Unit,
    onBackClick    : () -> Unit,
    // ✅ Removed unused `viewModel` parameter
    tricountId     : Int,
    tricountName   : String
) {
    val context          = LocalContext.current
    var showMenu         by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val currentPage = pagerState.currentPage
    val hasPrev     = currentPage > 0
    val hasNext     = currentPage < sortedExpenses.lastIndex
    val totalCount  = sortedExpenses.size
    val expense     = sortedExpenses.getOrNull(currentPage)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // ── Fully static — never moves during swipe ──────────────────────
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        // ✅ Fixed: use AutoMirrored variant
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onPrev, enabled = hasPrev) {
                        Icon(
                            Icons.Filled.ChevronLeft, "Previous",
                            tint = if (hasPrev) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(onClick = onNext, enabled = hasNext) {
                        Icon(
                            Icons.Filled.ChevronRight, "Next",
                            tint = if (hasNext) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    // ⋮ menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "Options")
                        }
                        DropdownMenu(
                            expanded         = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // ── Edit ─────────────────────────────────────────
                            NormalMenuItem(
                                label   = "Edit",
                                icon    = Icons.Filled.Edit,
                                onClick = {
                                    showMenu = false
                                    expense?.let { exp ->
                                        // ✅ Fixed: derive split mode from the expense's
                                        //    own splits and pass it directly inside apply{}
                                        val splits    = expenseSplits[exp.id] ?: emptyList()
                                        val splitMode = detectSplitModeFromAmounts(splits)
                                        val editIntent = Intent(
                                            context,
                                            EditExpenseActivity::class.java
                                        ).apply {
                                            putExtra(EditExpenseActivity.EXTRA_EXPENSE_ID,    exp.id)
                                            putExtra(EditExpenseActivity.EXTRA_TRICOUNT_ID,   tricountId)
                                            putExtra(EditExpenseActivity.EXTRA_TRICOUNT_NAME, tricountName)
                                            // ✅ Fixed: EXTRA_SPLIT_MODE goes into editIntent,
                                            //    not the incoming `intent`, using the derived mode
                                            putExtra(EditExpenseActivity.EXTRA_SPLIT_MODE,    splitMode.name)
                                        }
                                        context.startActivity(editIntent)
                                        // ✅ Fixed deprecated overridePendingTransition (API 34+)
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                            @Suppress("DEPRECATION")
                                            (context as? Activity)?.overridePendingTransition(
                                                R.anim.slide_in_right, R.anim.slide_out_left
                                            )
                                        } else {
                                            (context as? Activity)?.overrideActivityTransition(
                                                Activity.OVERRIDE_TRANSITION_OPEN,
                                                R.anim.slide_in_right,
                                                R.anim.slide_out_left
                                            )
                                        }
                                    }
                                }
                            )
                            // ── Delete ───────────────────────────────────────
                            DestructiveMenuItem(
                                label   = "Delete",
                                icon    = Icons.Filled.Delete,
                                onClick = { showMenu = false; showDeleteDialog = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor     = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            // ── Number sliding pill indicator ─────────────────────────────────
            if (totalCount > 1) {
                PageNumberIndicator(
                    totalCount  = totalCount,
                    currentPage = currentPage,
                    onPrev      = onPrev,
                    onNext      = onNext,
                    modifier    = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp)
                )
            }

            // ── HorizontalPager — ONLY this section swipes ───────────────────
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val exp    = sortedExpenses.getOrNull(page) ?: return@HorizontalPager
                val splits = expenseSplits[exp.id] ?: emptyList()

                ExpensePageContent(
                    expense       = exp,
                    splits        = splits,
                    members       = members,
                    currentUserId = currentUserId
                )
            }
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    if (showDeleteDialog && expense != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon  = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Expense?") },
            text  = { Text("Delete \"${expense.name}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; onDelete(expense.id) },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// =============================================================================
// Per-page content — this is what slides during swipe
// =============================================================================

@Composable
private fun ExpensePageContent(
    expense       : ExpenseWithDetails,
    splits        : List<ExpenseSplitWithUser>,
    members       : List<MemberWithDetails>,
    currentUserId : Int
) {
    val categoryEmoji = mapOf(
        "Food & Drinks"  to "🍔", "Transport"      to "🚕", "Accommodation" to "🏨",
        "Entertainment"  to "🎬", "Shopping"       to "🛍️", "Health"         to "💊",
        "Groceries"      to "🛒", "Utilities"      to "⚡", "Travel"         to "✈️",
        "Education"      to "📚", "General"        to "📌"
    )
    val emoji       = categoryEmoji[expense.category] ?: "📌"
    val isMe        = expense.paidBy == currentUserId
    val dateStr     = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        .format(Date(expense.createdAt))
    val totalShares = splits.sumOf { it.shares }.coerceAtLeast(1)

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {

        // Hero
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

        // Paid By
        item {
            DetailSectionHeader("Paid By")
            Spacer(Modifier.height(8.dp))
            DetailPersonRow(
                name          = expense.paidByName,
                subtitle      = if (isMe) "Me" else null,
                amount        = expense.amount,
                isCurrentUser = isMe,
                amountColor   = Color(0xFFE65100)
            )
            Spacer(Modifier.height(24.dp))
        }

        // Participants header
        item {
            DetailSectionHeader("Participants")
            Spacer(Modifier.height(8.dp))
        }

        // Participants rows
        if (splits.isEmpty()) {
            item {
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
                            val owedAmount = expense.amount * split.shares / totalShares
                            val isCurrent  = split.userId == currentUserId
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

        // Note
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

// =============================================================================
// Number sliding-window indicator
// =============================================================================

private const val WINDOW_SIZE = 5

@Composable
fun PageNumberIndicator(
    totalCount  : Int,
    currentPage : Int,
    onPrev      : () -> Unit,
    onNext      : () -> Unit,
    modifier    : Modifier = Modifier
) {
    val primary   = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val hasPrev   = currentPage > 0
    val hasNext   = currentPage < totalCount - 1

    val half        = WINDOW_SIZE / 2
    val windowStart = (currentPage - half)
        .coerceIn(0, (totalCount - WINDOW_SIZE).coerceAtLeast(0))
    val windowEnd   = (windowStart + WINDOW_SIZE - 1).coerceAtMost(totalCount - 1)

    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {

        // ── "Previous" label ─────────────────────────────────────────────────
        TextButton(
            onClick  = onPrev,
            enabled  = hasPrev,
            modifier = Modifier.defaultMinSize(minWidth = 72.dp)
        ) {
            Text(
                "Previous",
                fontSize = 13.sp,
                color    = if (hasPrev) primary
                else onSurface.copy(alpha = 0.3f)
            )
        }

        // ── Left overflow hint ───────────────────────────────────────────────
        if (windowStart > 0) {
            Text(
                "‹",
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                color      = onSurface.copy(alpha = 0.35f)
            )
            Spacer(Modifier.width(4.dp))
        }

        // ── Visible window ───────────────────────────────────────────────────
        (windowStart..windowEnd).forEachIndexed { slot, pageIndex ->
            if (slot > 0) Spacer(Modifier.width(4.dp))

            val isActive = pageIndex == currentPage

            val pillAlpha by animateFloatAsState(
                targetValue   = if (isActive) 1f else 0f,
                animationSpec = tween(durationMillis = 180),
                label         = "pill_$pageIndex"
            )
            val textAlpha by animateFloatAsState(
                targetValue   = if (isActive) 1f else 0.45f,
                animationSpec = tween(durationMillis = 180),
                label         = "text_$pageIndex"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(28.dp)
                    .background(
                        color = primary.copy(alpha = pillAlpha),
                        shape = CircleShape
                    )
            ) {
                Text(
                    text       = "${pageIndex + 1}",
                    fontSize   = 13.sp,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    color      = if (isActive) onPrimary.copy(alpha = pillAlpha)
                    else onSurface.copy(alpha = textAlpha)
                )
            }
        }

        // ── Right overflow hint ──────────────────────────────────────────────
        if (windowEnd < totalCount - 1) {
            Spacer(Modifier.width(4.dp))
            Text(
                "›",
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                color      = onSurface.copy(alpha = 0.35f)
            )
        }

        // ── "Next" label ─────────────────────────────────────────────────────
        TextButton(
            onClick  = onNext,
            enabled  = hasNext,
            modifier = Modifier.defaultMinSize(minWidth = 72.dp)
        ) {
            Text(
                "Next",
                fontSize = 13.sp,
                color    = if (hasNext) primary
                else onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

// =============================================================================
// Helpers
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
        Surface(
            shape    = CircleShape,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isCurrentUser) {
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

        Text(
            "₹${"%.2f".format(amount)}",
            fontSize   = 17.sp,
            fontWeight = FontWeight.Bold,
            color      = amountColor
        )
    }
}