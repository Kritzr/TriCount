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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.AddMemberResult
import com.example.tricount.viewModel.TricountViewModel

class TricountDetailActivity : ComponentActivity() {

    private val tricountViewModel: TricountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tricountId = intent.getIntExtra("TRICOUNT_ID", -1)
        val tricountName = intent.getStringExtra("TRICOUNT_NAME") ?: "Tricount"
        val sessionManager = SessionManager(this)

        setContent {
            TriCountTheme(darkTheme = false) {
                // Load the specific tricount details
                LaunchedEffect(tricountId) {
                    tricountViewModel.loadTricountDetails(tricountId)
                }

                val tricountDetails by tricountViewModel.currentTricount.collectAsStateWithLifecycle()
                val members by tricountViewModel.tricountMembers.collectAsStateWithLifecycle()

                TricountDetailScreen(
                    tricountName = tricountName,
                    tricountDetails = tricountDetails,
                    members = members,
                    currentUserId = sessionManager.getUserId() ?: -1,
                    viewModel = tricountViewModel,
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
    tricountDetails: com.example.tricount.data.entity.TricountEntity?,
    members: List<com.example.tricount.data.entity.MemberWithDetails>,
    currentUserId: Int,
    viewModel: TricountViewModel,
    onBackClick: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Expenses", "Balances", "Details")
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(tricountName) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Filled.ArrowBack, null)
                        }
                    },
                    actions = {
                        // Share button
                        IconButton(
                            onClick = {
                                tricountDetails?.let { tricount ->
                                    shareTricount(context, tricount.name, tricount.joinCode)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share Tricount")
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
                2 -> DetailsTab(
                    tricountDetails = tricountDetails,
                    members = members,
                    currentUserId = currentUserId,
                    viewModel = viewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsTab(
    tricountDetails: com.example.tricount.data.entity.TricountEntity?,
    members: List<com.example.tricount.data.entity.MemberWithDetails>,
    currentUserId: Int,
    viewModel: TricountViewModel
) {
    val context = LocalContext.current
    var showAddMemberDialog by remember { mutableStateOf(false) }

    if (tricountDetails == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val isCreator = tricountDetails.creatorId == currentUserId

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Join Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Join Code",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tricountDetails.joinCode,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Copy button
                        IconButton(
                            onClick = {
                                copyToClipboard(context, tricountDetails.joinCode)
                                Toast.makeText(
                                    context,
                                    "Code copied!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copy code",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Share this code with friends to let them join this Tricount",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Share button
                    Button(
                        onClick = {
                            shareTricount(context, tricountDetails.name, tricountDetails.joinCode)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Tricount")
                    }
                }
            }
        }

        item {
            // Description Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Description",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (tricountDetails.description.isNotBlank())
                            tricountDetails.description
                        else
                            "No description provided",
                        fontSize = 14.sp,
                        color = if (tricountDetails.description.isNotBlank())
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            // Members Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Members (${members.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Add member button (only for creator)
                        if (isCreator) {
                            IconButton(onClick = { showAddMemberDialog = true }) {
                                Icon(
                                    Icons.Filled.PersonAdd,
                                    contentDescription = "Add Member",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (members.isEmpty()) {
                        Text(
                            text = "No members yet",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        members.forEach { member ->
                            MemberItem(
                                member = member,
                                isCreator = isCreator,
                                canRemove = isCreator && !member.isCreator,
                                onRemoveClick = {
                                    viewModel.removeMember(member.userId, tricountDetails.id)
                                }
                            )
                            if (member != members.last()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Member Dialog
    if (showAddMemberDialog) {
        AddMemberDialog(
            tricountId = tricountDetails.id,
            viewModel = viewModel,
            onDismiss = { showAddMemberDialog = false }
        )
    }
}

@Composable
fun MemberItem(
    member: com.example.tricount.data.entity.MemberWithDetails,
    isCreator: Boolean,
    canRemove: Boolean,
    onRemoveClick: () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (member.isCreator)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (member.isCreator) Icons.Filled.Star else Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (member.isCreator)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = member.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = member.email,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (member.isCreator) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "CREATOR",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else if (canRemove) {
            IconButton(onClick = { showRemoveDialog = true }) {
                Icon(
                    Icons.Filled.RemoveCircle,
                    contentDescription = "Remove member",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // Remove confirmation dialog
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove Member?") },
            text = { Text("Are you sure you want to remove ${member.name} from this Tricount?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveClick()
                        showRemoveDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberDialog(
    tricountId: Int,
    viewModel: TricountViewModel,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Email validation
    val isValidEmail = remember(email) {
        val emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex()
        email.isBlank() || emailRegex.matches(email)
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Add Member") },
        text = {
            Column {
                Text(
                    text = "Enter the email address of the person you want to add:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    placeholder = { Text("user@example.com") },
                    leadingIcon = { Icon(Icons.Filled.Email, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isValidEmail,
                    supportingText = {
                        if (!isValidEmail) {
                            Text(
                                text = "Please enter a valid email address",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
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
                                    Toast.makeText(
                                        context,
                                        "${result.memberName} added successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }
                                is AddMemberResult.Error -> {
                                    Toast.makeText(
                                        context,
                                        result.message,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                },
                enabled = isValidEmail && email.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
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

@SuppressLint("DefaultLocale")
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

// Helper functions
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Tricount Code", text)
    clipboard.setPrimaryClip(clip)
}

private fun shareTricount(context: Context, tricountName: String, joinCode: String) {
    val shareText = """
        Join my Tricount: $tricountName
        
        Use this code to join: $joinCode
        
        Download TriCount app to split expenses easily!
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Join $tricountName on TriCount")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }

    context.startActivity(Intent.createChooser(intent, "Share Tricount"))
}