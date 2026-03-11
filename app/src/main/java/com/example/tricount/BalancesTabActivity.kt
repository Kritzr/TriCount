package com.example.tricount

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tricount.data.entity.ExpenseSplitWithUser
import com.example.tricount.data.entity.ExpenseWithDetails
import com.example.tricount.data.entity.PaymentEntity
import com.example.tricount.viewModel.Settlement
import com.example.tricount.viewModel.TricountViewModel

private val Green  = Color(0xFF2E7D32)
private val Red    = Color(0xFFC62828)

// ─────────────────────────────────────────────────────────────────────────────
// Public entry point — called from TricountDetailActivity tab 1
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BalancesContent(
    modifier      : Modifier                            = Modifier,
    expenses      : List<ExpenseWithDetails>,
    expenseSplits : Map<Int, List<ExpenseSplitWithUser>>,
    settlements   : List<Settlement>,
    payments      : List<PaymentEntity>                 = emptyList(),
    currentUserId : Int,
    memberCount   : Int,
    expenseCount  : Int,
    tricountId    : Int,
    viewModel     : TricountViewModel
) {
    val context = LocalContext.current

    // Dialog state
    var settlementToPay    by remember { mutableStateOf<Settlement?>(null) }
    var reminderTarget     by remember { mutableStateOf<Settlement?>(null) }
    var busyKey            by remember { mutableStateOf<String?>(null) }  // "fromId-toId"
    var showPaymentHistory by remember { mutableStateOf(false) }

    // ── Empty state ───────────────────────────────────────────────────────────
    if (expenses.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.AccountBalance, null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(16.dp))
                Text("No expenses yet",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Add expenses in the Expenses tab to see balances here",
                    fontSize  = 14.sp,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier  = Modifier.padding(horizontal = 32.dp))
            }
        }
        return
    }

    // ── Balance computation ───────────────────────────────────────────────────
    // Mirror exactly the ViewModel's recomputeSettlements logic so UI stays in sync.
    // Key off size+sum so Compose recomputes whenever expenses or splits change.
    val expensesKey = expenses.size.toString() + expenses.sumOf { it.amount }.toString()
    val splitsKey   = expenseSplits.values.sumOf { it.size }.toString()
    val paymentsKey = payments.size.toString()

    val netMap  = remember(expensesKey, splitsKey, paymentsKey) {
        val net     = mutableMapOf<Int, Double>()
        val nameMap = mutableMapOf<Int, String>()

        for (expense in expenses) {
            net[expense.paidBy]     = (net[expense.paidBy] ?: 0.0) + expense.amount
            nameMap[expense.paidBy] = expense.paidByName

            val splits = expenseSplits[expense.id]
            if (!splits.isNullOrEmpty()) {
                for (split in splits) {
                    net[split.userId]     = (net[split.userId] ?: 0.0) - split.amount
                    nameMap[split.userId] = split.userName
                }
            } else {
                // No splits — payer covered themselves, net = 0
                net[expense.paidBy] = (net[expense.paidBy] ?: 0.0) - expense.amount
            }
        }

        // Subtract already-paid settlements
        for (payment in payments) {
            net[payment.fromUserId]     = (net[payment.fromUserId] ?: 0.0) + payment.amount
            net[payment.toUserId]       = (net[payment.toUserId]   ?: 0.0) - payment.amount
            nameMap[payment.fromUserId] = payment.fromUserName
            nameMap[payment.toUserId]   = payment.toUserName
        }

        Pair(net, nameMap)
    }

    val balanceRows = remember(netMap) {
        netMap.first
            .map { (id, v) -> Triple(id, netMap.second[id] ?: "?", v) }
            .sortedByDescending { it.third }
    }

    // Plain derived values — recalculate on every recomposition
    val totalSpent   = expenses.sumOf { it.amount }
    val myTotalPaid  = expenses.filter { it.paidBy == currentUserId }.sumOf { it.amount }
    val myNetBalance = netMap.first[currentUserId] ?: 0.0

    // ── Main list ─────────────────────────────────────────────────────────────
    LazyColumn(
        modifier        = modifier.fillMaxSize(),
        contentPadding  = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── Overview ──────────────────────────────────────────────────────────
        item {
            Card(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Overview",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.primary)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatChip("Total",    "$${String.format("%.2f", totalSpent)}")
                        StatChip("Members",  "$memberCount")
                        StatChip("Expenses", "$expenseCount")
                        StatChip("Payments", "${payments.size}")
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically) {
                        Column {
                            Text("I paid", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text("$${String.format("%.2f", myTotalPaid)}",
                                fontSize   = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("My balance", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(
                                when {
                                    myNetBalance >  0.01 -> "+${"$"}${"%.2f".format(myNetBalance)}"
                                    myNetBalance < -0.01 -> "-${"$"}${"%.2f".format(-myNetBalance)}"
                                    else                 -> "$0.00 ✓"
                                },
                                fontSize   = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color      = when {
                                    myNetBalance >  0.01 -> Green
                                    myNetBalance < -0.01 -> Red
                                    else                 -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Individual balances ───────────────────────────────────────────────
        item {
            Text("Individual Balances",
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary)
        }

        item {
            Card(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    balanceRows.forEachIndexed { idx, (userId, name, net) ->
                        val isMe = userId == currentUserId
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape    = CircleShape,
                                color    = if (isMe) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(name.first().uppercase(),
                                        fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                        color = if (isMe) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (isMe) "You ($name)" else name,
                                    fontSize   = 14.sp,
                                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal)
                                Text(
                                    when {
                                        net >  0.01 -> "gets back"
                                        net < -0.01 -> "owes"
                                        else        -> "settled up ✓"
                                    },
                                    fontSize = 12.sp,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                when {
                                    net >  0.01 -> "+${"$"}${"%.2f".format(net)}"
                                    net < -0.01 -> "-${"$"}${"%.2f".format(-net)}"
                                    else        -> "$0.00"
                                },
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color      = when {
                                    net >  0.01 -> Green
                                    net < -0.01 -> Red
                                    else        -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (idx < balanceRows.lastIndex)
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                    }
                }
            }
        }

        // ── Settle Up ─────────────────────────────────────────────────────────
        item {
            Text("Settle Up",
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary)
        }

        // One card per outstanding settlement (nothing shown when all settled)
        items(settlements, key = { "${it.fromUserId}-${it.toUserId}" }) { s ->
            val isDebtor   = s.fromUserId == currentUserId
            val isCreditor = s.toUserId   == currentUserId
            val key        = "${s.fromUserId}-${s.toUserId}"
            val isBusy     = busyKey == key

            SettlementCard(
                settlement  = s,
                isDebtor    = isDebtor,
                isCreditor  = isCreditor,
                isBusy      = isBusy,
                onMarkPaid  = { settlementToPay = s },
                onRemind    = { reminderTarget  = s }
            )
        }

        // ── Payment history ───────────────────────────────────────────────────
        if (payments.isNotEmpty()) {
            item {
                TextButton(
                    onClick  = { showPaymentHistory = !showPaymentHistory },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (showPaymentHistory) Icons.Filled.ExpandLess
                        else Icons.Filled.ExpandMore,
                        null, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (showPaymentHistory) "Hide payment history (${payments.size})"
                        else "Show payment history (${payments.size})",
                        fontSize = 13.sp
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = showPaymentHistory,
                    enter   = expandVertically(),
                    exit    = shrinkVertically()
                ) {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        colors    = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Payment History",
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.primary)
                            payments.forEach { p ->
                                val isMyPayment = p.fromUserId == currentUserId
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null,
                                        tint     = Green,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (isMyPayment) "You → ${p.toUserName}"
                                            else "${p.fromUserName} → ${p.toUserName}",
                                            fontSize   = 13.sp,
                                            fontWeight = if (isMyPayment) FontWeight.Bold
                                            else FontWeight.Normal
                                        )
                                        Text(
                                            java.text.SimpleDateFormat(
                                                "dd MMM yyyy, HH:mm",
                                                java.util.Locale.getDefault()
                                            ).format(java.util.Date(p.paidAt)),
                                            fontSize = 11.sp,
                                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text("${"$"}${"%.2f".format(p.amount)}",
                                        fontSize   = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = Green)
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    // ── Mark as Paid dialog ───────────────────────────────────────────────────
    settlementToPay?.let { s ->
        val isDebtor = s.fromUserId == currentUserId
        AlertDialog(
            onDismissRequest = { settlementToPay = null },
            icon  = { Icon(Icons.Filled.CheckCircle, null,
                tint = Green, modifier = Modifier.size(32.dp)) },
            title = { Text("Mark as Paid") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        when {
                            isDebtor -> "Confirm that you paid ${s.toUserName} ${"$"}${"%.2f".format(s.amount)}?"
                            else     -> "Confirm that you received ${"$"}${"%.2f".format(s.amount)} from ${s.fromUserName}?"
                        },
                        fontSize = 15.sp
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null,
                                modifier = Modifier.size(16.dp),
                                tint     = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("This payment will be saved and the settlement balances will update immediately.",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val key = "${s.fromUserId}-${s.toUserId}"
                        busyKey        = key
                        settlementToPay = null
                        viewModel.markSettlementPaid(
                            tricountId   = tricountId,
                            fromUserId   = s.fromUserId,
                            fromUserName = s.fromUserName,
                            toUserId     = s.toUserId,
                            toUserName   = s.toUserName,
                            amount       = s.amount
                        ) {
                            busyKey = null
                            Toast.makeText(
                                context,
                                "${"$"}${"%.2f".format(s.amount)} marked as paid ✓",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Confirm Payment")
                }
            },
            dismissButton = {
                TextButton(onClick = { settlementToPay = null }) { Text("Cancel") }
            }
        )
    }

    // ── Reminder dialog ───────────────────────────────────────────────────────
    reminderTarget?.let { s ->
        val isCreditor = s.toUserId == currentUserId
        val message    = buildReminderMessage(s, isCreditor)
        AlertDialog(
            onDismissRequest = { reminderTarget = null },
            icon  = { Icon(Icons.Filled.Send, null,
                tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Remind ${s.fromUserName}") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("The following message will be sent:")
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(message,
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp)
                    }
                    Text("Choose any app to send the reminder (WhatsApp, SMS, email…)",
                        fontSize = 12.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    reminderTarget = null
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    context.startActivity(Intent.createChooser(intent, "Send reminder via…"))
                }) {
                    Icon(Icons.Filled.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Send Reminder")
                }
            },
            dismissButton = {
                TextButton(onClick = { reminderTarget = null }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settlement card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettlementCard(
    settlement  : Settlement,
    isDebtor    : Boolean,
    isCreditor  : Boolean,
    isBusy      : Boolean,
    onMarkPaid  : () -> Unit,
    onRemind    : () -> Unit
) {
    val s = settlement
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Who → Whom + amount
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Debtor chip
                MemberChip(
                    name      = if (isDebtor) "You" else s.fromUserName,
                    isHighlit = isDebtor,
                    chipColor = if (isDebtor) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                    textColor = if (isDebtor) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )

                // Arrow + amount
                Column(
                    modifier              = Modifier.weight(1f),
                    horizontalAlignment   = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.ArrowForward, null,
                        tint     = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp))
                    Text("${"$"}${"%.2f".format(s.amount)}",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.tertiary)
                }

                // Creditor chip
                MemberChip(
                    name      = if (isCreditor) "You" else s.toUserName,
                    isHighlit = isCreditor,
                    chipColor = if (isCreditor) Green.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.secondaryContainer,
                    textColor = if (isCreditor) Green
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // Context hint
            Text(
                when {
                    isDebtor   -> "You owe ${s.toUserName} ${"$"}${"%.2f".format(s.amount)}"
                    isCreditor -> "${s.fromUserName} owes you ${"$"}${"%.2f".format(s.amount)}"
                    else       -> "${s.fromUserName} owes ${s.toUserName} ${"$"}${"%.2f".format(s.amount)}"
                },
                fontSize  = 13.sp,
                color     = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mark as Paid — available to both debtor (I paid) and creditor (I received it)
                Button(
                    onClick  = onMarkPaid,
                    modifier = Modifier.weight(1f),
                    enabled  = !isBusy,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Green,
                        contentColor   = Color.White)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color       = Color.White)
                    } else {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (isDebtor) "I Paid" else "Mark Paid",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }

                // Remind — useful for creditor to nudge debtor, or debtor to ping creditor
                OutlinedButton(
                    onClick  = onRemind,
                    modifier = Modifier.weight(1f),
                    enabled  = !isBusy,
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.NotificationsActive, null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remind",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MemberChip(
    name      : String,
    isHighlit : Boolean,
    chipColor : Color,
    textColor : Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape    = CircleShape,
            color    = chipColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(name.first().uppercase(),
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = textColor)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(name,
            fontSize   = 12.sp,
            fontWeight = if (isHighlit) FontWeight.Bold else FontWeight.Normal,
            color      = textColor,
            maxLines   = 1)
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label,
            fontSize = 11.sp,
            color    = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

private fun buildReminderMessage(s: Settlement, senderIsCreditor: Boolean): String {
    val amount = "${"$"}${"%.2f".format(s.amount)}"
    return if (senderIsCreditor) {
        "Hey ${s.fromUserName}!  Just a friendly reminder — you owe me $amount on TriCount. Please settle up when you get a chance "
    } else {
        "Hey ${s.toUserName}!  It's ${s.fromUserName} — wanted to check if you received my payment of $amount on TriCount. Please let me know!"
    }
}