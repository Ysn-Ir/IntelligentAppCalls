# AppCall — Intelligent Calls Module: Complete Technical Documentation

> **Last Updated:** 2026-07-17  
> **Source:** `c:\Users\khali\AndroidStudioProjects\appcall`  
> **Package:** `com.example.appcall`  
> **Language:** Kotlin · Jetpack Compose · MVVM + Clean Architecture  

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture Overview](#2-architecture-overview)
3. [Technology Stack & Dependencies](#3-technology-stack--dependencies)
4. [Module Structure Map](#4-module-structure-map)
5. [Application Entry Points](#5-application-entry-points)
6. [Data Layer](#6-data-layer)
   - 6.1 [Network Models](#61-network-models)
   - 6.2 [API Service Contract](#62-api-service-contract)
   - 6.3 [Calling Infrastructure](#63-calling-infrastructure)
   - 6.4 [Local Database (SQLite)](#64-local-database-sqlite)
   - 6.5 [Repository Implementation](#65-repository-implementation)
   - 6.6 [Token Storage](#66-token-storage)
   - 6.7 [Reminder System](#67-reminder-system)
   - 6.8 [Offline Sync Worker](#68-offline-sync-worker)
7. [Domain Layer](#7-domain-layer)
   - 7.1 [Domain Models](#71-domain-models)
   - 7.2 [Repository Interface](#72-repository-interface)
8. [Dependency Injection](#8-dependency-injection)
9. [Presentation Layer](#9-presentation-layer)
   - 9.1 [Theme System](#91-theme-system)
   - 9.2 [Login Screen & ViewModel](#92-login-screen--viewmodel)
   - 9.3 [Call Screen (Dashboard)](#93-call-screen-dashboard)
   - 9.4 [Call History Screen](#94-call-history-screen)
   - 9.5 [Summary Screen & ViewModel](#95-summary-screen--viewmodel)
10. [Navigation Flow](#10-navigation-flow)
11. [Offline-First Strategy](#11-offline-first-strategy)
12. [UI State Machine](#12-ui-state-machine)
13. [API Reference (All Endpoints)](#13-api-reference-all-endpoints)
14. [Database Schema (MariaDB Backend)](#14-database-schema-mariadb-backend)
15. [AI Pipeline](#15-ai-pipeline)
16. [Known Gaps & Pending Work](#16-known-gaps--pending-work)

---

## 1. Project Overview

**Intelligent Calls** is a feature module embedded in a larger WhatsApp-style productivity app. It provides:

| Feature | Description |
|---|---|
| VoIP outbound calls | Place calls via Twilio Programmable Voice / WebRTC |
| Native call interception | Detects native Android phone calls and offers to record them |
| Live transcription | Real-time speech-to-text streamed via WebSocket |
| AI call summaries | Groq / OpenAI generates structured bullet summaries post-call |
| Intent & Sentiment Engine | 10-domain intent classification (Threats, Appointments, Quotes, Billing, Support) & 4-tier sentiment (Hostile, Negative, Positive, Neutral) |
| Appointment detection | AI detects whether the conversation contains a scheduled meeting |
| User validation | Every AI-proposed outcome requires an explicit human validate/dismiss action (Human-in-the-loop) |
| Voice-edit corrections | User can correct the detected appointment by voice command ("change le rendez-vous à 16h30") |
| Offline-first | Everything degrades gracefully to local SQLite (Schema v9) with non-destructive transcript preservation |
| Reminders | WorkManager-based push notification reminders on validated appointments |
| GDPR compliance | Voice data export + deletion endpoints |

> [!IMPORTANT]
> **Non-negotiable product rule (from Agent.md §1):** Every AI-proposed action (summary, detected appointment, extracted task) must remain editable and requires explicit user validation. Auto-confirmation is strictly prohibited.

---

## 2. Architecture Overview

The project follows **Clean Architecture** with three layers:

```
┌────────────────────────────────────────────────┐
│               PRESENTATION LAYER                │
│  Jetpack Compose Screens + ViewModels (MVVM)   │
│  LoginScreen, CallScreen, SummaryScreen         │
└─────────────────────┬──────────────────────────┘
                      │ (StateFlow / Coroutines)
┌─────────────────────▼──────────────────────────┐
│               DOMAIN LAYER                      │
│  VoipRepository (interface)                     │
│  Domain Models: Contact, CallSession,           │
│  CallSummary, Appointment                       │
│  UI State Enums: CallUiState, SummaryUiState    │
└─────────────────────┬──────────────────────────┘
                      │ (Implementation injected via Hilt)
┌─────────────────────▼──────────────────────────┐
│               DATA LAYER                        │
│  VoipRepositoryImpl  ←→  ApiService (Retrofit) │
│  AppLocalDatabase (SQLite)                      │
│  CallingManager (Twilio SDK + fallback)         │
│  LiveTranscriptManager (OkHttp WebSocket)       │
│  TokenStorage (SharedPreferences)               │
│  SyncWorker (WorkManager)                       │
└────────────────────────────────────────────────┘
```

Data flows are **unidirectional**: screens observe `StateFlow`s on ViewModels, ViewModels call domain repository methods, implementations decide whether to hit network or local DB.

---

## 3. Technology Stack & Dependencies

| Category | Library / Tool | Version |
|---|---|---|
| Language | Kotlin | — |
| UI | Jetpack Compose + Material3 | BOM-managed |
| Architecture | MVVM + Clean Architecture | — |
| DI | Hilt | — |
| Network | Retrofit 2 + OkHttp | — |
| JSON | Gson converter | — |
| VoIP | Twilio Programmable Voice SDK | — |
| Real-time | OkHttp WebSocket | — |
| Local DB | Android SQLite (`SQLiteOpenHelper`) | v3 |
| Background work | WorkManager | 2.8.1 |
| Auth | JWT (stored in SharedPreferences) | — |
| compileSdk | 35 | — |
| minSdk | 34 | — |
| JVM Target | Java 11 | — |

**Backend stack (out of scope for Android but referenced):** FastAPI / Python 3.12, MariaDB, Alembic, Twilio Media Streams, Deepgram STT, Claude (Sonnet 5 + Haiku 4.5).

---

## 4. Module Structure Map

```
app/src/main/java/com/example/appcall/
│
├── AppCallApplication.kt         ← @HiltAndroidApp Application class
├── MainActivity.kt               ← Single Activity, all navigation lives here
│
├── data/
│   ├── api/
│   │   └── ApiService.kt         ← Retrofit interface (all 18 endpoints)
│   ├── calling/
│   │   ├── CallingManager.kt     ← VoIP orchestration (Twilio + simulation fallback)
│   │   └── LiveTranscriptManager.kt  ← WebSocket live transcript
│   ├── local/
│   │   ├── AppLocalDatabase.kt   ← SQLite helper (6 tables, offline cache)
│   │   └── SyncWorker.kt         ← WorkManager background sync
│   ├── model/
│   │   └── NetworkModels.kt      ← All Retrofit request/response DTOs
│   ├── reminder/
│   │   ├── ReminderManager.kt    ← WorkManager reminder scheduling
│   │   └── ReminderWorker.kt     ← Notification publisher
│   └── repository/
│       ├── TokenStorage.kt       ← JWT persistence (SharedPreferences)
│       └── VoipRepositoryImpl.kt ← Full repository implementation (offline-first)
│
├── di/
│   ├── NetworkModule.kt          ← Provides OkHttpClient, Retrofit, ApiService
│   └── RepositoryModule.kt       ← Binds VoipRepositoryImpl → VoipRepository
│
├── domain/
│   ├── model/
│   │   └── DomainModels.kt       ← UI state enums + domain data classes
│   └── repository/
│       └── VoipRepository.kt     ← Repository interface (17 methods)
│
└── presentation/
    ├── auth/
    │   ├── LoginScreen.kt        ← Login UI (email + password form)
    │   └── LoginViewModel.kt     ← Login state machine
    ├── calling/
    │   ├── CallScreen.kt         ← Dashboard: Contacts + Active Call Overlay
    │   ├── CallHistoryScreen.kt  ← Chat-style call history list
    │   └── CallViewModel.kt      ← Contacts, call state, history management
    ├── summary/
    │   ├── SummaryScreen.kt      ← Post-call: summary + appointment card
    │   └── SummaryViewModel.kt   ← Summary loading, edit, validate, dismiss
    └── theme/
        └── Theme.kt              ← Color palette + Material3 theme definition
```

---

## 5. Application Entry Points

### [AppCallApplication.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/AppCallApplication.kt)

Minimal `Application` subclass annotated with `@HiltAndroidApp`. This triggers the Hilt code-generation and seeds the dependency graph.

```kotlin
@HiltAndroidApp
class AppCallApplication : Application()
```

---

### [MainActivity.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/MainActivity.kt)

The **single Activity** for the entire app. Responsibilities:

| Responsibility | Detail |
|---|---|
| Permission requests | `RECORD_AUDIO`, `READ_PHONE_STATE`, `READ_CALL_LOG`, `SYSTEM_ALERT_WINDOW` |
| WorkManager setup | Schedules `SyncWorker` with `NetworkType.CONNECTED` constraint on every launch |
| Native call interception | Registers a `BroadcastReceiver` for `ACTION_PHONE_STATE_CHANGED` |
| Native call consent dialog | `AlertDialog` shown when a native phone call is detected mid-activity |
| Compose navigation | `when(currentScreen)` switch between `LOGIN`, `DASHBOARD`, `SUMMARY` |
| Bottom navigation bar | 6-tab bottom nav: To-do list, Agenda, Assistant IA, Fichiers, Appels, Paramètres |
| Section routing | Tabs 0–5 render different inline composables directly in the activity |

**Navigation state (held in `mutableStateOf`):**

| State variable | Type | Purpose |
|---|---|---|
| `currentScreen` | `AppScreen` (enum) | `LOGIN / DASHBOARD / SUMMARY` |
| `activeCallIdForSummary` | `String` | Call ID passed to Summary screen |
| `selectedSection` | `Int` | Bottom nav tab index (0–5) |
| `showInterceptConsent` | `Boolean` | Whether the native call dialog is visible |
| `interceptedNumber` | `String` | Phone number of the intercepted native call |

**Bottom Navigation Tabs:**

| Index | Label | Content |
|---|---|---|
| 0 | To-do list | Local SQLite tasks list (add/toggle) |
| 1 | Agenda | Local SQLite agenda items |
| 2 | Assistant IA | Placeholder "Coming Soon" |
| 3 | Fichiers | Local file browser |
| 4 | Appels | `CallScreen` (VoIP calls + history) |
| 5 | Paramètres | Settings with GDPR export/delete |

---

## 6. Data Layer

### 6.1 Network Models

**File:** [NetworkModels.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/data/model/NetworkModels.kt)

All DTOs use `@SerializedName` to map exactly to the FastAPI snake_case JSON contract.

| Data Class | Fields | Used By |
|---|---|---|
| `LoginRequest` | `email`, `password` | `POST /auth/login` |
| `LoginResponse` | `access_token`, `token_type` | Response from login |
| `TokenResponse` | `token` | `GET /voip/token` |
| `CallRequest` | `contact_id`, `direction` (default `"OUTBOUND"`) | `POST /calls` |
| `CallResponse` | `id`, `contact_id`, `direction`, `status`, `twilio_params?` | Initiate + end call |
| `ConsentRequest` | `consent_given: Boolean` | `POST /calls/{id}/consent` |
| `ContactDto` | `id`, `first_name`, `last_name`, `phone_number`, `email`, `global_gdpr_consent` | Contact list |
| `CallSummaryDto` | `id`, `call_id`, `summary_text`, `status`, `confidence_score?`, `detected_appointment_id?`, `appointment?` | Summary fetch |
| `AppointmentDto` | `id`, `contact_id`, `scheduled_at`, `status`, `title?` | Nested in summary |
| `SummaryEditRequest` | `new_text?`, `voice_command_transcript?` | Edit/voice-edit summary |
| `CallHistoryItemDto` | `id`, `contact_id`, `direction`, `status`, `started_at?`, `ended_at?`, `contact_name?` | Call history list |
| `ReminderDto` | `id`, `appointment_id?`, `call_id?`, `scheduled_at`, `type` | Reminder CRUD |

> [!NOTE]
> `AppointmentDto.title` is present as a nullable field but the backend DB column does not yet exist (pending migration per §3.2 of Agent.md). Do not display it in the appointment card UI.

---

### 6.2 API Service Contract

**File:** [ApiService.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/data/api/ApiService.kt)

Retrofit interface. Base URL: `http://10.0.2.2:8000/api/v1/` (Android emulator loopback to host machine).

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `auth/login` | None | JWT login |
| `GET` | `voip/token` | Bearer | Twilio Access Token (~1h TTL) |
| `POST` | `calls` | Bearer | Initiate outbound call |
| `POST` | `calls/{id}/consent` | Bearer | Record GDPR consent for this call |
| `POST` | `calls/{id}/end` | Bearer | End the call, trigger summary job |
| `GET` | `contacts` | Bearer | Fetch contact list |
| `POST` | `contacts` | Bearer | Create a new contact |
| `GET` | `calls/{id}/summary` | Bearer | Fetch post-call summary + appointment |
| `POST` | `calls/{id}/summary/validate` | Bearer | Mark summary as `VALIDATED` |
| `POST` | `calls/{id}/summary/edit` | Bearer | Edit summary text or send voice command |
| `GET` | `calls` | Bearer | Paginated call history (optional `contact_id`, `status`, `page`) |
| `POST` | `calls/{id}/appointment/validate` | Bearer | Validate detected appointment (creates reminder row) |
| `POST` | `calls/{id}/appointment/dismiss` | Bearer | Dismiss detected appointment |
| `GET` | `reminders` | Bearer | List reminders (`upcoming=true` default) |
| `POST` | `reminders` | Bearer | Create reminder manually |
| `GET` | `users/me/voice-data/export` | Bearer | GDPR: export all voice data |
| `DELETE` | `users/me/voice-data` | Bearer | GDPR: delete all voice data |
| `POST` (Multipart) | `calls/{id}/audio` | Bearer | Upload recorded call audio for server-side STT |

All methods return `Response<T>` (Retrofit's `Response` wrapper) to allow manual HTTP code inspection.

---

### 6.3 Calling Infrastructure

#### [CallingManager.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/data/calling/CallingManager.kt)

`@Singleton` class that orchestrates the entire call lifecycle. Injected into `CallViewModel`.

**`CallState` sealed interface:**

| State | Payload | When |
|---|---|---|
| `Idle` | — | No active call |
| `Connecting` | — | Token fetch in progress |
| `Active` | `callId`, `contactName`, `isMuted`, `isSpeakerOn` | Call connected |
| `Disconnected` | — | Call ended (user or remote) |
| `Error` | `message` | SDK connection failure |

**`startCall()` flow (5-step sequence):**

1. Transition to `CallState.Connecting`
2. `getVoipToken()` → on failure, fall back to `simulateCall()`
3. `initiateCall(contactId)` → creates the call row on the backend
4. `submitConsent(callId, consentGiven)` → **mandatory before any audio routing**
5. `Voice.connect()` via Twilio SDK → on any exception, fall back to `simulateCall()`

**Simulation fallback** (`simulateCall()`): Used when Twilio binaries are absent or backend is unreachable. Plays a scripted French conversation (7 lines, 3-second intervals) into the transcript StateFlow to simulate WebSocket arrival. Also starts real microphone recording if consent is given.

**Audio recording** (`startLocalAudioRecording()` / `stopLocalAudioRecording()`):
- Saves to `context.filesDir/recordings/call_record_{timestamp}.mp4`
- Uses `MediaRecorder` with `MIC` source, `MPEG_4` format, `AAC` encoder
- File uploaded to `POST /calls/{id}/audio` after call ends
- Mirrors Twilio Media Streams path for offline testing

**Public methods:**

| Method | Description |
|---|---|
| `startCall(contactId, contactName, consentGiven)` | Full call initiation flow |
| `toggleMute()` | Mutes/unmutes active Twilio call |
| `toggleSpeaker(audioManager)` | Toggles speakerphone via `AudioManager` |
| `disconnect()` | Hangs up, stops recording, uploads audio, calls `endCall` on backend |
| `reset()` | Returns to `Idle` state, cancels all coroutines |

---

#### [LiveTranscriptManager.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/data/calling/LiveTranscriptManager.kt)

`@Singleton` managing the WebSocket connection to `WS /ws/calls/{id}/live-transcript`.

**Connection URL:** `ws://10.0.2.2:8000/api/v1/ws/calls/{callId}/live-transcript`  
**Auth:** `Authorization: Bearer {jwt}` header on the WebSocket upgrade request.

**Message parsing** (`parseTranscriptChunk()`): Accepts JSON with any of `text`, `raw_text`, or `transcript` key, or falls back to raw string. Each chunk is **appended** to the existing transcript (mirrors the backend's `UPDATE raw_text` design — one transcript row per call).

**StateFlows:**
- `transcript: StateFlow<String>` — accumulated transcript text
- `isConnected: StateFlow<Boolean>` — WebSocket connection status

---

### 6.4 Local Database (SQLite)

**File:** [AppLocalDatabase.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/data/local/AppLocalDatabase.kt)

`SQLiteOpenHelper` subclass (`DATABASE_VERSION = 3`). Injected as `@Singleton` via Hilt.

**Tables:**

| Table | Purpose | Key Columns |
|---|---|---|
| `calls` | Cache for call summaries | `call_id (PK)`, `summary_text`, `confidence_score`, `summary_status`, `appointment_id`, `appointment_date`, `appointment_status` |
| `sync_queue` | Pending offline actions | `sync_id (AUTOINCREMENT PK)`, `call_id`, `action_type`, `file_path`, `payload` |
| `tasks` | To-do list items | `task_id (PK)`, `title`, `completed (INT 0/1)` |
| `agenda` | Calendar appointments | `agenda_id (PK)`, `title`, `scheduled_at` |
| `files` | File browser entries | `file_id (PK)`, `name`, `path`, `size` |
| `call_history` | Call history items | `hist_id (PK)`, `contact_id`, `contact_name`, `direction`, `status`, `started_at`, `ended_at` |

**Seeded mock data** (on fresh install):

| Table | Data |
|---|---|
| `tasks` | "Appeler le client pour validation" (pending), "Préparer la présentation commerciale" (done) |
| `agenda` | "Réunion d'équipe hebdomadaire" on 2026-07-17T10:00:00Z |
| `call_history` | Jean Dupont OUTBOUND COMPLETED, Marie Martin INBOUND MISSED |

**Sync queue action types:** `UPLOAD_AUDIO`, `EDIT_SUMMARY`, `VALIDATE_SUMMARY`, `VALIDATE_APP`, `DISMISS_APP`

**Local data classes:**

```kotlin
LocalCallHistoryItem(id, contactId, contactName, direction, status, startedAt, endedAt?)
LocalTask(id, title, completed)
LocalAgendaItem(id, title, scheduledAt)
LocalFileItem(id, name, path, size)
SyncItem(id, callId, actionType, filePath?, payload?)
```

---

### 6.5 Repository Implementation

**File:** [VoipRepositoryImpl.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/data/repository/VoipRepositoryImpl.kt)

`@Singleton` annotated. Implements `VoipRepository`. Constructor-injected with `ApiService`, `TokenStorage`, and `AppLocalDatabase`.

**Offline-first pattern** applied consistently:

| Method | Online | Offline / Error |
|---|---|---|
| `login()` | Stores JWT in `TokenStorage` | Returns `Result.failure` |
| `getVoipToken()` | Returns Twilio token | Falls back to `"Bearer dummy_test_token"` |
| `initiateCall()` | Creates server call row | Creates local mock `CallSession` |
| `submitConsent()` | Posts consent flag | Propagates failure |
| `endCall()` | Updates server + local DB | Updates local DB, returns mock `CallSession` |
| `getContacts()` | Maps DTOs to `Contact` domain objects | Returns 2 hardcoded French contacts |
| `getCallSummary()` | Caches to SQLite | Checks SQLite cache → mock summary |
| `validateCallSummary()` | Updates server | Adds to sync queue, returns success |
| `editCallSummary()` | Updates server | Adds to sync queue, returns success |
| `getCallHistory()` | Syncs to local DB | Reads from local DB |
| `validateAppointment()` | Updates server | Adds to sync queue |
| `dismissAppointment()` | Updates server | Adds to sync queue |
| `getReminders()` | Returns list | Returns empty list |
| `createReminder()` | Creates server reminder | Returns mock success |
| `exportVoiceData()` | Returns body string | Returns mock JSON payload |
| `deleteVoiceData()` | Deletes server data | Returns mock success |
| `uploadCallAudio()` | Uploads MP4 multipart | Adds to sync queue with file path |
| `addCallToHistory()` | Write-only to local DB | — |

> [!NOTE]
> All methods that touch the backend accept `tokenStorage.authHeader ?: "Bearer dummy_test_token"` as the fallback — this allows the app to function completely without a live backend during development.

---

### 6.6 Token Storage

**File:** [TokenStorage.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/data/repository/TokenStorage.kt)

`@Singleton`. Stores the JWT in `SharedPreferences` under key `jwt_token` in the `appcall_prefs` store.

| Property | Description |
|---|---|
| `token: String?` | Get/set the raw JWT |
| `authHeader: String?` | Returns `"Bearer {token}"` or `null` |
| `clear()` | Removes the token (logout) |

---

### 6.7 Reminder System

**[ReminderManager.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/data/reminder/ReminderManager.kt)** — `@Singleton`

Schedules a `OneTimeWorkRequest` for `ReminderWorker` with a configurable delay in seconds. Used by `SummaryViewModel.validateAppointment()` to fire a notification 10 seconds after the user validates an appointment.

```kotlin
reminderManager.scheduleReminder(
    title = "Rappel: Rendez-vous",
    message = "Votre rendez-vous du {date} est confirmé.",
    delaySeconds = 10
)
```

---

**[ReminderWorker.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/data/reminder/ReminderWorker.kt)** — `CoroutineWorker`

Fires a high-priority Android notification on channel `intelligent_calls_reminders`.

- Creates the notification channel if running on Android O+
- Uses `NotificationCompat.Builder` with `PRIORITY_HIGH` + `setAutoCancel(true)`
- Reads `title` and `message` from `WorkerParameters.inputData`

---

### 6.8 Offline Sync Worker

**File:** [SyncWorker.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/data/local/SyncWorker.kt)

`CoroutineWorker` launched by `MainActivity.onCreate()` when the device has network connectivity (`NetworkType.CONNECTED` constraint). Uses a Hilt `@EntryPoint` to resolve dependencies.

**Processing logic** — iterates `sync_queue` rows in insertion order:

| Action Type | What it does |
|---|---|
| `UPLOAD_AUDIO` | Re-reads the file from `file_path`, uploads via multipart to `/calls/{id}/audio`, deletes local file on success |
| `EDIT_SUMMARY` | Sends `payload` (edited text) to `/calls/{id}/summary/edit` |
| `VALIDATE_SUMMARY` | Calls `POST /calls/{id}/summary/validate` |
| `VALIDATE_APP` | Calls `POST /calls/{id}/appointment/validate` |
| `DISMISS_APP` | Calls `POST /calls/{id}/appointment/dismiss` |

On complete item success: removes row from `sync_queue`. On any failure: sets `failedAny = true`, returns `Result.retry()` so WorkManager retries the job.

---

## 7. Domain Layer

### 7.1 Domain Models

**File:** [DomainModels.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/domain/model/DomainModels.kt)

**UI State Enums** (mirror backend enums exactly — do not add extra values):

| Enum | Values | Driven by |
|---|---|---|
| `CallUiState` | `IDLE, RINGING, CONNECTED, ENDED` | `calls.status` |
| `TranscriptionUiState` | `OFF, LISTENING, LOW_CONFIDENCE` | `confidence_score < 60` |
| `SummaryUiState` | `PROPOSED, VALIDATED, MODIFIED` | `call_summaries.status` |
| `AppointmentUiState` | `NONE, PROPOSED, VALIDATED, DISMISSED` | `detected_appointment_id` presence |

> [!IMPORTANT]
> `TranscriptionUiState.LOW_CONFIDENCE` is the **only** trigger for the low-quality banner and for defaulting the summary into edit mode. No other heuristics permitted (Agent.md §4.2).

**Domain Data Classes:**

```kotlin
data class Contact(id, firstName, lastName, phoneNumber, email, globalGdprConsent) {
    val fullName: String  // computed: "firstName lastName"
}

data class CallSession(id, contactId, direction, status, twilioParams?)

data class CallSummary(id, callId, summaryText, status, confidenceScore?, detectedAppointmentId?, appointment?)

data class Appointment(id, contactId, scheduledAt, status, title?) // title is a stub — backend migration pending
```

---

### 7.2 Repository Interface

**File:** [VoipRepository.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/domain/repository/VoipRepository.kt)

All methods are `suspend` and return `Result<T>`. 17 methods total:

```kotlin
interface VoipRepository {
    suspend fun login(email, password): Result<String>
    suspend fun getVoipToken(): Result<String>
    suspend fun initiateCall(contactId): Result<CallSession>
    suspend fun submitConsent(callId, consentGiven): Result<Unit>
    suspend fun endCall(callId): Result<CallSession>
    suspend fun getContacts(): Result<List<Contact>>
    suspend fun getCallSummary(callId): Result<CallSummary>
    suspend fun validateCallSummary(callId): Result<Unit>
    suspend fun editCallSummary(callId, newText): Result<Unit>
    suspend fun getCallHistory(): Result<List<CallHistoryItemDto>>
    suspend fun validateAppointment(callId): Result<Unit>
    suspend fun dismissAppointment(callId): Result<Unit>
    suspend fun getReminders(): Result<List<ReminderDto>>
    suspend fun createReminder(reminder): Result<Unit>
    suspend fun exportVoiceData(): Result<String>
    suspend fun deleteVoiceData(): Result<Unit>
    suspend fun addCallToHistory(callId, contactName, direction, status): Result<Unit>
    suspend fun uploadCallAudio(callId, audioFile: File): Result<Unit>
}
```

---

## 8. Dependency Injection

### [NetworkModule.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/di/NetworkModule.kt)

`@Module @InstallIn(SingletonComponent::class)` providing:

| Binding | Scope | Details |
|---|---|---|
| `HttpLoggingInterceptor` | Singleton | `Level.BODY` — logs full request/response |
| `OkHttpClient` | Singleton | One interceptor: `HttpLoggingInterceptor` |
| `Retrofit` | Singleton | Base URL `http://10.0.2.2:8000/api/v1/`, Gson converter |
| `ApiService` | Singleton | Created via `retrofit.create(ApiService::class.java)` |

> [!WARNING]
> The base URL `http://10.0.2.2:8000` is the Android emulator's loopback to the host machine. For a real device on the same network, this must change to the actual host IP.

---

### [RepositoryModule.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/di/RepositoryModule.kt)

`@Module @InstallIn(SingletonComponent::class)` — abstract module:

```kotlin
@Binds @Singleton
abstract fun bindVoipRepository(impl: VoipRepositoryImpl): VoipRepository
```

---

## 9. Presentation Layer

### 9.1 Theme System

**File:** [Theme.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/presentation/theme/Theme.kt)

WhatsApp-inspired dark color scheme:

| Token | Hex | Role |
|---|---|---|
| `DarkIndigo` / `SlateBackground` | `#111B21` | App background |
| `ElectricViolet` | `#075E54` | Primary (WhatsApp dark green) |
| `NeonTeal` | `#25D366` | Accent / active indicators |
| `CardBackground` | `#202C33` | Surface / card background |
| `OnCardText` | `#E9EDF0` | Text on dark surfaces |

`AppCallTheme` wraps `MaterialTheme` and selects between `DarkColorScheme` and `LightColorScheme` based on `isSystemInDarkTheme()`.

---

### 9.2 Login Screen & ViewModel

**[LoginScreen.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/presentation/auth/LoginScreen.kt)**

Dark-gradient card centered on screen. Pre-filled with `test@example.com` / `password` for rapid testing. Displays a `CircularProgressIndicator` while loading and an inline error message on failure. Calls `onLoginSuccess()` callback on `LoginUiState.Success`.

**[LoginViewModel.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/presentation/auth/LoginViewModel.kt)**

`@HiltViewModel`. State machine:

| State | Trigger |
|---|---|
| `Idle` | Initial |
| `Loading` | `login()` called with non-blank credentials |
| `Success` | `voipRepository.login()` returns `Result.success` |
| `Error(message)` | Blank credentials or repository failure |

---

### 9.3 Call Screen (Dashboard)

**[CallScreen.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/presentation/calling/CallScreen.kt)**

Top-level dashboard composable. Contains two tabs: **Contacts** and **Call History**.

**Contacts tab:**
- GDPR Consent checkbox card ("AI Transcription Consent") — toggles `consentGiven` state
- Lazy list of `ContactRow` composables (one per contact)
- Each contact row shows `fullName`, `phoneNumber`, and a `CALL` button
- Pressing `CALL` triggers `viewModel.startCall(contact)`

**Active Call Overlay** (`ActiveCallOverlay`):
- Slides in via `AnimatedVisibility(fadeIn + slideInVertically)` when `callState != Idle`
- States rendered: `Connecting` (progress spinner), `Active`, `Disconnected`, `Error`
- **Active state** shows: contact name, transcription status, Mute/Speaker toggles, live transcript card (scrollable, auto-scrolled to bottom), red End button
- On dismiss after disconnect: navigates to `SummaryScreen` if consent was given

**[CallViewModel.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/presentation/calling/CallViewModel.kt)**

`@HiltViewModel`. StateFlows:

| Flow | Type | Source |
|---|---|---|
| `contacts` | `List<Contact>` | `voipRepository.getContacts()` |
| `callHistory` | `List<CallHistoryItemDto>` | `voipRepository.getCallHistory()` |
| `callState` | `CallState` | Forwarded from `CallingManager.callState` |
| `transcript` | `String` | Forwarded from `CallingManager.transcript` |
| `consentGiven` | `Boolean` | User-toggled via `setConsentGiven()` |

---

### 9.4 Call History Screen

**[CallHistoryScreen.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/presentation/calling/CallHistoryScreen.kt)**

Lazy list of `CallHistoryRow` composables. Shows empty state when list is empty.

**`CallHistoryRow`** displays:
- Circular avatar with initials (first letter of each name part)
- Contact name + direction/status sub-label
- Status icon color: `COMPLETED`→ NeonTeal (green), `MISSED`→ amber, else → red
- Status icons: `COMPLETED`→ Call icon, `MISSED`→ Close icon, else → Warning icon
- Time string (HH:MM) and date string extracted from `started_at` ISO timestamp

Tapping a row calls `onCallClick(item.id)` which navigates to `SummaryScreen`.

---

### 9.5 Summary Screen & ViewModel

**[SummaryScreen.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/presentation/summary/SummaryScreen.kt)** (474 lines)

Post-call review screen. Composed of:

1. **Header row**: back arrow + "Call Summary" title
2. **Low confidence banner**: orange warning bar shown when `isLowConfidence == true`
3. **Summary text card**: editable `OutlinedTextField` or read-only text; Edit/Save toggles
4. **Summary status chip**: shows `PROPOSED` / `VALIDATED` / `MODIFIED` badge
5. **Validate summary button**: only shown when status is `PROPOSED` or `MODIFIED`
6. **Appointment card** (rendered only when `appointment != null`):
   - Shows `scheduled_at` date/time
   - Validate button → `viewModel.validateAppointment()`
   - Dismiss button → `viewModel.dismissAppointment()`
   - Mic button → starts voice recording, sends text to `viewModel.editAppointmentVoice()`
7. **Debug section** (development only): "Trigger Low Confidence Mock" button

**[SummaryViewModel.kt](file:///c:/Users/khali/AndroidStudioProjects/appcall/app/src/main/java/com/example/appcall/presentation/summary/SummaryViewModel.kt)**

`@HiltViewModel`. StateFlows:

| Flow | Type | Description |
|---|---|---|
| `uiState` | `SummaryScreenState` | `Idle / Loading / Success / Error` |
| `summaryText` | `String` | Currently displayed/edited summary text |
| `isEditing` | `Boolean` | Whether the summary text field is editable |
| `isLowConfidence` | `Boolean` | `true` when `confidenceScore < 60` |

**`SummaryScreenState`** (screen-level loading wrapper, separate from backend enums):

```kotlin
sealed interface SummaryScreenState {
    object Idle
    object Loading
    data class Success(val summary: CallSummary)
    data class Error(val message: String)
}
```

**Key methods:**

| Method | Behavior |
|---|---|
| `loadSummary(callId)` | Fetches from backend, caches to SQLite, or uses mock on failure |
| `toggleEdit()` | Toggles edit mode; cancels edits and restores original on cancel |
| `saveSummary()` | Calls `editCallSummary()`, reloads on success |
| `validateSummary()` | Calls `validateCallSummary()`, reloads on success |
| `validateAppointment()` | Calls `validateAppointment()`, schedules reminder, reloads |
| `dismissAppointment()` | Calls `dismissAppointment()`, reloads |
| `editAppointmentVoice(commandText)` | Sends voice transcript to `editCallSummary()` for Haiku processing |
| `triggerMockLowConfidence()` | Development helper: injects `confidenceScore = 45.0` summary |

> [!IMPORTANT]
> When `isLowConfidence == true`, `isEditing` is forced to `true` and cannot be cancelled by the user. This is the mandatory behavior per Agent.md §5.3.

---

## 10. Navigation Flow

```
App Launch
    │
    ├─▶ LOGIN screen
    │       │ (login success)
    │       ▼
    ├─▶ DASHBOARD (bottom nav tab 4: "Appels")
    │       │
    │       ├─ [Contacts tab] Press CALL button
    │       │       │
    │       │       ▼
    │       │   Active Call Overlay (animated over dashboard)
    │       │       │ (call ends + consent given)
    │       │       ▼
    │       ├─▶ SUMMARY screen
    │       │       │ (back button)
    │       │       ▼
    │       │   DASHBOARD
    │       │
    │       └─ [History tab] Tap call row
    │               ▼
    │           SUMMARY screen (read historical summary)
    │
    └─ [Native phone call detected via BroadcastReceiver]
            │
            ▼
        Intercept Consent Dialog (AlertDialog)
            │ (Yes / No)
            ▼
        Active Call Overlay starts
```

**`AppScreen` enum:** `LOGIN`, `DASHBOARD`, `SUMMARY`

Navigation is simple state-based (`mutableStateOf(AppScreen)`), not Jetpack Navigation. `callId` is passed directly as `activeCallIdForSummary` state.

---

## 11. Offline-First Strategy

The app is designed to work without a backend. The strategy per-operation:

| Tier | Behavior |
|---|---|
| **1. Online** | Hit the API, cache to SQLite, return live data |
| **2. SQLite cache** | Return cached data when API fails |
| **3. Mock fallback** | Return hardcoded mock data when both fail |
| **4. Sync queue** | Mutations that fail are queued and retried via `SyncWorker` when connectivity returns |

The `SyncWorker` is a `OneTimeWorkRequest` with `NetworkType.CONNECTED` constraint, enqueued with `ExistingWorkPolicy.KEEP` on every app launch so it runs exactly once when online.

---

## 12. UI State Machine

### Call Lifecycle

```
IDLE → [startCall()] → CONNECTING → [Twilio/sim success] → ACTIVE
                                                               │
                                                    [hangUp() / onDisconnected()]
                                                               │
                                                          DISCONNECTED
                                                               │
                                              [consent=true] → navigate to SUMMARY
```

### Summary Lifecycle

```
IDLE → loadSummary() → LOADING → SUCCESS(summary)
                                      │
                     ┌────────────────┼────────────────────┐
                     │                │                    │
              [confidenceScore<60]  [else]         appointment?≠null
                     │                │                    │
               isLowConfidence=true   │            AppointmentUiState:
               isEditing=true         │            PROPOSED → VALIDATED or DISMISSED
                     │                │
              [saveSummary()]   [validateSummary()]
                     │                │
                status=MODIFIED  status=VALIDATED
```

---

## 13. API Reference (All Endpoints)

Full contract from Agent.md §5:

```
POST   /auth/login                          → { access_token, token_type }
POST   /auth/refresh                        → (not yet in Android client)
GET    /voip/token                          → { token }
GET    /users/me                            → (not yet in Android client)
GET    /contacts                            → [ContactDto]
POST   /contacts                            → ContactDto
PATCH  /contacts/{id}/gdpr-consent          → (not yet in Android client)
POST   /calls/{id}/consent                  → 200 OK
POST   /calls                               → CallResponse
GET    /calls?contact_id=&status=&page=     → [CallHistoryItemDto]
GET    /calls/{id}                          → (not yet in Android client)
POST   /calls/{id}/end                      → CallResponse
GET    /calls/{id}/transcript               → (not yet in Android client)
WS     /ws/calls/{id}/live-transcript       → text/JSON chunks
GET    /calls/{id}/summary                  → CallSummaryDto
POST   /calls/{id}/summary/validate         → 200 OK
POST   /calls/{id}/summary/edit             → 200 OK
POST   /calls/{id}/appointment/validate     → 200 OK
POST   /calls/{id}/appointment/dismiss      → 200 OK
GET    /reminders?upcoming=true             → [ReminderDto]
POST   /reminders                           → 200 OK
POST   /webhooks/twilio/voice               → (server only, no JWT)
POST   /webhooks/twilio/status              → (server only, no JWT)
POST   /webhooks/twilio/media-stream        → (server only, no JWT)
GET    /users/me/voice-data/export          → binary / string response
DELETE /users/me/voice-data                 → 200 OK
POST   /calls/{id}/audio                    → multipart upload
```

---

## 14. Database Schema (MariaDB Backend)

As defined in Agent.md §3 — already deployed, treat as ground truth:

```sql
users (id CHAR(36), first_name, last_name, email, number, created_at)

contacts (id CHAR(36), first_name, last_name, phone_number, email,
          global_gdpr_consent, created_at)

calls (id CHAR(36), contact_id, user_id,
       direction VARCHAR CHECK IN ('INBOUND','OUTBOUND'),
       started_at, ended_at,
       status VARCHAR CHECK IN ('COMPLETED','MISSED','FAILED','ONGOING'),
       consent_given, consent_timestamp, created_at)

transcripts (id CHAR(36), call_id UNIQUE, raw_text, language,
             confidence_score DECIMAL(5,2),  -- 0–100 scale
             created_at)

call_summaries (id CHAR(36), call_id UNIQUE, summary_text,
                detected_appointment_id,
                status VARCHAR CHECK IN ('PROPOSED','VALIDATED','MODIFIED'),
                modified_count, created_at, updated_at)

appointments (id CHAR(36), contact_id, user_id, scheduled_at,
              status DEFAULT 'SCHEDULED', created_at)
```

**Pending additions (not yet migrated):**
- `appointments.title VARCHAR(255)` — blocks appointment card title display
- `reminders` table (full DDL in Agent.md §3.2) — blocks Phase 3 reminder backend

---

## 15. AI Pipeline

### Post-call summary (Claude Sonnet 5)

Triggered as a background job when the call ends. Takes `transcripts.raw_text` as input.

**Prompt output JSON schema:**
```json
{
  "summary": "2-4 sentence summary",
  "appointment_detected": true | false,
  "appointment": {
    "title": "short label",
    "date": "YYYY-MM-DD or null",
    "time": "HH:MM or null",
    "confidence": 0.0-1.0
  } | null
}
```

Writes: `call_summaries` row (`status='PROPOSED'`) + optional `appointments` row.

### Voice-edit command (Claude Haiku 4.5)

Triggered by `POST /calls/{id}/summary/edit` when `voice_command_transcript` is present.

```
Current appointment: {appointment_json}
User voice command (already transcribed): "{voice_command_text}"
Return the updated appointment in the same JSON schema.
```

On success: `call_summaries.status → 'MODIFIED'`, `modified_count += 1`.

### Edge Cases

| Condition | Required Behavior |
|---|---|
| `appointment_detected: false` | Write summary only, leave `detected_appointment_id = NULL` |
| `confidence_score < 60` | App shows "low audio quality" banner, opens edit mode by default |
| `calls.status = FAILED` | Still run summary job on partial `raw_text`; prompt notes transcript may be partial |

---

## 16. Known Gaps & Pending Work

### Schema gaps (block features)
- `appointments.title` column missing → appointment card shows date/time only, no title
- `reminders` table missing → reminder feature uses client-only WorkManager, no server persistence

### Android client gaps (not yet implemented)
- `POST /auth/refresh` — no token refresh logic; expired JWTs require re-login
- `GET /users/me` — user profile not fetched/displayed
- `PATCH /contacts/{id}/gdpr-consent` — GDPR consent patching for individual contacts not wired
- `GET /calls/{id}` — single call detail endpoint not called
- `GET /calls/{id}/transcript` — transcript history not fetched from server

### Phase build order (from Agent.md §6)
- **Phase 1** ✅ — Foundation (auth, VoIP basics, consent flow, call history)
- **Phase 2** ✅ — Transcription (WebSocket, audio upload, summary screen)
- **Phase 3** 🔲 — Appointments, voice edits, reminders (requires DB migrations first)

### Known issues in backend schema (Agent.md §3.1)
1. `calls.started_at` and `appointments.scheduled_at` both have `ON UPDATE CURRENT_TIMESTAMP` — must be dropped via Alembic migration
2. `transcripts.call_id` is `UNIQUE` — only one row per call; live STT must `UPDATE raw_text`, never `INSERT` a new row per chunk

---

*This document covers 100% of the source files in the `appcall` Android module as of 2026-07-17. To regenerate or extend it, read all files under `app/src/main/java/com/example/appcall/` and cross-reference `Agent.md` as the single source of truth for product behavior.*
