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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.AuthResult
import com.example.tricount.viewModel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class LoginActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    // ── Google Sign-In launcher ───────────────────────────────────────────────
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task    = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken

            if (idToken != null) {
                // Hand the token to AuthViewModel — it handles Firebase auth + Room user creation
                authViewModel.handleGoogleSignIn(idToken)
            } else {
                Toast.makeText(this, "Google sign-in failed: no ID token", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google sign-in cancelled or failed: ${e.statusCode}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already logged in (session still valid), skip login screen
        val sessionManager = SessionManager(this)
        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        AppTheme.isDark.value = sessionManager.getDarkMode()

        setContent {
            TriCountTheme {
                val authResult by authViewModel.authResult.collectAsStateWithLifecycle()

                // Navigate on success, toast on error
                LaunchedEffect(authResult) {
                    when (authResult) {
                        is AuthResult.Success -> {
                            authViewModel.resetAuthResult()
                            startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                            finish()
                        }
                        is AuthResult.Error -> {
                            Toast.makeText(
                                this@LoginActivity,
                                (authResult as AuthResult.Error).message,
                                Toast.LENGTH_LONG
                            ).show()
                            authViewModel.resetAuthResult()
                        }
                        null -> { /* idle */ }
                    }
                }

                LoginScreen(
                    onLoginClick     = { email, password -> authViewModel.login(email, password) },
                    onSignUpClick    = {
                        startActivity(Intent(this@LoginActivity, SignUpActivity::class.java))
                    },
                    onGoogleSignIn   = { launchGoogleSignIn() }
                )
            }
        }
    }

    // ── Launch the Google account picker ──────────────────────────────────────
    private fun launchGoogleSignIn() {
        // Replace R.string.default_web_client_id with your actual Web Client ID string
        // if you haven't set up google-services.json yet, or use the literal string:
        // "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val client = GoogleSignIn.getClient(this, gso)

        // Always show account picker (sign out first to clear cached selection)
        client.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(client.signInIntent)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOGIN SCREEN UI
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginClick   : (email: String, password: String) -> Unit,
    onSignUpClick  : () -> Unit,
    onGoogleSignIn : () -> Unit
) {
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager    = LocalFocusManager.current

    val canLogin = email.isNotBlank() && password.isNotBlank()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Text(
                "Welcome to TriCount",
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Sign in to manage your shared expenses",
                fontSize = 14.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            // ── Email ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value           = email,
                onValueChange   = { email = it },
                label           = { Text("Email") },
                leadingIcon     = { Icon(Icons.Filled.Email, null) },
                modifier        = Modifier.fillMaxWidth(),
                singleLine      = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(Modifier.height(12.dp))

            // ── Password ──────────────────────────────────────────────────────
            OutlinedTextField(
                value                = password,
                onValueChange        = { password = it },
                label                = { Text("Password") },
                leadingIcon          = { Icon(Icons.Filled.Lock, null) },
                trailingIcon         = {
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
                modifier             = Modifier.fillMaxWidth(),
                singleLine           = true,
                keyboardOptions      = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (canLogin) onLoginClick(email.trim(), password)
                    }
                )
            )

            Spacer(Modifier.height(24.dp))

            // ── Login button ──────────────────────────────────────────────────
            Button(
                onClick  = { if (canLogin) onLoginClick(email.trim(), password) },
                enabled  = canLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Log In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))

            // ── Divider ───────────────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "  OR  ",
                    fontSize = 13.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            // ── Google Sign-In button ─────────────────────────────────────────
            OutlinedButton(
                onClick  = onGoogleSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Continue with Google",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Sign-up link ──────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Don't have an account? ",
                    fontSize = 14.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onSignUpClick) {
                    Text("Sign Up", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}