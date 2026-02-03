package com.example.tricount

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tricount.ui.theme.TriCountTheme

class TricountDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tricountName =
            intent.getStringExtra("TRICOUNT_NAME") ?: "Tricount"

        setContent {
            TriCountTheme {
                TricountDetailScreen(
                    tricountName = tricountName,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

data class Expense(
    val name: String,
    val description: String,
    val totalCost: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TricountDetailScreen(
    tricountName: String,
    onBackClick: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Expenses", "Balances", "Photos")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(tricountName) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Filled.ArrowBack, null)
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (selectedTabIndex) {
                0 -> ExpensesTab()
                1 -> BalancesTab()
                2 -> PhotosTab()
            }
        }
    }
}

@Composable
fun ExpensesTab() {
    val context = LocalContext.current

    val expenses = remember {
        listOf(
            Expense(
                name = "Taxi",
                description = "Airport to hotel ride",
                totalCost = 45.50
            ),
            Expense(
                name = "Hotel",
                description = "3 nights accommodation",
                totalCost = 350.00
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(expenses) { expense ->
            ExpenseCard(
                expense = expense,
                onClick = {
                    val intent = Intent(context, TemporaryActivity::class.java).apply {
                        putExtra("EXPENSE_NAME", expense.name)
                        putExtra("EXPENSE_DESCRIPTION", expense.description)
                        putExtra("EXPENSE_COST", expense.totalCost)
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun ExpenseCard(
    expense: Expense,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = expense.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = expense.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "$${String.format("%.2f", expense.totalCost)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun BalancesTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Balances will be displayed here",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PhotosTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Photos will be displayed here",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}