package com.example.appcall.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcall.domain.repository.VoipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    object Idle : LoginUiState
    object Loading : LoginUiState
    object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val voipRepository: VoipRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Email and password cannot be empty")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            val result = voipRepository.login(email, password)
            if (result.isSuccess) {
                _uiState.value = LoginUiState.Success
            } else {
                _uiState.value = LoginUiState.Error(result.exceptionOrNull()?.message ?: "Unknown login error")
            }
        }
    }

    fun register(firstName: String, lastName: String, email: String, password: String, number: String?) {
        if (firstName.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Please fill out all required fields")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            val result = voipRepository.register(firstName, lastName, email, password, number)
            if (result.isSuccess) {
                _uiState.value = LoginUiState.Success
            } else {
                _uiState.value = LoginUiState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }
}
