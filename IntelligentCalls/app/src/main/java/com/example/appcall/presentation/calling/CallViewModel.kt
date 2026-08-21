package com.example.appcall.presentation.calling

import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcall.data.calling.CallState
import com.example.appcall.data.calling.CallingManager
import com.example.appcall.domain.model.Contact
import com.example.appcall.domain.repository.VoipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val voipRepository: VoipRepository,
    private val callingManager: CallingManager
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _callHistory = MutableStateFlow<List<com.example.appcall.data.model.CallHistoryItemDto>>(emptyList())
    val callHistory: StateFlow<List<com.example.appcall.data.model.CallHistoryItemDto>> = _callHistory

    val callState: StateFlow<CallState> = callingManager.callState
    val transcript: StateFlow<String> = callingManager.transcript

    private val _consentGiven = MutableStateFlow(true)
    val consentGiven: StateFlow<Boolean> = _consentGiven

    init {
        loadContacts()
        loadCallHistory()
    }

    fun loadContacts() {
        viewModelScope.launch {
            voipRepository.getContacts().onSuccess {
                _contacts.value = it
            }.onFailure {
                _contacts.value = emptyList()
            }
        }
    }

    fun setConsentGiven(value: Boolean) {
        _consentGiven.value = value
    }

    fun startCall(contact: Contact) {
        callingManager.startCall(contact)
    }

    fun toggleMute() {
        callingManager.toggleMute()
    }

    fun toggleSpeaker(audioManager: AudioManager) {
        callingManager.toggleSpeaker(audioManager)
    }

    fun hangUp() {
        callingManager.disconnect()
    }

    fun resetCallState() {
        callingManager.reset()
    }

    fun loadCallHistory() {
        viewModelScope.launch {
            voipRepository.getCallHistory().onSuccess {
                _callHistory.value = it
            }
        }
    }

    fun deleteCall(callId: String) {
        viewModelScope.launch {
            voipRepository.deleteCallData(callId)
            _callHistory.value = _callHistory.value.filter { it.id != callId }
        }
    }

    fun clearCallHistory() {
        viewModelScope.launch {
            voipRepository.deleteVoiceData()
            _callHistory.value = emptyList()
        }
    }
}
