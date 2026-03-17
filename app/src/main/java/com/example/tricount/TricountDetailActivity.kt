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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    var selectedTab      by remember { mutableStateOf(0) }
    var expenseToEdit    by remember { mutableStateOf<ExpenseWithDetails?>(null) }
    var showMenu         by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var searchActive     by remember { mutableStateOf(false) }
    var searchQuery      by remember { mutableStateOf("") }

    // Filter expenses by search query when search is active
    val displayedExpenses = remember(expenses, searchQuery, searchActive) {
        if (searchActive && searchQuery.isNotBlank())
            expenses.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true) ||
                        it.paidByName.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true)
            }
        else expenses
    }

    // tabs: 0=Expenses, 1=Balances, 2=Details
    val tabs = listOf("Expenses", "Balances", "Details")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                // ── Top icon bar ─────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Row {
                        // Search toggle
                        IconButton(onClick = {
                            searchActive = !searchActive
                            if (!searchActive) searchQuery = ""
                        }) {
                            Icon(
                                if (searchActive) Icons.Filled.SearchOff else Icons.Filled.Search,
                                "Search",
                                tint = if (searchActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground
                            )
                        }
                        // Three-dot menu
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, "Options",
                                    tint = MaterialTheme.colorScheme.onBackground)
                            }
                            DropdownMenu(
                                expanded         = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                // Share
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                                    text    = { Text("Share") },
                                    onClick = {
                                        showMenu = false
                                        tricountDetails?.let {
                                            shareTricount(context, it.name, it.joinCode)
                                        }
                                    }
                                )
                                HorizontalDivider()
                                // Edit → EditTripActivity
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    text    = { Text("Edit") },
                                    onClick = {
                                        showMenu = false
                                        context.startActivity(
                                            Intent(context, EditTripActivity::class.java).apply {
                                                putExtra(EditTripActivity.EXTRA_TRICOUNT_ID, tricountId)
                                            }
                                        )
                                    }
                                )
                                HorizontalDivider()
                                // Insights
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(Icons.Filled.PieChart, null,
                                            tint = MaterialTheme.colorScheme.primary)
                                    },
                                    text    = {
                                        Text("Insights",
                                            color = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = {
                                        showMenu = false
                                        context.startActivity(
                                            Intent(context, InsightsActivity::class.java).apply {
                                                putExtra(InsightsActivity.EXTRA_TRICOUNT_ID,   tricountId)
                                                putExtra(InsightsActivity.EXTRA_TRICOUNT_NAME, tricountName)
                                            }
                                        )
                                    }
                                )
                                HorizontalDivider()
                                // Archived Expenses
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Inventory, null) },
                                    text    = { Text("Archived Expenses") },
                                    onClick = {
                                        showMenu = false
                                        context.startActivity(
                                            Intent(context, ArchivedExpensesActivity::class.java).apply {
                                                putExtra(ArchivedExpensesActivity.EXTRA_TRICOUNT_ID,   tricountId)
                                                putExtra(ArchivedExpensesActivity.EXTRA_TRICOUNT_NAME, tricountName)
                                            }
                                        )
                                    }
                                )
                                HorizontalDivider()
                                // Archive tricount
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Archive, null,
                                        tint = MaterialTheme.colorScheme.secondary) },
                                    text    = { Text("Archive Tricount",
                                        color = MaterialTheme.colorScheme.secondary) },
                                    onClick = { showMenu = false; showArchiveDialog = true }
                                )
                                HorizontalDivider()
                                // Delete tricount
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Delete, null,
                                        tint = MaterialTheme.colorScheme.error) },
                                    text    = { Text("Delete Tricount",
                                        color = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; showDeleteDialog = true }
                                )
                            }
                        }
                    }
                }

                // ── Search bar — slides in when search is active ──────────────
                if (searchActive) {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder   = { Text("Search expenses…") },
                        leadingIcon   = { Icon(Icons.Filled.Search, null) },
                        trailingIcon  = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, "Clear")
                                }
                            }
                        },
                        singleLine  = true,
                        modifier    = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape       = RoundedCornerShape(50),
                        colors      = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    // Show result count
                    if (searchQuery.isNotBlank()) {
                        Text(
                            "${displayedExpenses.size} result${if (displayedExpenses.size == 1) "" else "s"}",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        contentColor   = MaterialTheme.colorScheme.onPrimary,
                        modifier       = Modifier.size(60.dp)
                    ) {
                        Icon(Icons.Filled.Add, "Add Expense",
                            modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Add Expense", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── Tricount header: emoji + name ────────────────────────────────
            if (!searchActive) {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⛺", fontSize = 48.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tricountName,
                        fontSize   = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // ── Pill segmented tab row ───────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape    = RoundedCornerShape(50),
                color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    tabs.forEachIndexed { index, label ->
                        val selected = selectedTab == index
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = index },
                            shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.surface
                            else           androidx.compose.ui.graphics.Color.Transparent
                        ) {
                            Text(
                                label,
                                modifier   = Modifier.padding(vertical = 8.dp),
                                fontSize   = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color      = if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Tab content ──────────────────────────────────────────────────
            when (selectedTab) {
                0 -> ExpensesContent(
                    modifier                = Modifier.weight(1f),
                    expenses                = displayedExpenses,
                    archivedExpenses        = archivedExpenses,
                    currentUserId           = currentUserId,
                    onDeleteExpense         = { id -> viewModel.deleteExpense(id, tricountId) },
                    onArchiveExpense        = { id -> viewModel.archiveExpense(id, tricountId) },
                    onUnarchiveExpense      = { id -> viewModel.unarchiveExpense(id, tricountId) },
                    onDeleteArchivedExpense = { id -> viewModel.deleteExpense(id, tricountId) },
                    onEditExpense           = { expense -> expenseToEdit = expense },
                    showArchived            = false,
                    onToggleArchived        = {}
                )
                1 -> BalancesContent(
                    modifier      = Modifier.weight(1f),
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
                    modifier        = Modifier.weight(1f),
                    tricountDetails = tricountDetails,
                    members         = members,
                    currentUserId   = currentUserId,
                    viewModel       = viewModel,
                    tricountId      = tricountId
                )
            }
        }
    }

    // Edit expense dialog
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

    // Archive tricount confirmation
    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            icon  = { Icon(Icons.Filled.Archive, null,
                tint = MaterialTheme.colorScheme.secondary) },
            title = { Text("Archive \"$tricountName\"?") },
            text  = { Text("This tricount will be archived. You can restore it from the home screen.") },
            confirmButton = {
                Button(
                    onClick = {
                        showArchiveDialog = false
                        viewModel.archiveTricount(tricountId)
                        (context as? android.app.Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary)
                ) { Text("Archive") }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete tricount confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon  = { Icon(Icons.Filled.Delete, null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete \"$tricountName\"?") },
            text  = { Text("This will permanently delete the tricount and all its expenses. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteTricount(tricountId)
                        (context as? android.app.Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(
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
// Details / Edit tab
// =============================================================================

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

    if (tricountDetails == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isCreator = tricountDetails.creatorId == currentUserId

    // Editable state — pre-filled from current tricount
    var editName        by remember(tricountDetails.id) { mutableStateOf(tricountDetails.name) }
    var editDescription by remember(tricountDetails.id) { mutableStateOf(tricountDetails.description) }
    var selectedEmoji   by remember { mutableStateOf("⛺") }
    var isSaving        by remember { mutableStateOf(false) }
    var showAddMember   by remember { mutableStateOf(false) }

    val emojiOptions = listOf(
        "⛺","🏕️","✈️","🚗","🍕","🎉","🎬","🏖️",
        "🏔️","🛳️","🎭","🏋️","🎮","🛍️","🍜","☕"
    )

    val nameValid = editName.isNotBlank()
    val isDirty   = editName != tricountDetails.name ||
            editDescription != tricountDetails.description

    LazyColumn(
        modifier       = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Emoji picker ──────────────────────────────────────────────────────
        item {
            Text("Trip Icon", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                // Display selected emoji large, then grid of choices
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(selectedEmoji, fontSize = 52.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    // 4-column emoji grid
                    emojiOptions.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { emoji ->
                                val chosen = emoji == selectedEmoji
                                Surface(
                                    modifier  = Modifier
                                        .weight(1f)
                                        .clickable { selectedEmoji = emoji },
                                    shape     = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                    color     = if (chosen) MaterialTheme.colorScheme.primaryContainer
                                    else        MaterialTheme.colorScheme.surface,
                                    border    = if (chosen) androidx.compose.foundation.BorderStroke(
                                        2.dp, MaterialTheme.colorScheme.primary)
                                    else null
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier         = Modifier.padding(10.dp)
                                    ) {
                                        Text(emoji, fontSize = 24.sp)
                                    }
                                }
                            }
                            // Fill remaining cells if row is short
                            repeat(4 - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // ── Name field ────────────────────────────────────────────────────────
        item {
            Text("Trip Name", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
            OutlinedTextField(
                value         = editName,
                onValueChange = { editName = it },
                label         = { Text("Name *") },
                placeholder   = { Text("e.g. Goa trip, Team lunch") },
                leadingIcon   = { Text(selectedEmoji, fontSize = 18.sp,
                    modifier = Modifier.padding(start = 4.dp)) },
                singleLine    = true,
                isError       = editName.isEmpty(),
                modifier      = Modifier.fillMaxWidth(),
                enabled       = isCreator && !isSaving,
                shape         = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
        }

        // ── Description field ─────────────────────────────────────────────────
        item {
            Text("Description", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
            OutlinedTextField(
                value         = editDescription,
                onValueChange = { editDescription = it },
                label         = { Text("Description (optional)") },
                placeholder   = { Text("What is this trip about?") },
                modifier      = Modifier.fillMaxWidth(),
                minLines      = 3,
                maxLines      = 5,
                enabled       = isCreator && !isSaving,
                shape         = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
        }

        // ── Save button ───────────────────────────────────────────────────────
        if (isCreator) {
            item {
                Button(
                    onClick  = {
                        if (nameValid) {
                            isSaving = true
                            viewModel.editTricount(
                                tricountId  = tricountId,
                                name        = editName.trim(),
                                description = editDescription.trim()
                            )
                            isSaving = false
                            Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled  = nameValid && isDirty && !isSaving,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color       = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Changes", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Divider ───────────────────────────────────────────────────────────
        item { HorizontalDivider() }

        // ── Members section ───────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Members (${members.size})",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary)
                if (isCreator) {
                    FilledTonalButton(
                        onClick = { showAddMember = true },
                        shape   = androidx.compose.foundation.shape.RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Member", fontSize = 13.sp)
                    }
                }
            }
        }

        // Member rows
        items(members) { member ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                MemberItem(
                    member        = member,
                    isCreator     = isCreator,
                    canRemove     = isCreator && !member.isCreator,
                    onRemoveClick = {
                        viewModel.removeMember(member.userId, tricountDetails.id)
                    }
                )
            }
        }

        // ── Join code card ────────────────────────────────────────────────────
        item {
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color    = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Join Code", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(tricountDetails.joinCode,
                            fontSize     = 28.sp,
                            fontWeight   = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            color        = MaterialTheme.colorScheme.onPrimaryContainer)
                        Row {
                            IconButton(onClick = {
                                copyToClipboard(context, tricountDetails.joinCode)
                                Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, "Copy",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            IconButton(onClick = {
                                shareTricount(context, tricountDetails.name, tricountDetails.joinCode)
                            }) {
                                Icon(Icons.Filled.Share, "Share",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }

    if (showAddMember) {
        AddMemberDialog(
            tricountId = tricountId,
            viewModel  = viewModel,
            onDismiss  = { showAddMember = false }
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