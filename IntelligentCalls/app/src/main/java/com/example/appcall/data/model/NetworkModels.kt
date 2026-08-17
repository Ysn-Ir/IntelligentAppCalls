package com.example.appcall.data.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("number") val number: String? = null
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
)

data class TokenResponse(
    @SerializedName("token") val token: String
)

data class CallRequest(
    @SerializedName("contact_id") val contactId: String,
    @SerializedName("direction") val direction: String = "OUTBOUND"
)

data class CallResponse(
    @SerializedName("id") val id: String,
    @SerializedName("contact_id") val contactId: String,
    @SerializedName("direction") val direction: String,
    @SerializedName("status") val status: String,
    @SerializedName("twilio_params") val twilioParams: Map<String, String>? = null
)

data class ConsentRequest(
    @SerializedName("consent_given") val consentGiven: Boolean
)

data class ContactDto(
    @SerializedName("id") val id: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("email") val email: String,
    @SerializedName("global_gdpr_consent") val globalGdprConsent: Boolean
)

data class CallSummaryDto(
    @SerializedName("id") val id: String,
    @SerializedName("call_id") val callId: String,
    @SerializedName("summary_text") val summaryText: String,
    @SerializedName("status") val status: String,
    @SerializedName("confidence_score") val confidenceScore: Double?,
    @SerializedName("detected_appointment_id") val detectedAppointmentId: String?,
    @SerializedName("appointment") val appointment: AppointmentDto? = null
)

data class AppointmentDto(
    @SerializedName("id") val id: String,
    @SerializedName("contact_id") val contactId: String,
    @SerializedName("scheduled_at") val scheduledAt: String,
    @SerializedName("status") val status: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("summary_context") val summaryContext: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("contact_name") val contactName: String? = null
)

data class SummaryEditRequest(
    @SerializedName("new_text") val newText: String?,
    @SerializedName("voice_command_transcript") val voiceCommandTranscript: String?
)

data class CallHistoryItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("contact_id") val contactId: String,
    @SerializedName("direction") val direction: String,
    @SerializedName("status") val status: String,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("ended_at") val endedAt: String?,
    @SerializedName("contact_name") val contactName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("summary_preview") val summaryPreview: String? = null
)

data class ReminderDto(
    @SerializedName("id") val id: String,
    @SerializedName("appointment_id") val appointmentId: String?,
    @SerializedName("call_id") val callId: String?,
    @SerializedName("scheduled_at") val scheduledAt: String,
    @SerializedName("type") val type: String
)

data class TaskDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("completed") val completed: Boolean
)

data class AgendaDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("scheduled_at") val scheduledAt: String,
    @SerializedName("contact_name") val contactName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("call_id") val callId: String? = null,
    @SerializedName("status") val status: String? = "SCHEDULED"
)

data class FileDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("path") val path: String,
    @SerializedName("size") val size: String
)

// ── AI Status ────────────────────────────────────────────────────────────
data class AiStatusDto(
    @SerializedName("call_id") val callId: String,
    @SerializedName("ai_status") val aiStatus: String,       // PENDING | PROCESSING | DONE | FAILED
    @SerializedName("has_transcript") val hasTranscript: Boolean,
    @SerializedName("has_summary") val hasSummary: Boolean,
    @SerializedName("transcript_confidence") val transcriptConfidence: Double?
)

// ── Transcript + Speaker Diarisation ────────────────────────────────────
data class SpeakerSegmentDto(
    @SerializedName("speaker") val speaker: String,           // "agent" | "contact"
    @SerializedName("start") val start: Double,
    @SerializedName("end") val end: Double,
    @SerializedName("text") val text: String
)

data class TranscriptDto(
    @SerializedName("id") val id: String,
    @SerializedName("call_id") val callId: String,
    @SerializedName("raw_text") val rawText: String,
    @SerializedName("language") val language: String,
    @SerializedName("confidence_score") val confidenceScore: Double,
    @SerializedName("speaker_segments") val speakerSegments: List<SpeakerSegmentDto>?
)

// ── Chatbot RAG ──────────────────────────────────────────────────────────
data class ChatRequest(
    @SerializedName("message") val message: String,
    @SerializedName("session_id") val sessionId: String? = null
)

data class ChatSourceDto(
    @SerializedName("call_id") val callId: String?,
    @SerializedName("call_date") val callDate: String?,
    @SerializedName("excerpt") val excerpt: String?
)

data class ChatResponseDto(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("reply") val reply: String,
    @SerializedName("sources") val sources: List<ChatSourceDto>
)

data class ChatMessageDto(
    @SerializedName("role") val role: String,               // "user" | "assistant"
    @SerializedName("content") val content: String
)

data class ChatHistoryResponse(
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("messages") val messages: List<ChatMessageDto>
)

// ── GDPR Full Account Deletion ─────────────────────────────────────────
data class DeleteAccountResponse(
    @SerializedName("status") val status: String,
    @SerializedName("summary") val summary: Map<String, Any>?
)

