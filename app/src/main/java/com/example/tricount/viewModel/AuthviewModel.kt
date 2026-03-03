package com.example.tricount.viewModel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tricount.data.SessionManager
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val userDao = TricountDatabase.getDatabase(application).userDao()
    private val sessionManager = SessionManager(application)

    private val _authResult = MutableStateFlow<AuthResult?>(null)
    val authResult: StateFlow<AuthResult?> = _authResult

    // -------------------------
    // SIGN UP
    // -------------------------
    fun signUp(name: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Starting signup for: $email")

                val trimmedName = name.trim()
                val trimmedEmail = email.trim()
                val trimmedPassword = password.trim()

                // Validation
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

                // Check existing user
                val existingUser = userDao.getUserByEmail(trimmedEmail)
                if (existingUser != null) {
                    _authResult.value = AuthResult.Error("Email already registered")
                    return@launch
                }

                // Create user
                val newUser = UserEntity(
                    email = trimmedEmail,
                    password = trimmedPassword, // ⚠️ Hash in production
                    name = trimmedName
                )

                val userId = userDao.insertUser(newUser).toInt()

                if (userId > 0) {
                    // Save session BEFORE success
                    sessionManager.saveSession(userId, trimmedEmail, trimmedName)
                    _authResult.value = AuthResult.Success(userId)
                    Log.d("AuthViewModel", "Signup successful for userId: $userId")
                } else {
                    _authResult.value = AuthResult.Error("Failed to create account")
                }

            } catch (e: Exception) {
                Log.e("AuthViewModel", "Signup Error: ${e.message}", e)
                _authResult.value =
                    AuthResult.Error("Registration failed: ${e.localizedMessage}")
            }
        }
    }

    // -------------------------
    // LOGIN
    // -------------------------
    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Attempting login for: $email")

                val trimmedEmail = email.trim()
                val trimmedPassword = password.trim()

                if (trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
                    _authResult.value =
                        AuthResult.Error("Email and password are required")
                    return@launch
                }

                if (!isValidEmail(trimmedEmail)) {
                    _authResult.value =
                        AuthResult.Error("Please enter a valid email address")
                    return@launch
                }

                val user = userDao.login(trimmedEmail, trimmedPassword)

                if (user != null) {
                    // Save session BEFORE success
                    sessionManager.saveSession(user.id, user.email, user.name)
                    _authResult.value = AuthResult.Success(user.id)
                    Log.d("AuthViewModel", "Login successful for userId: ${user.id}")
                } else {
                    _authResult.value =
                        AuthResult.Error("Incorrect email or password")
                }

            } catch (e: Exception) {
                Log.e("AuthViewModel", "Login Error: ${e.message}", e)
                _authResult.value =
                    AuthResult.Error("Login failed: ${e.localizedMessage}")
            }
        }
    }

    // -------------------------
    // LOGOUT
    // -------------------------
    fun logout() {
        sessionManager.clearSession()
        Log.d("AuthViewModel", "User logged out")
    }

    // -------------------------
    // RESET AUTH STATE
    // -------------------------
    fun resetAuthResult() {
        _authResult.value = null
    }

    // -------------------------
    // EMAIL VALIDATION
    // -------------------------
    private fun isValidEmail(email: String): Boolean {
        val emailRegex =
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex()
        return emailRegex.matches(email)
    }
}

// -------------------------
// AUTH RESULT SEALED CLASS
// -------------------------
sealed class AuthResult {
    data class Success(val userId: Int) : AuthResult()
    data class Error(val message: String) : AuthResult()
}