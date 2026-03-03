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
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.AuthResult
import com.example.tricount.viewModel.AuthViewModel

class LoginActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TriCountTheme(darkTheme = false) {

                val authResult by authViewModel.authResult.collectAsStateWithLifecycle()

                //  Proper auth result handler
                LaunchedEffect(authResult) {
                    authResult?.let { result ->
                        when (result) {
                            is AuthResult.Success -> {
                                Toast.makeText(
                                    this@LoginActivity,
                                    "Welcome back!",
                                    Toast.LENGTH_SHORT
                                ).show()

                                val intent = Intent(
                                    this@LoginActivity,
                                    HomeActivity::class.java
                                ).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }

                                startActivity(intent)
                                finish()
                            }

                            is AuthResult.Error -> {
                                Toast.makeText(
                                    this@LoginActivity,
                                    result.message,
                                    Toast.LENGTH_LONG
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
                        startActivity(
                            Intent(this@LoginActivity, SignUpActivity::class.java)
                        )
                    }
                )
            }
        }
    }
}

private fun isValidEmail(email: String): Boolean {
    val emailRegex =
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex()
    return emailRegex.matches(email)
}

@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onSignUpClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val isEmailValid = email.isBlank() || isValidEmail(email)
    val showEmailError = email.isNotBlank() && !isValidEmail(email)

    val focusManager = LocalFocusManager.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            Spacer(modifier = Modifier.height(80.dp))

            Text(
                text = "TriCount",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Split expenses with friends",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // EMAIL FIELD
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Filled.Email, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = showEmailError,
                supportingText = {
                    if (showEmailError) {
                        Text(
                            text = "Please enter a valid email address",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PASSWORD FIELD
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation =
                    if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (email.isNotBlank() &&
                            password.isNotBlank() &&
                            isEmailValid
                        ) {
                            focusManager.clearFocus()
                            onLoginClick(email.trim(), password)
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // LOGIN BUTTON
            Button(
                onClick = {
                    if (email.isNotBlank() &&
                        password.isNotBlank() &&
                        isEmailValid
                    ) {
                        onLoginClick(email.trim(), password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = email.isNotBlank() &&
                        password.isNotBlank() &&
                        isEmailValid
            ) {
                Text(
                    text = "Login",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Don't have an account?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onSignUpClick) {
                    Text(
                        text = "Sign Up",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}