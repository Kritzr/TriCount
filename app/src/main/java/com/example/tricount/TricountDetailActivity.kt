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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.tricount.data.entity.*
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.*
import java.text.SimpleDateFormat
import java.util.*

class TricountDetailActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tricountId   = intent.getIntExtra("TRICOUNT_ID", -1)
        val tricountName = intent.getStringExtra("TRICOUNT_NAME") ?: "Tricount"
        val sessionManager = SessionManager(this)

        setContent {
            TriCountTheme(darkTheme = false) {
                LaunchedEffect(tricountId) {
                    viewModel.loadTricountDetails(tricountId)
                }

                val tricountDetails by viewModel.currentTricount.collectAsStateWithLifecycle()
                val members         by viewModel.tricountMembers.collectAsStateWithLifecycle()
                val expenses        by viewModel.expenses.collectAsStateWithLifecycle()
                val expenseSplits   by viewModel.expenseSplits.collectAsStateWithLifecycle()
                val settlements     by viewModel.settlements.collectAsStateWithLifecycle()
                val currentUserId   = sessionManager.getUserId() ?: -1

                TricountDetailScreen(
                    tricountId      = tricountId,
                    tricountName    = tricountName,
                    tricountDetails = tricountDetails,
                    members         = members,
                    expenses        = expenses,
                    expenseSplits   = expenseSplits,
                    settlements     = settlements,
                    currentUserId   = currentUserId,
                    viewModel       = viewModel,
                    onBackClick     = { finish() }
                )
            }
        }
    }

    // Refresh expenses when returning from AddExpenseActivity
    override fun onResume() {
        super.onResume()
        val tricountId = intent.getIntExtra("TRICOUNT_ID", -1)
        if (tricountId != -1) viewModel.loadExpenses(tricountId)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root screen — TabRow + per-tab content
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TricountDetailScreen(
    tricountId      : Int,
    tricountName    : String,
    tricountDetails : TricountEntity?,
    members         : List<MemberWithDetails>,
    expenses        : List<ExpenseWithDetails>,
    expenseSplits   : Map<Int, List<ExpenseSplitWithUser>>,
    settlements     : List<Settlement>,
    currentUserId   : Int,
    viewModel       : TricountViewModel,
    onBackClick     : () -> Unit
) {
    val context     = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(tricountName, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            tricountDetails?.let {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT,
                                        "Join my Tricount \"${it.name}\"!\nCode: ${it.joinCode}")
                                }
                                context.startActivity(Intent.createChooser(send, "Share"))
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                TabRow(selectedTabIndex = selectedTab) {
                    listOf(
                        Icons.Filled.Receipt       to "Expenses",
                        Icons.Filled.AccountBalance to "Balances",
                        Icons.Filled.Info           to "Details"
                    ).forEachIndexed { index, (icon, label) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick  = { selectedTab = index },
                            text = {
                                Text(label,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold
                                    else FontWeight.Normal)
                            },
                            icon = {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // FAB only visible on Expenses tab — navigates to AddExpenseActivity
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, AddExpenseActivity::class.java).apply {
                                putExtra(AddExpenseActivity.EXTRA_TRICOUNT_ID,   tricountId)
                                putExtra(AddExpenseActivity.EXTRA_TRICOUNT_NAME, tricountName)
                            }
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Expense")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> ExpensesTab(
                    expenses        = expenses,
                    currentUserId   = currentUserId,
                    onDeleteExpense = { expenseId -> viewModel.deleteExpense(expenseId, tricountId) }
                )
                1 -> BalancesTab(
                    expenses      = expenses,
                    expenseSplits = expenseSplits,
                    settlements   = settlements,
                    currentUserId = currentUserId,
                    memberCount   = members.size,
                    expenseCount  = expenses.size
                )
                2 -> DetailsTab(
                    tricountDetails = tricountDetails,
                    members         = members,
                    currentUserId   = currentUserId,
                    viewModel       = viewModel
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// TAB 0 — EXPENSES
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun ExpensesTab(
    expenses        : List<ExpenseWithDetails>,
    currentUserId   : Int,
    onDeleteExpense : (Int) -> Unit
) {
    if (expenses.isEmpty()) {
        EmptyState(
            icon    = Icons.Filled.Receipt,
            title   = "No expenses yet",
            subtitle = "Tap the + button to add the first expense"
        )
        return
    }

    val myTotal    = expenses.filter { it.paidBy == currentUserId }.sumOf { it.amount }
    val totalSpent = expenses.sumOf { it.amount }

    LazyColumn(
        modifier        = Modifier.fillMaxSize(),
        contentPadding  = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Summary card ──
        item {
            Card(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text("My Expenses", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Spacer(Modifier.height(4.dp))
                        Text("${"$"}${"%.2f".format(myTotal)}", fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    VerticalDivider(
                        modifier = Modifier.height(52.dp),
                        color    = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total Expenses", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Spacer(Modifier.height(4.dp))
                        Text("${"$"}${"%.2f".format(totalSpent)}", fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        // ── Expense rows ──
        items(expenses, key = { it.id }) { expense ->
            ExpenseCard(
                expense       = expense,
                isUserExpense = expense.paidBy == currentUserId,
                onDeleteClick = { onDeleteExpense(expense.id) }
            )
        }
    }
}

@SuppressLint("SimpleDateFormat")
@Composable
private fun ExpenseCard(
    expense       : ExpenseWithDetails,
    isUserExpense : Boolean,
    onDeleteClick : () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = if (isUserExpense)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (expense.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(expense.description, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, null,
                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Paid by ${expense.paidByName}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
                Text(
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(expense.createdAt)),
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Text("${"$"}${"%.2f".format(expense.amount)}", fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (isUserExpense) {
                    IconButton(onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, null,
                            modifier = Modifier.size(18.dp),
                            tint     = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
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

// ═════════════════════════════════════════════════════════════════════════════
// TAB 1 — BALANCES
// ═════════════════════════════════════════════════════════════════════════════

private data class BalanceRow(val userId: Int, val name: String, val net: Double)

@Composable
fun BalancesTab(
    expenses      : List<ExpenseWithDetails>,
    expenseSplits : Map<Int, List<ExpenseSplitWithUser>>,
    settlements   : List<Settlement>,
    currentUserId : Int,
    memberCount   : Int,
    expenseCount  : Int
) {
    if (expenses.isEmpty()) {
        EmptyState(
            icon     = Icons.Filled.AccountBalance,
            title    = "No balances yet",
            subtitle = "Add expenses to see who owes what"
        )
        return
    }

    val balanceRows = remember(expenses, expenseSplits) {
        val netMap  = mutableMapOf<Int, Double>()
        val nameMap = mutableMapOf<Int, String>()
        for (expense in expenses) {
            netMap[expense.paidBy]  = (netMap[expense.paidBy]  ?: 0.0) + expense.amount
            nameMap[expense.paidBy] = expense.paidByName
            for (split in expenseSplits[expense.id] ?: emptyList()) {
                netMap[split.userId]  = (netMap[split.userId]  ?: 0.0) - split.amount
                nameMap[split.userId] = split.userName
            }
        }
        netMap.map { (id, net) -> BalanceRow(id, nameMap[id] ?: "?", net) }
            .sortedByDescending { it.net }
    }

    val totalSpent = expenses.sumOf { it.amount }
    val myPaid     = expenses.filter { it.paidBy == currentUserId }.sumOf { it.amount }
    val myNet      = balanceRows.find { it.userId == currentUserId }?.net ?: 0.0
    val green      = Color(0xFF2E7D32)
    val red        = Color(0xFFC62828)

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Overview ──
        item {
            Card(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Overview", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatChip("Total Spent",  "${"$"}${"%.2f".format(totalSpent)}")
                        StatChip("Members",      "$memberCount")
                        StatChip("Expenses",     "$expenseCount")
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically) {
                        Column {
                            Text("I paid", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text("${"$"}${"%.2f".format(myPaid)}", fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("My balance", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(
                                when {
                                    myNet >  0.01 -> "+${"$"}${"%.2f".format(myNet)}"
                                    myNet < -0.01 -> "-${"$"}${"%.2f".format(-myNet)}"
                                    else          -> "$0.00"
                                },
                                fontSize   = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    myNet >  0.01 -> green
                                    myNet < -0.01 -> red
                                    else          -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Individual balances ──
        item {
            Text("Individual Balances", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    balanceRows.forEachIndexed { idx, row ->
                        val isMe = row.userId == currentUserId
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            // Avatar
                            Surface(modifier = Modifier.size(40.dp), shape = CircleShape,
                                color = if (isMe) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondaryContainer) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(row.name.first().uppercase(),
                                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                        color = if (isMe) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (isMe) "You (${row.name})" else row.name,
                                    fontSize   = 14.sp,
                                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal)
                                Text(when {
                                    row.net >  0.01 -> "gets back"
                                    row.net < -0.01 -> "owes"
                                    else            -> "settled up ✓"
                                }, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                when {
                                    row.net >  0.01 -> "+${"$"}${"%.2f".format(row.net)}"
                                    row.net < -0.01 -> "-${"$"}${"%.2f".format(-row.net)}"
                                    else            -> "$0.00"
                                },
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    row.net >  0.01 -> green
                                    row.net < -0.01 -> red
                                    else            -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (idx < balanceRows.lastIndex)
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                }
            }
        }


    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// TAB 2 — DETAILS
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsTab(
    tricountDetails : TricountEntity?,
    members         : List<MemberWithDetails>,
    currentUserId   : Int,
    viewModel       : TricountViewModel
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
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Join code
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically) {
                        Column {
                            Text("Join Code", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Spacer(Modifier.height(4.dp))
                            Text(tricountDetails.joinCode, fontSize = 30.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 4.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                            cb.setPrimaryClip(
                                ClipData.newPlainText("code", tricountDetails.joinCode))
                            Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.ContentCopy, null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick   = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT,
                                    "Join my Tricount \"${tricountDetails.name}\"!\n" +
                                            "Code: ${tricountDetails.joinCode}")
                            }
                            context.startActivity(Intent.createChooser(send, "Share"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share Tricount")
                    }
                }
            }
        }

        // Description
        if (!tricountDetails.description.isNullOrBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Description", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(tricountDetails.description, fontSize = 14.sp)
                    }
                }
            }
        }

        // Members
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically) {
                        Text("Members (${members.size})", fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        if (isCreator) {
                            IconButton(onClick = { showAddMemberDialog = true }) {
                                Icon(Icons.Filled.PersonAdd, null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (members.isEmpty()) {
                        Text("No members yet.", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        members.forEachIndexed { idx, member ->
                            MemberRow(
                                member        = member,
                                isCreator     = isCreator,
                                canRemove     = isCreator && !member.isCreator,
                                onRemoveClick = {
                                    viewModel.removeMember(member.userId, tricountDetails.id)
                                }
                            )
                            if (idx < members.lastIndex) Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddMemberDialog) {
        AddMemberDialog(
            tricountId = tricountDetails.id,
            viewModel  = viewModel,
            onDismiss  = { showAddMemberDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Member row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MemberRow(
    member        : MemberWithDetails,
    isCreator     : Boolean,
    canRemove     : Boolean,
    onRemoveClick : () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Surface(modifier = Modifier.size(40.dp), shape = CircleShape,
                color = if (member.isCreator) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (member.isCreator) Icons.Filled.Star else Icons.Filled.Person,
                        null, modifier = Modifier.size(22.dp),
                        tint = if (member.isCreator) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(member.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(member.email, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (member.isCreator) {
            Surface(shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                Text("CREATOR",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color    = MaterialTheme.colorScheme.primary)
            }
        } else if (canRemove) {
            IconButton(onClick = { showRemoveDialog = true }) {
                Icon(Icons.Filled.RemoveCircle, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove Member?") },
            text  = { Text("Remove ${member.name} from this Tricount?") },
            confirmButton = {
                TextButton(
                    onClick = { onRemoveClick(); showRemoveDialog = false },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
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
    tricountId : Int,
    viewModel  : TricountViewModel,
    onDismiss  : () -> Unit
) {
    var email     by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isValidEmail = remember(email) {
        email.isBlank() ||
                "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\$".toRegex().matches(email)
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Add Member") },
        text  = {
            Column {
                Text("Enter the email of the person to add:", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value         = email,
                    onValueChange = { email = it },
                    label         = { Text("Email") },
                    placeholder   = { Text("user@example.com") },
                    leadingIcon   = { Icon(Icons.Filled.Email, null) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    isError       = !isValidEmail,
                    supportingText = {
                        if (!isValidEmail)
                            Text("Invalid email", color = MaterialTheme.colorScheme.error)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction    = ImeAction.Done),
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
                                    Toast.makeText(context,
                                        "${result.memberName} added!",
                                        Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                is AddMemberResult.Error ->
                                    Toast.makeText(context, result.message,
                                        Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = isValidEmail && email.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
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
// Shared empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    title    : String,
    subtitle : String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)) {
            Icon(icon, null, modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(20.dp))
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}