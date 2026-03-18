package com.example.tricount

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.data.entity.MemberWithDetails
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.AddMemberResult
import com.example.tricount.viewModel.TricountViewModel

// =============================================================================
// Activity
// =============================================================================

class EditTripActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        val id = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        if (id != -1) viewModel.loadTricountDetails(id)
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
                    onBackClick     = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TRICOUNT_ID = "edit_trip_tricount_id"
    }
}

// =============================================================================
// Emoji options
// =============================================================================

private val TRIP_EMOJIS = listOf(
    "⛺","🏕️","✈️","🚗","🍕","🎉","🎬","🏖️",
    "🏔️","🛳️","🎭","🏋️","🎮","🛍️","🍜","☕",
    "🌍","🎸","🏄","🚴","🏊","🎯","🎪","🌅",
    "🦁","🐬","🌸","⚽","🏀","🎾","🧗","🏂"
)

// =============================================================================
// Screen
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTripScreen(
    tricountId      : Int,
    tricountDetails : com.example.tricount.data.entity.TricountEntity?,
    members         : List<MemberWithDetails>,
    currentUserId   : Int,
    viewModel       : TricountViewModel,
    onBackClick     : () -> Unit
) {
    val context   = LocalContext.current
    val isCreator = tricountDetails?.creatorId == currentUserId

    // ── Local state — seeded from DB once, then user edits freely ────────────
    var editName     by remember { mutableStateOf("") }
    var editDesc     by remember { mutableStateOf("") }
    var editEmoji    by remember { mutableStateOf("⛺") }
    var seeded       by remember { mutableStateOf(false) }

    // Seed from DB exactly once when data arrives
    LaunchedEffect(tricountDetails?.id) {
        tricountDetails?.let { t ->
            if (!seeded) {
                editName  = t.name
                editDesc  = t.description
                editEmoji = if (t.emoji.isNotBlank()) t.emoji else "⛺"
                seeded    = true
            }
        }
    }

    var showEmojiPicker by remember { mutableStateOf(false) }
    var isSaving        by remember { mutableStateOf(false) }

    val nameValid = editName.isNotBlank()
    val hasChanges = tricountDetails != null && (
            editName.trim() != tricountDetails.name ||
                    editDesc.trim() != tricountDetails.description ||
                    editEmoji       != (if (tricountDetails.emoji.isNotBlank()) tricountDetails.emoji else "⛺")
            )

    // ── Add member ────────────────────────────────────────────────────────────
    var showAddDialog  by remember { mutableStateOf(false) }
    var addEmail       by remember { mutableStateOf("") }
    var addEmailError  by remember { mutableStateOf<String?>(null) }
    var isAdding       by remember { mutableStateOf(false) }

    // ── Remove member ─────────────────────────────────────────────────────────
    var memberToRemove by remember { mutableStateOf<MemberWithDetails?>(null) }

    // ── Save function ─────────────────────────────────────────────────────────
    fun saveAll() {
        if (!nameValid || isSaving) return
        isSaving = true
        viewModel.editTricountFull(
            tricountId  = tricountId,
            name        = editName.trim(),
            description = editDesc.trim(),
            emoji       = editEmoji
        )
        isSaving = false
        Toast.makeText(context, "Trip updated!", Toast.LENGTH_SHORT).show()
        onBackClick()
    }

    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Edit Trip", fontWeight = FontWeight.Bold)
                        if (tricountDetails != null) {
                            Text(
                                tricountDetails.name,
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick  = ::saveAll,
                        enabled  = nameValid && hasChanges && !isSaving
                    ) {
                        Text(
                            "Save",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            color      = if (nameValid && hasChanges && !isSaving)
                                MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)
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

        if (tricountDetails == null) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier            = Modifier.padding(padding).fillMaxSize(),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── TRIP ICON ─────────────────────────────────────────────────────
            item {
                ELabel("Trip Icon")
                Spacer(Modifier.height(10.dp))

                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    // Tappable icon circle
                    Surface(
                        shape    = CircleShape,
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(88.dp)
                            .clickable { showEmojiPicker = !showEmojiPicker }
                    ) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(editEmoji, fontSize = 44.sp)
                        }
                    }
                    // Edit badge
                    Surface(
                        shape    = CircleShape,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = (-28).dp)
                    ) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Icon(
                                Icons.Filled.Edit, null,
                                tint     = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    if (showEmojiPicker) "Tap an icon to select · tap circle to close"
                    else "Tap the icon to change it",
                    fontSize  = 12.sp,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier  = Modifier.fillMaxWidth()
                )

                // Animated emoji grid
                AnimatedVisibility(
                    visible = showEmojiPicker,
                    enter   = expandVertically(),
                    exit    = shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(16.dp),
                            color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                TRIP_EMOJIS.chunked(4).forEach { row ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        row.forEach { emoji ->
                                            val chosen = emoji == editEmoji
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        editEmoji       = emoji
                                                        showEmojiPicker = false
                                                    },
                                                shape  = RoundedCornerShape(10.dp),
                                                color  = if (chosen)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface,
                                                border = if (chosen) BorderStroke(
                                                    2.dp, MaterialTheme.colorScheme.primary)
                                                else null
                                            ) {
                                                Box(Modifier.padding(10.dp), Alignment.Center) {
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
                }
            }

            // ── TRIP NAME ─────────────────────────────────────────────────────
            item {
                ELabel("Trip Name")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = editName,
                    onValueChange = { editName = it },
                    label         = { Text("Name *") },
                    placeholder   = { Text("e.g. Goa trip, Team offsite") },
                    leadingIcon   = {
                        Text(editEmoji, fontSize = 20.sp,
                            modifier = Modifier.padding(start = 4.dp))
                    },
                    trailingIcon  = {
                        if (editName.isNotBlank())
                            IconButton(onClick = { editName = "" }) {
                                Icon(Icons.Filled.Clear, "Clear")
                            }
                    },
                    singleLine     = true,
                    isError        = editName.isEmpty(),
                    supportingText = {
                        if (editName.isEmpty())
                            Text("Name cannot be empty",
                                color = MaterialTheme.colorScheme.error)
                    },
                    modifier  = Modifier.fillMaxWidth(),
                    enabled   = true,
                    shape     = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            // ── DESCRIPTION ───────────────────────────────────────────────────
            item {
                ELabel("Description")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = editDesc,
                    onValueChange = { editDesc = it },
                    label         = { Text("Description (optional)") },
                    placeholder   = { Text("What is this trip about?") },
                    leadingIcon   = { Icon(Icons.Filled.Notes, null) },
                    modifier      = Modifier.fillMaxWidth(),
                    minLines      = 3,
                    maxLines      = 6,
                    enabled       = true,
                    shape         = RoundedCornerShape(12.dp)
                )
            }

            // ── SAVE BUTTON ───────────────────────────────────────────────────
            item {
                Button(
                    onClick  = ::saveAll,
                    enabled  = nameValid && hasChanges && !isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Saving…")
                    } else {
                        Icon(Icons.Filled.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Changes", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── JOIN CODE (read-only) ─────────────────────────────────────────
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Lock, null,
                            tint     = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Join Code: ${tricountDetails.joinCode}",
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "The trip code is fixed and cannot be changed.",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // ── MEMBERS HEADER ────────────────────────────────────────────────
            item {
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        ELabel("Members")
                        Text(
                            "${members.size} participant${if (members.size == 1) "" else "s"}",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isCreator) {
                        FilledTonalButton(
                            onClick = { showAddDialog = true },
                            shape   = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Filled.PersonAdd, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Member", fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── MEMBER ROWS ───────────────────────────────────────────────────
            items(members, key = { it.userId }) { member ->
                val isMe = member.userId == currentUserId
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape    = CircleShape,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(
                                    member.name.first().uppercase(),
                                    fontSize   = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
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
                                member.email, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Remove button — creator only, not self, not other creator
                        if (isCreator && !member.isCreator && !isMe) {
                            IconButton(
                                onClick  = { memberToRemove = member },
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

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    // ── ADD MEMBER DIALOG ─────────────────────────────────────────────────────
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isAdding) { showAddDialog = false; addEmail = ""; addEmailError = null }
            },
            icon  = { Icon(Icons.Filled.PersonAdd, null,
                tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Add Member") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the email address of the person you want to add. They must already have an account.",
                        fontSize = 14.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value         = addEmail,
                        onValueChange = { addEmail = it; addEmailError = null },
                        label         = { Text("Email address") },
                        placeholder   = { Text("friend@example.com") },
                        leadingIcon   = { Icon(Icons.Filled.Email, null) },
                        trailingIcon  = {
                            if (addEmail.isNotBlank())
                                IconButton(onClick = { addEmail = "" }) {
                                    Icon(Icons.Filled.Clear, "Clear")
                                }
                        },
                        isError        = addEmailError != null,
                        supportingText = addEmailError?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine      = true,
                        modifier        = Modifier.fillMaxWidth(),
                        enabled         = !isAdding,
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
                    enabled  = addEmail.isNotBlank() && !isAdding,
                    onClick  = {
                        val email = addEmail.trim()
                        if (email.isBlank()) { addEmailError = "Please enter an email"; return@Button }
                        val regex = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$".toRegex()
                        if (!regex.matches(email)) { addEmailError = "Invalid email address"; return@Button }
                        isAdding = true
                        viewModel.addMemberByEmail(tricountId, email) { result ->
                            isAdding = false
                            when (result) {
                                is AddMemberResult.Success -> {
                                    Toast.makeText(context,
                                        "${result.memberName} added!", Toast.LENGTH_SHORT).show()
                                    showAddDialog = false; addEmail = ""; addEmailError = null
                                }
                                is AddMemberResult.Error -> { addEmailError = result.message }
                            }
                        }
                    }
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Adding…")
                    } else {
                        Text("Add Member")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick  = { showAddDialog = false; addEmail = ""; addEmailError = null },
                    enabled  = !isAdding
                ) { Text("Cancel") }
            }
        )
    }

    // ── REMOVE MEMBER CONFIRMATION ────────────────────────────────────────────
    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            icon  = { Icon(Icons.Filled.PersonRemove, null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove Member?") },
            text  = {
                Text(
                    "Remove ${member.name} from this trip?\n\nTheir expenses will remain in the records.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeMember(member.userId, tricountId)
                        Toast.makeText(context, "${member.name} removed.", Toast.LENGTH_SHORT).show()
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

// =============================================================================
// Section label
// =============================================================================

@Composable
private fun ELabel(text: String) {
    Text(
        text,
        fontSize   = 14.sp,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.primary,
        modifier   = Modifier.padding(start = 2.dp)
    )
}