package com.example.tricount.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tricount.data.SessionManager
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.data.entity.TricountMemberEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class TricountViewModel(application: Application) : AndroidViewModel(application) {

    private val tricountDao = TricountDatabase.getDatabase(application).tricountDao()
    private val memberDao = TricountDatabase.getDatabase(application).tricountMemberDao()
    private val sessionManager = SessionManager(application)

    // StateFlow to hold the list of tricounts for current user
    private val _tricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val tricounts: StateFlow<List<TricountEntity>> = _tricounts

    // StateFlow for join result
    private val _joinResult = MutableStateFlow<JoinResult?>(null)
    val joinResult: StateFlow<JoinResult?> = _joinResult

    init {
        // Load tricounts when ViewModel is created
        loadTricounts()
    }

    // Load all tricounts for the current user
    fun loadTricounts() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                if (userId != -1) {
                    _tricounts.value = tricountDao.getTricountsForUser(userId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Generate a unique 6-character join code
    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }

    // Insert a new tricount with the current user as creator
    fun insertTricount(name: String, description: String) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                if (userId == -1) {
                    _joinResult.value = JoinResult.Error("User not logged in")
                    return@launch
                }

                val joinCode = generateJoinCode()
                val tricount = TricountEntity(
                    name = name,
                    description = description,
                    joinCode = joinCode,
                    creatorId = userId
                )
                val tricountId = tricountDao.insertTricount(tricount).toInt()

                // Add creator as a member
                val member = TricountMemberEntity(
                    tricountId = tricountId,
                    userId = userId,
                    isCreator = true
                )
                memberDao.insertMember(member)

                // Reload the list after inserting
                loadTricounts()
            } catch (e: Exception) {
                e.printStackTrace()
                _joinResult.value = JoinResult.Error("Failed to create Tricount")
            }
        }
    }

    // Join an existing tricount using a code
    fun joinTricountByCode(code: String) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                if (userId == -1) {
                    _joinResult.value = JoinResult.Error("User not logged in")
                    return@launch
                }

                val tricount = tricountDao.getTricountByCode(code.uppercase())
                if (tricount != null) {
                    // Check if user is already a member
                    val isMember = memberDao.isMemberOfTricount(userId, tricount.id) > 0
                    if (isMember) {
                        _joinResult.value = JoinResult.Error("You are already a member of this Tricount")
                        return@launch
                    }

                    // Add user as member
                    val member = TricountMemberEntity(
                        tricountId = tricount.id,
                        userId = userId,
                        isCreator = false
                    )
                    memberDao.insertMember(member)

                    _joinResult.value = JoinResult.Success(tricount)
                    // Reload the list to show the joined tricount
                    loadTricounts()
                } else {
                    _joinResult.value = JoinResult.Error("Invalid code. Tricount not found.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _joinResult.value = JoinResult.Error("An error occurred. Please try again.")
            }
        }
    }

    // Leave a tricount
    fun leaveTricount(tricountId: Int) {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId()
                if (userId != -1) {
                    memberDao.removeMember(userId, tricountId)
                    loadTricounts()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Reset join result
    fun resetJoinResult() {
        _joinResult.value = null
    }

    // Get current user ID
    fun getCurrentUserId(): Int {
        return sessionManager.getUserId()
    }
}

// Sealed class for join result states
sealed class JoinResult {
    data class Success(val tricount: TricountEntity) : JoinResult()
    data class Error(val message: String) : JoinResult()
}