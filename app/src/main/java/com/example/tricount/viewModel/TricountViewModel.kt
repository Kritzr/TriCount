package com.example.tricount.viewModel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tricount.data.SessionManager
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.TricountEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class TricountViewModel(application: Application) : AndroidViewModel(application) {

    private val tricountDao = TricountDatabase.getDatabase(application).tricountDao()
    private val sessionManager = SessionManager(application)

    private val _tricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val tricounts: StateFlow<List<TricountEntity>> = _tricounts

    private val _currentTricount = MutableStateFlow<TricountEntity?>(null)
    val currentTricount: StateFlow<TricountEntity?> = _currentTricount

    private val _tricountMembers = MutableStateFlow<List<com.example.tricount.data.entity.MemberWithDetails>>(emptyList())
    val tricountMembers: StateFlow<List<com.example.tricount.data.entity.MemberWithDetails>> = _tricountMembers

    private val _joinResult = MutableStateFlow<JoinResult?>(null)
    val joinResult: StateFlow<JoinResult?> = _joinResult

    // Load all tricounts for the current user
    fun loadTricounts() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                if (userId != null) {
                    Log.d("TricountViewModel", "Loading tricounts for user: $userId")
                    val userTricounts = tricountDao.getTricountsForUser(userId)
                    _tricounts.value = userTricounts
                    Log.d("TricountViewModel", "Loaded ${userTricounts.size} tricounts")
                } else {
                    Log.e("TricountViewModel", "No user ID found in session")
                    _tricounts.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error loading tricounts: ${e.message}", e)
                _tricounts.value = emptyList()
            }
        }
    }

    // Insert a new tricount
    fun insertTricount(name: String, description: String) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                if (userId != null) {
                    val joinCode = generateJoinCode()
                    val tricount = TricountEntity(
                        name = name,
                        description = description,
                        creatorId = userId,
                        joinCode = joinCode
                    )

                    Log.d("TricountViewModel", "Inserting tricount: $name with code: $joinCode")
                    tricountDao.insertTricount(tricount)

                    // Reload the list immediately after insertion
                    loadTricounts()
                    Log.d("TricountViewModel", "Tricount inserted successfully")
                } else {
                    Log.e("TricountViewModel", "Cannot insert tricount: No user ID")
                }
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error inserting tricount: ${e.message}", e)
            }
        }
    }

    // Delete a tricount
    fun deleteTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                Log.d("TricountViewModel", "Deleting tricount with ID: $tricountId")
                tricountDao.deleteTricountById(tricountId)

                // CRITICAL: Reload the list immediately after deletion
                loadTricounts()
                Log.d("TricountViewModel", "Tricount deleted and list reloaded")
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error deleting tricount: ${e.message}", e)
            }
        }
    }

    // Load specific tricount details
    fun loadTricountDetails(tricountId: Int) {
        viewModelScope.launch {
            try {
                Log.d("TricountViewModel", "Loading details for tricount: $tricountId")
                val tricount = tricountDao.getTricountById(tricountId)
                _currentTricount.value = tricount
                Log.d("TricountViewModel", "Tricount details loaded: ${tricount?.name}")

                // Also load members when loading tricount details
                if (tricount != null) {
                    loadTricountMembers(tricountId)
                }
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error loading tricount details: ${e.message}", e)
                _currentTricount.value = null
            }
        }
    }

    // Load members of a tricount
    fun loadTricountMembers(tricountId: Int) {
        viewModelScope.launch {
            try {
                Log.d("TricountViewModel", "Loading members for tricount: $tricountId")
                val members = tricountDao.getTricountMembersWithDetails(tricountId)
                _tricountMembers.value = members
                Log.d("TricountViewModel", "Loaded ${members.size} members")
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error loading members: ${e.message}", e)
                _tricountMembers.value = emptyList()
            }
        }
    }

    // Add member by email (for creators)
    fun addMemberByEmail(tricountId: Int, email: String, onResult: (AddMemberResult) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("TricountViewModel", "Attempting to add member: $email to tricount: $tricountId")

                // Find user by email
                val user = tricountDao.getUserByEmail(email)
                if (user == null) {
                    Log.e("TricountViewModel", "User not found with email: $email")
                    onResult(AddMemberResult.Error("No user found with this email"))
                    return@launch
                }

                // Check if user is already the creator
                val tricount = tricountDao.getTricountById(tricountId)
                if (tricount?.creatorId == user.id) {
                    Log.d("TricountViewModel", "User is the creator")
                    onResult(AddMemberResult.Error("This user is the creator of this Tricount"))
                    return@launch
                }

                // Check if user is already a member
                val existingMember = tricountDao.getMembership(user.id, tricountId)
                if (existingMember != null) {
                    Log.d("TricountViewModel", "User is already a member")
                    onResult(AddMemberResult.Error("${user.name} is already a member"))
                    return@launch
                }

                // Add user as member
                tricountDao.addMember(user.id, tricountId)
                Log.d("TricountViewModel", "Member added successfully")

                // Reload members list
                loadTricountMembers(tricountId)

                onResult(AddMemberResult.Success(user.name))
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error adding member: ${e.message}", e)
                onResult(AddMemberResult.Error("Failed to add member: ${e.message}"))
            }
        }
    }

    // Remove member from tricount
    fun removeMember(userId: Int, tricountId: Int) {
        viewModelScope.launch {
            try {
                Log.d("TricountViewModel", "Removing member: $userId from tricount: $tricountId")
                tricountDao.removeMember(userId, tricountId)

                // Reload members list
                loadTricountMembers(tricountId)
                Log.d("TricountViewModel", "Member removed successfully")
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error removing member: ${e.message}", e)
            }
        }
    }

    // Join a tricount by code
    fun joinTricountByCode(code: String) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                if (userId == null) {
                    _joinResult.value = JoinResult.Error("User not logged in")
                    return@launch
                }

                Log.d("TricountViewModel", "Attempting to join tricount with code: $code")

                // Find tricount by join code
                val tricount = tricountDao.getTricountByJoinCode(code)

                if (tricount == null) {
                    Log.e("TricountViewModel", "Tricount not found with code: $code")
                    _joinResult.value = JoinResult.Error("Invalid code. Tricount not found.")
                    return@launch
                }

                // Check if user is already the creator
                if (tricount.creatorId == userId) {
                    Log.d("TricountViewModel", "User is already the creator of this tricount")
                    _joinResult.value = JoinResult.Error("You are already the creator of this Tricount")
                    return@launch
                }

                // Check if user is already a member
                val existingMember = tricountDao.getMembership(userId, tricount.id)
                if (existingMember != null) {
                    Log.d("TricountViewModel", "User is already a member")
                    _joinResult.value = JoinResult.Error("You are already a member of this Tricount")
                    return@launch
                }

                // Add user as member
                tricountDao.addMember(userId, tricount.id)
                Log.d("TricountViewModel", "User successfully joined tricount")

                // Reload tricounts to show the newly joined one
                loadTricounts()

                _joinResult.value = JoinResult.Success(tricount)
            } catch (e: Exception) {
                Log.e("TricountViewModel", "Error joining tricount: ${e.message}", e)
                _joinResult.value = JoinResult.Error("Failed to join: ${e.message}")
            }
        }
    }

    // Reset join result
    fun resetJoinResult() {
        _joinResult.value = null
    }

    // Generate a random 6-character join code
    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }
}

// Sealed class for join results
sealed class JoinResult {
    data class Success(val tricount: TricountEntity) : JoinResult()
    data class Error(val message: String) : JoinResult()
}

// Sealed class for add member results
sealed class AddMemberResult {
    data class Success(val memberName: String) : AddMemberResult()
    data class Error(val message: String) : AddMemberResult()
}