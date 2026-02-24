package com.example.tricount

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed // Added for better list handling
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.* // Ensure Material3 is used
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.viewModel.Settlement
import com.example.tricount.viewModel.TricountViewModel
import java.util.Locale

class SummaryActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tricountId = intent.getIntExtra("tricountId", -1)
        val currentUserId = intent.getIntExtra("currentUserId", -1)

        setContent {
            // Standard Material Theme Wrapper (Assumed name based on typical projects)
            MaterialTheme {
                SummaryScreen(
                    tricountId = tricountId,
                    currentUserId = currentUserId,
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context, tricountId: Int, currentUserId: Int): Intent {
            return Intent(context, SummaryActivity::class.java).apply {
                putExtra("tricountId", tricountId)
                putExtra("currentUserId", currentUserId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // REQUIRED for TopAppBar
@Composable
fun SummaryScreen(
    tricountId: Int,
    currentUserId: Int,
    viewModel: TricountViewModel,
    onBack: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val settlements by viewModel.settlements.collectAsStateWithLifecycle()

    LaunchedEffect(tricountId) {
        viewModel.loadExpenses(tricountId)
        viewModel.loadTricountMembers(tricountId)
        viewModel.calculateSettlements(tricountId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Summary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                TotalSummaryCard(
                    totalAmount = expenses.sumOf { it.amount },
                    expenseCount = expenses.size
                )
            }

            if (settlements.isNotEmpty()) {
                item {
                    SettlementCard(
                        settlements = settlements,
                        currentUserId = currentUserId
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TotalSummaryCard(totalAmount: Double, expenseCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    "$${String.format(Locale.US, "%.2f", totalAmount)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "$expenseCount expense${if (expenseCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }

            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.medium,
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

@Composable
private fun SettlementCard(
    settlements: List<Settlement>,
    currentUserId: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Settle Up",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            settlements.forEachIndexed { index, s ->
                val isDebtor = s.fromUserId == currentUserId
                val isCreditor = s.toUserId == currentUserId

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDebtor) "You" else s.fromUserName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isDebtor) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isDebtor) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )

                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(horizontal = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        "$${String.format(Locale.US, "%.2f", s.amount)}",
                        modifier = Modifier.width(70.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(horizontal = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = if (isCreditor) "You" else s.toUserName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCreditor) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isCreditor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                if (index != settlements.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}