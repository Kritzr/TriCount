package com.example.tricount

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.data.entity.ExpenseWithDetails
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.TricountViewModel
import java.text.SimpleDateFormat
import java.util.*

// =============================================================================
// Activity
// =============================================================================

class ArchivedExpensesActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        val tricountId = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        if (tricountId != -1) viewModel.loadExpenses(tricountId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tricountId   = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        val tricountName = intent.getStringExtra(EXTRA_TRICOUNT_NAME) ?: "Tricount"
        val sessionManager = SessionManager(this)

        AppTheme.isDark.value = sessionManager.getDarkMode()

        if (tricountId == -1) { finish(); return }

        setContent {
            TriCountTheme() {
                LaunchedEffect(tricountId) {
                    viewModel.loadExpenses(tricountId)
                }

                val archivedExpenses by viewModel.archivedExpenses.collectAsStateWithLifecycle()

                ArchivedExpensesScreen(
                    tricountName        = tricountName,
                    archivedExpenses    = archivedExpenses,
                    onUnarchive         = { id -> viewModel.unarchiveExpense(id, tricountId) },
                    onDeletePermanently = { id -> viewModel.deleteExpense(id, tricountId) },
                    onBackClick         = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TRICOUNT_ID   = "archived_tricount_id"
        const val EXTRA_TRICOUNT_NAME = "archived_tricount_name"
    }
}

// =============================================================================
// Screen
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedExpensesScreen(
    tricountName        : String,
    archivedExpenses    : List<ExpenseWithDetails>,
    onUnarchive         : (Int) -> Unit,
    onDeletePermanently : (Int) -> Unit,
    onBackClick         : () -> Unit
) {
    val grouped: Map<String, List<ExpenseWithDetails>> = remember(archivedExpenses) {
        archivedExpenses
            .sortedByDescending { it.createdAt }
            .groupBy { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(it.createdAt)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Archived Expenses", fontWeight = FontWeight.Bold)
                        Text(
                            tricountName,
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->

        if (archivedExpenses.isEmpty()) {
            EmptyArchivedState(
                modifier     = Modifier.padding(padding),
                tricountName = tricountName
            )
        } else {
            LazyColumn(
                modifier       = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Summary row
                item(key = "summary") {
                    SummaryRow(expenses = archivedExpenses)
                    Spacer(Modifier.height(4.dp))
                }

                // Info banner
                item(key = "info") {
                    InfoBanner()
                    Spacer(Modifier.height(6.dp))
                }

                // Grouped sections
                grouped.forEach { (monthLabel, group) ->
                    item(key = "header_$monthLabel") {
                        MonthHeader(label = monthLabel, count = group.size)
                    }
                    items(group, key = { "exp_${it.id}" }) { expense ->
                        ArchivedExpenseDetailCard(
                            expense             = expense,
                            onUnarchiveClick    = { onUnarchive(expense.id) },
                            onDeleteClick       = { onDeletePermanently(expense.id) }
                        )
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

// =============================================================================
// Empty state
// =============================================================================

@Composable
private fun EmptyArchivedState(modifier: Modifier = Modifier, tricountName: String) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier            = Modifier.padding(40.dp)
        ) {
            Icon(
                Icons.Filled.Archive, null,
                modifier = Modifier.size(80.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
            Text(
                "No Archived Expenses",
                fontSize   = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Expenses you archive in \"$tricountName\" will appear here.\nYou can restore or delete them permanently at any time.",
                fontSize  = 14.sp,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =============================================================================
// Summary row — archived count + total amount chips
// =============================================================================

@Composable
private fun SummaryRow(expenses: List<ExpenseWithDetails>) {
    val total = expenses.sumOf { it.amount }
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatChip(
            modifier = Modifier.weight(1f),
            icon     = Icons.Filled.Archive,
            label    = "${expenses.size} archived",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
        )
        StatChip(
            modifier = Modifier.weight(1f),
            icon     = Icons.Filled.AttachMoney,
            label    = "$${"%.2f".format(total)} total",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor   = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun StatChip(
    modifier       : Modifier = Modifier,
    icon           : androidx.compose.ui.graphics.vector.ImageVector,
    label          : String,
    containerColor : androidx.compose.ui.graphics.Color,
    contentColor   : androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(50),
        color    = containerColor
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = contentColor)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
        }
    }
}

// =============================================================================
// Info banner
// =============================================================================

@Composable
private fun InfoBanner() {
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(10.dp),
        color          = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, null,
                tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Tap or long-press any expense to restore it or delete it permanently.",
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// =============================================================================
// Month section header
// =============================================================================

@Composable
private fun MonthHeader(label: String, count: Int) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
            Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color    = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}

// =============================================================================
// Archived expense card
// =============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchivedExpenseDetailCard(
    expense          : ExpenseWithDetails,
    onUnarchiveClick : () -> Unit,
    onDeleteClick    : () -> Unit
) {
    var showMenu          by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }

    val categoryEmoji = mapOf(
        "Food & Drinks" to "🍔", "Transport"    to "🚕", "Accommodation" to "🏨",
        "Entertainment" to "🎬", "Shopping"     to "🛍️", "Health"        to "💊",
        "Groceries"     to "🛒", "Utilities"    to "⚡", "Travel"        to "✈️",
        "Education"     to "📚", "General"      to "📌"
    )
    val emoji = categoryEmoji[expense.category] ?: "📌"

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border    = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { showMenu = true }, onLongClick = { showMenu = true })
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Left — emoji + info
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape    = RoundedCornerShape(10.dp),
                    color    = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 20.sp)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        expense.name,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (expense.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            expense.description,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, null,
                            modifier = Modifier.size(12.dp),
                            tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "Paid by ${expense.paidByName}",
                            fontSize = 11.sp,
                            color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("·", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(expense.createdAt)),
                            fontSize = 11.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Right — amount + icon
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$${"%.2f".format(expense.amount)}",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(4.dp))
                Icon(Icons.Filled.Archive, null,
                    modifier = Modifier.size(14.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
    }

    // Context menu
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            icon  = { Icon(Icons.Filled.Archive, null, tint = MaterialTheme.colorScheme.secondary) },
            title = { Text(expense.name, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text  = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { showMenu = false; showRestoreDialog = true }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Unarchive, null,
                            modifier = Modifier.size(22.dp),
                            tint     = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Restore", fontSize = 15.sp)
                            Text("Move back to active expenses",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { showMenu = false; showDeleteDialog = true }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.DeleteForever, null,
                            modifier = Modifier.size(22.dp),
                            tint     = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Delete Permanently",
                                fontSize = 15.sp,
                                color    = MaterialTheme.colorScheme.error)
                            Text("This cannot be undone",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Cancel") } }
        )
    }

    // Restore confirmation
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            icon  = { Icon(Icons.Filled.Unarchive, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Restore Expense?") },
            text  = { Text("\"${expense.name}\" will be moved back to the active expenses list.") },
            confirmButton = {
                Button(onClick = { onUnarchiveClick(); showRestoreDialog = false }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon  = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Permanently?") },
            text  = { Text("\"${expense.name}\" will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { onDeleteClick(); showDeleteDialog = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}