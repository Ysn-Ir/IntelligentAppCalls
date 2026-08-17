package com.example.appcall.data.api

import com.example.appcall.data.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<LoginResponse>

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>


    @POST("calls")
    suspend fun initiateCall(
        @Header("Authorization") token: String,
        @Body request: CallRequest
    ): Response<CallResponse>

    @POST("calls/{id}/end")
    suspend fun endCall(
        @Header("Authorization") token: String,
        @Path("id") callId: String
    ): Response<CallResponse>

    @GET("contacts")
    suspend fun getContacts(
        @Header("Authorization") token: String
    ): Response<List<ContactDto>>

    @POST("contacts")
    suspend fun createContact(
        @Header("Authorization") token: String,
        @Body contact: ContactDto
    ): Response<ContactDto>

    @GET("calls/{id}/summary")
    suspend fun getCallSummary(
        @Header("Authorization") token: String,
        @Path("id") callId: String
    ): Response<CallSummaryDto>

    @POST("calls/{id}/summary/validate")
    suspend fun validateCallSummary(
        @Header("Authorization") token: String,
        @Path("id") callId: String
    ): Response<Unit>

    @POST("calls/{id}/summary/edit")
    suspend fun editCallSummary(
        @Header("Authorization") token: String,
        @Path("id") callId: String,
        @Body request: SummaryEditRequest
    ): Response<Unit>

    @GET("calls")
    suspend fun getCallHistory(
        @Header("Authorization") token: String,
        @Query("contact_id") contactId: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null
    ): Response<List<CallHistoryItemDto>>

    @POST("calls/{id}/appointment/validate")
    suspend fun validateAppointment(
        @Header("Authorization") token: String,
        @Path("id") callId: String
    ): Response<Unit>

    @POST("calls/{id}/appointment/dismiss")
    suspend fun dismissAppointment(
        @Header("Authorization") token: String,
        @Path("id") callId: String
    ): Response<Unit>

    @GET("reminders")
    suspend fun getReminders(
        @Header("Authorization") token: String,
        @Query("upcoming") upcoming: Boolean = true
    ): Response<List<ReminderDto>>

    @POST("reminders")
    suspend fun createReminder(
        @Header("Authorization") token: String,
        @Body reminder: ReminderDto
    ): Response<Unit>

    @GET("users/me/voice-data/export")
    suspend fun exportVoiceData(
        @Header("Authorization") token: String
    ): Response<ResponseBody> // we can return ResponseBody to handle file streams or strings

    @DELETE("users/me/voice-data")
    suspend fun deleteVoiceData(
        @Header("Authorization") token: String
    ): Response<Unit>

    @GET("tasks")
    suspend fun getTasks(
        @Header("Authorization") token: String
    ): Response<List<TaskDto>>

    @POST("tasks")
    suspend fun createTask(
        @Header("Authorization") token: String,
        @Body task: TaskDto
    ): Response<TaskDto>

    @DELETE("tasks/{id}")
    suspend fun deleteTask(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>

    @GET("agenda")
    suspend fun getAgenda(
        @Header("Authorization") token: String
    ): Response<List<AgendaDto>>

    @POST("agenda")
    suspend fun createAgendaItem(
        @Header("Authorization") token: String,
        @Body item: AgendaDto
    ): Response<AgendaDto>

    @DELETE("agenda/{id}")
    suspend fun deleteAgendaItem(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>

    @POST("files")
    suspend fun createFileItem(
        @Header("Authorization") token: String,
        @Body file: FileDto
    ): Response<FileDto>

    @DELETE("files/{id}")
    suspend fun deleteFileItem(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>

    @DELETE("calls/{id}")
    suspend fun deleteCall(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>

    @DELETE("contacts/{id}")
    suspend fun deleteContact(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>

    /**
     * Upload the recorded call audio for server-side STT processing.
     * The server feeds this into the Deepgram / Google STT pipeline and writes
     * the result to the transcripts table (UPDATE raw_text on the existing row).
     */
    @Multipart
    @POST("calls/{id}/audio")
    suspend fun uploadCallAudio(
        @Header("Authorization") token: String,
        @Path("id") callId: String,
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    // ── AI Pipeline & Transcript Endpoints ───────────────────────────────
    @GET("calls/{id}/ai-status")
    suspend fun getAiStatus(
        @Header("Authorization") token: String,
        @Path("id") callId: String
    ): Response<AiStatusDto>

    @GET("calls/{id}/transcript")
    suspend fun getTranscript(
        @Header("Authorization") token: String,
        @Path("id") callId: String
    ): Response<TranscriptDto>

    // ── Contact-Scoped & Global Chatbot Endpoints ────────────────────────
    @POST("contacts/{contact_id}/chat")
    suspend fun chatWithContact(
        @Header("Authorization") token: String,
        @Path("contact_id") contactId: String,
        @Body request: ChatRequest
    ): Response<ChatResponseDto>

    @POST("chat")
    suspend fun globalChat(
        @Header("Authorization") token: String,
        @Body request: ChatRequest
    ): Response<ChatResponseDto>

    @GET("contacts/{contact_id}/chat/history")
    suspend fun getContactChatHistory(
        @Header("Authorization") token: String,
        @Path("contact_id") contactId: String
    ): Response<ChatHistoryResponse>

    @DELETE("contacts/{contact_id}/chat")
    suspend fun clearContactChat(
        @Header("Authorization") token: String,
        @Path("contact_id") contactId: String
    ): Response<Unit>

    // ── GDPR Endpoints (Full account, call data, contact anonymization) ──
    @DELETE("me")
    suspend fun deleteAccount(
        @Header("Authorization") token: String
    ): Response<DeleteAccountResponse>

    @GET("me/export")
    suspend fun exportAllData(
        @Header("Authorization") token: String
    ): Response<ResponseBody>

    @DELETE("calls/{id}/data")
    suspend fun deleteCallData(
        @Header("Authorization") token: String,
        @Path("id") callId: String
    ): Response<Unit>

    @DELETE("contacts/{id}/data")
    suspend fun eraseContactData(
        @Header("Authorization") token: String,
        @Path("id") contactId: String
    ): Response<Unit>
}
