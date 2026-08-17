package com.example.appcall.domain.model

// --- UI State Enums (Agent.md §4.2) — mirror backend enums exactly ---

/** Driven by calls.status from the backend. */
enum class CallUiState { IDLE, RINGING, CONNECTED, ENDED }

/**
 * LOW_CONFIDENCE when transcripts.confidence_score < 60.
 * This is the ONLY trigger for the low-quality banner — do not add other heuristics.
 */
enum class TranscriptionUiState { OFF, LISTENING, LOW_CONFIDENCE }

/** Mirrors call_summaries.status verbatim. */
enum class SummaryUiState { PROPOSED, VALIDATED, MODIFIED }

/** NONE when detected_appointment_id is null. */
enum class AppointmentUiState { NONE, PROPOSED, VALIDATED, DISMISSED }

// --- Domain models ---

data class Contact(
    val id: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val email: String,
    val globalGdprConsent: Boolean
) {
    val fullName: String get() = "$firstName $lastName".trim()
}

data class CallSession(
    val id: String,
    val contactId: String,
    val direction: String,
    val status: String,
    val twilioParams: Map<String, String>? = null
)

data class CallSummary(
    val id: String,
    val callId: String,
    val summaryText: String,
    val status: String,
    val confidenceScore: Double?,
    val detectedAppointmentId: String?,
    val appointment: Appointment? = null
)

data class Appointment(
    val id: String,
    val contactId: String,
    val scheduledAt: String,
    val status: String,
    val title: String? = null,
    val summaryContext: String? = null,
    val phoneNumber: String? = null,
    val contactName: String? = null
)

