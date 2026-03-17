package com.example.tricount

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.AddMemberResult
import com.example.tricount.viewModel.TricountViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Activity
// ─────────────────────────────────────────────────────────────────────────────

class EditTripActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        val tricountId = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        if (tricountId != -1) {
            viewModel.loadTricountDetails(tricountId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tricountId     = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        val sessionManager = SessionManager(this)

        AppTheme.isDark.value = sessionManager.getDarkMode()

        if (tricountId == -1) { finish(); return }

        setContent {
            TriCountTheme() {
                LaunchedEffect(tricountId) {
                    viewModel.loadTricountDetails(tricountId)
                }

                val tricountDetails by viewModel.currentTricount.collectAsStateWithLifecycle()
                val members         by viewModel.tricountMembers.collectAsStateWithLifecycle()
                val currentUserId    = sessionManager.getUserId() ?: -1

                EditTripScreen(
                    tricountId      = tricountId,
                    tricountDetails = tricountDetails,
                    members         = members,
                    currentUserId   = currentUserId,
                    viewModel       = viewModel,
                    onBackClick     = { finish() },
                    onSaved         = {
                        Toast.makeText(this, "Trip updated!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TRICOUNT_ID = "edit_trip_tricount_id"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Emoji list
// ─────────────────────────────────────────────────────────────────────────────

private val TRIP_EMOJIS = listOf(
    "⛺","🏕️","✈️","🚗","🍕","🎉","🎬","🏖️",
    "🏔️","🛳️","🎭","🏋️","🎮","🛍️","🍜","☕",
    "🌍","🎸","🏄","🚴","🏊","🎯","🎪","🌅",
    "🦁","🐬","🌸","⚽","🏀","🎾","🧗","🏂"
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTripScreen(
    tricountId      : Int,
    tricountDetails : TricountEntity?,
    members         : List<MemberWithDetails>,
    currentUserId   : Int,
    viewModel       : TricountViewModel,
    onBackClick     : () -> Unit,
    onSaved         : () -> Unit
) {
    val context   = LocalContext.current
    val isCreator = tricountDetails?.creatorId == currentUserId

    // ── Editable state (pre-filled once details load) ─────────────────────────
    var tripName        by remember(tricountDetails?.id) {
        mutableStateOf(tricountDetails?.name ?: "")
    }
    var tripDescription by remember(tricountDetails?.id) {
        mutableStateOf(tricountDetails?.description ?: "")
    }
    var selectedEmoji   by remember { mutableStateOf("⛺") }
    var isSaving        by remember { mutableStateOf(false) }

    // ── Add-member state ──────────────────────────────────────────────────────
    var showAddMemberSheet  by remember { mutableStateOf(false) }
    var addEmail            by remember { mutableStateOf("") }
    var addEmailError       by remember { mutableStateOf<String?>(null) }
    var isAddingMember      by remember { mutableStateOf(false) }

    // ── Remove confirmation ───────────────────────────────────────────────────
    var memberToRemove      by remember { mutableStateOf<MemberWithDetails?>(null) }

    val nameValid = tripName.isNotBlank()
    val isDirty   = tricountDetails != null &&
            (tripName.trim()        != tricountDetails.name ||
                    tripDescription.trim() != tricountDetails.description)

    // ── Loading state ─────────────────────────────────────────────────────────
    if (tricountDetails == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Trip", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Filled.ArrowBack, "Back")
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
            Box(
                modifier         = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Edit Trip", fontWeight = FontWeight.Bold)
                        Text(
                            tricountDetails.name,
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Save action in top bar
                    TextButton(
                        onClick  = {
                            if (nameValid) {
                                isSaving = true
                                viewModel.editTricount(
                                    tricountId  = tricountId,
                                    name        = tripName.trim(),
                                    description = tripDescription.trim()
                                )
                                isSaving = false
                                onSaved()
                            }
                        },
                        enabled = nameValid && isDirty && !isSaving
                    ) {
                        Text(
                            "Save",
                            color      = if (nameValid && isDirty && !isSaving)
                                MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp
                        )
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
        LazyColumn(
            modifier       = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Section: Trip icon ────────────────────────────────────────────
            item {
                SectionLabel("Trip Icon")
                Spacer(Modifier.height(8.dp))

                // Large selected emoji preview
                Box(
                    modifier         = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape    = CircleShape,
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(88.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(selectedEmoji, fontSize = 44.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Emoji grid — 4 columns
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        TRIP_EMOJIS.chunked(4).forEach { row ->
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { emoji ->
                                    val chosen = emoji == selectedEmoji
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedEmoji = emoji },
                                        shape  = RoundedCornerShape(10.dp),
                                        color  = if (chosen)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface,
                                        border = if (chosen) BorderStroke(
                                            2.dp, MaterialTheme.colorScheme.primary)
                                        else null
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier         = Modifier.padding(10.dp)
                                        ) {
                                            Text(emoji, fontSize = 22.sp)
                                        }
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            // ── Section: Trip name ────────────────────────────────────────────
            item {
                SectionLabel("Trip Name")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = tripName,
                    onValueChange = { tripName = it },
                    label         = { Text("Name *") },
                    placeholder   = { Text("e.g. Goa trip, Team offsite") },
                    leadingIcon   = {
                        Text(
                            selectedEmoji,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    },
                    trailingIcon  = {
                        if (tripName.isNotBlank())
                            IconButton(onClick = { tripName = "" }) {
                                Icon(Icons.Filled.Clear, "Clear")
                            }
                    },
                    singleLine    = true,
                    isError       = tripName.isEmpty(),
                    supportingText = {
                        if (tripName.isEmpty())
                            Text("Name cannot be empty",
                                color = MaterialTheme.colorScheme.error)
                    },
                    modifier  = Modifier.fillMaxWidth(),
                    enabled   = isCreator && !isSaving,
                    shape     = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            // ── Section: Description ──────────────────────────────────────────
            item {
                SectionLabel("Description")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = tripDescription,
                    onValueChange = { tripDescription = it },
                    label         = { Text("Description (optional)") },
                    placeholder   = { Text("What is this trip about?") },
                    leadingIcon   = { Icon(Icons.Filled.Notes, null) },
                    modifier      = Modifier.fillMaxWidth(),
                    minLines      = 3,
                    maxLines      = 6,
                    enabled       = isCreator && !isSaving,
                    shape         = RoundedCornerShape(12.dp)
                )
            }

            // ── Save button (also at bottom) ──────────────────────────────────
            if (isCreator) {
                item {
                    Button(
                        onClick  = {
                            if (nameValid) {
                                isSaving = true
                                viewModel.editTricount(
                                    tricountId  = tricountId,
                                    name        = tripName.trim(),
                                    description = tripDescription.trim()
                                )
                                isSaving = false
                                onSaved()
                            }
                        },
                        enabled  = nameValid && isDirty && !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Filled.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Save Changes",
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── Section: Members ──────────────────────────────────────────────
            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        SectionLabel("Members")
                        Text(
                            "${members.size} participant${if (members.size == 1) "" else "s"}",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isCreator) {
                        FilledTonalButton(
                            onClick = { showAddMemberSheet = true },
                            shape   = RoundedCornerShape(50)
                        ) {
                            Icon(
                                Icons.Filled.PersonAdd, null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Add", fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── Member rows ───────────────────────────────────────────────────
            items(members, key = { it.userId }) { member ->
                MemberEditRow(
                    member        = member,
                    currentUserId = currentUserId,
                    isCreator     = isCreator,
                    onRemove      = { memberToRemove = member }
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // ── Add member bottom sheet ───────────────────────────────────────────────
    if (showAddMemberSheet) {
        AlertDialog(
            onDismissRequest = {
                if (!isAddingMember) {
                    showAddMemberSheet = false
                    addEmail = ""
                    addEmailError = null
                }
            },
            icon  = { Icon(Icons.Filled.PersonAdd, null,
                tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Add Member") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the email address of the person you want to add.",
                        fontSize = 14.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value         = addEmail,
                        onValueChange = {
                            addEmail = it
                            addEmailError = null
                        },
                        label         = { Text("Email address") },
                        placeholder   = { Text("friend@example.com") },
                        leadingIcon   = { Icon(Icons.Filled.Email, null) },
                        trailingIcon  = {
                            if (addEmail.isNotBlank())
                                IconButton(onClick = { addEmail = "" }) {
                                    Icon(Icons.Filled.Clear, "Clear")
                                }
                        },
                        isError       = addEmailError != null,
                        supportingText = addEmailError?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        enabled       = !isAddingMember,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction    = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick  = {
                        val email = addEmail.trim()
                        if (email.isBlank()) {
                            addEmailError = "Please enter an email"
                            return@Button
                        }
                        val emailRegex = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$".toRegex()
                        if (!emailRegex.matches(email)) {
                            addEmailError = "Invalid email address"
                            return@Button
                        }
                        isAddingMember = true
                        viewModel.addMemberByEmail(tricountId, email) { result ->
                            isAddingMember = false
                            when (result) {
                                is AddMemberResult.Success -> {
                                    Toast.makeText(
                                        context,
                                        "${result.memberName} added!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showAddMemberSheet = false
                                    addEmail = ""
                                    addEmailError = null
                                }
                                is AddMemberResult.Error -> {
                                    addEmailError = result.message
                                }
                            }
                        }
                    },
                    enabled  = addEmail.isNotBlank() && !isAddingMember
                ) {
                    if (isAddingMember) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color       = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Adding…")
                    } else {
                        Text("Add Member")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick  = {
                        showAddMemberSheet = false
                        addEmail = ""
                        addEmailError = null
                    },
                    enabled  = !isAddingMember
                ) { Text("Cancel") }
            }
        )
    }

    // ── Remove member confirmation ────────────────────────────────────────────
    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            icon  = { Icon(Icons.Filled.PersonRemove, null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove Member?") },
            text  = {
                Text(
                    "Remove ${member.name} from this trip? " +
                            "Their expenses will remain but they won't be a participant.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeMember(member.userId, tricountId)
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section label
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize   = 13.sp,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.primary,
        modifier   = Modifier.padding(start = 2.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Member row in the edit screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MemberEditRow(
    member        : MemberWithDetails,
    currentUserId : Int,
    isCreator     : Boolean,
    onRemove      : () -> Unit
) {
    val isMe       = member.userId == currentUserId
    val bgColor    = when {
        member.isCreator -> MaterialTheme.colorScheme.primaryContainer
        isMe             -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        else             -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val avatarColor = when {
        member.isCreator -> MaterialTheme.colorScheme.primary
        isMe             -> MaterialTheme.colorScheme.secondary
        else             -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    }
    val avatarTextColor = when {
        member.isCreator -> MaterialTheme.colorScheme.onPrimary
        isMe             -> MaterialTheme.colorScheme.onSecondary
        else             -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        color    = bgColor
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(
                shape    = CircleShape,
                color    = avatarColor,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        member.name.first().uppercase(),
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = avatarTextColor
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name + email
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isMe) "${member.name} (You)" else member.name,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    if (member.isCreator) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Creator",
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.primary,
                                modifier   = Modifier.padding(
                                    horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Text(
                    member.email,
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Remove button — only creator can remove, can't remove self or other creator
            if (isCreator && !member.isCreator && !isMe) {
                IconButton(
                    onClick  = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.PersonRemove, "Remove",
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}