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

    // Sign up new user
    fun signUp(name: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Starting signup for: $email")

                // Validate inputs
                if (name.isBlank() || email.isBlank() || password.isBlank()) {
                    Log.e("AuthViewModel", "Validation failed: empty fields")
                    _authResult.value = AuthResult.Error("All fields are required")
                    return@launch
                }

                if (!isValidEmail(email)) {
                    Log.e("AuthViewModel", "Validation failed: invalid email")
                    _authResult.value = AuthResult.Error("please enter a valid mailid format")
                    return@launch
                }

                if (password.length < 6) {
                    Log.e("AuthViewModel", "Validation failed: password too short")
                    _authResult.value = AuthResult.Error("Password must be at least 6 characters")
                    return@launch
                }

                // Check if user already exists
                val existingUser = userDao.getUserByEmail(email)
                if (existingUser != null) {
                    Log.e("AuthViewModel", "User already exists: $email")
                    _authResult.value = AuthResult.Error("Email already registered")
                    return@launch
                }

                // Create new user
                val newUser = UserEntity(
                    email = email,
                    password = password,  // In production, hash this!
                    name = name
                )

                Log.d("AuthViewModel", "Attempting to insert user")
                val userId = userDao.insertUser(newUser).toInt()
                Log.d("AuthViewModel", "User inserted successfully with ID: $userId")

                // Save session
                sessionManager.saveSession(userId, email, name)
                Log.d("AuthViewModel", "Session saved for user: $userId")

                _authResult.value = AuthResult.Success(userId)
                Log.d("AuthViewModel", "Signup completed successfully")

            } catch (e: Exception) {
                Log.e("AuthViewModel", "Signup error: ${e.message}", e)
                e.printStackTrace()
                _authResult.value = AuthResult.Error("Registration failed: ${e.message}")
            }
        }
    }

    // Login existing user
    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Attempting login for: $email")

                // Validate inputs
                if (email.isBlank() || password.isBlank()) {
                    Log.e("AuthViewModel", "Login validation failed: empty fields")
                    _authResult.value = AuthResult.Error("Email and password are required")
                    return@launch
                }

                // Attempt login
                val user = userDao.login(email, password)
                if (user != null) {
                    Log.d("AuthViewModel", "Login successful for user: ${user.id}")
                    // Save session
                    sessionManager.saveSession(user.id, user.email, user.name)
                    _authResult.value = AuthResult.Success(user.id)
                } else {
                    Log.e("AuthViewModel", "Login failed: invalid credentials")
                    _authResult.value = AuthResult.Error("Invalid email or password")
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Login error: ${e.message}", e)
                e.printStackTrace()
                _authResult.value = AuthResult.Error("Login failed: ${e.message}")
            }
        }
    }

    // Logout
    fun logout() {
        sessionManager.clearSession()
        Log.d("AuthViewModel", "User logged out")
    }

    // Reset auth result
    fun resetAuthResult() {
        _authResult.value = null
    }

    // Email validation
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}

// Sealed class for authentication results
sealed class AuthResult {
    data class Success(val userId: Int) : AuthResult()
    data class Error(val message: String) : AuthResult()
}