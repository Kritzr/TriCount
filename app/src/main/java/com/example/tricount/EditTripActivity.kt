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

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

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
// Pending member change — staged but not yet committed to DB
// =============================================================================

private data class PendingMember(
    val email  : String,
    val name   : String,   // display name derived from email
    val action : PendingAction
)

private enum class PendingAction { ADD, REMOVE }

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
    var editName  by remember { mutableStateOf("") }
    var editDesc  by remember { mutableStateOf("") }
    var editEmoji by remember { mutableStateOf("⛺") }
    var seeded    by remember { mutableStateOf(false) }

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

    // ── Pending member changes (staged, applied only on Save) ─────────────────
    // Key = email (lowercase). Later entries for same email override earlier ones.
    val pendingChanges = remember { mutableStateMapOf<String, PendingMember>() }

    // Effective member list = DB members minus pending removals + pending additions
    val effectiveMembers: List<MemberWithDetails> = remember(members, pendingChanges.toMap()) {
        val removedEmails = pendingChanges.values
            .filter { it.action == PendingAction.REMOVE }
            .map { it.email }
            .toSet()

        val base = members.filter { it.email.lowercase() !in removedEmails }

        val addedEmails = members.map { it.email.lowercase() }.toSet()
        val additions   = pendingChanges.values
            .filter { it.action == PendingAction.ADD && it.email !in addedEmails }
            .map { pending ->
                // Create a preview MemberWithDetails for display only
                MemberWithDetails(
                    userId    = -1,
                    name      = pending.name,
                    email     = pending.email,
                    photoUri  = "",
                    isCreator = false
                )
            }

        base + additions
    }

    var showEmojiPicker by remember { mutableStateOf(false) }
    var isSaving        by remember { mutableStateOf(false) }

    val nameValid = editName.isNotBlank()

    val tripFieldsChanged = tricountDetails != null && (
            editName.trim() != tricountDetails.name ||
                    editDesc.trim() != tricountDetails.description ||
                    editEmoji       != (if (tricountDetails.emoji.isNotBlank()) tricountDetails.emoji else "⛺")
            )
    val hasPendingMemberChanges = pendingChanges.isNotEmpty()
    val hasChanges = tripFieldsChanged || hasPendingMemberChanges

    // ── Add member dialog ─────────────────────────────────────────────────────
    var showAddDialog by remember { mutableStateOf(false) }
    var addEmail      by remember { mutableStateOf("") }
    var addEmailError by remember { mutableStateOf<String?>(null) }

    // ── Remove member confirmation ────────────────────────────────────────────
    var memberToRemove by remember { mutableStateOf<MemberWithDetails?>(null) }

    // ── Save: applies ALL staged changes at once ──────────────────────────────
    fun saveAll() {
        if (!nameValid || isSaving) return
        isSaving = true

        // 1. Save trip fields if changed
        if (tripFieldsChanged) {
            viewModel.editTricountFull(
                tricountId  = tricountId,
                name        = editName.trim(),
                description = editDesc.trim(),
                emoji       = editEmoji
            )
        }

        // 2. Apply member changes in order: removals first, then additions
        val toRemove = pendingChanges.values.filter { it.action == PendingAction.REMOVE }
        val toAdd    = pendingChanges.values.filter { it.action == PendingAction.ADD }

        toRemove.forEach { pending ->
            val member = members.find { it.email.lowercase() == pending.email }
            if (member != null) viewModel.removeMember(member.userId, tricountId)
        }

        var addCount = toAdd.size
        if (addCount == 0) {
            pendingChanges.clear()
            isSaving = false
            Toast.makeText(context, "Trip updated!", Toast.LENGTH_SHORT).show()
            onBackClick()
            return
        }

        toAdd.forEach { pending ->
            viewModel.addMemberByEmail(tricountId, pending.email) { result ->
                addCount--
                if (addCount == 0) {
                    pendingChanges.clear()
                    isSaving = false
                    Toast.makeText(context, "Trip updated!", Toast.LENGTH_SHORT).show()
                    onBackClick()
                }
                if (result is AddMemberResult.Error) {
                    Toast.makeText(context, "Could not add ${pending.email}: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
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

                // Pending changes summary banner
                if (hasPendingMemberChanges) {
                    Spacer(Modifier.height(8.dp))
                    val addCount    = pendingChanges.values.count { it.action == PendingAction.ADD }
                    val removeCount = pendingChanges.values.count { it.action == PendingAction.REMOVE }
                    val parts = buildList {
                        if (addCount    > 0) add("$addCount to add")
                        if (removeCount > 0) add("$removeCount to remove")
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        color    = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Info, null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Unsaved member changes: ${parts.joinToString(", ")}. Tap Save to apply.",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // ── JOIN CODE ─────────────────────────────────────────────────────
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
                            "${effectiveMembers.size} participant${if (effectiveMembers.size == 1) "" else "s"}" +
                                    if (hasPendingMemberChanges) " (unsaved changes)" else "",
                            fontSize = 12.sp,
                            color    = if (hasPendingMemberChanges)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
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
            items(effectiveMembers, key = { "${it.userId}_${it.email}" }) { member ->
                val isMe      = member.userId == currentUserId
                // userId == -1 means this is a pending addition (not yet in DB)
                val isPending = member.userId == -1
                val isRemovedPending = pendingChanges[member.email.lowercase()]?.action == PendingAction.REMOVE

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    color    = when {
                        isPending        -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        isRemovedPending -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        else             -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    border = when {
                        isPending        -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        isRemovedPending -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        else             -> null
                    }
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape    = CircleShape,
                            color    = when {
                                isPending        -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                isRemovedPending -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                else             -> MaterialTheme.colorScheme.primary
                            },
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
                                    color      = when {
                                        isRemovedPending -> MaterialTheme.colorScheme.error
                                        else             -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                when {
                                    member.isCreator -> Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "Creator",
                                            fontSize   = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color      = MaterialTheme.colorScheme.primary,
                                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    isPending -> Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "Pending",
                                            fontSize   = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color      = MaterialTheme.colorScheme.primary,
                                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    isRemovedPending -> Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "Removing",
                                            fontSize   = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color      = MaterialTheme.colorScheme.error,
                                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                member.email, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Action buttons
                        when {
                            // Pending add → undo button
                            isPending -> IconButton(
                                onClick  = { pendingChanges.remove(member.email.lowercase()) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close, "Undo add",
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Pending removal → undo button
                            isRemovedPending -> IconButton(
                                onClick  = { pendingChanges.remove(member.email.lowercase()) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Undo, "Undo remove",
                                    tint     = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Normal member — creator can remove non-creators
                            isCreator && !member.isCreator && !isMe -> IconButton(
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

    // ── ADD MEMBER DIALOG — stages the addition, does NOT call ViewModel yet ──
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false; addEmail = ""; addEmailError = null
            },
            icon  = { Icon(Icons.Filled.PersonAdd, null,
                tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Add Member") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the email address of the person you want to add. The change will be applied when you tap Save.",
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
                    enabled = addEmail.isNotBlank(),
                    onClick = {
                        val email = addEmail.trim().lowercase()
                        // Basic validation
                        val regex = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$".toRegex()
                        if (!regex.matches(email)) {
                            addEmailError = "Invalid email address"
                            return@Button
                        }
                        // Already in effective list?
                        if (effectiveMembers.any { it.email.lowercase() == email }) {
                            addEmailError = "This person is already a member"
                            return@Button
                        }
                        // Stage the addition
                        val displayName = email.substringBefore("@")
                            .replaceFirstChar { it.uppercase() }
                        pendingChanges[email] = PendingMember(
                            email  = email,
                            name   = displayName,
                            action = PendingAction.ADD
                        )
                        showAddDialog = false
                        addEmail      = ""
                        addEmailError = null
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false; addEmail = ""; addEmailError = null
                }) { Text("Cancel") }
            }
        )
    }

    // ── REMOVE MEMBER DIALOG — stages the removal, does NOT call ViewModel yet ─
    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            icon  = { Icon(Icons.Filled.PersonRemove, null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove Member?") },
            text  = {
                Text(
                    "Stage removal of ${member.name} from this trip?\n\nTheir expenses will remain in the records. The change won't be applied until you tap Save.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingChanges[member.email.lowercase()] = PendingMember(
                            email  = member.email.lowercase(),
                            name   = member.name,
                            action = PendingAction.REMOVE
                        )
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Stage Remove") }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text("Cancel") }
            }
        )
    }
}

// Section label
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