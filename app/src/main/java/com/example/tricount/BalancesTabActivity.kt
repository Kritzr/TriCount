package com.example.tricount

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.data.entity.ExpenseSplitWithUser
import com.example.tricount.data.entity.ExpenseWithDetails
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.Settlement
import com.example.tricount.viewModel.TricountViewModel

class BalancesTabActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tricountId   = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        val tricountName = intent.getStringExtra(EXTRA_TRICOUNT_NAME) ?: "Balances"
        val sessionManager = SessionManager(this)

        if (tricountId == -1) { finish(); return }

        setContent {
            TriCountTheme(darkTheme = false) {
                LaunchedEffect(tricountId) {
                    viewModel.loadTricountDetails(tricountId)
                    viewModel.loadExpenses(tricountId)
                }

                val expenses      by viewModel.expenses.collectAsStateWithLifecycle()
                val expenseSplits by viewModel.expenseSplits.collectAsStateWithLifecycle()
                val settlements   by viewModel.settlements.collectAsStateWithLifecycle()
                val members       by viewModel.tricountMembers.collectAsStateWithLifecycle()
                val currentUserId = sessionManager.getUserId() ?: -1

                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = {
                                Column {
                                    Text(tricountName, fontWeight = FontWeight.Bold)
                                    Text("Balances", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    BalancesContent(
                        modifier      = Modifier.padding(padding),
                        expenses      = expenses,
                        expenseSplits = expenseSplits,
                        settlements   = settlements,
                        currentUserId = currentUserId,
                        memberCount   = members.size,
                        expenseCount  = expenses.size
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_TRICOUNT_ID   = "extra_tricount_id"
        const val EXTRA_TRICOUNT_NAME = "extra_tricount_name"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI — all composables live here, no separate BalancesTab.kt needed
// ─────────────────────────────────────────────────────────────────────────────

private data class BalanceRow(val userId: Int, val name: String, val net: Double)

@Composable
private fun BalancesContent(
    modifier      : Modifier = Modifier,
    expenses      : List<ExpenseWithDetails>,
    expenseSplits : Map<Int, List<ExpenseSplitWithUser>>,
    settlements   : List<Settlement>,
    currentUserId : Int,
    memberCount   : Int,
    expenseCount  : Int
) {
    if (expenses.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(Icons.Filled.AccountBalance, contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(16.dp))
                Text("No expenses yet", fontSize = 20.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Add expenses to see balances here", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
        return
    }

    val balanceRows = remember(expenses, expenseSplits) {
        val netMap  = mutableMapOf<Int, Double>()
        val nameMap = mutableMapOf<Int, String>()
        for (expense in expenses) {
            netMap[expense.paidBy]  = (netMap[expense.paidBy]  ?: 0.0) + expense.amount
            nameMap[expense.paidBy] = expense.paidByName
            val splits = expenseSplits[expense.id] ?: continue
            for (split in splits) {
                netMap[split.userId]  = (netMap[split.userId]  ?: 0.0) - split.amount
                nameMap[split.userId] = split.userName
            }
        }
        netMap.map { (id, net) -> BalanceRow(id, nameMap[id] ?: "?", net) }
            .sortedByDescending { it.net }
    }

    val totalSpent   = expenses.sumOf { it.amount }
    val myTotalPaid  = expenses.filter { it.paidBy == currentUserId }.sumOf { it.amount }
    val myNetBalance = balanceRows.find { it.userId == currentUserId }?.net ?: 0.0
    val green = Color(0xFF2E7D32)
    val red   = Color(0xFFC62828)

    LazyColumn(
        modifier       = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Overview card
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Overview", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem("Total Spent", "$${String.format("%.2f", totalSpent)}")
                        StatItem("Members",     "$memberCount")
                        StatItem("Expenses",    "$expenseCount")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("I paid", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text("$${String.format("%.2f", myTotalPaid)}", fontSize = 20.sp,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("My balance", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(
                                when {
                                    myNetBalance >  0.01 -> "+$${String.format("%.2f",  myNetBalance)}"
                                    myNetBalance < -0.01 -> "-$${String.format("%.2f", -myNetBalance)}"
                                    else                  -> "$0.00"
                                },
                                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = when {
                                    myNetBalance >  0.01 -> green
                                    myNetBalance < -0.01 -> red
                                    else                  -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }
                }
            }
        }

        // Individual Balances
        item {
            Text("Individual Balances", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    balanceRows.forEachIndexed { index, row ->
                        val isMe = row.userId == currentUserId
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Surface(modifier = Modifier.size(40.dp), shape = CircleShape,
                                color = if (isMe) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondaryContainer) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(row.name.first().uppercase(),
                                        fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                        color = if (isMe) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (isMe) "You (${row.name})" else row.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal)
                                Text(when {
                                    row.net >  0.01 -> "gets back"
                                    row.net < -0.01 -> "owes"
                                    else             -> "settled up ✓"
                                }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(when {
                                row.net >  0.01 -> "+$${String.format("%.2f",  row.net)}"
                                row.net < -0.01 -> "-$${String.format("%.2f", -row.net)}"
                                else             -> "$0.00"
                            }, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = when {
                                    row.net >  0.01 -> green
                                    row.net < -0.01 -> red
                                    else             -> MaterialTheme.colorScheme.onSurfaceVariant
                                })
                        }
                        if (index < balanceRows.lastIndex)
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                }
            }
        }

        // Settle Up
        item {
            Text("Settle Up", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
        }
        item {
            if (settlements.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = green.copy(alpha = 0.08f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.CheckCircle, null, tint = green,
                            modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("All settled up! 🎉", fontSize = 16.sp,
                            fontWeight = FontWeight.Medium, color = green)
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth(),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        settlements.forEachIndexed { index, s ->
                            val isDebtor   = s.fromUserId == currentUserId
                            val isCreditor = s.toUserId   == currentUserId
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Surface(modifier = Modifier.size(34.dp), shape = CircleShape,
                                    color = if (isDebtor) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.secondaryContainer) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text((if (isDebtor) "You" else s.fromUserName).first().uppercase(),
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            color = if (isDebtor) MaterialTheme.colorScheme.onErrorContainer
                                            else MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(if (isDebtor) "You" else s.fromUserName,
                                    fontSize = 14.sp, modifier = Modifier.weight(1f),
                                    fontWeight = if (isDebtor) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isDebtor) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onTertiaryContainer)
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Icon(Icons.Filled.ArrowForward, null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.tertiary)
                                    Text("$${String.format("%.2f", s.amount)}",
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary)
                                }
                                Text(if (isCreditor) "You" else s.toUserName,
                                    fontSize = 14.sp, modifier = Modifier.weight(1f),
                                    fontWeight = if (isCreditor) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.End,
                                    color = if (isCreditor) green
                                    else MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(Modifier.width(8.dp))
                                Surface(modifier = Modifier.size(34.dp), shape = CircleShape,
                                    color = if (isCreditor) green.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.secondaryContainer) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text((if (isCreditor) "You" else s.toUserName).first().uppercase(),
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            color = if (isCreditor) green
                                            else MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                            }
                            if (index < settlements.lastIndex)
                                HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}