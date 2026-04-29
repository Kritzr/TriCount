package com.example.tricount

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tricount.data.SessionManager
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.UserEntity
import com.example.tricount.ui.theme.TriCountTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────────────────────────────────────
// Constants passed via Intent extras
// ─────────────────────────────────────────────────────────────────────────────
// Required always:
//   EXTRA_EMAIL    — the user's email address
//   EXTRA_MODE     — "signup" or "login"
//
// Required for signup mode only:
//   EXTRA_NAME     — display name chosen at signup
//   EXTRA_PASSWORD — password chosen at signup (used to sign in after verify)
// ─────────────────────────────────────────────────────────────────────────────

private const val RESEND_COOLDOWN_MS = 60_000L  // 60 s

class EmailVerificationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EMAIL    = "extra_email"
        const val EXTRA_NAME     = "extra_name"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_MODE     = "extra_mode"   // "signup" | "login"

        const val MODE_SIGNUP = "signup"
        const val MODE_LOGIN  = "login"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val email    = intent.getStringExtra(EXTRA_EMAIL)    ?: ""
        val name     = intent.getStringExtra(EXTRA_NAME)     ?: ""
        val password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
        val mode     = intent.getStringExtra(EXTRA_MODE)     ?: MODE_SIGNUP

        val sessionManager = SessionManager(this)
        val db             = TricountDatabase.getDatabase(this)

        setContent {
            val isDarkMode = remember { mutableStateOf(sessionManager.getDarkMode()) }
            TriCountTheme(darkTheme = isDarkMode.value) {
                EmailVerificationScreen(
                    email          = email,
                    name           = name,
                    password       = password,
                    mode           = mode,
                    sessionManager = sessionManager,
                    db             = db,
                    onVerified     = {
                        // Navigate to home, clearing the back stack so the user
                        // can't press Back to return to the verification screen.
                        startActivity(
                            Intent(this, HomeActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                        finish()
                    },
                    onBackToLogin  = {
                        startActivity(Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EmailVerificationScreen(
    email          : String,
    name           : String,
    password       : String,
    mode           : String,
    sessionManager : SessionManager,
    db             : TricountDatabase,
    onVerified     : () -> Unit,
    onBackToLogin  : () -> Unit
) {
    val auth      = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val scope     = rememberCoroutineScope()

    // ── UI state ──────────────────────────────────────────────────────────────
    var uiState        by remember { mutableStateOf<VerificationUiState>(VerificationUiState.Waiting) }
    var resendCooldown by remember { mutableStateOf(0) }   // seconds remaining
    var isChecking     by remember { mutableStateOf(false) }

    // ── Pulse animation for the envelope icon ─────────────────────────────────
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue   = 1f,
        targetValue    = 1.08f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // ── On first composition ──────────────────────────────────────────────────
    // FIX: Do NOT call sendVerificationEmail() here for either mode.
    // AuthViewModel.signUp() and AuthViewModel.login() already send the
    // verification email before navigating to this screen. Sending it again
    // immediately triggers Firebase's abuse/rate-limit protection, which shows
    // "We have blocked all requests from this device due to unusual activity."
    // The Resend button below lets the user request another email if needed.
    LaunchedEffect(Unit) {
        uiState = VerificationUiState.Waiting
        startResendCooldown(RESEND_COOLDOWN_MS) { remaining ->
            resendCooldown = remaining
        }
    }

    // ── Background gradient ───────────────────────────────────────────────────
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surface
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .navigationBarsPadding()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Icon ─────────────────────────────────────────────────────────
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                },
                label = "iconAnim"
            ) { state ->
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(if (state is VerificationUiState.Waiting) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            when (state) {
                                is VerificationUiState.Verified    ->
                                    MaterialTheme.colorScheme.primaryContainer
                                is VerificationUiState.SendError,
                                is VerificationUiState.CheckError  ->
                                    MaterialTheme.colorScheme.errorContainer
                                else ->
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (state) {
                            is VerificationUiState.Verified   -> Icons.Filled.CheckCircle
                            is VerificationUiState.SendError,
                            is VerificationUiState.CheckError -> Icons.Filled.ErrorOutline
                            else                              -> Icons.Filled.Email
                        },
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = when (state) {
                            is VerificationUiState.Verified   -> MaterialTheme.colorScheme.primary
                            is VerificationUiState.SendError,
                            is VerificationUiState.CheckError -> MaterialTheme.colorScheme.error
                            else                              -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Title ────────────────────────────────────────────────────────
            AnimatedContent(
                targetState = uiState,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "titleAnim"
            ) { state ->
                Text(
                    text = when (state) {
                        is VerificationUiState.Verified   -> "Email Verified!"
                        is VerificationUiState.SendError  -> "Couldn't Send Email"
                        is VerificationUiState.CheckError -> "Verification Failed"
                        else                              -> "Check Your Email"
                    },
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    textAlign  = TextAlign.Center
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Subtitle ─────────────────────────────────────────────────────
            AnimatedContent(
                targetState = uiState,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "subtitleAnim"
            ) { state ->
                Text(
                    text = when (state) {
                        is VerificationUiState.Verified ->
                            "You're all set! Continuing to TriCount…"
                        is VerificationUiState.SendError ->
                            state.message
                        is VerificationUiState.CheckError ->
                            state.message
                        else ->
                            "We sent a verification link to\n$email\n\nTap the link in the email, then come back and press \"I've Verified\" below."
                    },
                    fontSize   = 15.sp,
                    color      = when (state) {
                        is VerificationUiState.SendError,
                        is VerificationUiState.CheckError ->
                            MaterialTheme.colorScheme.error
                        else ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign  = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            Spacer(Modifier.height(40.dp))

            // ── "I've Verified" button ────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState !is VerificationUiState.Verified,
                enter   = fadeIn() + slideInVertically { it / 2 },
                exit    = fadeOut()
            ) {
                Button(
                    onClick = {
                        if (!isChecking) {
                            isChecking = true
                            scope.launch {
                                checkVerificationAndProceed(
                                    auth           = auth,
                                    firestore      = firestore,
                                    email          = email,
                                    name           = name,
                                    password       = password,
                                    mode           = mode,
                                    sessionManager = sessionManager,
                                    db             = db,
                                    onVerified     = {
                                        uiState    = VerificationUiState.Verified
                                        // Brief delay so user sees the "Verified!" state
                                        scope.launch {
                                            delay(1200)
                                            onVerified()
                                        }
                                    },
                                    onNotYet       = { msg ->
                                        uiState    = VerificationUiState.CheckError(msg)
                                        isChecking = false
                                    }
                                )
                            }
                        }
                    },
                    enabled  = !isChecking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape  = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier  = Modifier.size(22.dp),
                            color     = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            "I've Verified My Email",
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Resend button ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState !is VerificationUiState.Verified,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                OutlinedButton(
                    onClick = {
                        if (resendCooldown == 0) {
                            uiState = VerificationUiState.Waiting
                            scope.launch {
                                sendVerificationEmail(
                                    auth      = auth,
                                    firestore = firestore,
                                    email     = email,
                                    name      = name,
                                    password  = password,
                                    mode      = mode,
                                    onResult  = { success, error ->
                                        uiState = if (success) VerificationUiState.Waiting
                                        else         VerificationUiState.SendError(error ?: "Failed to resend")
                                    }
                                )
                            }
                            startResendCooldown(RESEND_COOLDOWN_MS) { remaining ->
                                resendCooldown = remaining
                            }
                        }
                    },
                    enabled  = resendCooldown == 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape  = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (resendCooldown > 0)
                            "Resend Email (${resendCooldown}s)"
                        else
                            "Resend Verification Email",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Back to login ────────────────────────────────────────────────
            TextButton(onClick = {
                // Sign out of any partially-created Firebase session before
                // sending the user back to the login screen.
                FirebaseAuth.getInstance().signOut()
                onBackToLogin()
            }) {
                Text(
                    "Back to Login",
                    fontSize = 14.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────

private sealed class VerificationUiState {
    object Waiting                            : VerificationUiState()
    object Verified                           : VerificationUiState()
    data class SendError(val message: String) : VerificationUiState()
    data class CheckError(val message: String): VerificationUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
// Logic helpers (suspend funs — called from coroutineScope)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sends Firebase's built-in verification email.
 * Only called by the Resend button — NOT on screen launch.
 *
 * SIGNUP mode:
 *   1. Creates the Firebase Auth account (email + password) if not already done.
 *   2. Sets the display name via UserProfileChangeRequest.
 *   3. Sends the verification email.
 *
 * LOGIN mode:
 *   1. Signs in with the existing account.
 *   2. Re-sends the verification email.
 */
private suspend fun sendVerificationEmail(
    auth     : FirebaseAuth,
    firestore: FirebaseFirestore,
    email    : String,
    name     : String,
    password : String,
    mode     : String,
    onResult : (success: Boolean, error: String?) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val user = when (mode) {
            EmailVerificationActivity.MODE_SIGNUP -> {
                // Create the account if it doesn't exist yet
                val existing = try {
                    auth.signInWithEmailAndPassword(email, password).await().user
                } catch (e: Exception) { null }

                val authUser = existing
                    ?: auth.createUserWithEmailAndPassword(email, password).await().user
                    ?: run { onResult(false, "Could not create account"); return@withContext }

                // Set display name in Firebase Auth profile
                authUser.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(name).build()
                ).await()

                authUser
            }
            else -> { // LOGIN mode — account already exists
                auth.signInWithEmailAndPassword(email, password).await().user
                    ?: run { onResult(false, "Sign-in failed"); return@withContext }
            }
        }

        if (user.isEmailVerified) {
            // Already verified — caller will handle this by going straight to Home
            onResult(true, null)
            return@withContext
        }

        user.sendEmailVerification().await()
        onResult(true, null)

    } catch (e: Exception) {
        val friendly = when {
            e.message?.contains("email address is already in use") == true ->
                "This email is already registered. Please log in instead."
            e.message?.contains("badly formatted") == true ->
                "Invalid email address."
            e.message?.contains("network") == true ->
                "No internet connection. Please try again."
            else ->
                e.message ?: "Something went wrong. Please try again."
        }
        onResult(false, friendly)
    }
}

/**
 * Reloads the Firebase user to get the latest emailVerified flag,
 * then (if verified) completes the local Room + Firestore setup.
 */
private suspend fun checkVerificationAndProceed(
    auth          : FirebaseAuth,
    firestore     : FirebaseFirestore,
    email         : String,
    name          : String,
    password      : String,
    mode          : String,
    sessionManager: SessionManager,
    db            : TricountDatabase,
    onVerified    : () -> Unit,
    onNotYet      : (String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        // Make sure we're signed in
        val user = auth.currentUser
            ?: auth.signInWithEmailAndPassword(email, password).await().user
            ?: run { onNotYet("Could not sign in. Please try again."); return@withContext }

        // Force-reload so emailVerified reflects the server state
        user.reload().await()

        if (!user.isEmailVerified) {
            onNotYet("Your email hasn't been verified yet.\nPlease click the link we sent to $email")
            return@withContext
        }

        // ── Email is verified — complete local setup ──────────────────────

        val firebaseUid = user.uid

        when (mode) {
            EmailVerificationActivity.MODE_SIGNUP -> {
                // Insert (or update) the Room user record
                val userDao      = db.userDao()
                val existingUser = userDao.getUserByEmail(email)

                val localUserId: Int
                if (existingUser != null) {
                    userDao.updateFirebaseUid(existingUser.id, firebaseUid)
                    localUserId = existingUser.id
                } else {
                    val entity = UserEntity(
                        name        = name,
                        email       = email,
                        password    = password,   // already hashed if you hash on signup
                        firebaseUid = firebaseUid
                    )
                    localUserId = userDao.insertUser(entity).toInt()
                }

                // Save session (synchronous commit — see SessionManager)
                sessionManager.saveSession(
                    userId      = localUserId,
                    name        = name,
                    email       = email,
                    firebaseUid = firebaseUid
                )
                sessionManager.clearPendingSignup()

                // Write Firestore profile document
                val profileData = mapOf(
                    "name"          to name,
                    "email"         to email,
                    "nickname"      to "",
                    "photoUrl"      to "",
                    "emailVerified" to true,
                    "createdAt"     to System.currentTimeMillis()
                )
                firestore.collection("users")
                    .document(firebaseUid)
                    .set(profileData, SetOptions.merge())
                    .await()
            }

            EmailVerificationActivity.MODE_LOGIN -> {
                // Find the Room record by email and update the Firebase UID
                val userDao      = db.userDao()
                val existingUser = userDao.getUserByEmail(email)
                    ?: run { onNotYet("Account not found locally. Please sign up."); return@withContext }

                userDao.updateFirebaseUid(existingUser.id, firebaseUid)

                sessionManager.saveSession(
                    userId      = existingUser.id,
                    name        = existingUser.name,
                    email       = email,
                    firebaseUid = firebaseUid
                )

                // Mark email as verified in Firestore
                firestore.collection("users")
                    .document(firebaseUid)
                    .set(mapOf("emailVerified" to true), SetOptions.merge())
                    .await()
            }
        }

        withContext(Dispatchers.Main) { onVerified() }

    } catch (e: Exception) {
        val friendly = when {
            e.message?.contains("network") == true ->
                "No internet connection. Please check your connection and try again."
            else ->
                "Verification check failed: ${e.message}"
        }
        withContext(Dispatchers.Main) { onNotYet(friendly) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Countdown helper — updates the resend button cooldown every second
// ─────────────────────────────────────────────────────────────────────────────

private fun startResendCooldown(
    durationMs : Long,
    onTick     : (secondsRemaining: Int) -> Unit
) {
    object : CountDownTimer(durationMs, 1_000) {
        override fun onTick(millisUntilFinished: Long) {
            onTick((millisUntilFinished / 1000).toInt())
        }
        override fun onFinish() { onTick(0) }
    }.start()
}