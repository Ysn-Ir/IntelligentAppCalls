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
    private val reminderManager: ReminderManager,
    private val localDatabase: com.example.appcall.data.local.AppLocalDatabase
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

    private val _audioFile = MutableStateFlow<java.io.File?>(null)
    val audioFile: StateFlow<java.io.File?> = _audioFile

    private val _isAudioLoading = MutableStateFlow(false)
    val isAudioLoading: StateFlow<Boolean> = _isAudioLoading

    private var currentCallId: String? = null
    private var pollingJob: kotlinx.coroutines.Job? = null

    fun resolveAndFetchAudio(callId: String, context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val targetDirs = listOf(
                java.io.File(context.filesDir, "recordings"),
                java.io.File(context.filesDir, "recordings_native"),
                context.getExternalFilesDir(null)?.let { java.io.File(it, "recordings") },
                context.cacheDir
            ).filterNotNull()

            val allFiles = mutableListOf<java.io.File>()
            targetDirs.forEach { dir ->
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.filter {
                        it.isFile && (it.name.endsWith(".wav") || it.name.endsWith(".mp4") || it.name.endsWith(".m4a")) && it.length() > 512
                    }?.let { allFiles.addAll(it) }
                }
            }

            val prefix = if (callId.length >= 8) callId.take(8) else callId
            val cleanId = callId.removePrefix("native-")
            val matchedLocal = allFiles.firstOrNull {
                it.name.contains(callId, ignoreCase = true) ||
                it.name.contains(prefix, ignoreCase = true) ||
                (cleanId.isNotBlank() && it.name.contains(cleanId, ignoreCase = true))
            }

            if (matchedLocal != null && matchedLocal.exists() && matchedLocal.length() > 512) {
                _audioFile.value = matchedLocal
                return@launch
            }

            // Check if already downloaded cached file
            val cachedFile = java.io.File(context.filesDir, "recordings/call_remote_${callId}.mp4")
            if (cachedFile.exists() && cachedFile.length() > 512) {
                _audioFile.value = cachedFile
                return@launch
            }

            // If not found locally, fetch from backend
            _isAudioLoading.value = true
            voipRepository.downloadCallAudio(callId, cachedFile)
                .onSuccess { downloaded ->
                    if (downloaded.exists() && downloaded.length() > 100) {
                        _audioFile.value = downloaded
                    }
                }
                .onFailure {
                    // Audio not on server, leave as null
                }
            _isAudioLoading.value = false
        }
    }

    fun loadSummary(callId: String) {
        currentCallId = callId
        _uiState.value = SummaryScreenState.Loading
        _transcript.value = null
        _aiStatus.value = null
        _audioFile.value = null
        pollingJob?.cancel()

        viewModelScope.launch {
            // Load transcript in parallel
            launch {
                voipRepository.getTranscript(callId).onSuccess {
                    _transcript.value = it
                }
            }

            // Start AI status & live transcript polling
            pollingJob = launch {
                var attempts = 0
                while (isActive && attempts < 40) {
                    attempts++
                    
                    // Fetch transcript
                    voipRepository.getTranscript(callId).onSuccess { tr ->
                        if (tr.rawText.isNotBlank()) {
                            _transcript.value = tr
                        }
                    }

                    // Fetch latest summary
                    voipRepository.getCallSummary(callId).onSuccess { summary ->
                        val isFinal = summary.status in listOf("CONFIRMED", "VALIDATED", "MODIFIED") && 
                                      !summary.summaryText.startsWith("Traitement IA")
                        if (isFinal) {
                            _uiState.value = SummaryScreenState.Success(summary)
                            _summaryText.value = summary.summaryText
                            val lowConf = summary.confidenceScore != null && summary.confidenceScore < 60.0
                            _isLowConfidence.value = lowConf
                            _isEditing.value = lowConf
                            // Also do one final transcript fetch
                            voipRepository.getTranscript(callId).onSuccess { _transcript.value = it }
                            return@launch
                        } else if (_uiState.value !is SummaryScreenState.Success) {
                            _uiState.value = SummaryScreenState.Success(summary)
                            _summaryText.value = summary.summaryText
                        }
                    }

                    delay(2000)
                }
            }

            voipRepository.getCallSummary(callId)
                .onSuccess { summary ->
                    _uiState.value = SummaryScreenState.Success(summary)
                    _summaryText.value = summary.summaryText
                    val lowConfidence = summary.confidenceScore != null && summary.confidenceScore < 60.0
                    _isLowConfidence.value = lowConfidence
                    _isEditing.value = lowConfidence

                    launch {
                        voipRepository.getTranscript(callId).onSuccess {
                            if (it.rawText.isNotBlank()) _transcript.value = it
                        }
                    }
                }
                .onFailure {
                    // Placeholder while processing
                    val pendingSummary = CallSummary(
                        id = "pending-$callId",
                        callId = callId,
                        summaryText = "Traitement IA en cours... Vos transcriptions et résumés seront affichés dès leur génération.",
                        status = SummaryStatusEnum.PROPOSED.name,
                        confidenceScore = 0.0,
                        detectedAppointmentId = null,
                        appointment = null
                    )
                    _uiState.value = SummaryScreenState.Success(pendingSummary)
                    _summaryText.value = pendingSummary.summaryText
                    _isLowConfidence.value = false
                    _isEditing.value = false
                }
        }
    }

    fun refreshCurrent() {
        currentCallId?.let { loadSummary(it) }
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

        val title = appointment.title ?: "Rendez-vous détecté"
        val scheduledAt = appointment.scheduledAt
        val contactName = appointment.contactName ?: "Contact"
        val phoneNumber = appointment.phoneNumber
        localDatabase.addAgendaAppointment(
            com.example.appcall.data.local.LocalAgendaItem(
                id = appointment.id,
                title = title,
                scheduledAt = scheduledAt,
                contactName = contactName,
                phoneNumber = phoneNumber,
                callId = callId,
                status = "CONFIRMED"
            )
        )

        viewModelScope.launch {
            voipRepository.createAgenda(appointment.id, title, scheduledAt)
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
                        title = "Rappel: Rendez-vous",
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

    fun refreshTranscript() {
        val callId = currentCallId ?: return
        viewModelScope.launch {
            voipRepository.getTranscript(callId).onSuccess {
                _transcript.value = it
            }
        }
    }
}
