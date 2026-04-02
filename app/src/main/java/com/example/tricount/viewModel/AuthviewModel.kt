package com.example.tricount.viewModel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tricount.data.FirebaseSyncRepository
import com.example.tricount.data.SessionManager
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthResult {
    data class Success(val userId: Int) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val db             = TricountDatabase.getDatabase(application)
    private val userDao        = db.userDao()
    private val sessionManager = SessionManager(application)
    private val firebaseAuth   = FirebaseAuth.getInstance()

    private val _authResult = MutableStateFlow<AuthResult?>(null)
    val authResult: StateFlow<AuthResult?> = _authResult

    // ─────────────────────────────────────────────────────────────────────────
    // GOOGLE SIGN-IN
    // Call this from LoginActivity after Google returns an idToken
    // ─────────────────────────────────────────────────────────────────────────
    fun handleGoogleSignIn(idToken: String) {
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "handleGoogleSignIn: authenticating with Firebase")

                val credential   = GoogleAuthProvider.getCredential(idToken, null)
                val firebaseUser = firebaseAuth.signInWithCredential(credential).await().user
                    ?: run {
                        _authResult.value = AuthResult.Error("Google sign-in failed")
                        return@launch
                    }

                val firebaseUid = firebaseUser.uid
                val email       = firebaseUser.email?.lowercase() ?: ""
                val name        = firebaseUser.displayName
                    ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }

                Log.d("AuthViewModel", "Firebase OK — uid=$firebaseUid email=$email")

                // Find or create the Room integer user for this Google account
                val roomUserId = findOrCreateRoomUser(email, name)

                Log.d("AuthViewModel", "Room userId=$roomUserId")

                sessionManager.saveSession(roomUserId, email, name)
                sessionManager.saveFirebaseUid(firebaseUid)

                // Pull Firestore → Room ONCE here after session is fully saved.
                // Never do this in TricountViewModel.init — that causes a race
                // condition that wipes freshly created local data.
                FirebaseSyncRepository(db, sessionManager).pullFromFirebase(roomUserId)

                _authResult.value = AuthResult.Success(roomUserId)

            } catch (e: Exception) {
                Log.e("AuthViewModel", "handleGoogleSignIn error: ${e.message}", e)
                _authResult.value = AuthResult.Error(
                    "Google sign-in failed: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EMAIL / PASSWORD SIGN-UP
    // ─────────────────────────────────────────────────────────────────────────
    fun signUp(name: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                val trimmedName     = name.trim()
                val trimmedEmail    = email.trim().lowercase()
                val trimmedPassword = password.trim()

                if (trimmedName.isBlank() || trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
                    _authResult.value = AuthResult.Error("All fields are required")
                    return@launch
                }
                if (!isValidEmail(trimmedEmail)) {
                    _authResult.value = AuthResult.Error("Please enter a valid email address")
                    return@launch
                }
                if (trimmedPassword.length < 6) {
                    _authResult.value = AuthResult.Error("Password must be at least 6 characters")
                    return@launch
                }

                val existingUser = userDao.getUserByEmail(trimmedEmail)
                if (existingUser != null) {
                    _authResult.value = AuthResult.Error("Email already registered")
                    return@launch
                }

                val newUser = UserEntity(
                    email    = trimmedEmail,
                    password = trimmedPassword,
                    name     = trimmedName
                )
                val userId = userDao.insertUser(newUser).toInt()

                if (userId > 0) {
                    sessionManager.saveSession(userId, trimmedEmail, trimmedName)
                    _authResult.value = AuthResult.Success(userId)
                    Log.d("AuthViewModel", "SignUp OK userId=$userId")
                } else {
                    _authResult.value = AuthResult.Error("Failed to create account")
                }

            } catch (e: Exception) {
                Log.e("AuthViewModel", "SignUp error: ${e.message}", e)
                _authResult.value = AuthResult.Error(
                    "Registration failed: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EMAIL / PASSWORD LOGIN
    // ─────────────────────────────────────────────────────────────────────────
    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val trimmedEmail    = email.trim().lowercase()
                val trimmedPassword = password.trim()

                if (trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
                    _authResult.value = AuthResult.Error("Email and password are required")
                    return@launch
                }
                if (!isValidEmail(trimmedEmail)) {
                    _authResult.value = AuthResult.Error("Please enter a valid email address")
                    return@launch
                }

                val user = userDao.login(trimmedEmail, trimmedPassword)
                if (user != null) {
                    sessionManager.saveSession(user.id, user.email, user.name)
                    _authResult.value = AuthResult.Success(user.id)
                    Log.d("AuthViewModel", "Login OK userId=${user.id}")
                } else {
                    _authResult.value = AuthResult.Error("Incorrect email or password")
                }

            } catch (e: Exception) {
                Log.e("AuthViewModel", "Login error: ${e.message}", e)
                _authResult.value = AuthResult.Error(
                    "Login failed: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOGOUT  ← fixes the crash: must sign out from Firebase too
    // ─────────────────────────────────────────────────────────────────────────
    fun logout() {
        try {
            firebaseAuth.signOut()
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Firebase signOut error: ${e.message}")
        }
        sessionManager.clearSession()
        Log.d("AuthViewModel", "Logged out")
    }

    fun resetAuthResult() {
        _authResult.value = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the Room integer userId for the given Google-account email.
     * Creates a new UserEntity if one doesn't exist yet.
     * Password is empty because Google users authenticate via Firebase, not locally.
     */
    private suspend fun findOrCreateRoomUser(email: String, name: String): Int {
        val existing = userDao.getUserByEmail(email)
        if (existing != null) {
            Log.d("AuthViewModel", "Found existing Room user id=${existing.id}")
            return existing.id
        }
        val newUser = UserEntity(
            email    = email,
            password = "",   // Google-auth user — no local password needed
            name     = name
        )
        val newId = userDao.insertUser(newUser).toInt()
        Log.d("AuthViewModel", "Created new Room user id=$newId for $email")
        return newId
    }

    private fun isValidEmail(email: String): Boolean =
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex().matches(email)
}