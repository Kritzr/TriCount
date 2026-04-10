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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

        // Register back callback using the modern OnBackPressedDispatcher API
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToHome()
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    sessionManager  : SessionManager,
    viewModel       : TricountViewModel,
    onBackClick     : () -> Unit,
    onLogout        : () -> Unit
) {
    val context = LocalContext.current

    // Read initial values from SessionManager (populated from Room on login)
    var photoUrl         by remember { mutableStateOf(sessionManager.getProfilePhotoUri()) }
    var nickname         by remember { mutableStateOf(sessionManager.getNickname()) }
    var nicknameEdit     by remember { mutableStateOf(nickname) }
    var isSaving         by remember { mutableStateOf(false) }
    var uploadStatusMsg  by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val userName  = sessionManager.getUserName()  ?: "User"
    val userEmail = sessionManager.getUserEmail() ?: ""

    // Image picker → upload to Firebase Storage → save https:// URL everywhere
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        uploadStatusMsg = "Uploading photo..."
        isSaving        = true

        viewModel.uploadProfilePhoto(
            uri = uri
        ) { result ->
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
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Profile photo ──────────────────────────────────────────────
            Box(contentAlignment = Alignment.BottomEnd) {
                if (!photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model              = photoUrl,
                        contentDescription = "Profile photo",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { imagePickerLauncher.launch("image/*") }
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text       = userName.first().uppercase(),
                                fontSize   = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                // Camera badge
                Surface(
                    modifier = Modifier
                        .size(34.dp)
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
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Upload progress / status
            if (uploadStatusMsg != null) {
                Row(
                    verticalAlignment      = Alignment.CenterVertically,
                    horizontalArrangement  = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        uploadStatusMsg ?: "",
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                userName,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                userEmail,
                fontSize = 14.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // ── Nickname ───────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier            = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Display Nickname",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Shown to other members inside a Tricount.",
                        fontSize = 12.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value         = nicknameEdit,
                        onValueChange = { nicknameEdit = it },
                        label         = { Text("Nickname") },
                        placeholder   = { Text("e.g. @${userName.lowercase()}") },
                        leadingIcon   = { Icon(Icons.Filled.Person, null) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        enabled       = !isSaving
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = nicknameEdit.trim() != nickname && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Save Nickname")
                    }
                }
            }

            // ── Remove photo ───────────────────────────────────────────────
            if (!photoUrl.isNullOrEmpty()) {
                OutlinedButton(
                    onClick  = {
                        isSaving = true
                        viewModel.savePhotoUri("") {
                            photoUrl = null
                            isSaving = false
                            Toast.makeText(context, "Photo removed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    enabled  = !isSaving
                ) {
                    Icon(Icons.Filled.DeleteForever, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remove Profile Photo")
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            // ── Logout ─────────────────────────────────────────────────────
            Button(
                onClick  = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor   = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Log Out", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon  = { Icon(Icons.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Log Out?") },
            text  = { Text("Are you sure you want to log out?") },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; onLogout() },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Log Out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }
}