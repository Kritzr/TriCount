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

    // ── Google Sign-In ────────────────────────────────────────────────────────

    fun handleGoogleSignIn(idToken: String) {
        viewModelScope.launch {
            try {
                Log.d("AuthVM", "handleGoogleSignIn: start")
                val credential   = GoogleAuthProvider.getCredential(idToken, null)
                val firebaseUser = firebaseAuth.signInWithCredential(credential).await().user
                    ?: run { _authResult.value = AuthResult.Error("Google sign-in failed: no user"); return@launch }

                val firebaseUid = firebaseUser.uid
                val email       = firebaseUser.email?.lowercase() ?: ""
                val name        = firebaseUser.displayName
                    ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }

                val roomUserId = findOrCreateRoomUser(email, name)

                sessionManager.saveSession(roomUserId, email, name)
                sessionManager.saveFirebaseUid(firebaseUid)

                // Pull Firestore data once — this also restores photoUri + nickname
                // from Firestore into Room and SessionManager
                FirebaseSyncRepository(db, sessionManager).pullFromFirebase(roomUserId)

                // After pull, make sure SessionManager has the latest photo/nickname
                // from Room (in case pull updated Room but not SessionManager)
                restoreProfileFromRoom(roomUserId)

                Log.d("AuthVM", "handleGoogleSignIn: done roomUserId=$roomUserId")
                _authResult.value = AuthResult.Success(roomUserId)
            } catch (e: Exception) {
                Log.e("AuthVM", "handleGoogleSignIn error: ${e.message}", e)
                _authResult.value = AuthResult.Error("Google sign-in failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    // ── Email / Password Sign-Up ──────────────────────────────────────────────

    fun signUp(name: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                val n = name.trim(); val e = email.trim().lowercase(); val p = password.trim()
                if (n.isBlank() || e.isBlank() || p.isBlank()) {
                    _authResult.value = AuthResult.Error("All fields are required"); return@launch
                }
                if (!isValidEmail(e)) {
                    _authResult.value = AuthResult.Error("Please enter a valid email address"); return@launch
                }
                if (p.length < 6) {
                    _authResult.value = AuthResult.Error("Password must be at least 6 characters"); return@launch
                }
                if (userDao.getUserByEmail(e) != null) {
                    _authResult.value = AuthResult.Error("Email already registered"); return@launch
                }
                val userId = userDao.insertUser(UserEntity(email = e, password = p, name = n)).toInt()
                if (userId > 0) {
                    sessionManager.saveSession(userId, e, n)
                    restoreProfileFromRoom(userId)
                    _authResult.value = AuthResult.Success(userId)
                } else {
                    _authResult.value = AuthResult.Error("Failed to create account")
                }
            } catch (e: Exception) {
                Log.e("AuthVM", "signUp error: ${e.message}", e)
                _authResult.value = AuthResult.Error("Registration failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    // ── Email / Password Login ────────────────────────────────────────────────

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val e = email.trim().lowercase(); val p = password.trim()
                if (e.isBlank() || p.isBlank()) {
                    _authResult.value = AuthResult.Error("Email and password are required"); return@launch
                }
                if (!isValidEmail(e)) {
                    _authResult.value = AuthResult.Error("Please enter a valid email address"); return@launch
                }
                val user = userDao.login(e, p)
                if (user != null) {
                    sessionManager.saveSession(user.id, user.email, user.name)
                    // Restore photo + nickname from Room for this specific user
                    restoreProfileFromRoom(user.id)
                    _authResult.value = AuthResult.Success(user.id)
                } else {
                    _authResult.value = AuthResult.Error("Incorrect email or password")
                }
            } catch (e: Exception) {
                Log.e("AuthVM", "login error: ${e.message}", e)
                _authResult.value = AuthResult.Error("Login failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    fun logout() {
        try { firebaseAuth.signOut() } catch (e: Exception) { Log.e("AuthVM", "signOut: ${e.message}") }
        sessionManager.clearSession()
        Log.d("AuthVM", "logout complete")
    }

    fun resetAuthResult() { _authResult.value = null }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Reads the photo URI and nickname stored in Room for [userId] and writes
     * them back into SessionManager. Called on every login so:
     *   - Logging out and back in shows the correct photo (from Room)
     *   - Switching users shows each user's own photo (different Room row)
     *
     * Room is the permanent store. SessionManager is just the session cache.
     */
    private suspend fun restoreProfileFromRoom(userId: Int) {
        try {
            val user = userDao.getUserById(userId) ?: return
            if (!user.photoUri.isNullOrEmpty()) {
                sessionManager.setProfilePhotoUri(user.photoUri)
                Log.d("AuthVM", "restoreProfileFromRoom: photoUri restored for userId=$userId")
            } else {
                sessionManager.clearProfilePhotoUri()
            }
            sessionManager.setNickname(user.nickname ?: "")
        } catch (e: Exception) {
            Log.e("AuthVM", "restoreProfileFromRoom error: ${e.message}", e)
        }
    }

    private suspend fun findOrCreateRoomUser(email: String, name: String): Int {
        val existing = userDao.getUserByEmail(email)
        if (existing != null) return existing.id
        return userDao.insertUser(UserEntity(email = email, password = "", name = name)).toInt()
    }

    private fun isValidEmail(email: String) =
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex().matches(email)
}