package com.example.tricount

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.JoinResult
import com.example.tricount.viewModel.TricountViewModel

class JoinTricountActivity : ComponentActivity() {

    private val tricountViewModel: TricountViewModel by viewModels {
        TricountViewModel.factory(application)
    }

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
                val joinResult by tricountViewModel.joinResult.collectAsStateWithLifecycle()

                // Hoist isLoading here so the LaunchedEffect can both read and write it
                var isLoading by remember { mutableStateOf(false) }

                // Capture joinResult into a local val so Kotlin can smart-cast it safely
                LaunchedEffect(joinResult) {
                    when (val result = joinResult) {

                        is JoinResult.Success -> {
                            isLoading = false
                            Toast.makeText(
                                this@JoinTricountActivity,
                                "Successfully joined ${result.tricount.name}!",
                                Toast.LENGTH_SHORT
                            ).show()
                            tricountViewModel.resetJoinResult()
                            finish()
                        }

                        is JoinResult.Pending -> {
                            isLoading = false
                            Toast.makeText(
                                this@JoinTricountActivity,
                                "Request sent to join \"${result.tricountName}\"! " +
                                        "The owner will review it — you'll be notified once approved.",
                                Toast.LENGTH_LONG
                            ).show()
                            tricountViewModel.resetJoinResult()
                            // Bring HomeActivity to the front without re-creating it
                            startActivity(
                                Intent(this@JoinTricountActivity, HomeActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                            )
                            finish()
                        }

                        is JoinResult.Error -> {
                            isLoading = false
                            Toast.makeText(
                                this@JoinTricountActivity,
                                result.message,
                                Toast.LENGTH_LONG
                            ).show()
                            tricountViewModel.resetJoinResult()
                        }

                        null -> { /* initial / reset state — do nothing */ }
                    }
                }

                JoinTricountScreen(
                    isLoading       = isLoading,
                    onLoadingChange = { isLoading = it },
                    onBackClick     = { finish() },
                    // Set isLoading = true here in the caller, before the ViewModel call,
                    // so the button disables and the spinner starts immediately
                    onJoinClick     = { code ->
                        isLoading = true
                        tricountViewModel.joinTricountByCode(code)
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable screen — isLoading is now fully driven by the Activity above
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinTricountScreen(
    isLoading       : Boolean = false,
    onLoadingChange : (Boolean) -> Unit = {},
    onBackClick     : () -> Unit,
    onJoinClick     : (String) -> Unit
) {
    var joinCode by remember { mutableStateOf("") }

    val isValidCode = remember(joinCode) {
        joinCode.length == 6 && joinCode.all { it.isLetterOrDigit() }
    }

    val focusManager = LocalFocusManager.current

    // Safety-net: if the ViewModel never responds within 10 s, unlock the UI
    LaunchedEffect(isLoading) {
        if (isLoading) {
            kotlinx.coroutines.delay(10_000L)
            onLoadingChange(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Tricount") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated icon
            val infiniteTransition = rememberInfiniteTransition(label = "join_anim")
            @Suppress("UNUSED_VARIABLE")
            val scale by infiniteTransition.animateFloat(
                initialValue  = 1f,
                targetValue   = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "icon_scale"
            )

            Surface(
                modifier = Modifier.size(80.dp),
                shape    = MaterialTheme.shapes.extraLarge,
                color    = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                    Icon(
                        imageVector        = Icons.Filled.Check,
                        contentDescription = null,
                        modifier           = Modifier.size(48.dp),
                        tint               = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text       = "Join an Existing Tricount",
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text     = "Enter the 6-character code shared by your friend",
                fontSize = 14.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value         = joinCode,
                onValueChange = {
                    if (it.length <= 6 && it.all { ch -> ch.isLetterOrDigit() }) {
                        joinCode = it.uppercase()
                    }
                },
                label       = { Text("Tricount Code") },
                placeholder = { Text("ABC123") },
                modifier    = Modifier.fillMaxWidth(),
                singleLine  = true,
                isError     = joinCode.isNotEmpty() && joinCode.length < 6,
                supportingText = {
                    AnimatedVisibility(
                        visible = joinCode.isNotEmpty(),
                        enter   = fadeIn() + expandVertically(),
                        exit    = fadeOut() + shrinkVertically()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isValidCode) {
                                Icon(
                                    Icons.Filled.Check, null,
                                    modifier = Modifier.size(16.dp),
                                    tint     = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Valid code format", color = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(
                                    Icons.Filled.Error, null,
                                    modifier = Modifier.size(16.dp),
                                    tint     = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Code must be exactly 6 characters",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType   = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction      = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (isValidCode && !isLoading) {
                            focusManager.clearFocus()
                            onJoinClick(joinCode)
                        }
                    }
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontSize      = 20.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (isValidCode && !isLoading) {
                        focusManager.clearFocus()
                        onJoinClick(joinCode)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = isValidCode && !isLoading,
                colors  = ButtonDefaults.buttonColors(
                    containerColor         = MaterialTheme.colorScheme.primary,
                    contentColor           = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(24.dp),
                        color       = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Joining...", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                } else {
                    Text("Join Tricount", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text       = "How to get a code:",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    InfoBullet("Ask the Tricount creator to share their invite code")
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoBullet("The code is exactly 6 characters long")
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoBullet("Your request will be sent to the creator for approval")
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoBullet("You'll get a notification once you're approved")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text     = "Can't find a code? Ask your friend to check their Tricount details.",
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun InfoBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text       = "•",
            fontSize   = 14.sp,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier   = Modifier.padding(end = 8.dp)
        )
        Text(
            text     = text,
            fontSize = 14.sp,
            color    = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}