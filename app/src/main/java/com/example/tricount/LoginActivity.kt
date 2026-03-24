package com.example.tricount

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.AuthResult
import com.example.tricount.viewModel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    // ── Declare sessionManager inside the class ───────────────────────────────
    private lateinit var sessionManager: SessionManager

    // ── Google Sign-In client ─────────────────────────────────────────────────
    private lateinit var googleSignInClient: GoogleSignInClient

    // ── registerForActivityResult must be inside the class ───────────────────
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken  = account?.idToken
            if (idToken != null) {
                firebaseAuthWithGoogle(idToken)
            } else {
                Toast.makeText(this, "Google sign-in failed: no ID token", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google sign-in failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Authentication failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Initialise sessionManager ─────────────────────────────────────────
        sessionManager = SessionManager(this)
        AppTheme.isDark.value = sessionManager.getDarkMode()

        // ── Build Google Sign-In client ───────────────────────────────────────
        // Replace R.string.default_web_client_id with your actual Web Client ID
        // string from Firebase Console → Authentication → Web SDK config
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // ── Skip login if already signed in ──────────────────────────────────
        if (sessionManager.isLoggedIn() &&
            FirebaseAuth.getInstance().currentUser != null) {
            navigateToHome()
            return
        }

        setContent {
            TriCountTheme() {
                val authResult by authViewModel.authResult.collectAsStateWithLifecycle()

                LaunchedEffect(authResult) {
                    authResult?.let { result ->
                        when (result) {
                            is AuthResult.Success -> {
                                Toast.makeText(
                                    this@LoginActivity, "Welcome back!", Toast.LENGTH_SHORT
                                ).show()
                                authViewModel.resetAuthResult()
                                navigateToHome()
                            }
                            is AuthResult.Error -> {
                                Toast.makeText(
                                    this@LoginActivity, result.message, Toast.LENGTH_LONG
                                ).show()
                                authViewModel.resetAuthResult()
                            }
                        }
                    }
                }

                LoginScreen(
                    onLoginClick = { email, password ->
                        authViewModel.login(email, password)
                    },
                    onSignUpClick = {
                        startActivity(Intent(this@LoginActivity, SignUpActivity::class.java))
                    },
                    onGoogleSignInClick = {
                        val signInIntent = googleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                    }
                )
            }
        }
    }

    // ── Firebase credential exchange — inside the class ───────────────────────
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                val user = authResult.user ?: run {
                    Toast.makeText(this, "Sign-in failed: no user returned", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                // Save session — convert Firebase UID String to Int via hashCode
                sessionManager.saveSession(
                    userId = user.uid.hashCode(),
                    email  = user.email  ?: "",
                    name   = user.displayName ?: ""
                )
                sessionManager.saveFirebaseUid(user.uid)
                Toast.makeText(this, "Welcome, ${user.displayName}!", Toast.LENGTH_SHORT).show()
                navigateToHome()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Auth failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ── Navigate to Home and clear back-stack ─────────────────────────────────
    private fun navigateToHome() {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}

// =============================================================================
// Email validation helper (top-level — fine here)
// =============================================================================

private fun isValidEmail(email: String): Boolean =
    "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex().matches(email)

// =============================================================================
// Login screen composable
// =============================================================================

@Composable
fun LoginScreen(
    onLoginClick       : (String, String) -> Unit,
    onSignUpClick      : () -> Unit,
    onGoogleSignInClick: () -> Unit = {}
) {
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val showEmailError = email.isNotBlank() && !isValidEmail(email)
    val isEmailValid   = email.isBlank()    ||  isValidEmail(email)
    val canSubmit      = email.isNotBlank() && password.isNotBlank() && isEmailValid

    val focusManager = LocalFocusManager.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(80.dp))

            Text(
                "TriCount", fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Split expenses with friends",
                fontSize = 16.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            // ── Email ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value         = email,
                onValueChange = { email = it },
                label         = { Text("Email") },
                leadingIcon   = { Icon(Icons.Filled.Email, null) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                isError       = showEmailError,
                supportingText = {
                    if (showEmailError)
                        Text("Please enter a valid email address",
                            color = MaterialTheme.colorScheme.error)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(Modifier.height(16.dp))

            // ── Password ──────────────────────────────────────────────────────
            OutlinedTextField(
                value         = password,
                onValueChange = { password = it },
                label         = { Text("Password") },
                leadingIcon   = { Icon(Icons.Filled.Lock, null) },
                trailingIcon  = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                modifier        = Modifier.fillMaxWidth(),
                singleLine      = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canSubmit) {
                            focusManager.clearFocus()
                            onLoginClick(email.trim(), password)
                        }
                    }
                )
            )

            Spacer(Modifier.height(24.dp))

            // ── Login button ──────────────────────────────────────────────────
            Button(
                onClick  = { if (canSubmit) onLoginClick(email.trim(), password) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled  = canSubmit
            ) {
                Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(12.dp))

            // ── Divider ───────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(Modifier.weight(1f))
                Text(
                    "  or  ", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            // ── Google Sign-In button ─────────────────────────────────────────
            OutlinedButton(
                onClick  = onGoogleSignInClick,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(
                    Icons.Filled.AccountCircle, null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Continue with Google", fontSize = 15.sp)
            }

            Spacer(Modifier.height(16.dp))

            // ── Sign-up link ──────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Don't have an account?",
                    fontSize = 14.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onSignUpClick) {
                    Text("Sign Up", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}