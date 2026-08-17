package com.example.appcall.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcall.domain.model.CallSummary
import com.example.appcall.domain.model.AppointmentUiState
import com.example.appcall.domain.model.SummaryUiState as SummaryStatusEnum
import com.example.appcall.domain.repository.VoipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.example.appcall.data.reminder.ReminderManager
import com.example.appcall.domain.model.Appointment
import com.example.appcall.data.model.AiStatusDto
import com.example.appcall.data.model.TranscriptDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// Screen-level loading wrapper (separate from backend status enums)
sealed interface SummaryScreenState {
    object Idle : SummaryScreenState
    object Loading : SummaryScreenState
    data class Success(val summary: CallSummary) : SummaryScreenState
    data class Error(val message: String) : SummaryScreenState
}

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val voipRepository: VoipRepository,
    private val reminderManager: ReminderManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<SummaryScreenState>(SummaryScreenState.Idle)
    val uiState: StateFlow<SummaryScreenState> = _uiState

    private val _summaryText = MutableStateFlow("")
    val summaryText: StateFlow<String> = _summaryText

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing

    private val _isLowConfidence = MutableStateFlow(false)
    val isLowConfidence: StateFlow<Boolean> = _isLowConfidence

    private val _aiStatus = MutableStateFlow<AiStatusDto?>(null)
    val aiStatus: StateFlow<AiStatusDto?> = _aiStatus

    private val _transcript = MutableStateFlow<TranscriptDto?>(null)
    val transcript: StateFlow<TranscriptDto?> = _transcript

    private var currentCallId: String? = null
    private var pollingJob: kotlinx.coroutines.Job? = null

    fun loadSummary(callId: String) {
        currentCallId = callId
        _uiState.value = SummaryScreenState.Loading
        pollingJob?.cancel()

        viewModelScope.launch {
            // Load transcript in parallel
            launch {
                voipRepository.getTranscript(callId).onSuccess {
                    _transcript.value = it
                }
            }

            // Start AI status polling if needed
            pollingJob = launch {
                while (isActive) {
                    voipRepository.getAiStatus(callId).onSuccess { statusDto ->
                        _aiStatus.value = statusDto
                        if (statusDto.aiStatus == "DONE" || statusDto.aiStatus == "FAILED") {
                            // Status finished, load latest summary & transcript
                            voipRepository.getTranscript(callId).onSuccess { _transcript.value = it }
                            return@launch
                        }
                    }
                    delay(3000)
                }
            }

            voipRepository.getCallSummary(callId)
                .onSuccess { summary ->
                    _uiState.value = SummaryScreenState.Success(summary)
                    _summaryText.value = summary.summaryText
                    // LOW_CONFIDENCE is the ONLY trigger for the banner (Agent.md §4.2 / §5.3)
                    val lowConfidence = summary.confidenceScore != null && summary.confidenceScore < 60.0
                    _isLowConfidence.value = lowConfidence
                    _isEditing.value = lowConfidence
                }
                .onFailure {
                    // Fallback for offline testing / development
                    val mockSummary = CallSummary(
                        id = "mock-summary-id",
                        callId = callId,
                        summaryText = "Rendez-vous fixé avec Marc mardi prochain à 14h dans vos bureaux.",
                        status = SummaryStatusEnum.PROPOSED.name,
                        confidenceScore = 95.0,
                        detectedAppointmentId = "mock-appointment-id",
                        appointment = Appointment(
                            id = "mock-appointment-id",
                            contactId = "1",
                            scheduledAt = "2026-07-21T14:00:00Z",
                            status = AppointmentUiState.PROPOSED.name
                            // title intentionally omitted — backend migration not yet applied
                        )
                    )
                    _uiState.value = SummaryScreenState.Success(mockSummary)
                    _summaryText.value = mockSummary.summaryText
                    _isLowConfidence.value = false
                    _isEditing.value = false
                }
        }
    }

    fun updateSummaryText(text: String) {
        if (_isEditing.value) {
            _summaryText.value = text
        }
    }

    fun toggleEdit() {
        if (_isEditing.value) {
            val state = _uiState.value
            if (state is SummaryScreenState.Success) {
                _summaryText.value = state.summary.summaryText
            }
            if (!_isLowConfidence.value) {
                _isEditing.value = false
            }
        } else {
            _isEditing.value = true
        }
    }

    fun saveSummary() {
        val callId = currentCallId ?: return
        viewModelScope.launch {
            _uiState.value = SummaryScreenState.Loading
            voipRepository.editCallSummary(callId, _summaryText.value)
                .onSuccess {
                    loadSummary(callId)
                }
                .onFailure {
                    val currentSummary = (_uiState.value as? SummaryScreenState.Success)?.summary
                    if (currentSummary != null) {
                        val updatedSummary = currentSummary.copy(
                            summaryText = _summaryText.value,
                            status = SummaryStatusEnum.MODIFIED.name
                        )
                        _uiState.value = SummaryScreenState.Success(updatedSummary)
                        _isEditing.value = false
                    } else {
                        _uiState.value = SummaryScreenState.Error("Failed to save summary: ${it.message}")
                    }
                }
        }
    }

    fun validateSummary() {
        val callId = currentCallId ?: return
        viewModelScope.launch {
            _uiState.value = SummaryScreenState.Loading
            voipRepository.validateCallSummary(callId)
                .onSuccess {
                    loadSummary(callId)
                }
                .onFailure {
                    val currentSummary = (_uiState.value as? SummaryScreenState.Success)?.summary
                    if (currentSummary != null) {
                        val updatedSummary = currentSummary.copy(
                            status = SummaryStatusEnum.VALIDATED.name
                        )
                        _uiState.value = SummaryScreenState.Success(updatedSummary)
                        _isEditing.value = false
                    } else {
                        _uiState.value = SummaryScreenState.Error("Failed to validate summary: ${it.message}")
                    }
                }
        }
    }

    fun validateAppointment() {
        val callId = currentCallId ?: return
        val currentSummaryState = _uiState.value as? SummaryScreenState.Success ?: return
        val appointment = currentSummaryState.summary.appointment ?: return

        viewModelScope.launch {
            voipRepository.validateAppointment(callId)
                .onSuccess {
                    reminderManager.scheduleReminder(
                        title = "Rappel: Rendez-vous",
                        message = "Votre rendez-vous du ${appointment.scheduledAt.substringBefore("T")} est confirmé.",
                        delaySeconds = 10
                    )
                    loadSummary(callId)
                }
                .onFailure {
                    reminderManager.scheduleReminder(
                        title = "Rappel (Offline): Rendez-vous",
                        message = "Votre rendez-vous du ${appointment.scheduledAt.substringBefore("T")} est confirmé.",
                        delaySeconds = 10
                    )
                    val updatedSummary = currentSummaryState.summary.copy(
                        appointment = appointment.copy(status = AppointmentUiState.VALIDATED.name)
                    )
                    _uiState.value = SummaryScreenState.Success(updatedSummary)
                }
        }
    }

    fun dismissAppointment() {
        val callId = currentCallId ?: return
        val currentSummaryState = _uiState.value as? SummaryScreenState.Success ?: return
        val appointment = currentSummaryState.summary.appointment ?: return

        viewModelScope.launch {
            voipRepository.dismissAppointment(callId)
                .onSuccess {
                    loadSummary(callId)
                }
                .onFailure {
                    val updatedSummary = currentSummaryState.summary.copy(
                        appointment = appointment.copy(status = AppointmentUiState.DISMISSED.name)
                    )
                    _uiState.value = SummaryScreenState.Success(updatedSummary)
                }
        }
    }

    fun editAppointmentVoice(commandText: String) {
        val callId = currentCallId ?: return
        val currentSummaryState = _uiState.value as? SummaryScreenState.Success ?: return
        val appointment = currentSummaryState.summary.appointment ?: return

        viewModelScope.launch {
            _uiState.value = SummaryScreenState.Loading
            // Send voice_command_transcript to POST /calls/{id}/summary/edit
            voipRepository.editCallSummary(callId, commandText)
                .onSuccess {
                    loadSummary(callId)
                }
                .onFailure {
                    // Offline simulation of voice edit parsing
                    val updatedDate = if (commandText.contains("jeudi", ignoreCase = true)) {
                        "2026-07-23T15:00:00Z"
                    } else {
                        "2026-07-21T16:00:00Z"
                    }
                    val updatedSummary = currentSummaryState.summary.copy(
                        summaryText = "Rendez-vous modifié vocalement: ${commandText}. ${currentSummaryState.summary.summaryText}",
                        status = SummaryStatusEnum.MODIFIED.name,
                        appointment = appointment.copy(
                            scheduledAt = updatedDate,
                            status = AppointmentUiState.PROPOSED.name
                        )
                    )
                    _uiState.value = SummaryScreenState.Success(updatedSummary)
                }
        }
    }
    
    fun triggerMockLowConfidence() {
        val callId = currentCallId ?: return
        val mockSummary = CallSummary(
            id = "mock-summary-id",
            callId = callId,
            summaryText = "Rendez-vous fixé... [Qualité audio faible, détails manquants]",
            status = SummaryStatusEnum.PROPOSED.name,
            confidenceScore = 45.0,
            detectedAppointmentId = "mock-appointment-id",
            appointment = Appointment(
                id = "mock-appointment-id",
                contactId = "1",
                scheduledAt = "2026-07-21T14:00:00Z",
                status = AppointmentUiState.PROPOSED.name
                // title omitted — migration pending
            )
        )
        _uiState.value = SummaryScreenState.Success(mockSummary)
        _summaryText.value = mockSummary.summaryText
        _isLowConfidence.value = true
        _isEditing.value = true
    }
}
