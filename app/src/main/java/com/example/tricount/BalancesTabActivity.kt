package com.example.tricount

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.ui.theme.TriCountTheme
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
                    Box(modifier = Modifier.padding(padding)) {
                        // Reuses the BalancesTab composable from TricountDetailActivity.kt
                        BalancesTab(
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
    }

    companion object {
        const val EXTRA_TRICOUNT_ID   = "extra_tricount_id"
        const val EXTRA_TRICOUNT_NAME = "extra_tricount_name"
    }
}