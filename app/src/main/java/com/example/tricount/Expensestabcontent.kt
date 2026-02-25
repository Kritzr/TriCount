package com.example.tricount

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.material3.VerticalDivider

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

                val expenses by viewModel.expenses.collectAsStateWithLifecycle()
                val members  by viewModel.tricountMembers.collectAsStateWithLifecycle()
                val currentUserId = sessionManager.getUserId() ?: -1

                ExpensesScreen(
                    tricountId    = tricountId,
                    tricountName  = tricountName,
                    expenses      = expenses,
                    members       = members,
                    currentUserId = currentUserId,
                    viewModel     = viewModel,
                    onBackClick   = { finish() }
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
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    tricountId    : Int,
    tricountName  : String,
    expenses      : List<ExpenseWithDetails>,
    members       : List<MemberWithDetails>,
    currentUserId : Int,
    viewModel     : TricountViewModel,
    onBackClick   : () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

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
            modifier      = Modifier.padding(padding),
            expenses      = expenses,
            currentUserId = currentUserId,
            onDeleteExpense = { expenseId ->
                viewModel.deleteExpense(expenseId, tricountId)
            }
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Content (list)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExpensesContent(
    modifier      : Modifier = Modifier,
    expenses      : List<ExpenseWithDetails>,
    currentUserId : Int,
    onDeleteExpense: (Int) -> Unit
) {
    if (expenses.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(
                    Icons.Filled.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
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

        // Expense rows
        items(expenses, key = { it.id }) { expense ->
            ExpenseItemCard(
                expense       = expense,
                isUserExpense = expense.paidBy == currentUserId,
                onDeleteClick = { onDeleteExpense(expense.id) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Expense card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExpenseItemCard(
    expense       : ExpenseWithDetails,
    isUserExpense : Boolean,
    onDeleteClick : () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isUserExpense)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
                    Icon(Icons.Filled.Person, contentDescription = null,
                        modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Paid by ${expense.paidByName}", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Text(formatExpenseDate(expense.createdAt), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$${String.format("%.2f", expense.amount)}", fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (isUserExpense) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text("Delete Expense?") },
            text    = { Text("Are you sure you want to delete \"${expense.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteClick(); showDeleteDialog = false },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
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
        mutableStateMapOf<Int, String>().also { map ->
            members.forEach { map[it.userId] = "1" }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Add Expense") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Expense Name") },
                    placeholder = { Text("e.g., Dinner, Hotel") },
                    leadingIcon = { Icon(Icons.Filled.ShoppingCart, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !isLoading
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it },
                    label = { Text("Amount") },
                    placeholder = { Text("0.00") },
                    leadingIcon = { Text("$", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    enabled = !isLoading
                )

                // Payer dropdown
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded && !isLoading }) {
                    OutlinedTextField(
                        value = members.find { it.userId == selectedPayerId }?.name ?: "Select",
                        onValueChange = {}, readOnly = true,
                        label = { Text("Paid By") },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(), enabled = !isLoading
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (member.isCreator) Icons.Filled.Star else Icons.Filled.Person,
                                            contentDescription = null, modifier = Modifier.size(20.dp),
                                            tint = if (member.isCreator) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(member.name)
                                    }
                                },
                                onClick = { selectedPayerId = member.userId; expanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Add details...") },
                    leadingIcon = { Icon(Icons.Filled.Description, null) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3, enabled = !isLoading
                )

                HorizontalDivider()

                // Shares header
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Split Ratios", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    Text("${sharesInput.values.mapNotNull { it.toIntOrNull() }.sum()} parts total",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("How many parts does each person owe? (e.g. 1 & 2 → one pays ⅓, other pays ⅔)",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Per-member share row
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
                                Text(member.name.first().uppercase(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isCurrentUser) "You" else member.name, fontSize = 14.sp,
                                fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal)
                            if (preview != null) {
                                Text("≈ ${"$"}${"%.2f".format(preview)}", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        OutlinedTextField(
                            value = sharesInput[member.userId] ?: "1",
                            onValueChange = { v -> sharesInput[member.userId] = v.filter { it.isDigit() } },
                            modifier = Modifier.width(80.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, suffix = { Text("pt", fontSize = 11.sp) }, enabled = !isLoading
                        )
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
                                is AddExpenseResult.Success ->
                                { Toast.makeText(context, "Expense added!", Toast.LENGTH_SHORT).show(); onDismiss() }
                                is AddExpenseResult.Error ->
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = name.isNotBlank() && amount.toDoubleOrNull() != null &&
                        amount.toDoubleOrNull()!! > 0 &&
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
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("SimpleDateFormat")
private fun formatExpenseDate(timestamp: Long): String =
    SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date(timestamp))