package com.example.tricount

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.viewModel.TricountViewModel

class AchivedTricountsActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)

        AppTheme.isDark.value = sessionManager.getDarkMode()
        setContent {
            TriCountTheme() {
                AchivedTricountsScreen(
                    viewModel       = viewModel,
                    sessionManager  = sessionManager,
                    onBackClick     = { finish() },
                    onTricountClick = { tricountId, tricountName ->
                        val intent = Intent(this, TricountDetailActivity::class.java).apply {
                            putExtra("TRICOUNT_ID",   tricountId)
                            putExtra("TRICOUNT_NAME", tricountName)
                            putExtra("IS_ARCHIVED",   true)
                        }
                        startActivity(intent)
                        // Forward transition: new screen slides in from right
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadArchivedTricounts()
    }

    // Back transition: current screen slides out to right, previous slides in from left
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchivedTricountsScreen(
    viewModel       : TricountViewModel,
    sessionManager  : SessionManager,
    onBackClick     : () -> Unit,
    onTricountClick : (Int, String) -> Unit
) {
    val archivedTricounts by viewModel.archivedTricounts.collectAsStateWithLifecycle()
    val archivedCount: Int = archivedTricounts.size
    val currentUserId: Int = sessionManager.getUserId() ?: -1
    var tricountToUnarchive by remember { mutableStateOf<TricountEntity?>(null) }
    var tricountToDelete    by remember { mutableStateOf<Pair<Int, String>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadArchivedTricounts()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Archived Tricounts", fontWeight = FontWeight.Bold)
                        Text(
                            "$archivedCount archived",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
        if (archivedTricounts.isEmpty()) {
            // Empty state
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Archive, null,
                        modifier = Modifier.size(72.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No Archived Tricounts",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Long-press a Tricount and tap Archive\nto move it here",
                        fontSize  = 14.sp,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    // Info banner
                    Card(
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier          = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Info, null,
                                modifier = Modifier.size(18.dp),
                                tint     = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Long-press any trip to unarchive or delete it.",
                                fontSize = 13.sp,
                                color    = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                items(archivedTricounts, key = { it.id }) { tricount ->
                    AchivedTricountCard(
                        tricount         = tricount,
                        isCreator        = tricount.creatorId == currentUserId,
                        onTricountClick  = { onTricountClick(tricount.id, tricount.name) },
                        onUnarchiveClick = { tricountToUnarchive = tricount },
                        onDeleteClick    = { tricountToDelete = Pair(tricount.id, tricount.name) }
                    )
                }
            }
        }
    }

    // Unarchive confirmation
    tricountToUnarchive?.let { tricount ->
        AlertDialog(
            onDismissRequest = { tricountToUnarchive = null },
            icon  = { Icon(Icons.Filled.Unarchive, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Unarchive \"${tricount.name}\"?") },
            text  = { Text("This will move the Tricount back to your main list.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.unarchiveTricount(tricount.id)
                    tricountToUnarchive = null
                }) { Text("Unarchive") }
            },
            dismissButton = {
                TextButton(onClick = { tricountToUnarchive = null }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation
    tricountToDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { tricountToDelete = null },
            icon  = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete \"$name\"?") },
            text  = { Text("This will permanently delete the Tricount and all its expenses. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteTricount(id); tricountToDelete = null },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { tricountToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AchivedTricountCard(
    tricount         : TricountEntity,
    isCreator        : Boolean,
    onTricountClick  : () -> Unit,
    onUnarchiveClick : () -> Unit,
    onDeleteClick    : () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick     = { onTricountClick() },
                    onLongClick = { showMenu = true }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon badge
            Surface(
                modifier = Modifier.size(44.dp),
                shape    = CircleShape,
                color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isCreator) Icons.Filled.Star else Icons.Filled.Group,
                        null,
                        modifier = Modifier.size(22.dp),
                        tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tricount.name,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (tricount.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tricount.description,
                        fontSize = 12.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Code: ${tricount.joinCode}",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
            // Archive badge
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    "Archived",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    // Long-press context menu
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text(tricount.name, fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMenu = false; onUnarchiveClick() }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Unarchive, null,
                            modifier = Modifier.size(22.dp),
                            tint     = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(16.dp))
                        Text("Unarchive", fontSize = 15.sp)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMenu = false; onDeleteClick() }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Delete, null,
                            modifier = Modifier.size(22.dp),
                            tint     = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "Delete Permanently",
                            fontSize = 15.sp,
                            color    = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenu = false }) { Text("Cancel") }
            }
        )
    }
}