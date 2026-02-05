package com.example.tricount.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tricount.data.database.TricountDatabase
import com.example.tricount.data.entity.TricountEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TricountViewModel(application: Application) : AndroidViewModel(application) {

    private val tricountDao = TricountDatabase.getDatabase(application).tricountDao()

    // StateFlow to hold the list of tricounts
    private val _tricounts = MutableStateFlow<List<TricountEntity>>(emptyList())
    val tricounts: StateFlow<List<TricountEntity>> = _tricounts

    init {
        // Load tricounts when ViewModel is created
        loadTricounts()
    }

    // Load all tricounts from database
    fun loadTricounts() {
        viewModelScope.launch {
            try {
                _tricounts.value = tricountDao.getAllTricounts()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Insert a new tricount
    fun insertTricount(name: String, description: String) {
        viewModelScope.launch {
            try {
                val tricount = TricountEntity(
                    name = name,
                    description = description
                )
                tricountDao.insertTricount(tricount)
                // Reload the list after inserting
                loadTricounts()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Delete all tricounts (useful for testing)
    fun deleteAllTricounts() {
        viewModelScope.launch {
            try {
                tricountDao.deleteAllTricounts()
                loadTricounts()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}