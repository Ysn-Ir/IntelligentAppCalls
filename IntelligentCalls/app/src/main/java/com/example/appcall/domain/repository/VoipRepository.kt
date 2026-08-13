package com.example.appcall.domain.repository

import com.example.appcall.data.model.CallHistoryItemDto
import com.example.appcall.data.model.ReminderDto
import com.example.appcall.domain.model.CallSession
import com.example.appcall.domain.model.CallSummary
import com.example.appcall.domain.model.Contact

interface VoipRepository {
    suspend fun register(firstName: String, lastName: String, email: String, password: String, number: String?): Result<String>
    suspend fun login(email: String, password: String): Result<String>
    suspend fun initiateCall(contactId: String): Result<CallSession>
    suspend fun endCall(callId: String): Result<CallSession>
    suspend fun getContacts(): Result<List<Contact>>
    suspend fun getCallSummary(callId: String): Result<CallSummary>
    suspend fun validateCallSummary(callId: String): Result<Unit>
    suspend fun editCallSummary(callId: String, newText: String): Result<Unit>
    suspend fun getCallHistory(): Result<List<CallHistoryItemDto>>
    suspend fun validateAppointment(callId: String): Result<Unit>
    suspend fun dismissAppointment(callId: String): Result<Unit>
    suspend fun getReminders(): Result<List<ReminderDto>>
    suspend fun createReminder(reminder: ReminderDto): Result<Unit>
    suspend fun exportVoiceData(): Result<String>
    suspend fun deleteVoiceData(): Result<Unit>
    suspend fun addCallToHistory(callId: String, contactId: String = "1", contactName: String, direction: String, status: String): Result<Unit>

    /**
     * Upload recorded call audio file to the backend.
     * The server will run STT on it and write the transcript row.
     */
    suspend fun uploadCallAudio(callId: String, audioFile: java.io.File): Result<Unit>

    /**
     * Tasks & Agenda Backend + Local Sync API
     */
    suspend fun fetchTasks(): Result<List<com.example.appcall.data.local.LocalTask>>
    suspend fun createTask(id: String, title: String, completed: Boolean): Result<Unit>
    suspend fun toggleTask(id: String, completed: Boolean): Result<Unit>

    suspend fun fetchAgenda(): Result<List<com.example.appcall.data.local.LocalAgendaItem>>
    suspend fun createAgenda(id: String, title: String, time: String): Result<Unit>
}
