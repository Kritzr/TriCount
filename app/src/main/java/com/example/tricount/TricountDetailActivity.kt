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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.data.entity.ExpenseSplitWithUser
import com.example.tricount.data.entity.ExpenseWithDetails
import com.example.tricount.data.entity.MemberWithDetails
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.viewModel.AddMemberResult
import com.example.tricount.viewModel.Settlement
import com.example.tricount.data.entity.PaymentEntity
import com.example.tricount.viewModel.TricountViewModel

class TricountDetailActivity : ComponentActivity() {

    private val tricountViewModel: TricountViewModel by viewModels()

    private var tricountId = -1

    override fun onResume() {
        super.onResume()
        if (tricountId != -1) {
            tricountViewModel.loadTricountDetails(tricountId)
            tricountViewModel.loadExpenses(tricountId)
            tricountViewModel.loadPayments(tricountId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tricountId = intent.getIntExtra("TRICOUNT_ID", -1)
        val tricountName = intent.getStringExtra("TRICOUNT_NAME") ?: "Tricount"
        val sessionManager = SessionManager(this)

        AppTheme.isDark.value = sessionManager.getDarkMode()

        setContent {
            TriCountTheme() {
                LaunchedEffect(tricountId) {
                    tricountViewModel.loadTricountDetails(tricountId)
                    tricountViewModel.loadExpenses(tricountId)
                }

                val tricountDetails  by tricountViewModel.currentTricount.collectAsStateWithLifecycle()
                val members          by tricountViewModel.tricountMembers.collectAsStateWithLifecycle()
                val expenses         by tricountViewModel.expenses.collectAsStateWithLifecycle()
                val archivedExpenses by tricountViewModel.archivedExpenses.collectAsStateWithLifecycle()
                val expenseSplits    by tricountViewModel.expenseSplits.collectAsStateWithLifecycle()
                val settlements      by tricountViewModel.settlements.collectAsStateWithLifecycle()
                val payments         by tricountViewModel.payments.collectAsStateWithLifecycle()
                val currentUserId    = sessionManager.getUserId() ?: -1

                TricountDetailScreen(
                    tricountId       = tricountId,
                    tricountName     = tricountName,
                    tricountDetails  = tricountDetails,
                    members          = members,
                    expenses         = expenses,
                    archivedExpenses = archivedExpenses,
                    expenseSplits    = expenseSplits,
                    settlements      = settlements,
                    payments         = payments,
                    currentUserId    = currentUserId,
                    viewModel        = tricountViewModel,
                    onBackClick      = { finish() }
                )
            }
        }
    }
}

private data class TabItem(val icon: ImageVector, val title: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TricountDetailScreen(
    tricountId       : Int,
    tricountName     : String,
    tricountDetails  : TricountEntity?,
    members          : List<MemberWithDetails>,
    expenses         : List<ExpenseWithDetails>,
    archivedExpenses : List<ExpenseWithDetails>,
    expenseSplits    : Map<Int, List<ExpenseSplitWithUser>>,
    settlements      : List<Settlement>,
    payments         : List<com.example.tricount.data.entity.PaymentEntity>,
    currentUserId    : Int,
    viewModel        : TricountViewModel,
    onBackClick      : () -> Unit
) {
    val context = LocalContext.current
    var selectedTab   by remember { mutableStateOf(0) }
    var expenseToEdit by remember { mutableStateOf<ExpenseWithDetails?>(null) }
    var showArchived  by remember { mutableStateOf(false) }

    val tabs = listOf(
        TabItem(Icons.Filled.Receipt,        "Expenses"),
        TabItem(Icons.Filled.AccountBalance, "Summary"),
        TabItem(Icons.Filled.Info,           "Details")
    )

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
                            tricountDetails?.let { shareTricount(context, it.name, it.joinCode) }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick  = { selectedTab = index },
                            text     = { Text(tab.title, fontSize = 12.sp) },
                            icon     = {
                                Icon(tab.icon, contentDescription = tab.title,
                                    modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // View Archived — small FAB
                    if (archivedExpenses.isNotEmpty()) {
                        SmallFloatingActionButton(
                            onClick = {
                                context.startActivity(
                                    Intent(context, ArchivedExpensesActivity::class.java).apply {
                                        putExtra(ArchivedExpensesActivity.EXTRA_TRICOUNT_ID,   tricountId)
                                        putExtra(ArchivedExpensesActivity.EXTRA_TRICOUNT_NAME, tricountName)
                                    }
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            BadgedBox(badge = {
                                Badge { Text("${archivedExpenses.size}") }
                            }) {
                                Icon(Icons.Filled.Archive, contentDescription = "View Archived")
                            }
                        }
                    }
                    // Add Expense — main FAB
                    FloatingActionButton(
                        onClick = {
                            context.startActivity(
                                Intent(context, AddExpenseActivity::class.java).apply {
                                    putExtra("extra_tricount_id",   tricountId)
                                    putExtra("extra_tricount_name", tricountName)
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
        }
    ) { padding ->
        when (selectedTab) {
            0 -> ExpensesContent(
                modifier                = Modifier.padding(padding),
                expenses                = expenses,
                archivedExpenses        = archivedExpenses,
                currentUserId           = currentUserId,
                onDeleteExpense         = { expenseId -> viewModel.deleteExpense(expenseId, tricountId) },
                onArchiveExpense        = { expenseId -> viewModel.archiveExpense(expenseId, tricountId) },
                onUnarchiveExpense      = { expenseId -> viewModel.unarchiveExpense(expenseId, tricountId) },
                onDeleteArchivedExpense = { expenseId -> viewModel.deleteExpense(expenseId, tricountId) },
                onEditExpense           = { expense   -> expenseToEdit = expense },
                showArchived            = false,
                onToggleArchived        = {}
            )
            1 -> BalancesContent(
                modifier      = Modifier.padding(padding),
                expenses      = expenses,
                expenseSplits = expenseSplits,
                settlements   = settlements,
                payments      = payments,
                currentUserId = currentUserId,
                memberCount   = members.size,
                expenseCount  = expenses.size,
                tricountId    = tricountId,
                viewModel     = viewModel
            )
            2 -> DetailsTab(
                modifier        = Modifier.padding(padding),
                tricountDetails = tricountDetails,
                members         = members,
                currentUserId   = currentUserId,
                viewModel       = viewModel,
                tricountId      = tricountId
            )
        }
    }
    // Expense edit dialog (rendered outside Scaffold so it overlays correctly)
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
// Details tab
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsTab(
    modifier        : Modifier = Modifier,
    tricountDetails : TricountEntity?,
    members         : List<MemberWithDetails>,
    currentUserId   : Int,
    tricountId      : Int,
    viewModel       : TricountViewModel
) {
    val context = LocalContext.current
    var showAddMemberDialog by remember { mutableStateOf(false) }

    if (tricountDetails == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isCreator = tricountDetails.creatorId == currentUserId

    LazyColumn(
        modifier       = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Join Code", fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tricountDetails.joinCode,
                                fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = {
                            copyToClipboard(context, tricountDetails.joinCode)
                            Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick  = { shareTricount(context, tricountDetails.name, tricountDetails.joinCode) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share Tricount")
                    }
                }
            }
        }

        if (tricountDetails.description.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Description", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(tricountDetails.description, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Members (${members.size})", fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        if (isCreator) {
                            IconButton(onClick = { showAddMemberDialog = true }) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = "Add Member",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (members.isEmpty()) {
                        Text("No members yet", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        members.forEach { member ->
                            MemberItem(
                                member        = member,
                                isCreator     = isCreator,
                                canRemove     = isCreator && !member.isCreator,
                                onRemoveClick = {
                                    viewModel.removeMember(member.userId, tricountDetails.id)
                                }
                            )
                            if (member != members.last()) Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddMemberDialog) {
        AddMemberDialog(
            tricountId = tricountId,
            viewModel  = viewModel,
            onDismiss  = { showAddMemberDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Member row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MemberItem(
    member        : MemberWithDetails,
    isCreator     : Boolean,
    canRemove     : Boolean,
    onRemoveClick : () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Surface(
                modifier = Modifier.size(40.dp), shape = CircleShape,
                color = if (member.isCreator) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (member.isCreator) Icons.Filled.Star else Icons.Filled.Person,
                        contentDescription = null, modifier = Modifier.size(24.dp),
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
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ) {
                Text("CREATOR",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
        } else if (canRemove) {
            IconButton(onClick = { showRemoveDialog = true }) {
                Icon(Icons.Filled.RemoveCircle, contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title   = { Text("Remove Member?") },
            text    = { Text("Remove ${member.name} from this Tricount?") },
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
                "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex().matches(email)
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Add Member") },
        text = {
            Column {
                Text("Enter the email of the person you want to add:",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value           = email,
                    onValueChange   = { email = it },
                    label           = { Text("Email") },
                    placeholder     = { Text("user@example.com") },
                    leadingIcon     = { Icon(Icons.Filled.Email, null) },
                    modifier        = Modifier.fillMaxWidth(),
                    singleLine      = true,
                    isError         = !isValidEmail,
                    supportingText  = {
                        if (!isValidEmail)
                            Text("Invalid email", color = MaterialTheme.colorScheme.error)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction    = ImeAction.Done
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
                                    Toast.makeText(context,
                                        "${result.memberName} added!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                is AddMemberResult.Error ->
                                    Toast.makeText(context,
                                        result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = isValidEmail && email.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText("Tricount Code", text))
}

private fun shareTricount(context: Context, name: String, joinCode: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Join $name on TriCount")
        putExtra(Intent.EXTRA_TEXT, "Join my Tricount: $name\n\nCode: $joinCode")
    }
    context.startActivity(Intent.createChooser(intent, "Share Tricount"))
}