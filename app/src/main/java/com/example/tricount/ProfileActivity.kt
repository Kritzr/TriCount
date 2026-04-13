package com.example.tricount

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tricount.data.SessionManager
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.TricountViewModel

class ProfileActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { navigateToHome() }
        })

        setContent {
            TriCountTheme(darkTheme = sessionManager.getDarkMode()) {
                ProfileScreen(
                    sessionManager = sessionManager,
                    viewModel      = viewModel,
                    onBackClick    = { navigateToHome() },
                    onLogout       = {
                        sessionManager.clearSession()
                        startActivity(
                            Intent(this, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun navigateToHome() {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable settings-style row — matches the Dark Mode row look exactly
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsInfoRow(
    icon        : ImageVector,
    label       : String,
    value       : String,
    showDivider : Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon bubble — same style as the sun icon in Dark Mode row
            Surface(
                shape    = CircleShape,
                color    = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = label,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text     = value,
                    fontSize = 13.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier  = Modifier.padding(start = 72.dp),
                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    sessionManager : SessionManager,
    viewModel      : TricountViewModel,
    onBackClick    : () -> Unit,
    onLogout       : () -> Unit
) {
    val context = LocalContext.current

    var photoUrl         by remember { mutableStateOf(sessionManager.getProfilePhotoUri()) }
    var nickname         by remember { mutableStateOf(sessionManager.getNickname()) }
    var nicknameEdit     by remember { mutableStateOf(nickname) }
    var isSaving         by remember { mutableStateOf(false) }
    var uploadStatusMsg  by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val userName  = sessionManager.getUserName()  ?: "User"
    val userEmail = sessionManager.getUserEmail() ?: ""

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploadStatusMsg = "Uploading photo…"
        isSaving        = true
        viewModel.uploadProfilePhoto(uri = uri) { result ->
            isSaving = false
            if (result != null) {
                photoUrl        = result
                uploadStatusMsg = null
                Toast.makeText(context, "Photo saved!", Toast.LENGTH_SHORT).show()
            } else {
                uploadStatusMsg = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Hero section — equal padding above and below the avatar ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 36.dp),   // same value top & bottom = equal spacing
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (!photoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model              = photoUrl,
                            contentDescription = "Profile photo",
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .border(
                                    3.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    CircleShape
                                )
                                .clickable { imagePickerLauncher.launch("image/*") }
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text       = userName.first().uppercase(),
                                    fontSize   = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    // Camera badge
                    Surface(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = "Change photo",
                                tint     = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Text(
                    text       = userName,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )

                // Upload status pill
                if (uploadStatusMsg != null) {
                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(50)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                        Text(uploadStatusMsg ?: "", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Account info card: email + nickname as settings rows ───────
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column {
                    SettingsInfoRow(
                        icon        = Icons.Filled.Email,
                        label       = "Email",
                        value       = userEmail,
                        showDivider = !nickname.isNullOrEmpty()
                    )
                    if (!nickname.isNullOrEmpty()) {
                        SettingsInfoRow(
                            icon        = Icons.Filled.AlternateEmail,
                            label       = "Nickname",
                            value       = nickname ?: "",
                            showDivider = false
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Nickname edit card ─────────────────────────────────────────
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Edit Nickname",
                            fontSize      = 13.sp,
                            fontWeight    = FontWeight.SemiBold,
                            color         = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.6.sp
                        )
                    }
                    Text(
                        "Shown to other members inside a Tricount.",
                        fontSize   = 12.sp,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value         = nicknameEdit,
                        onValueChange = { nicknameEdit = it },
                        label         = { Text("Nickname") },
                        placeholder   = { Text("e.g. @${userName.lowercase()}") },
                        leadingIcon   = { Icon(Icons.Filled.AlternateEmail, null) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        enabled       = !isSaving,
                        shape         = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick  = {
                            isSaving = true
                            viewModel.saveNickname(nicknameEdit.trim()) {
                                nickname = nicknameEdit.trim()
                                isSaving = false
                                Toast.makeText(context, "Nickname saved!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        enabled  = nicknameEdit.trim() != nickname && !isSaving,
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(15.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Save Nickname", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Danger actions card (remove photo + log out) ───────────────
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column {
                    if (!photoUrl.isNullOrEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSaving) {
                                    isSaving = true
                                    viewModel.savePhotoUri("") {
                                        photoUrl = null
                                        isSaving = false
                                        Toast.makeText(context, "Photo removed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape    = CircleShape,
                                color    = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.NoPhotography,
                                        contentDescription = null,
                                        tint     = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Remove Photo",
                                    fontSize   = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "Resets to initial letter avatar",
                                    fontSize = 13.sp,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier  = Modifier.padding(start = 72.dp),
                                 color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )
                    }

                    // Log Out row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLogoutDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape    = CircleShape,
                            color    = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Logout,
                                    contentDescription = null,
                                    tint     = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Log Out",
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color      = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "You'll be signed out of this device",
                                fontSize = 13.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Logout confirmation dialog ─────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            shape = RoundedCornerShape(20.dp),
            icon  = {
                Surface(
                    shape    = CircleShape,
                    color    = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Logout,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    "Log out of TriCount?",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                )
            },
            text = {
                Text(
                    "You'll be signed out of this device. Your data will remain safe and you can log back in at any time.",
                    fontSize   = 14.sp,
                    lineHeight = 20.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick  = { showLogoutDialog = false; onLogout() },
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Yes, Log Out", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick  = { showLogoutDialog = false },
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}