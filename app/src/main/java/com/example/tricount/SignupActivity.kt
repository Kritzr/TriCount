package com.example.tricount

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class SignUpActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    // These are captured after the user taps "Create Account" so we can pass
    // them to EmailVerificationActivity once the ViewModel confirms signup.
    private var pendingEmail    = ""
    private var pendingName     = ""
    private var pendingPassword = ""

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        AppTheme.isDark.value = sessionManager.getDarkMode()

        setContent {
            TriCountTheme {
                val authResult by authViewModel.authResult.collectAsStateWithLifecycle()

                LaunchedEffect(authResult) {
                    when (val result = authResult) {

                        // ── Signup succeeded locally in Room — now send the
                        //    Firebase verification email via EmailVerificationActivity.
                        is AuthResult.Success -> {
                            authViewModel.resetAuthResult()
                            navigateToVerification()
                        }

                        // ── The ViewModel already triggered email verification
                        //    (e.g. Firebase account created) — same destination.
                        is AuthResult.VerificationEmailSent -> {
                            authViewModel.resetAuthResult()
                            navigateToVerification()
                        }

                        is AuthResult.Error -> {
                            Toast.makeText(
                                this@SignUpActivity,
                                result.message,
                                Toast.LENGTH_LONG
                            ).show()
                            authViewModel.resetAuthResult()
                        }

                        is AuthResult.AwaitingOtpVerification -> {
                            // Not used for email/password flow — no-op
                            authViewModel.resetAuthResult()
                        }

                        is AuthResult.NeedsEmailVerification -> {
                            authViewModel.resetAuthResult()
                            navigateToVerification()
                        }

                        null -> { /* idle */ }
                    }
                }

                SignUpScreen(
                    onSignUpClick = { name, email, password ->
                        // Stash the values so the LaunchedEffect above can use them
                        pendingName     = name
                        pendingEmail    = email
                        pendingPassword = password

                        // Also save to SessionManager so EmailVerificationActivity
                        // can read them via getPendingSignupName/Email if needed.
                        sessionManager.setPendingSignupName(name)
                        sessionManager.setPendingSignupEmail(email)

                        authViewModel.signUp(name, email, password)
                    },
                    onBackClick = { finish() }
                )
            }
        }
    }

    private fun navigateToVerification() {
        startActivity(
            Intent(this, EmailVerificationActivity::class.java).apply {
                putExtra(EmailVerificationActivity.EXTRA_EMAIL,    pendingEmail)
                putExtra(EmailVerificationActivity.EXTRA_NAME,     pendingName)
                putExtra(EmailVerificationActivity.EXTRA_PASSWORD, pendingPassword)
                putExtra(EmailVerificationActivity.EXTRA_MODE,     EmailVerificationActivity.MODE_SIGNUP)
                // Clear the back stack so the user can't press Back to the signup form
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Email validation
// ─────────────────────────────────────────────────────────────────────────────

private fun isValidEmailSignup(email: String): Boolean =
    "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex().matches(email)

// ─────────────────────────────────────────────────────────────────────────────
// SignUpScreen (Composable — unchanged from your original)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onSignUpClick: (name: String, email: String, password: String) -> Unit,
    onBackClick  : () -> Unit
) {
    var name                   by remember { mutableStateOf("") }
    var email                  by remember { mutableStateOf("") }
    var password               by remember { mutableStateOf("") }
    var confirmPassword        by remember { mutableStateOf("") }
    var passwordVisible        by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val isEmailValid     = remember(email) { email.isBlank() || isValidEmailSignup(email) }
    val showEmailError   = remember(email) { email.isNotBlank() && !isValidEmailSignup(email) }
    val passwordsMatch   = remember(password, confirmPassword) { password == confirmPassword }
    val showPasswordError = remember(confirmPassword, passwordsMatch) {
        confirmPassword.isNotEmpty() && !passwordsMatch
    }

    val focusManager = LocalFocusManager.current
    val canSubmit    = name.isNotBlank() && email.isNotBlank() &&
            password.isNotBlank() && passwordsMatch && isEmailValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Account") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(10.dp))

            Text(
                text       = "Join TriCount",
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text  = "Create your account to start splitting expenses",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            // Name
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Full Name") },
                leadingIcon   = { Icon(Icons.Filled.Person, null) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction    = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(Modifier.height(10.dp))

            // Email
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

            Spacer(Modifier.height(10.dp))

            // Password
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
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(Modifier.height(10.dp))

            // Confirm Password
            OutlinedTextField(
                value         = confirmPassword,
                onValueChange = { confirmPassword = it },
                label         = { Text("Confirm Password") },
                leadingIcon   = { Icon(Icons.Filled.Lock, null) },
                trailingIcon  = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            if (confirmPasswordVisible) Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                isError       = showPasswordError,
                supportingText = {
                    if (showPasswordError)
                        Text("Passwords do not match",
                            color = MaterialTheme.colorScheme.error)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canSubmit) {
                            focusManager.clearFocus()
                            onSignUpClick(name.trim(), email.trim(), password)
                        }
                    }
                )
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick  = {
                    if (canSubmit) onSignUpClick(name.trim(), email.trim(), password)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled  = canSubmit
            ) {
                Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}