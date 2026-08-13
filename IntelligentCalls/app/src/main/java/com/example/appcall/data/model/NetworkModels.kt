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
    @SerializedName("title") val title: String? = null
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
    @SerializedName("contact_name") val contactName: String? = null
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
    @SerializedName("scheduled_at") val scheduledAt: String
)

data class FileDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("path") val path: String,
    @SerializedName("size") val size: String
)
