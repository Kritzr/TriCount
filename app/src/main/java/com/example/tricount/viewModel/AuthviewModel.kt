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
    object VerificationEmailSent : AuthResult()   // Step 1 of OTP email flow
    object AwaitingOtpVerification : AuthResult() // Waiting for user to verify
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
                val authResult   = firebaseAuth.signInWithCredential(credential).await()
                val firebaseUser = authResult.user
                    ?: run { _authResult.value = AuthResult.Error("Google sign-in failed: no user"); return@launch }

                val firebaseUid = firebaseUser.uid
                val email       = firebaseUser.email?.lowercase() ?: ""
                val name        = firebaseUser.displayName
                    ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }

                val roomUserId = findOrCreateRoomUser(email, name)

                // EXTRA_CHANGES § C — persist UID after Google sign-in
                userDao.updateFirebaseUidByEmail(email, firebaseUid)

                sessionManager.saveSession(roomUserId, name, email, firebaseUid)

                val syncRepo = FirebaseSyncRepository(db, sessionManager)
                syncRepo.pushFullUserProfile(name = name, email = email)  // EXTRA_CHANGES § C
                syncRepo.pullFromFirebase(roomUserId)
                syncRepo.registerFcmToken()

                restoreProfileFromRoom(roomUserId)

                Log.d("AuthVM", "handleGoogleSignIn: done roomUserId=$roomUserId")
                _authResult.value = AuthResult.Success(roomUserId)
            } catch (e: Exception) {
                Log.e("AuthVM", "handleGoogleSignIn error: ${e.message}", e)
                _authResult.value = AuthResult.Error("Google sign-in failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    // ── Email / Password Sign-Up with OTP verification ───────────────────────
    //
    // Flow:
    //   1. signUp()          → creates Firebase Auth account, sends verification email
    //   2. User clicks link  → Firebase marks email as verified
    //   3. checkEmailVerifiedAndComplete() → called after user taps "I've verified"
    //      → creates Room user, saves session, emits Success

    fun signUp(name: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                val n = name.trim()
                val e = email.trim().lowercase()
                val p = password.trim()

                if (n.isBlank() || e.isBlank() || p.isBlank()) {
                    _authResult.value = AuthResult.Error("All fields are required"); return@launch
                }
                if (!isValidEmail(e)) {
                    _authResult.value = AuthResult.Error("Please enter a valid email address"); return@launch
                }
                if (p.length < 6) {
                    _authResult.value = AuthResult.Error("Password must be at least 6 characters"); return@launch
                }

                val firebaseResult = try {
                    firebaseAuth.createUserWithEmailAndPassword(e, p).await()
                } catch (ex: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                    _authResult.value = AuthResult.Error("Email already registered. Please log in.")
                    return@launch
                }

                val firebaseUser = firebaseResult.user ?: run {
                    _authResult.value = AuthResult.Error("Failed to create Firebase account")
                    return@launch
                }

                // EXTRA_CHANGES § C — persist UID immediately after account creation
                userDao.updateFirebaseUidByEmail(e, firebaseUser.uid)

                // Send email verification (OTP link)
                firebaseUser.sendEmailVerification().await()

                // Store pending signup info in session for step 3
                sessionManager.setPendingSignupName(n)
                sessionManager.setPendingSignupEmail(e)

                Log.d("AuthVM", "signUp: verification email sent to $e, uid=${firebaseUser.uid}")
                _authResult.value = AuthResult.VerificationEmailSent

            } catch (ex: Exception) {
                Log.e("AuthVM", "signUp error: ${ex.message}", ex)
                _authResult.value = AuthResult.Error("Registration failed: ${ex.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Called when user taps "I've verified my email" button.
     * Reloads Firebase user, confirms email is verified, then creates Room user.
     */
    fun checkEmailVerifiedAndComplete() {
        viewModelScope.launch {
            try {
                val firebaseUser = firebaseAuth.currentUser ?: run {
                    _authResult.value = AuthResult.Error("Session expired. Please sign up again.")
                    return@launch
                }

                // Reload to get fresh verification status
                firebaseUser.reload().await()

                if (!firebaseUser.isEmailVerified) {
                    _authResult.value = AuthResult.Error("Email not verified yet. Please check your inbox and click the link.")
                    return@launch
                }

                val firebaseUid = firebaseUser.uid
                val email       = (sessionManager.getPendingSignupEmail() ?: firebaseUser.email ?: "").lowercase()
                val name        = sessionManager.getPendingSignupName()
                    ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }

                // Create Room user (or find existing)
                val roomUserId: Int
                val roomUser = userDao.getUserByEmail(email)
                roomUserId = if (roomUser == null) {
                    userDao.insertUser(UserEntity(email = email, password = "", name = name)).toInt()
                } else {
                    roomUser.id
                }

                // EXTRA_CHANGES § C — ensure UID is recorded after verification completes
                userDao.updateFirebaseUidByEmail(email, firebaseUid)

                sessionManager.saveSession(roomUserId, name, email, firebaseUid)
                sessionManager.clearPendingSignup()

                val syncRepo = FirebaseSyncRepository(db, sessionManager)
                syncRepo.pushFullUserProfile(name = name, email = email)  // EXTRA_CHANGES § C
                syncRepo.pullFromFirebase(roomUserId)
                syncRepo.registerFcmToken()

                restoreProfileFromRoom(roomUserId)

                Log.d("AuthVM", "checkEmailVerifiedAndComplete: done roomUserId=$roomUserId")
                _authResult.value = AuthResult.Success(roomUserId)
            } catch (e: Exception) {
                Log.e("AuthVM", "checkEmailVerifiedAndComplete error: ${e.message}", e)
                _authResult.value = AuthResult.Error("Verification check failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Resend verification email (user taps "Resend").
     */
    fun resendVerificationEmail() {
        viewModelScope.launch {
            try {
                firebaseAuth.currentUser?.sendEmailVerification()?.await()
                _authResult.value = AuthResult.Error("Verification email resent. Check your inbox.")
            } catch (e: Exception) {
                _authResult.value = AuthResult.Error("Failed to resend: ${e.localizedMessage}")
            }
        }
    }

    // ── Email / Password Login ────────────────────────────────────────────────

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val e = email.trim().lowercase()
                val p = password.trim()

                if (e.isBlank() || p.isBlank()) {
                    _authResult.value = AuthResult.Error("Email and password are required"); return@launch
                }
                if (!isValidEmail(e)) {
                    _authResult.value = AuthResult.Error("Please enter a valid email address"); return@launch
                }

                val firebaseResult = try {
                    firebaseAuth.signInWithEmailAndPassword(e, p).await()
                } catch (ex: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                    _authResult.value = AuthResult.Error("Incorrect email or password")
                    return@launch
                } catch (ex: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                    _authResult.value = AuthResult.Error("No account found for this email. Please sign up.")
                    return@launch
                }

                val firebaseUser = firebaseResult.user ?: run {
                    _authResult.value = AuthResult.Error("Login failed: no Firebase user")
                    return@launch
                }

                // Enforce email verification
                firebaseUser.reload().await()
                if (!firebaseUser.isEmailVerified) {
                    firebaseUser.sendEmailVerification().await()
                    sessionManager.setPendingSignupEmail(e)
                    _authResult.value = AuthResult.AwaitingOtpVerification
                    return@launch
                }

                val firebaseUid = firebaseUser.uid
                val name = firebaseUser.displayName
                    ?: e.substringBefore("@").replaceFirstChar { it.uppercase() }

                val roomUserId = findOrCreateRoomUser(e, name)

                // EXTRA_CHANGES § C — persist UID after login
                userDao.updateFirebaseUidByEmail(e, firebaseUid)

                sessionManager.saveSession(roomUserId, name, e, firebaseUid)

                val syncRepo = FirebaseSyncRepository(db, sessionManager)
                syncRepo.pushFullUserProfile(name = name, email = e)  // EXTRA_CHANGES § C
                syncRepo.pullFromFirebase(roomUserId)
                syncRepo.registerFcmToken()

                restoreProfileFromRoom(roomUserId)

                Log.d("AuthVM", "login: done roomUserId=$roomUserId")
                _authResult.value = AuthResult.Success(roomUserId)
            } catch (ex: Exception) {
                Log.e("AuthVM", "login error: ${ex.message}", ex)
                _authResult.value = AuthResult.Error("Login failed: ${ex.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    // ── Password reset ────────────────────────────────────────────────────────

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            try {
                firebaseAuth.sendPasswordResetEmail(email.trim().lowercase()).await()
                _authResult.value = AuthResult.Error("Password reset email sent. Check your inbox.")
            } catch (e: Exception) {
                _authResult.value = AuthResult.Error("Failed to send reset email: ${e.localizedMessage}")
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

    private suspend fun restoreProfileFromRoom(userId: Int) {
        try {
            val user = userDao.getUserById(userId) ?: return
            if (!user.photoUri.isNullOrEmpty()) {
                sessionManager.setProfilePhotoUri(user.photoUri)
            }
            if (!user.nickname.isNullOrEmpty()) {
                sessionManager.setNickname(user.nickname)
            }
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