package com.example.tricount

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.tricount.data.entity.ExpenseSplitWithUser
import com.example.tricount.data.entity.ExpenseWithDetails
import com.example.tricount.data.entity.MemberWithDetails
import com.example.tricount.viewModel.Settlement
import com.example.tricount.viewModel.TricountViewModel
import com.example.tricount.viewModel.AddExpenseResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Main Expenses Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExpensesTabContent(
    tricountId: Int,
    currentUserId: Int,
    viewModel: TricountViewModel,
    modifier: Modifier = Modifier
) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val expenseSplits by viewModel.expenseSplits.collectAsStateWithLifecycle()
    val settlements by viewModel.settlements.collectAsStateWithLifecycle()
    val members by viewModel.tricountMembers.collectAsStateWithLifecycle()

    var showAddExpense by remember { mutableStateOf(false) }

    LaunchedEffect(tricountId) {
        viewModel.loadExpenses(tricountId)
        viewModel.loadTricountMembers(tricountId)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (expenses.isEmpty()) {
            EmptyExpensesState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total summary card
                item {
                    TotalSummaryCard(
                        totalAmount = expenses.sumOf { it.amount },
                        expenseCount = expenses.size
                    )
                }

                // Settlement card (who pays whom)
                if (settlements.isNotEmpty()) {
                    item {
                        SettlementCard(
                            settlements = settlements,
                            currentUserId = currentUserId
                        )
                    }
                }

                // Each expense with its split breakdown
                items(expenses, key = { it.id }) { expense ->
                    val splits = expenseSplits[expense.id] ?: emptyList()
                    ExpenseCard(
                        expense = expense,
                        splits = splits,
                        currentUserId = currentUserId,
                        onDeleteClick = {
                            viewModel.deleteExpense(expense.id, tricountId)
                        }
                    )
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showAddExpense = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Expense")
        }
    }

    if (showAddExpense && members.isNotEmpty()) {
        AddExpenseDialog(
            members = members,
            currentUserId = currentUserId,
            onDismiss = { showAddExpense = false },
            onConfirm = { name, description, amount, paidBy, sharesMap ->
                viewModel.addExpense(
                    tricountId = tricountId,
                    name = name,
                    description = description,
                    amount = amount,
                    paidBy = paidBy,
                    sharesMap = sharesMap
                ) { result ->
                    if (result is AddExpenseResult.Success) showAddExpense = false
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Total Summary Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TotalSummaryCard(totalAmount: Double, expenseCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Total Expenses",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    "$${"%.2f".format(totalAmount)}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "$expenseCount expense${if (expenseCount != 1) "s" else ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settlement Card — who owes whom to settle up
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettlementCard(
    settlements: List<Settlement>,
    currentUserId: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Settle Up",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            settlements.forEach { s ->
                val isCurrentUserDebtor = s.fromUserId == currentUserId
                val isCurrentUserCreditor = s.toUserId == currentUserId

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // From
                    Text(
                        text = if (isCurrentUserDebtor) "You" else s.fromUserName,
                        fontSize = 14.sp,
                        fontWeight = if (isCurrentUserDebtor) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrentUserDebtor)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
                    )

                    // Amount
                    Text(
                        "$${"%.2f".format(s.amount)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .width(72.dp),
                        textAlign = TextAlign.Center
                    )

                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
                    )

                    // To
                    Text(
                        text = if (isCurrentUserCreditor) "You" else s.toUserName,
                        fontSize = 14.sp,
                        fontWeight = if (isCurrentUserCreditor) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrentUserCreditor)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }

                if (settlements.last() != s) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual Expense Card with expandable split breakdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpenseCard(
    expense: ExpenseWithDetails,
    splits: List<ExpenseSplitWithUser>,
    currentUserId: Int,
    onDeleteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
    val canDelete = expense.paidBy == currentUserId

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (splits.isNotEmpty()) expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (expense.paidBy == currentUserId)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        expense.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Paid by ${if (expense.paidBy == currentUserId) "You" else expense.paidByName}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        dateFormat.format(Date(expense.createdAt)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$${"%.2f".format(expense.amount)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (splits.isNotEmpty()) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "Toggle split",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    if (canDelete) {
                        Spacer(modifier = Modifier.height(4.dp))
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ── Expandable split section ──
            AnimatedVisibility(
                visible = expanded && splits.isNotEmpty(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Split header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Split Breakdown",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        val totalShares = splits.sumOf { it.shares }
                        Text(
                            "$totalShares total shares",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val totalShares = splits.sumOf { it.shares }.coerceAtLeast(1)

                    splits.forEach { split ->
                        val isCurrentUser = split.userId == currentUserId
                        val isPayer = split.userId == expense.paidBy
                        val fraction = split.shares.toFloat() / totalShares

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar initial
                            Surface(
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                color = if (isCurrentUser)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        split.userName.first().uppercase(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentUser)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            // Name + shares bar
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (isCurrentUser) "You" else split.userName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isPayer) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                "PAID",
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        "${split.shares} share${if (split.shares != 1) "s" else ""}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                // Share proportion bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                if (isCurrentUser) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.secondary
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Amount
                            Text(
                                "$${"%.2f".format(split.amount)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrentUser)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // My share callout
                    val myShare = splits.find { it.userId == currentUserId }
                    if (myShare != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val isPayer = expense.paidBy == currentUserId
                        val netAmount = if (isPayer) {
                            expense.amount - myShare.amount  // others owe you
                        } else {
                            -myShare.amount  // you owe payer
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (netAmount >= 0)
                                Color(0xFF2E7D32).copy(alpha = 0.1f)
                            else
                                Color(0xFFC62828).copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (netAmount >= 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (netAmount >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (netAmount >= 0)
                                        "Others owe you $${"%.2f".format(netAmount)} for this"
                                    else
                                        "You owe $${"%.2f".format(-netAmount)} for this",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (netAmount >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Expense Dialog — with share ratio inputs
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    members: List<MemberWithDetails>,
    currentUserId: Int,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        description: String,
        amount: Double,
        paidBy: Int,
        sharesMap: Map<Int, Int>
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedPayer by remember { mutableStateOf(members.find { it.userId == currentUserId } ?: members.first()) }
    var showPayerDropdown by remember { mutableStateOf(false) }

    // Share inputs per member — default 1 share each
    val sharesInput = remember {
        mutableStateMapOf<Int, String>().also { map ->
            members.forEach { map[it.userId] = "1" }
        }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Title
                Text(
                    "Add Expense",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Expense name *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Total amount *") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            leadingIcon = { Text("$", fontWeight = FontWeight.Bold) },
                            singleLine = true
                        )
                    }

                    // Payer dropdown
                    item {
                        Column {
                            Text(
                                "Paid by",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ExposedDropdownMenuBox(
                                expanded = showPayerDropdown,
                                onExpandedChange = { showPayerDropdown = it }
                            ) {
                                OutlinedTextField(
                                    value = if (selectedPayer.userId == currentUserId) "You (${selectedPayer.name})" else selectedPayer.name,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPayerDropdown)
                                    }
                                )
                                ExposedDropdownMenu(
                                    expanded = showPayerDropdown,
                                    onDismissRequest = { showPayerDropdown = false }
                                ) {
                                    members.forEach { member ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (member.userId == currentUserId) "You (${member.name})"
                                                    else member.name
                                                )
                                            },
                                            onClick = {
                                                selectedPayer = member
                                                showPayerDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Share ratios section
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Share Ratios",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                // Live preview of total shares
                                val totalShares = sharesInput.values.mapNotNull { it.toIntOrNull() }.sum()
                                Text(
                                    "Total: $totalShares parts",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            Text(
                                "Enter how many parts each person owes. E.g. 1 and 2 means one person pays ⅓, the other pays ⅔.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // One row per member
                    items(members) { member ->
                        val isCurrentUser = member.userId == currentUserId
                        val totalShares = sharesInput.values.mapNotNull { it.toIntOrNull() }.sum().coerceAtLeast(1)
                        val memberShares = sharesInput[member.userId]?.toIntOrNull() ?: 0
                        val previewAmount = amountText.toDoubleOrNull()?.let {
                            (memberShares.toDouble() / totalShares) * it
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
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
                                        color = if (isCurrentUser)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))

                            // Name
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isCurrentUser) "You" else member.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal
                                )
                                if (previewAmount != null && memberShares > 0) {
                                    Text(
                                        "≈ $${"%.2f".format(previewAmount)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Shares input
                            OutlinedTextField(
                                value = sharesInput[member.userId] ?: "1",
                                onValueChange = { value ->
                                    sharesInput[member.userId] = value.filter { it.isDigit() }
                                },
                                modifier = Modifier.width(72.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                suffix = { Text("pt", fontSize = 11.sp) }
                            )
                        }
                    }

                    // Error message
                    if (errorMessage != null) {
                        item {
                            Text(
                                errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull()
                            when {
                                name.isBlank() -> errorMessage = "Name is required"
                                amount == null || amount <= 0 -> errorMessage = "Enter a valid amount"
                                sharesInput.values.all { (it.toIntOrNull() ?: 0) == 0 } ->
                                    errorMessage = "At least one share must be > 0"
                                else -> {
                                    errorMessage = null
                                    val sharesMap = sharesInput
                                        .mapValues { it.value.toIntOrNull() ?: 0 }
                                        .filter { it.value > 0 }
                                    onConfirm(name, description, amount, selectedPayer.userId, sharesMap)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyExpensesState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Receipt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No expenses yet",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Tap + to add the first expense",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}