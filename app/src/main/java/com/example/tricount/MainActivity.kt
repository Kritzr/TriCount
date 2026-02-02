package com.example.tricount

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.tricount.ui.theme.TriCountTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TriCountTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2000)
        isLoading = false
    }

    if (isLoading) {
        LoadingScreen()
    } else {
        HomeScreen()
    }
}


@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            CircularProgressIndicator()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Loading TriCount...",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}


@Composable
fun HomeScreen() {
    var selectedItem by remember { mutableStateOf(0) }
    var selectedTricount by remember { mutableStateOf<String?>(null) }

    // If a tricount is selected, show its detail page
    if (selectedTricount != null) {
        TricountDetailPage(
            tricountName = selectedTricount!!,
            onBackClick = { selectedTricount = null }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "TriCount Details") },
                        label = { Text("TriCount Details") },
                        selected = selectedItem == 0,
                        onClick = { selectedItem = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                        selected = selectedItem == 1,
                        onClick = { selectedItem = 1 }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedItem) {
                    0 -> TriCountDetailsScreen(onTricountClick = { tricountName ->
                        selectedTricount = tricountName
                    })
                    1 -> ProfileScreen()
                }
            }
        }
    }
}

data class Tricount(
    val name: String,
    val description: String
)

@Composable
fun TriCountDetailsScreen(onTricountClick: (String) -> Unit) {
    val tricounts = remember {
        listOf(
            Tricount("City Trip", "Weekend getaway to the city with friends"),
            Tricount("Vacation 2024", "Summer beach vacation expenses"),
            Tricount("Roommate Expenses", "Monthly shared apartment costs"),
            Tricount("Birthday Party", "Sarah's surprise birthday celebration"),
            Tricount("Ski Weekend", "Winter sports trip to the mountains")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "TriCount Clone by Kika",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Tricount Cards
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tricounts) { tricount ->
                TricountCard(
                    tricount = tricount,
                    onClick = { onTricountClick(tricount.name) }
                )
            }
        }
    }
}

@Composable
fun TricountCard(
    tricount: Tricount,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = tricount.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tricount.description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TricountDetailPage(
    tricountName: String,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tricountName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = tricountName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Detailed view for $tricountName",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Here you can add expenses, view balances, and manage participants",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
fun ProfileScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Profile",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Manage your profile settings here",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}