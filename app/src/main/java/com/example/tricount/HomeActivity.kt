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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tricount.ui.theme.TriCountTheme

class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TriCountTheme {
                HomeScreen(
                    onTricountClick = { tricountName ->
                        val intent = Intent(
                            this,
                            TricountDetailActivity::class.java
                        )
                        intent.putExtra("TRICOUNT_NAME", tricountName)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onTricountClick: (String) -> Unit) {

    var selectedItem by remember { mutableStateOf(0) }
    val context = LocalContext.current
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, null) },
                    label = { Text("TriCounts") },
                    selected = selectedItem == 0,
                    onClick = { selectedItem = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Person, null) },
                    label = { Text("Profile") },
                    selected = selectedItem == 1,
                    onClick = { selectedItem = 1 }
                )
            }
        },
        floatingActionButton = {
            // Only show FAB when on TriCounts tab
            if (selectedItem == 0) {
                FloatingActionButton(
                    onClick = {
                        showBottomSheet = true
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Tricount")
                }
            }
        }
    ) { padding ->

        when (selectedItem) {
            0 -> TriCountListScreen(
                modifier = Modifier.padding(padding),
                onTricountClick = onTricountClick
            )
            1 -> ProfileScreen(modifier = Modifier.padding(padding))
        }

        // Bottom Sheet
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Choose an option",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Start a new Tricount option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showBottomSheet = false
                                val intent = Intent(context, AddTricountActivity::class.java)
                                context.startActivity(intent)
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "Start a New Tricount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a new expense group from scratch",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Join existing Tricount option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showBottomSheet = false
                                val intent = Intent(context, JoinTricountActivity::class.java)
                                context.startActivity(intent)
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "Join an Existing Tricount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enter a code to join a friend's Tricount",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------- UI SCREENS ---------- */

data class Tricount(
    val name: String,
    val description: String
)

@Composable
fun TriCountListScreen(
    modifier: Modifier = Modifier,
    onTricountClick: (String) -> Unit
) {

    val tricounts = listOf(
        Tricount("City Trip", "Weekend getaway"),
        Tricount("Vacation 2024", "Beach expenses"),
        Tricount("Roommates", "Shared apartment costs")
    )

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tricounts) { tricount ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onTricountClick(tricount.name)
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        tricount.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        tricount.description,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Profile Screen", fontSize = 22.sp)
    }
}