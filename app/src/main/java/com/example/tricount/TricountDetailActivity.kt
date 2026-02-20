package com.example.tricount

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.AddExpenseResult
import com.example.tricount.viewModel.AddMemberResult
import com.example.tricount.viewModel.TricountViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.VerticalDivider

class TricountDetailActivity : ComponentActivity() {

    private val tricountViewModel: TricountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tricountId = intent.getIntExtra("TRICOUNT_ID", -1)
        val tricountName = intent.getStringExtra("TRICOUNT_NAME") ?: "Tricount"
        val sessionManager = SessionManager(this)

        setContent {
            TriCountTheme(darkTheme = false) {
                LaunchedEffect(tricountId) {
                    tricountViewModel.loadTricountDetails(tricountId)
                    tricountViewModel.loadExpenses(tricountId)
                }

                val tricountDetails by tricountViewModel.currentTricount.collectAsStateWithLifecycle()
                val members by tricountViewModel.tricountMembers.collectAsStateWithLifecycle()
                val expenses by tricountViewModel.expenses.collectAsStateWithLifecycle()

                TricountDetailScreen(
                    tricountName = tricountName,
                    tricountDetails = tricountDetails,
                    members = members,
                    expenses = expenses,
                    currentUserId = sessionManager.getUserId() ?: -1,
                    viewModel = tricountViewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TricountDetailScreen(
    tricountName: String,
    tricountDetails: TricountEntity?,
    members: List<MemberWithDetails>,
    expenses: List<ExpenseWithDetails>,
    currentUserId: Int,
    viewModel: TricountViewModel,
    onBackClick: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    val tabs = listOf("Expenses", "Balances", "Details")
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(tricountName) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Filled.ArrowBack, null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                tricountDetails?.let { tricount ->
                                    shareTricount(context, tricount.name, tricount.joinCode)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share Tricount")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTabIndex == 0 && tricountDetails != null) {
                FloatingActionButton(
                    onClick = { showAddExpenseDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Expense")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (selectedTabIndex) {
                0 -> ExpensesTab(
                    expenses = expenses,
                    currentUserId = currentUserId,
                    onDeleteExpense = { expenseId ->
                        tricountDetails?.id?.let { tricountId ->
                            viewModel.deleteExpense(expenseId, tricountId)
                        }
                    }
                )
                1 -> BalancesTab()
                2 -> DetailsTab(
                    tricountDetails = tricountDetails,
                    members = members,
                    currentUserId = currentUserId,
                    viewModel = viewModel
                )
            }
        }
    }

    if (showAddExpenseDialog && tricountDetails != null) {
        AddExpenseDialog(
            tricountId = tricountDetails.id,
            currentUserId = currentUserId,
            members = members,
            viewModel = viewModel,
            onDismiss = { showAddExpenseDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Expense Dialog (with share ratios — defaults to 1 per person)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    tricountId: Int,
    currentUserId: Int,
    members: List<MemberWithDetails>,
    viewModel: TricountViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedPayerId by remember { mutableStateOf(currentUserId) }
    var expanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Default: 1 share per member (equal split)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Expense Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Expense Name") },
                    placeholder = { Text("e.g., Dinner, Hotel") },
                    leadingIcon = { Icon(Icons.Filled.ShoppingCart, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                // Amount
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amount = it
                        }
                    },
                    label = { Text("Amount") },
                    placeholder = { Text("0.00") },
                    leadingIcon = {
                        Text("$", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    enabled = !isLoading
                )

                // Who Paid - Dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded && !isLoading }
                ) {
                    OutlinedTextField(
                        value = members.find { it.userId == selectedPayerId }?.name ?: "Select",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Paid By") },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = !isLoading
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (member.isCreator) Icons.Filled.Star else Icons.Filled.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (member.isCreator)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(member.name)
                                    }
                                },
                                onClick = {
                                    selectedPayerId = member.userId
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Add details...") },
                    leadingIcon = { Icon(Icons.Filled.Description, null) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    enabled = !isLoading
                )

                // ── Share Ratios ──
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Split Ratios",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val totalShares = sharesInput.values.mapNotNull { it.toIntOrNull() }.sum()
                    Text(
                        "$totalShares parts total",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    "How many parts does each person owe? (e.g. 1 & 2 means one pays ⅓, other pays ⅔)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                members.forEach { member ->
                    val isCurrentUser = member.userId == currentUserId
                    val totalShares = sharesInput.values
                        .mapNotNull { it.toIntOrNull() }.sum().coerceAtLeast(1)
                    val memberShares = sharesInput[member.userId]?.toIntOrNull() ?: 0
                    val previewAmount = amount.toDoubleOrNull()?.let {
                        if (memberShares > 0) (memberShares.toDouble() / totalShares) * it else null
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = if (isCurrentUser)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    member.name.first().uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isCurrentUser)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isCurrentUser) "You" else member.name,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal
                            )
                            if (previewAmount != null) {
                                Text(
                                    "≈ $${"%.2f".format(previewAmount)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        OutlinedTextField(
                            value = sharesInput[member.userId] ?: "1",
                            onValueChange = { value ->
                                sharesInput[member.userId] = value.filter { it.isDigit() }
                            },
                            modifier = Modifier.width(80.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            suffix = { Text("pt", fontSize = 11.sp) },
                            enabled = !isLoading
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull()
                    val sharesMap = sharesInput
                        .mapValues { it.value.toIntOrNull() ?: 0 }
                        .filter { it.value > 0 }

                    if (name.isNotBlank() && amountDouble != null && amountDouble > 0 && sharesMap.isNotEmpty()) {
                        isLoading = true
                        viewModel.addExpense(
                            tricountId = tricountId,
                            name = name.trim(),
                            description = description.trim(),
                            amount = amountDouble,
                            paidBy = selectedPayerId,
                            sharesMap = sharesMap
                        ) { result ->
                            isLoading = false
                            when (result) {
                                is AddExpenseResult.Success -> {
                                    Toast.makeText(
                                        context,
                                        "Expense added successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }
                                is AddExpenseResult.Error -> {
                                    Toast.makeText(
                                        context,
                                        result.message,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                },
                enabled = name.isNotBlank() &&
                        amount.toDoubleOrNull() != null &&
                        amount.toDoubleOrNull()!! > 0 &&
                        sharesInput.values.any { (it.toIntOrNull() ?: 0) > 0 } &&
                        !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Add Expense")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Expenses Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExpensesTab(
    expenses: List<ExpenseWithDetails>,
    currentUserId: Int,
    onDeleteExpense: (Int) -> Unit
) {
    if (expenses.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Filled.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "No expenses yet",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap the + button to add your first expense",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // REPLACE the existing summary card item with this:
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Top row: My Expenses | Total Expenses
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "My Expenses",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "$${String.format("%.2f", expenses
                                        .filter { it.paidBy == currentUserId }
                                        .sumOf { it.amount })}",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            VerticalDivider(
                                modifier = Modifier
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                            )

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Total Expenses",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "$${String.format("%.2f", expenses.sumOf { it.amount })}",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
            items(expenses, key = { it.id }) { expense ->
                ExpenseCard(
                    expense = expense,
                    isUserExpense = expense.paidBy == currentUserId,
                    onDeleteClick = { onDeleteExpense(expense.id) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Expense Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExpenseCard(
    expense: ExpenseWithDetails,
    isUserExpense: Boolean,
    onDeleteClick: () -> Unit
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (expense.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        expense.description,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Paid by ${expense.paidByName}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    formatDate(expense.createdAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "$${String.format("%.2f", expense.amount)}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isUserExpense) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Expense?") },
            text = {
                Text("Are you sure you want to delete \"${expense.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Balances Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BalancesTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.AccountBalance,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Balances Coming Soon",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "We'll calculate who owes whom",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Details Tab
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsTab(
    tricountDetails: TricountEntity?,
    members: List<MemberWithDetails>,
    currentUserId: Int,
    viewModel: TricountViewModel
) {
    val context = LocalContext.current
    var showAddMemberDialog by remember { mutableStateOf(false) }

    if (tricountDetails == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isCreator = tricountDetails.creatorId == currentUserId

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Join Code",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tricountDetails.joinCode,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(
                            onClick = {
                                copyToClipboard(context, tricountDetails.joinCode)
                                Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copy code",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Share this code with friends to let them join this Tricount",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { shareTricount(context, tricountDetails.name, tricountDetails.joinCode) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Tricount")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Description",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (tricountDetails.description.isNotBlank())
                            tricountDetails.description
                        else
                            "No description provided",
                        fontSize = 14.sp,
                        color = if (tricountDetails.description.isNotBlank())
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Members (${members.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (isCreator) {
                            IconButton(onClick = { showAddMemberDialog = true }) {
                                Icon(
                                    Icons.Filled.PersonAdd,
                                    contentDescription = "Add Member",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (members.isEmpty()) {
                        Text(
                            text = "No members yet",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        members.forEach { member ->
                            MemberItem(
                                member = member,
                                isCreator = isCreator,
                                canRemove = isCreator && !member.isCreator,
                                onRemoveClick = {
                                    viewModel.removeMember(member.userId, tricountDetails.id)
                                }
                            )
                            if (member != members.last()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMemberDialog) {
        AddMemberDialog(
            tricountId = tricountDetails.id,
            viewModel = viewModel,
            onDismiss = { showAddMemberDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Member Item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MemberItem(
    member: MemberWithDetails,
    isCreator: Boolean,
    canRemove: Boolean,
    onRemoveClick: () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (member.isCreator)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (member.isCreator) Icons.Filled.Star else Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (member.isCreator)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = member.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = member.email,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (member.isCreator) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "CREATOR",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else if (canRemove) {
            IconButton(onClick = { showRemoveDialog = true }) {
                Icon(
                    Icons.Filled.RemoveCircle,
                    contentDescription = "Remove member",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove Member?") },
            text = { Text("Are you sure you want to remove ${member.name} from this Tricount?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveClick()
                        showRemoveDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Member Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberDialog(
    tricountId: Int,
    viewModel: TricountViewModel,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isValidEmail = remember(email) {
        val emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex()
        email.isBlank() || emailRegex.matches(email)
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Add Member") },
        text = {
            Column {
                Text(
                    text = "Enter the email address of the person you want to add:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    placeholder = { Text("user@example.com") },
                    leadingIcon = { Icon(Icons.Filled.Email, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isValidEmail,
                    supportingText = {
                        if (!isValidEmail) {
                            Text(
                                text = "Please enter a valid email address",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    enabled = !isLoading
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValidEmail && email.isNotBlank()) {
                        isLoading = true
                        viewModel.addMemberByEmail(tricountId, email.trim()) { result ->
                            isLoading = false
                            when (result) {
                                is AddMemberResult.Success -> {
                                    Toast.makeText(
                                        context,
                                        "${result.memberName} added successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }
                                is AddMemberResult.Error -> {
                                    Toast.makeText(
                                        context,
                                        result.message,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                },
                enabled = isValidEmail && email.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Add")
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
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Tricount Code", text)
    clipboard.setPrimaryClip(clip)
}

private fun shareTricount(context: Context, tricountName: String, joinCode: String) {
    val shareText = """
        Join my Tricount: $tricountName
        
        Use this code to join: $joinCode
        
        Download TriCount app to split expenses easily!
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Join $tricountName on TriCount")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "Share Tricount"))
}