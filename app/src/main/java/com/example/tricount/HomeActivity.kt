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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun HomeScreen(onTricountClick: (String) -> Unit) {

    var selectedItem by remember { mutableStateOf(0) }

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
        }
    ) { padding ->

        when (selectedItem) {
            0 -> TriCountListScreen(
                modifier = Modifier.padding(padding),
                onTricountClick = onTricountClick
            )
            1 -> ProfileScreen(modifier = Modifier.padding(padding))
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
