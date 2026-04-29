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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var sessionManager: SessionManager
    private lateinit var googleSignInClient: GoogleSignInClient

    private val isGoogleLoading = mutableStateOf(false)

    // Stash the email + password typed by the user so we can forward them to
    // EmailVerificationActivity when the account exists but isn't verified yet.
    private var pendingEmail    = ""
    private var pendingPassword = ""

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_CANCELED) {
            isGoogleLoading.value = false
            Toast.makeText(this, "Sign-in cancelled", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                authViewModel.handleGoogleSignIn(idToken)
            } else {
                isGoogleLoading.value = false
                Toast.makeText(
                    this,
                    "Google Sign-In failed: ID Token is null. Check SHA-1 in Firebase.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: ApiException) {
            isGoogleLoading.value = false
            val message = when (e.statusCode) {
                10    -> "Developer error: Check SHA-1 fingerprint and Web Client ID in Firebase."
                7     -> "Network error: Check your internet connection."
                12500 -> "Google Play Services needs an update."
                else  -> "Google sign-in failed (code ${e.statusCode})"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            isGoogleLoading.value = false
            Toast.makeText(this, "Authentication failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        AppTheme.isDark.value = sessionManager.getDarkMode()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Skip login if already signed in and verified
        if (sessionManager.isLoggedIn() && FirebaseAuth.getInstance().currentUser != null) {
            navigateToHome()
            return
        }

        setContent {
            TriCountTheme {
                val authResult    by authViewModel.authResult.collectAsStateWithLifecycle()
                val googleLoading by isGoogleLoading

                LaunchedEffect(authResult) {
                    authResult?.let { result ->
                        isGoogleLoading.value = false
                        when (result) {

                            is AuthResult.Success -> {
                                Toast.makeText(
                                    this@LoginActivity,
                                    "Welcome!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                authViewModel.resetAuthResult()
                                navigateToHome()
                            }

                            // ── Email exists but is not verified — send user to
                            //    the verification screen in LOGIN mode so they can
                            //    resend the link and confirm without re-entering
                            //    their password.
                            is AuthResult.NeedsEmailVerification -> {
                                authViewModel.resetAuthResult()
                                navigateToVerification()
                            }

                            // ── VerificationEmailSent is also treated the same way:
                            //    go to the verification screen.
                            is AuthResult.VerificationEmailSent -> {
                                authViewModel.resetAuthResult()
                                navigateToVerification()
                            }

                            is AuthResult.Error -> {
                                Toast.makeText(
                                    this@LoginActivity,
                                    result.message,
                                    Toast.LENGTH_LONG
                                ).show()
                                authViewModel.resetAuthResult()
                            }

                            is AuthResult.AwaitingOtpVerification -> {
                                // Not used for email/password flow — no-op
                                authViewModel.resetAuthResult()
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LoginScreen(
                        onLoginClick = { email, password ->
                            // Stash so navigateToVerification() can forward them
                            pendingEmail    = email
                            pendingPassword = password
                            authViewModel.login(email, password)
                        },
                        onSignUpClick = {
                            startActivity(Intent(this@LoginActivity, SignUpActivity::class.java))
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        },
                        onGoogleSignInClick = {
                            isGoogleLoading.value = true
                            googleSignInClient.signOut().addOnCompleteListener {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        }
                    )

                    // Full-screen loading overlay shown during Google sign-in
                    if (googleLoading) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color    = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                        ) {
                            Column(
                                modifier            = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(56.dp),
                                    strokeWidth = 4.dp,
                                    color       = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(20.dp))
                                Text(
                                    text       = "Signing in with Google…",
                                    fontSize   = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun navigateToHome() {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    private fun navigateToVerification() {
        startActivity(
            Intent(this, EmailVerificationActivity::class.java).apply {
                putExtra(EmailVerificationActivity.EXTRA_EMAIL,    pendingEmail)
                putExtra(EmailVerificationActivity.EXTRA_PASSWORD, pendingPassword)
                putExtra(EmailVerificationActivity.EXTRA_MODE,     EmailVerificationActivity.MODE_LOGIN)
            }
        )
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        // Do NOT finish() here — the user can press "Back to Login" in the
        // verification screen to come back.
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Email validation
// ─────────────────────────────────────────────────────────────────────────────

private fun isValidEmail(email: String): Boolean =
    "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex().matches(email)

// ─────────────────────────────────────────────────────────────────────────────
// LoginScreen (Composable — unchanged from your original)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LoginScreen(
    onLoginClick        : (String, String) -> Unit,
    onSignUpClick       : () -> Unit,
    onGoogleSignInClick : () -> Unit = {}
) {
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val showEmailError = email.isNotBlank() && !isValidEmail(email)
    val isEmailValid   = email.isBlank() || isValidEmail(email)
    val canSubmit      = email.isNotBlank() && password.isNotBlank() && isEmailValid

    val focusManager = LocalFocusManager.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(80.dp))

            Text(
                "TriCount",
                fontSize   = 40.sp,
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

            OutlinedTextField(
                value          = email,
                onValueChange  = { email = it },
                label          = { Text("Email") },
                leadingIcon    = { Icon(Icons.Filled.Email, contentDescription = null) },
                modifier       = Modifier.fillMaxWidth(),
                singleLine     = true,
                isError        = showEmailError,
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

            OutlinedTextField(
                value         = password,
                onValueChange = { password = it },
                label         = { Text("Password") },
                leadingIcon   = { Icon(Icons.Filled.Lock, contentDescription = null) },
                trailingIcon  = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector        = if (passwordVisible) Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password"
                            else "Show password"
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

            Button(
                onClick  = { if (canSubmit) onLoginClick(email.trim(), password) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled  = canSubmit
            ) {
                Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(Modifier.weight(1f))
                Text("  or  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick  = onGoogleSignInClick,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Continue with Google", fontSize = 15.sp)
            }

            Spacer(Modifier.height(16.dp))

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