package com.example.tricount.viewModel

import android.app.Application
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
                // Validate inputs
                if (name.isBlank() || email.isBlank() || password.isBlank()) {
                    _authResult.value = AuthResult.Error("All fields are required")
                    return@launch
                }

                if (!isValidEmail(email)) {
                    _authResult.value = AuthResult.Error("Invalid email format")
                    return@launch
                }

                if (password.length < 6) {
                    _authResult.value = AuthResult.Error("Password must be at least 6 characters")
                    return@launch
                }

                // Check if user already exists
                val existingUser = userDao.getUserByEmail(email)
                if (existingUser != null) {
                    _authResult.value = AuthResult.Error("Email already registered")
                    return@launch
                }

                // Create new user
                val newUser = UserEntity(
                    email = email,
                    password = password,  // In production, hash this!
                    name = name
                )
                val userId = userDao.insertUser(newUser).toInt()

                // Save session
                sessionManager.saveSession(userId, email, name)

                _authResult.value = AuthResult.Success(userId)
            } catch (e: Exception) {
                e.printStackTrace()
                _authResult.value = AuthResult.Error("Registration failed. Please try again.")
            }
        }
    }

    // Login existing user
    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                // Validate inputs
                if (email.isBlank() || password.isBlank()) {
                    _authResult.value = AuthResult.Error("Email and password are required")
                    return@launch
                }

                // Attempt login
                val user = userDao.login(email, password)
                if (user != null) {
                    // Save session
                    sessionManager.saveSession(user.id, user.email, user.name)
                    _authResult.value = AuthResult.Success(user.id)
                } else {
                    _authResult.value = AuthResult.Error("Invalid email or password")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _authResult.value = AuthResult.Error("Login failed. Please try again.")
            }
        }
    }

    // Logout
    fun logout() {
        sessionManager.clearSession()
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