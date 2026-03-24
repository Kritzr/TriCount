package com.example.tricount

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

        setContent {
            TriCountTheme(darkTheme = false) {
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
    // ── Empty state ──────────────────────────────────────────────────────────
    if (expenses.isEmpty() && archivedExpenses.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Filled.Receipt, contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(20.dp))
                Text("No expenses yet", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text("Tap + to add the first expense", fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
        return
    }

    // ── Group active expenses by full date label ("27 February 2026") ────────
    val grouped = remember(expenses) {
        expenses
            .sortedByDescending { it.createdAt }
            .groupBy { expense ->
                val cal       = Calendar.getInstance().apply { timeInMillis = expense.createdAt }
                val today     = Calendar.getInstance()
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                when {
                    cal.get(Calendar.YEAR)       == today.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
                    cal.get(Calendar.YEAR)       == yesterday.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
                    else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
                        .format(Date(expense.createdAt))
                }
            }
    }

    val myTotal    = expenses.filter { it.paidBy == currentUserId }.sumOf { it.amount }
    val totalAll   = expenses.sumOf { it.amount }

    LazyColumn(
        modifier       = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)   // space for FAB
    ) {
        // ── Summary row — "My Expenses / Total Expenses" ─────────────────────
        item(key = "summary") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("My Expenses", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("₹${"%.2f".format(myTotal)}",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Expenses", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("₹${"%.2f".format(totalAll)}",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }

        // ── Date-grouped expense rows ─────────────────────────────────────────
        grouped.forEach { (dateLabel, dayExpenses) ->

            // Date header — big bold like the screenshot
            item(key = "header_$dateLabel") {
                Text(
                    text     = dateLabel,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color    = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                )
            }

            // Expense rows — flat, no card background
            items(dayExpenses, key = { "active_${it.id}" }) { expense ->
                val ctx = LocalContext.current
                Column {
                    Surface(
                        modifier  = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ctx.startActivity(
                                    android.content.Intent(ctx, ExpenseDetailActivity::class.java).apply {
                                        putExtra(ExpenseDetailActivity.EXTRA_EXPENSE_ID,  expense.id)
                                        putExtra(ExpenseDetailActivity.EXTRA_TRICOUNT_ID, expense.tricountId)
                                    }
                                )
                            },
                        shape    = RoundedCornerShape(0.dp),
                        color    = Color.Transparent,
                        shadowElevation = 0.dp
                    ) {
                        ExpenseItemCard(
                            expense        = expense,
                            currentUserId  = currentUserId,
                            onEditClick    = { onEditExpense(expense) },
                            onArchiveClick = { onArchiveExpense(expense.id) },
                            onDeleteClick  = { onDeleteExpense(expense.id) }
                        )
                    }
                    HorizontalDivider(
                        modifier  = Modifier.padding(start = 76.dp, end = 16.dp),
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
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

@Composable
fun ExpenseItemCard(
    expense        : ExpenseWithDetails,
    currentUserId  : Int = -1,
    onEditClick    : () -> Unit = {},
    onArchiveClick : () -> Unit = {},
    onDeleteClick  : () -> Unit = {}
) {
    val categoryEmoji = mapOf(
        "Food & Drinks" to "🍔", "Transport"    to "🚕", "Accommodation" to "🏨",
        "Entertainment" to "🎬", "Shopping"     to "🛍️", "Health"        to "💊",
        "Groceries"     to "🛒", "Utilities"    to "⚡", "Travel"        to "✈️",
        "Education"     to "📚", "General"      to "📌"
    )
    val emoji       = categoryEmoji[expense.category] ?: "📌"
    val isMe        = expense.paidBy == currentUserId
    val paidByLabel = if (isMe) "${expense.paidByName} (me)" else expense.paidByName

    var showMenu          by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji circle
            Surface(
                shape    = CircleShape,
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 22.sp)
                }
            }

            Spacer(Modifier.width(14.dp))

            // Name + Paid by
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.name,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Paid by ",
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        paidByLabel,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (isMe) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Amount
            Text(
                "₹${"%.2f".format(expense.amount)}",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )

            // ── Three-dot menu ───────────────────────────────────────────────
            Box {
                IconButton(
                    onClick  = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert, "Options",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded         = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Filled.Edit, null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                        },
                        text    = { Text("Edit") },
                        onClick = { showMenu = false; onEditClick() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Filled.Archive, null,
                                tint     = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp))
                        },
                        text    = { Text("Archive",
                            color = MaterialTheme.colorScheme.secondary) },
                        onClick = { showMenu = false; showArchiveDialog = true }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, null,
                                tint     = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp))
                        },
                        text    = { Text("Delete",
                            color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; showDeleteDialog = true }
                    )
                }
            }
        }
    }

    // Archive confirmation
    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            icon  = { Icon(Icons.Filled.Archive, null,
                tint = MaterialTheme.colorScheme.secondary) },
            title = { Text("Archive Expense?") },
            text  = { Text("\"${expense.name}\" will be moved to the archive. You can restore it anytime.") },
            confirmButton = {
                Button(onClick = { onArchiveClick(); showArchiveDialog = false }) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon  = { Icon(Icons.Filled.Delete, null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Expense?") },
            text  = { Text("Delete \"${expense.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteClick(); showDeleteDialog = false },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

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