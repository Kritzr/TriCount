package com.example.tricount

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.data.entity.ExpenseWithDetails
import com.example.tricount.data.entity.MemberWithDetails
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.viewModel.AddExpenseResult
import com.example.tricount.viewModel.TricountViewModel
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Activity
// ─────────────────────────────────────────────────────────────────────────────

class ExpensesActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tricountId   = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        val tricountName = intent.getStringExtra(EXTRA_TRICOUNT_NAME) ?: "Expenses"
        val sessionManager = SessionManager(this)

        if (tricountId == -1) { finish(); return }

        AppTheme.isDark.value = sessionManager.getDarkMode()
        setContent {
            TriCountTheme() {
                LaunchedEffect(tricountId) {
                    viewModel.loadTricountDetails(tricountId)
                    viewModel.loadExpenses(tricountId)
                }

                val expenses         by viewModel.expenses.collectAsStateWithLifecycle()
                val archivedExpenses by viewModel.archivedExpenses.collectAsStateWithLifecycle()
                val members          by viewModel.tricountMembers.collectAsStateWithLifecycle()
                val currentUserId    = sessionManager.getUserId() ?: -1

                ExpensesScreen(
                    tricountId       = tricountId,
                    tricountName     = tricountName,
                    expenses         = expenses,
                    archivedExpenses = archivedExpenses,
                    members          = members,
                    currentUserId    = currentUserId,
                    viewModel        = viewModel,
                    onBackClick      = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TRICOUNT_ID   = "extra_tricount_id"
        const val EXTRA_TRICOUNT_NAME = "extra_tricount_name"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen wrapper
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    tricountId       : Int,
    tricountName     : String,
    expenses         : List<ExpenseWithDetails>,
    archivedExpenses : List<ExpenseWithDetails> = emptyList(),
    members          : List<MemberWithDetails>,
    currentUserId    : Int,
    viewModel        : TricountViewModel,
    onBackClick      : () -> Unit
) {
    var showAddDialog  by remember { mutableStateOf(false) }
    var expenseToEdit  by remember { mutableStateOf<ExpenseWithDetails?>(null) }
    var showArchived   by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tricountName, fontWeight = FontWeight.Bold)
                        Text("Expenses", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Expense")
            }
        }
    ) { padding ->
        ExpensesContent(
            modifier               = Modifier.padding(padding),
            expenses               = expenses,
            archivedExpenses       = archivedExpenses,
            currentUserId          = currentUserId,
            onDeleteExpense        = { expenseId -> viewModel.deleteExpense(expenseId, tricountId) },
            onArchiveExpense       = { expenseId -> viewModel.archiveExpense(expenseId, tricountId) },
            onUnarchiveExpense     = { expenseId -> viewModel.unarchiveExpense(expenseId, tricountId) },
            onDeleteArchivedExpense= { expenseId -> viewModel.deleteExpense(expenseId, tricountId) },
            onEditExpense          = { expense   -> expenseToEdit = expense },
            showArchived           = showArchived,
            onToggleArchived       = { showArchived = !showArchived }
        )
    }

    if (showAddDialog && members.isNotEmpty()) {
        ExpenseAddDialog(
            tricountId    = tricountId,
            currentUserId = currentUserId,
            members       = members,
            viewModel     = viewModel,
            onDismiss     = { showAddDialog = false }
        )
    }

    expenseToEdit?.let { expense ->
        ExpenseEditDialog(
            expense       = expense,
            tricountId    = tricountId,
            currentUserId = currentUserId,
            members       = members,
            viewModel     = viewModel,
            onDismiss     = { expenseToEdit = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Content list  — also called directly from TricountDetailActivity
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExpensesContent(
    modifier               : Modifier = Modifier,
    expenses               : List<ExpenseWithDetails>,
    archivedExpenses       : List<ExpenseWithDetails> = emptyList(),
    currentUserId          : Int,
    onDeleteExpense        : (Int) -> Unit,
    onArchiveExpense       : (Int) -> Unit = {},
    onUnarchiveExpense     : (Int) -> Unit = {},
    onDeleteArchivedExpense: (Int) -> Unit = {},
    onEditExpense          : (ExpenseWithDetails) -> Unit = {},
    showArchived           : Boolean = false,
    onToggleArchived       : () -> Unit = {}
) {
    if (expenses.isEmpty() && archivedExpenses.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Filled.Receipt, contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(24.dp))
                Text("No expenses yet", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Tap + to add the first expense", fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        return
    }

    val grouped = remember(expenses) {
        expenses
            .sortedByDescending { it.createdAt }
            .groupBy { expense ->
                val cal       = Calendar.getInstance().also { it.timeInMillis = expense.createdAt }
                val today     = Calendar.getInstance()
                val yesterday = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -1) }
                when {
                    cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
                    cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
                    else -> SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                        .format(Date(expense.createdAt))
                }
            }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary card
        if (expenses.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("My Expenses", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "$${String.format("%.2f", expenses.filter { it.paidBy == currentUserId }.sumOf { it.amount })}",
                                    fontSize = 26.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            VerticalDivider(
                                modifier = Modifier.height(56.dp).padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Expenses", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "$${String.format("%.2f", expenses.sumOf { it.amount })}",
                                    fontSize = 26.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active expenses grouped by date
        grouped.forEach { (dateLabel, dayExpenses) ->
            item(key = "header_$dateLabel") {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(dateLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp))
                    HorizontalDivider(modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    Text("  $${String.format("%.2f", dayExpenses.sumOf { it.amount })}",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                }
            }
            items(dayExpenses, key = { "active_${it.id}" }) { expense ->
                SwipeableExpenseCard(
                    expense        = expense,
                    onDeleteClick  = { onDeleteExpense(expense.id) },
                    onArchiveClick = { onArchiveExpense(expense.id) },
                    onEditClick    = { onEditExpense(expense) }
                )
            }
        }

        // ── Archived Expenses section ──────────────────────────────────────
        if (archivedExpenses.isNotEmpty()) {
            item(key = "archived_banner") {
                Spacer(Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onToggleArchived() },
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Archive, null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Archived Expenses (${archivedExpenses.size})",
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Icon(
                            if (showArchived) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (showArchived) {
                item(key = "archived_info") {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
                        Row(modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Long-press any expense to unarchive or delete it permanently.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                items(archivedExpenses, key = { "archived_${it.id}" }) { expense ->
                    ArchivedExpenseCard(
                        expense          = expense,
                        onUnarchiveClick = { onUnarchiveExpense(expense.id) },
                        onDeleteClick    = { onDeleteArchivedExpense(expense.id) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Swipeable expense card
//   Swipe RIGHT → Archive (green)    Swipe LEFT → Delete (red)
//   Long press  → context menu: Edit / Archive / Delete
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SwipeableExpenseCard(
    expense        : ExpenseWithDetails,
    onDeleteClick  : () -> Unit,
    onArchiveClick : () -> Unit,
    onEditClick    : () -> Unit = {}
) {
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showContextMenu   by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { showDeleteDialog  = true; false }
                SwipeToDismissBoxValue.StartToEnd -> { showArchiveDialog = true; false }
                SwipeToDismissBoxValue.Settled    -> false
            }
        },
        positionalThreshold = { it * 0.35f }
    )

    SwipeToDismissBox(
        state                       = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection

            val bgColor by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF2E7D32)
                    else                             -> Color.Transparent
                },
                label = "swipeBg"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor, shape = MaterialTheme.shapes.medium)
                    .padding(horizontal = 24.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else                             -> Alignment.CenterStart
                }
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Delete, "Delete",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    SwipeToDismissBoxValue.StartToEnd -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Archive, "Archive",
                            tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("Archive", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = Color.White)
                    }
                    else -> {}
                }
            }
        }
    ) {
        ExpenseItemCard(expense = expense, onLongClick = { showContextMenu = true })
    }

    // Context menu (long press)
    if (showContextMenu) {
        AlertDialog(
            onDismissRequest = { showContextMenu = false },
            title = { Text(expense.name, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text  = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth()
                        .clickable { showContextMenu = false; onEditClick() }
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(16.dp))
                        Text("Edit", fontSize = 15.sp)
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth()
                        .clickable { showContextMenu = false; showArchiveDialog = true }
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Archive, null, modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(16.dp))
                        Text("Archive", fontSize = 15.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth()
                        .clickable { showContextMenu = false; showDeleteDialog = true }
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(16.dp))
                        Text("Delete", fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContextMenu = false }) { Text("Cancel") } }
        )
    }

    // Delete confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text("Delete Expense?") },
            text    = { Text("Delete \"${expense.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDeleteClick(); showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    // Archive confirmation
    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title   = { Text("Archive Expense?") },
            text    = { Text("Archive \"${expense.name}\"? You can restore it from the archived section.") },
            confirmButton = {
                TextButton(onClick = { onArchiveClick(); showArchiveDialog = false }) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { showArchiveDialog = false }) { Text("Cancel") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Archived expense card — long-press to Unarchive or Delete
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchivedExpenseCard(
    expense          : ExpenseWithDetails,
    onUnarchiveClick : () -> Unit,
    onDeleteClick    : () -> Unit
) {
    var showMenu            by remember { mutableStateOf(false) }
    var showDeleteDialog    by remember { mutableStateOf(false) }
    var showUnarchiveDialog by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { showMenu = true })
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Archive, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.width(6.dp))
                    Text(expense.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
                if (expense.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(expense.description, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, null, modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    Spacer(Modifier.width(4.dp))
                    Text("Paid by ${expense.paidByName}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                }
                Text(formatExpenseDate(expense.createdAt), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
            Text("$${String.format("%.2f", expense.amount)}",
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        }
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text(expense.name, fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth()
                        .clickable { showMenu = false; showUnarchiveDialog = true }
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Unarchive, null, modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("Unarchive", fontSize = 15.sp)
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth()
                        .clickable { showMenu = false; showDeleteDialog = true }
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(16.dp))
                        Text("Delete Permanently", fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Cancel") } }
        )
    }

    if (showUnarchiveDialog) {
        AlertDialog(
            onDismissRequest = { showUnarchiveDialog = false },
            icon  = { Icon(Icons.Filled.Unarchive, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Unarchive \"${expense.name}\"?") },
            text  = { Text("This will move the expense back to the active list.") },
            confirmButton = {
                Button(onClick = { onUnarchiveClick(); showUnarchiveDialog = false }) { Text("Unarchive") }
            },
            dismissButton = { TextButton(onClick = { showUnarchiveDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon  = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete \"${expense.name}\"?") },
            text  = { Text("This permanently deletes this expense. This cannot be undone.") },
            confirmButton = {
                Button(onClick = { onDeleteClick(); showDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Plain expense card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpenseItemCard(expense: ExpenseWithDetails, onLongClick: () -> Unit = {}) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.name, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                if (expense.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(expense.description, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Paid by ${expense.paidByName}", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Text(formatExpenseDate(expense.createdAt), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("$${String.format("%.2f", expense.amount)}",
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Expense Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseAddDialog(
    tricountId    : Int,
    currentUserId : Int,
    members       : List<MemberWithDetails>,
    viewModel     : TricountViewModel,
    onDismiss     : () -> Unit
) {
    var name            by remember { mutableStateOf("") }
    var description     by remember { mutableStateOf("") }
    var amount          by remember { mutableStateOf("") }
    var selectedPayerId by remember { mutableStateOf(currentUserId) }
    var expanded        by remember { mutableStateOf(false) }
    var isLoading       by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val sharesInput = remember {
        mutableStateMapOf<Int, String>().also { map -> members.forEach { map[it.userId] = "1" } }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Add Expense") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {

                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Expense Name") }, placeholder = { Text("e.g., Dinner, Hotel") },
                    leadingIcon = { Icon(Icons.Filled.ShoppingCart, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !isLoading)

                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it },
                    label = { Text("Amount") }, placeholder = { Text("0.00") },
                    leadingIcon = { Text("$", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    enabled = !isLoading)

                ExposedDropdownMenuBox(expanded = expanded,
                    onExpandedChange = { expanded = !expanded && !isLoading }) {
                    OutlinedTextField(
                        value = members.find { it.userId == selectedPayerId }?.name ?: "Select",
                        onValueChange = {}, readOnly = true, label = { Text("Paid By") },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(), enabled = !isLoading)
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (member.isCreator) Icons.Filled.Star else Icons.Filled.Person,
                                            null, modifier = Modifier.size(20.dp),
                                            tint = if (member.isCreator) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.secondary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(member.name)
                                    }
                                },
                                onClick = { selectedPayerId = member.userId; expanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    leadingIcon = { Icon(Icons.Filled.Description, null) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3, enabled = !isLoading)

                HorizontalDivider()

                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Split Ratios", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    Text("${sharesInput.values.mapNotNull { it.toIntOrNull() }.sum()} parts total",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("How many parts does each person owe?",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                members.forEach { member ->
                    val isCurrentUser = member.userId == currentUserId
                    val totalShares   = sharesInput.values.mapNotNull { it.toIntOrNull() }.sum().coerceAtLeast(1)
                    val memberShares  = sharesInput[member.userId]?.toIntOrNull() ?: 0
                    val preview       = amount.toDoubleOrNull()?.let {
                        if (memberShares > 0) (memberShares.toDouble() / totalShares) * it else null
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(modifier = Modifier.size(36.dp), shape = CircleShape,
                            color = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(member.name.first().uppercase(), fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isCurrentUser) "You" else member.name, fontSize = 14.sp,
                                fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal)
                            if (preview != null)
                                Text("≈ ${"$"}${"%.2f".format(preview)}", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedTextField(
                            value = sharesInput[member.userId] ?: "1",
                            onValueChange = { v -> sharesInput[member.userId] = v.filter { it.isDigit() } },
                            modifier = Modifier.width(80.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, suffix = { Text("pt", fontSize = 11.sp) },
                            enabled = !isLoading)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull()
                    val sharesMap    = sharesInput.mapValues { it.value.toIntOrNull() ?: 0 }.filter { it.value > 0 }
                    if (name.isNotBlank() && amountDouble != null && amountDouble > 0 && sharesMap.isNotEmpty()) {
                        isLoading = true
                        viewModel.addExpense(
                            tricountId  = tricountId,
                            name        = name.trim(),
                            description = description.trim(),
                            amount      = amountDouble,
                            paidBy      = selectedPayerId,
                            sharesMap   = sharesMap
                        ) { result ->
                            isLoading = false
                            when (result) {
                                is AddExpenseResult.Success -> {
                                    Toast.makeText(context, "Expense added!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                is AddExpenseResult.Error ->
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = name.isNotBlank() && amount.toDoubleOrNull() != null &&
                        (amount.toDoubleOrNull() ?: 0.0) > 0 &&
                        sharesInput.values.any { (it.toIntOrNull() ?: 0) > 0 } && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Add Expense")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Edit Expense Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditDialog(
    expense       : ExpenseWithDetails,
    tricountId    : Int,
    currentUserId : Int,
    members       : List<MemberWithDetails>,
    viewModel     : TricountViewModel,
    onDismiss     : () -> Unit
) {
    var name            by remember { mutableStateOf(expense.name) }
    var description     by remember { mutableStateOf(expense.description) }
    var amount          by remember { mutableStateOf(String.format("%.2f", expense.amount)) }
    var selectedPayerId by remember { mutableStateOf(expense.paidBy) }
    var expanded        by remember { mutableStateOf(false) }
    var isLoading       by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val sharesInput = remember {
        mutableStateMapOf<Int, String>().also { map -> members.forEach { map[it.userId] = "1" } }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Edit Expense") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {

                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Expense Name") },
                    leadingIcon = { Icon(Icons.Filled.ShoppingCart, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !isLoading)

                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it },
                    label = { Text("Amount") },
                    leadingIcon = { Text("$", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !isLoading)

                ExposedDropdownMenuBox(expanded = expanded,
                    onExpandedChange = { expanded = !expanded && !isLoading }) {
                    OutlinedTextField(
                        value = members.find { it.userId == selectedPayerId }?.name ?: "Select",
                        onValueChange = {}, readOnly = true, label = { Text("Paid By") },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(), enabled = !isLoading)
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        members.forEach { member ->
                            DropdownMenuItem(text = { Text(member.name) },
                                onClick = { selectedPayerId = member.userId; expanded = false })
                        }
                    }
                }

                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    leadingIcon = { Icon(Icons.Filled.Description, null) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3, enabled = !isLoading)

                HorizontalDivider()
                Text("Split Ratios", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)

                members.forEach { member ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(modifier = Modifier.size(36.dp), shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(member.name.first().uppercase(), fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Text(if (member.userId == currentUserId) "You" else member.name,
                            modifier = Modifier.weight(1f), fontSize = 14.sp)
                        OutlinedTextField(
                            value = sharesInput[member.userId] ?: "1",
                            onValueChange = { v -> sharesInput[member.userId] = v.filter { it.isDigit() } },
                            modifier = Modifier.width(80.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, suffix = { Text("pt", fontSize = 11.sp) },
                            enabled = !isLoading)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull()
                    val sharesMap    = sharesInput.mapValues { it.value.toIntOrNull() ?: 0 }.filter { it.value > 0 }
                    if (name.isNotBlank() && amountDouble != null && amountDouble > 0 && sharesMap.isNotEmpty()) {
                        isLoading = true
                        viewModel.deleteExpense(expense.id, tricountId)
                        viewModel.addExpense(
                            tricountId  = tricountId,
                            name        = name.trim(),
                            description = description.trim(),
                            amount      = amountDouble,
                            paidBy      = selectedPayerId,
                            sharesMap   = sharesMap
                        ) { result ->
                            isLoading = false
                            when (result) {
                                is AddExpenseResult.Success -> {
                                    Toast.makeText(context, "Expense updated!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                is AddExpenseResult.Error ->
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = name.isNotBlank() && amount.toDoubleOrNull() != null &&
                        (amount.toDoubleOrNull() ?: 0.0) > 0 &&
                        sharesInput.values.any { (it.toIntOrNull() ?: 0) > 0 } && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Save Changes")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("SimpleDateFormat")
private fun formatExpenseDate(timestamp: Long): String =
    SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date(timestamp))