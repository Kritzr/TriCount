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
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.TricountViewModel

class InsightsActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        val tricountId = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        if (tricountId != -1) viewModel.loadExpenses(tricountId)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    @OptIn(ExperimentalMaterial3Api::class)
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

                val expenses      by viewModel.expenses.collectAsStateWithLifecycle()
                val currentUserId  = sessionManager.getUserId() ?: -1

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("Insights", fontWeight = FontWeight.Bold)
                                    Text(
                                        tricountName,
                                        fontSize = 12.sp,
                                        color    = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
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
                    InsightsContent(
                        modifier      = Modifier.padding(padding),
                        expenses      = expenses,
                        currentUserId = currentUserId
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_TRICOUNT_ID   = "insights_tricount_id"
        const val EXTRA_TRICOUNT_NAME = "insights_tricount_name"
    }
}