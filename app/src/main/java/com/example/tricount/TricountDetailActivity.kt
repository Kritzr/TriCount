package com.example.tricount

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.data.entity.MemberWithDetails
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.AddMemberResult
import com.example.tricount.viewModel.TricountViewModel

class TricountDetailActivity : ComponentActivity() {

    private val tricountViewModel: TricountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tricountId   = intent.getIntExtra("TRICOUNT_ID", -1)
        val tricountName = intent.getStringExtra("TRICOUNT_NAME") ?: "Tricount"
        val sessionManager = SessionManager(this)

        setContent {
            TriCountTheme(darkTheme = false) {
                LaunchedEffect(tricountId) {
                    tricountViewModel.loadTricountDetails(tricountId)
                }

                val tricountDetails by tricountViewModel.currentTricount.collectAsStateWithLifecycle()
                val members         by tricountViewModel.tricountMembers.collectAsStateWithLifecycle()
                val currentUserId   = sessionManager.getUserId() ?: -1

                TricountDetailScreen(
                    tricountId      = tricountId,
                    tricountName    = tricountName,
                    tricountDetails = tricountDetails,
                    members         = members,
                    currentUserId   = currentUserId,
                    viewModel       = tricountViewModel,
                    onBackClick     = { finish() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen — hub with nav cards + inline details
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TricountDetailScreen(
    tricountId      : Int,
    tricountName    : String,
    tricountDetails : TricountEntity?,
    members         : List<MemberWithDetails>,
    currentUserId   : Int,
    viewModel       : TricountViewModel,
    onBackClick     : () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tricountName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        tricountDetails?.let { shareTricount(context, it.name, it.joinCode) }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                Text("Sections", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
            }

            // Expenses nav card
            item {
                NavCard(
                    icon           = Icons.Filled.Receipt,
                    title          = "Expenses",
                    subtitle       = "View and add expenses",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    onClick        = {
                        context.startActivity(
                            Intent(context, ExpensesActivity::class.java).apply {
                                putExtra("extra_tricount_id",   tricountId)
                                putExtra("extra_tricount_name", tricountName)
                            }
                        )
                    }
                )
            }

            // Summary nav card
            item {
                NavCard(
                    icon           = Icons.Filled.AccountBalance,
                    title          = "Summary",
                    subtitle       = "Balances and settlements",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick        = {
                        context.startActivity(
                            Intent(context, BalancesTabActivity::class.java).apply {
                                putExtra("extra_tricount_id",   tricountId)
                                putExtra("extra_tricount_name", tricountName)
                            }
                        )
                    }
                )
            }

            // Inline details
            item {
                Spacer(Modifier.height(4.dp))
                Text("Details", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
            }

            item {
                DetailsSection(
                    tricountDetails = tricountDetails,
                    members         = members,
                    currentUserId   = currentUserId,
                    viewModel       = viewModel
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Navigation card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NavCard(
    icon           : ImageVector,
    title          : String,
    subtitle       : String,
    containerColor : androidx.compose.ui.graphics.Color,
    onClick        : () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        onClick   = onClick,
        colors    = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape    = CircleShape,
                color    = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline details (join code + members)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSection(
    tricountDetails : TricountEntity?,
    members         : List<MemberWithDetails>,
    currentUserId   : Int,
    viewModel       : TricountViewModel
) {
    val context = LocalContext.current
    var showAddMemberDialog by remember { mutableStateOf(false) }

    if (tricountDetails == null) {
        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isCreator = tricountDetails.creatorId == currentUserId

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Join code
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Join Code", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Spacer(Modifier.height(4.dp))
                        Text(tricountDetails.joinCode, fontSize = 32.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = {
                        copyToClipboard(context, tricountDetails.joinCode)
                        Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick   = { shareTricount(context, tricountDetails.name, tricountDetails.joinCode) },
                    modifier  = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share Tricount")
                }
            }
        }

        // Description
        if (tricountDetails.description.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Description", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(tricountDetails.description, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Members
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Members (${members.size})", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    if (isCreator) {
                        IconButton(onClick = { showAddMemberDialog = true }) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = "Add Member",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (members.isEmpty()) {
                    Text("No members yet", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    members.forEach { member ->
                        MemberItem(
                            member        = member,
                            isCreator     = isCreator,
                            canRemove     = isCreator && !member.isCreator,
                            onRemoveClick = { viewModel.removeMember(member.userId, tricountDetails.id) }
                        )
                        if (member != members.last()) Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showAddMemberDialog) {
        AddMemberDialog(tricountId = tricountDetails.id, viewModel = viewModel,
            onDismiss = { showAddMemberDialog = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Member row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MemberItem(
    member        : MemberWithDetails,
    isCreator     : Boolean,
    canRemove     : Boolean,
    onRemoveClick : () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Surface(modifier = Modifier.size(40.dp), shape = CircleShape,
                color = if (member.isCreator) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (member.isCreator) Icons.Filled.Star else Icons.Filled.Person,
                        contentDescription = null, modifier = Modifier.size(24.dp),
                        tint = if (member.isCreator) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(member.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(member.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (member.isCreator) {
            Surface(shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) {
                Text("CREATOR", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
        } else if (canRemove) {
            IconButton(onClick = { showRemoveDialog = true }) {
                Icon(Icons.Filled.RemoveCircle, contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title   = { Text("Remove Member?") },
            text    = { Text("Remove ${member.name} from this Tricount?") },
            confirmButton = {
                TextButton(
                    onClick = { onRemoveClick(); showRemoveDialog = false },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Member Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberDialog(
    tricountId : Int,
    viewModel  : TricountViewModel,
    onDismiss  : () -> Unit
) {
    var email     by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isValidEmail = remember(email) {
        email.isBlank() ||
                "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex().matches(email)
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Add Member") },
        text = {
            Column {
                Text("Enter the email of the person you want to add:",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") }, placeholder = { Text("user@example.com") },
                    leadingIcon = { Icon(Icons.Filled.Email, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = !isValidEmail,
                    supportingText = {
                        if (!isValidEmail) Text("Invalid email", color = MaterialTheme.colorScheme.error)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    enabled = !isLoading
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValidEmail && email.isNotBlank()) {
                        isLoading = true
                        viewModel.addMemberByEmail(tricountId, email.trim()) { result ->
                            isLoading = false
                            when (result) {
                                is AddMemberResult.Success -> {
                                    Toast.makeText(context, "${result.memberName} added!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                is AddMemberResult.Error ->
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = isValidEmail && email.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText("Tricount Code", text))
}

private fun shareTricount(context: Context, name: String, joinCode: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Join $name on TriCount")
        putExtra(Intent.EXTRA_TEXT, "Join my Tricount: $name\n\nCode: $joinCode")
    }
    context.startActivity(Intent.createChooser(intent, "Share Tricount"))
}