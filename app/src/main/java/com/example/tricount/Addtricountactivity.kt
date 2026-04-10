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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tricount.data.SessionManager
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.TricountViewModel

// =============================================================================
// Activity
// =============================================================================

class AddTricountActivity : ComponentActivity() {

    private val tricountViewModel: TricountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        AppTheme.isDark.value = sessionManager.getDarkMode()

        setContent {
            TriCountTheme() {
                AddTricountScreen(
                    onBackClick = { finish() },
                    onSaveClick = { name, description, emoji, memberEmails ->
                        tricountViewModel.insertTricount(
                            name         = name,
                            description  = description,
                            emoji        = emoji,
                            memberEmails = memberEmails
                        ) { _ ->
                            finish()
                        }
                    }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
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
fun AddTricountScreen(
    onBackClick : () -> Unit,
    onSaveClick : (name: String, description: String, emoji: String, memberEmails: List<String>) -> Unit
) {
    // ── Form state ────────────────────────────────────────────────────────────
    var tripName    by remember { mutableStateOf("") }
    var tripDesc    by remember { mutableStateOf("") }
    var tripEmoji   by remember { mutableStateOf("⛺") }
    var showPicker  by remember { mutableStateOf(false) }

    // ── Members state ─────────────────────────────────────────────────────────
    // List of email strings the user has typed (pending — not yet in DB)
    val pendingEmails = remember { mutableStateListOf<String>() }
    var emailInput    by remember { mutableStateOf("") }
    var emailError    by remember { mutableStateOf<String?>(null) }

    val nameValid  = tripName.isNotBlank()
    val emailRegex = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}\$".toRegex()

    fun addEmail() {
        val e = emailInput.trim().lowercase()
        when {
            e.isBlank()              -> emailError = "Please enter an email"
            !emailRegex.matches(e)   -> emailError = "Invalid email address"
            pendingEmails.contains(e)-> emailError = "Already added"
            else -> {
                pendingEmails.add(e)
                emailInput = ""
                emailError = null
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("New Trip", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick  = {
                            if (nameValid) {
                                onSaveClick(
                                    tripName.trim(),
                                    tripDesc.trim(),
                                    tripEmoji,
                                    pendingEmails.toList()
                                )
                            }
                        },
                        enabled  = nameValid
                    ) {
                        Text(
                            "Create",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            color      = if (nameValid)
                                MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
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
            modifier            = Modifier.padding(padding).fillMaxSize(),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── TRIP ICON ─────────────────────────────────────────────────────
            item {
                SLabel("Trip Icon")
                Spacer(Modifier.height(10.dp))

                // Tappable icon preview
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    Surface(
                        shape    = CircleShape,
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(88.dp)
                            .clickable { showPicker = !showPicker }
                    ) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(tripEmoji, fontSize = 44.sp)
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
                    if (showPicker) "Tap an icon to select · tap circle to close"
                    else "Tap the icon to choose",
                    fontSize  = 12.sp,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier  = Modifier.fillMaxWidth()
                )

                // Animated emoji grid
                AnimatedVisibility(
                    visible = showPicker,
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
                                            val chosen = emoji == tripEmoji
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        tripEmoji  = emoji
                                                        showPicker = false
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
                SLabel("Trip Name *")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = tripName,
                    onValueChange = { tripName = it },
                    label         = { Text("Name *") },
                    placeholder   = { Text("e.g. Goa trip, Team offsite") },
                    leadingIcon   = {
                        Text(tripEmoji, fontSize = 20.sp,
                            modifier = Modifier.padding(start = 4.dp))
                    },
                    trailingIcon  = {
                        if (tripName.isNotBlank())
                            IconButton(onClick = { tripName = "" }) {
                                Icon(Icons.Filled.Clear, "Clear")
                            }
                    },
                    singleLine     = true,
                    isError        = tripName.isEmpty(),
                    supportingText = {
                        if (tripName.isEmpty())
                            Text("Name is required",
                                color = MaterialTheme.colorScheme.error)
                    },
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            // ── DESCRIPTION ───────────────────────────────────────────────────
            item {
                SLabel("Description")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = tripDesc,
                    onValueChange = { tripDesc = it },
                    label         = { Text("Description (optional)") },
                    placeholder   = { Text("What is this trip about?") },
                    leadingIcon   = { Icon(Icons.Filled.Notes, null) },
                    modifier      = Modifier.fillMaxWidth(),
                    minLines      = 3,
                    maxLines      = 6,
                    shape         = RoundedCornerShape(12.dp)
                )
            }

            // ── MEMBERS ───────────────────────────────────────────────────────
            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Bottom
                ) {
                    Column {
                        SLabel("Invite Members")
                        Text(
                            "Add people by email — they'll be added when the trip is created",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Email input row
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.Top
                ) {
                    OutlinedTextField(
                        value         = emailInput,
                        onValueChange = { emailInput = it; emailError = null },
                        label         = { Text("Email address") },
                        placeholder   = { Text("friend@example.com") },
                        leadingIcon   = { Icon(Icons.Filled.Email, null) },
                        trailingIcon  = {
                            if (emailInput.isNotBlank())
                                IconButton(onClick = { emailInput = ""; emailError = null }) {
                                    Icon(Icons.Filled.Clear, "Clear")
                                }
                        },
                        isError        = emailError != null,
                        supportingText = emailError?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine      = true,
                        modifier        = Modifier.weight(1f),
                        shape           = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction    = ImeAction.Done
                        )
                    )
                    FilledTonalButton(
                        onClick  = ::addEmail,
                        enabled  = emailInput.isNotBlank(),
                        shape    = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Filled.PersonAdd, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            // Pending member chips
            if (pendingEmails.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            Text(
                                "${pendingEmails.size} member${if (pendingEmails.size == 1) "" else "s"} to invite",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.primary,
                                modifier   = Modifier.padding(bottom = 8.dp)
                            )
                            pendingEmails.forEachIndexed { index, email ->
                                Row(
                                    modifier          = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar
                                    Surface(
                                        shape    = CircleShape,
                                        color    = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                                            Text(
                                                email.first().uppercase(),
                                                fontSize   = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color      = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        email,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                        color    = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick  = { pendingEmails.removeAt(index) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close, "Remove",
                                            tint     = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (index < pendingEmails.lastIndex) {
                                    HorizontalDivider(
                                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── CREATE BUTTON (also at bottom) ────────────────────────────────
            item {
                Button(
                    onClick  = {
                        if (nameValid) {
                            onSaveClick(
                                tripName.trim(),
                                tripDesc.trim(),
                                tripEmoji,
                                pendingEmails.toList()
                            )
                        }
                    },
                    enabled  = nameValid,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildString {
                            append("Create Trip")
                            if (pendingEmails.isNotEmpty())
                                append(" · invite ${pendingEmails.size}")
                        },
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "You can always add more members later from the trip settings",
                    fontSize  = 12.sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// =============================================================================
// Section label helper
// =============================================================================

@Composable
private fun SLabel(text: String) {
    Text(
        text,
        fontSize   = 14.sp,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.primary,
        modifier   = Modifier.padding(start = 2.dp)
    )
}