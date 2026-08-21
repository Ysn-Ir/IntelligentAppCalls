# VerbAI call 📞🤖
### Enterprise AI-Powered Telephony, Real-Time Speech Diarization & Dual-Engine LLM Intelligence

[![Android](https://img.shields.io/badge/Android-15%20(API%2035)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](IntelligentCalls/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](IntelligentCalls/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](IntelligentCalls/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.110+-009688?style=for-the-badge&logo=fastapi&logoColor=white)](backend/)
[![Python](https://img.shields.io/badge/Python-3.12-3776AB?style=for-the-badge&logo=python&logoColor=white)](backend/)
[![Groq & Ollama](https://img.shields.io/badge/AI%20Engine-Groq%20%7C%20Ollama-F55036?style=for-the-badge&logo=openai&logoColor=white)](backend/app/ai/)
[![Render](https://img.shields.io/badge/Render-Deploy%20Ready-46E3B7?style=for-the-badge&logo=render&logoColor=black)](render.yaml)
[![Audit](https://img.shields.io/badge/Audit%20Score-35%2F35%20(100%25)-brightgreen?style=for-the-badge)](backend/audit_routes.py)

---

## 📚 Master Documentation Index

For detailed, in-depth architectural and operational guides, refer to the specialized documentation modules:

| Document | Description & Key Topics | Link |
| :--- | :--- | :---: |
| 🏛️ **Architecture Guide** | Full Clean Architecture diagrams, Mermaid sequence flows, offline sync protocols, SQLite v9 schema, and the complete 35 REST & WebSocket API specification. | [**Read ARCHITECTURE.md**](ARCHITECTURE.md) |
| 🌐 **Hosting & Deployment** | Production setup on **Render.com**, PostgreSQL schema configuration, Docker Compose, Nginx SSL, zero-recompilation server routing, and cloud VoIP integration. | [**Read HOSTING_AND_DEPLOYMENT_GUIDE.md**](HOSTING_AND_DEPLOYMENT_GUIDE.md) |
| 📜 **Engineering & Incident Log** | Detailed chronological record of all 15 production incidents, root-cause analyses, token limit mitigations, and solutions. | [**Read PROJECT_HISTORY_LOG.md**](PROJECT_HISTORY_LOG.md) |
| 📱 **Android Client Docs** | Samsung Knox elevation, Shizuku ADB permissions, background call interception service, and Gradle build instructions. | [**Read IntelligentCalls/README.md**](IntelligentCalls/README.md) |
| ⚙️ **Backend Server Docs** | FastAPI server configuration, environment variables, Groq/Ollama LLM dual-inference, and VoIP webhook bridges. | [**Read backend/README.md**](backend/README.md) |
| 🖥️ **Web Dashboard Docs** | Next.js 16 / React 19 CRM analytics dashboard, live WebSocket audio transcription viewer, and GDPR export portal. | [**Read dashboard/README.md**](dashboard/README.md) |
| 📦 **Production APK Download** | Compiled binary of the latest release (`VerbAI-Call-v2.0.0.apk`) ready for direct phone installation. | [**Download APK (v2.0.0)**](releases/VerbAI-Call-v2.0.0.apk) |

---

## 🌟 System Architecture

**VerbAI call** is designed in accordance with **Cahier des Charges — Partie 6 (Appels intelligents & architecture technique)**, organized into 4 cohesive layers:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                     COUCHE 1 — APPLICATION CLIENT MOBILE (ANDROID)                     │
│  (Samsung Galaxy / Android 15 / One UI 7 — Jetpack Compose Dark Glassmorphism)         │
│                                                                                        │
│   ┌───────────────────────┐   ┌────────────────────────┐   ┌───────────────────────┐   │
│   │    Jetpack Compose    │   │ CallAccessibilitySvc   │   │ DynamicUrlInterceptor │   │
│   │  (6 Core Modules UI)  │   │  (Knox Mic Elevation)  │   │ (Zero-Recompile URL)  │   │
│   └───────────┬───────────┘   └───────────┬────────────┘   └───────────┬───────────┘   │
│               │                           │                            │               │
│   ┌───────────▼───────────┐   ┌───────────▼────────────┐   ┌───────────▼───────────┐   │
│   │  CallingManager &     │   │ PhoneCallRecorderSvc   │   │  OfflineSyncManager   │   │
│   │  AppLocalDatabase(v9) │──▶│ (2-Way PSTN & VoIP)    │──▶│  (Non-Destructive)    │   │
│   └───────────┬───────────┘   └───────────┬────────────┘   └───────────┬───────────┘   │
└───────────────┼───────────────────────────┼────────────────────────────┼───────────────┘
                │                           │                            │
                │ HTTPS REST API            │ Multipart Audio Upload     │ WebSocket Stream
                │ (Token-Based Auth)        │ (.mp4 / .m4a)              │ (Live Diarization)
                ▼                           ▼                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        COUCHE 2 & 4 — BACKEND SERVEUR (FASTAPI)                        │
│                     (Python 3.12 • Uvicorn ASGI • Production-Ready)                    │
│                                                                                        │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ Modular Routers: /auth • /calls • /contacts • /agenda • /tasks • /files        │   │
│   │ /assistant • /gdpr • /webhooks (Twilio/Vonage/Telnyx/SIP) • /ws                │   │
│   └───────────────────────┬────────────────────────────────┬───────────────────────┘   │
│                           │                                │                           │
│   ┌───────────────────────▼───────────────┐   ┌────────────▼───────────────────────┐   │
│   │      PostgreSQL (Render) / MySQL      │   │   Object Storage (Local / MinIO)   │   │
│   │  Users • Calls • Transcripts • Agenda │   │   Audio Vault & Export Recordings  │   │
│   └───────────────────────┬───────────────┘   └────────────────────────────────────┘   │
└───────────────────────────┼────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                    COUCHE 3 — MOTEUR D'INTELLIGENCE ARTIFICIELLE                       │
│                                                                                        │
│   ┌────────────────────────────────────────┐   ┌───────────────────────────────────┐   │
│   │    GROQ CLOUD INFERENCE (Primary)      │   │     OLLAMA LOCAL LLM (Offline)    │   │
│   │ - Whisper Large v3 Turbo (STT)         │   │ - LLaMA 3.3 / Mistral / DeepSeek  │   │
│   │ - GPT-OSS 20B / 120B & Qwen 3.6 27B    │   │ - 100% Private, Zero Cloud Limits │   │
│   │ - Strict ISO-8601 RDV & Intent Engine  │   │ - Seamless Provider Fallback      │   │
│   └────────────────────────────────────────┘   └───────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Key Highlights & Capabilities

### 1. 📞 Automatic 2-Way PSTN & VoIP Call Recording
- **Zero-Touch Interception**: Automatically records incoming and outgoing phone calls in the background using Samsung Knox microphone elevation and `CallAccessibilityService`.
- **Contact Resolution**: Resolves contact names and numbers directly from the device's native address book with zero stuck or hardcoded numbers.
- **Universal Cloud Telephony**: Compatible with Twilio, Telnyx, Plivo, Vonage, SignalWire, and custom SIP PBX systems.

### 2. 🧠 Dual-Engine AI Intelligence (Groq Cloud + Local Ollama)
- **Fast Speech-to-Text**: Groq `whisper-large-v3-turbo` with multi-speaker diarization (`Agent` vs. `Contact`) and confidence scoring (>96%).
- **French Date & Meeting Extractor**: Automatically parses relative terms (*"demain à 14h30"*, *"vendredi prochain"*) into exact ISO-8601 datetime entries and synchronizes with the user's Agenda.
- **Sentiment & Intent Classification**: 10 enterprise domains (Threats, Appointments, Quotes, Logistics, Billing, Complaints, Support) with regex word boundaries (`\b`) preventing false positives.
- **Local Ollama Inference**: Switch to self-hosted models (`llama3.3`, `mistral`, `deepseek-r1`, `phi4`) via `LLM_PROVIDER=ollama`.
- **Model Discovery API**: Real-time supported model inspection via `GET /api/v1/ai/models`.

### 3. 🤖 Contextual RAG AI Assistant
- Conversational chat assistant answering natural language questions across the user's recorded calls, transcripts, agenda meetings, and tasks.
- Strict `user_id` tenant isolation across pgvector semantic embeddings and SQL context layers.
- Verbatim citation badges referencing source call timestamps.

### 4. 🔒 Enterprise Security & GDPR Sovereignty
- **Article 15 (Data Portability)**: Generates comprehensive JSON exports of all user profile data, contacts, call logs, and transcripts.
- **Article 17 (Right to Erasure)**: Atomic cascade deletion permanently purges audio files, transcripts, vector embeddings, reminders, and summaries without foreign key locks.
- **Native Cryptography**: Direct Python 3.12 `bcrypt` password hashing without deprecated `passlib` version dependencies.

### 5. 🔄 Zero-Recompilation Dynamic Server Routing
- **Default Cloud Backend**: The compiled APK points automatically to your Render cloud deployment (`https://intelligent-calls-api.onrender.com`).
- **Secret 5-Tap Developer Modal**: Tapping the **VerbAI logo 5 times** on the Login screen opens an administrative server configuration modal to switch endpoints (Render Cloud, Local USB, Wi-Fi) on the fly without recompiling.
- **Settings Override**: Switch backend endpoints at any time in **Paramètres $\rightarrow$ Serveur & Connectivité**.

---

## 📁 Repository Structure

```
IntelligentAppCalls/
├── IntelligentCalls/            # Native Android Client (Kotlin / Jetpack Compose / Hilt)
│   ├── app/
│   │   ├── src/main/java/com/example/appcall/
│   │   │   ├── data/           # CallingManager, PhoneCallRecorderService, AppLocalDatabase (v9)
│   │   │   ├── presentation/   # Jetpack Compose Screens (Login, Calls, Summary, Agenda, Tasks)
│   │   │   └── di/             # Hilt Network & Repository Modules
│   │   └── AndroidManifest.xml # Permissions (Knox Audio, PhoneState, Contacts, Notifications)
│   └── build.gradle.kts
│
├── backend/                     # FastAPI Python 3.12 Server (AI Telephony Backend)
│   ├── app/
│   │   ├── ai/                 # Summarizer (Groq/Ollama), Transcriber (Whisper), Embeddings (RAG)
│   │   ├── routers/            # 35 Modular Endpoints (Auth, Calls, Agenda, Tasks, Assistant, GDPR)
│   │   ├── database.py         # SQLAlchemy ORM Models & PostgreSQL Adapter
│   │   ├── gdpr.py             # GDPR Atomic Cascade Purge & JSON Export
│   │   └── main.py             # ASGI Application Entrypoint
│   ├── audit_routes.py         # 35/35 Automated Endpoint Test Suite
│   ├── requirements.txt        # Production Dependencies
│   └── .env.example            # Environment Variables Template
│
├── dashboard/                   # Web CRM Dashboard (Next.js 16 / React 19 / TypeScript)
│   ├── src/app/                # App Router (/calls, /chat, /contacts, /gdpr)
│   └── src/lib/api.ts          # Unified REST Client
│
├── releases/                    # Production Release Binaries
│   └── VerbAI-Call-v2.0.0.apk  # Standalone Android APK (v2.0.0)
│
├── ARCHITECTURE.md              # In-Depth Technical Architecture & Flowcharts
├── HOSTING_AND_DEPLOYMENT_GUIDE.md # Production Deployment on Render & VPS
├── PROJECT_HISTORY_LOG.md       # Engineering Incidents #1-#15 Resolution Log
└── render.yaml                  # Render.com Infrastructure-as-Code Blueprint
```

---

## ⚡ Quickstart Guide

### 1. Deploy the Backend to Render (1-Click)

1. Fork or push this repository to GitHub.
2. In [Render Dashboard](https://dashboard.render.com), create a **PostgreSQL** database named `intelligent-calls-db`.
3. Create a new **Web Service**:
   - **Root Directory**: `backend`
   - **Build Command**: `pip install -r requirements.txt`
   - **Start Command**: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`
4. Set the following environment variables:
   ```ini
   DATABASE_URL=<your-internal-render-postgres-url>
   JWT_SECRET=your_super_secret_jwt_key_32bytes
   GROQ_API_KEY=gsk_your_groq_api_key
   GROQ_CHAT_MODEL=openai/gpt-oss-20b
   GROQ_WHISPER_MODEL=whisper-large-v3-turbo
   LLM_PROVIDER=groq
   ```
5. Test your live endpoint: `https://<your-service>.onrender.com/health` $\rightarrow$ `{"status": "ok"}`.

*(For detailed VPS, Docker, and Cloudflare tunnel options, see [HOSTING_AND_DEPLOYMENT_GUIDE.md](HOSTING_AND_DEPLOYMENT_GUIDE.md)).*

---

### 2. Run the Backend Locally

```powershell
# Navigate to backend directory
cd backend

# Create & activate virtual environment
python -m venv venv
.\venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Run the 35/35 audit test suite
python audit_routes.py

# Launch FastAPI development server
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```
*Interactive Swagger Documentation:* `http://localhost:8000/docs`

---

### 3. Install the Android App on Your Device

#### Option A: Direct APK Install
Download [**VerbAI-Call-v2.0.0.apk**](releases/VerbAI-Call-v2.0.0.apk) directly to your phone and tap to install.

#### Option B: Install via ADB
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "releases\VerbAI-Call-v2.0.0.apk"
```

---

### 4. Run the Web Dashboard

```powershell
cd dashboard
npm install
npm run dev
```
---

## 🛡️ Optimal Device Setup: Shizuku & Accessibility Guide

Modern Android versions (Android 11, 12, 13, 14, and 15 / One UI 6 & 7) restrict standard 3rd-party apps from accessing the remote caller's downlink audio stream over cellular networks.

**VerbAI call** overcomes these restrictions using a dual-privilege elevation architecture:

```
                               ┌────────────────────────────────────────────────────────┐
                               │                    ANDROID DEVICE                      │
                               │                                                        │
                               │  ┌────────────────────────┐  ┌──────────────────────┐  │
                               │  │   SHIZUKU FRAMEWORK    │  │ CALLACCESSIBILITYSVC │  │
                               │  │ (Privileged ADB Shell) │  │  (Downlink Routing)  │  │
                               │  └───────────┬────────────┘  └──────────┬───────────┘  │
                               │              │                          │              │
                               │              ▼                          ▼              │
                               │  ┌──────────────────────────────────────────────────┐  │
                               │  │              PHONECALLRECORDERSERVICE            │  │
                               │  │      - Elevated Capture: AudioSource.VOICE_COMM   │  │
                               │  │      - Hardware Acoustic Echo Cancellation (AEC) │  │
                               │  │      - Automatic 2-Way Audio Stream Sync (.mp4)  │  │
                               │  └──────────────────────────────────────────────────┘  │
                               └────────────────────────────────────────────────────────┘
```

---

### 1. ♿ Enable the Accessibility Service (Mandatory)

The `CallAccessibilityService` allows the app to detect telephony state transitions (`OFFHOOK`, `RINGING`, `IDLE`) without delay and route both speaker channels into the audio pipeline:

1. On your phone, go to **Settings (Paramètres)** ➡️ **Accessibility (Accessibilité)** ➡️ **Installed Apps / Services (Applications installées)**.
2. Find and tap **VerbAI call** (or `IntelligentCalls`).
3. Toggle the switch to **ON** and tap **Allow / Autoriser** when prompted.
4. **Turn OFF Wi-Fi Calling (VoWiFi)** in your SIM settings (Wi-Fi calling encrypts audio at the modem level and prevents hardware audio capture).

---

### 2. ⚡ Setup Shizuku for Elevated ADB Privileges (Recommended)

**[Shizuku](https://shizuku.rikka.app/)** allows VerbAI call to execute system-level ADB commands directly on your device without needing root or keeping your phone connected to a computer:

#### Why use Shizuku with VerbAI call?
* **Captures 100% crystal-clear 2-way audio** on Samsung Galaxy, Google Pixel, Xiaomi, and OnePlus devices.
* Grants `CAPTURE_AUDIO_OUTPUT`, `READ_PRIVILEGED_PHONE_STATE`, and `DUMP` permissions without a PC.
* Prevents the Android battery manager (`Doze mode`) from terminating the background recorder during long phone calls.

#### Step-by-Step Shizuku Activation:
1. Install **Shizuku** from the [Google Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases).
2. Enable **Developer Options (Options de développement)** on your phone:
   - Go to **Settings** ➡️ **About phone (À propos du téléphone)** ➡️ **Software information**.
   - Tap **Build number (Numéro de version)** 7 times until developer mode is unlocked.
3. Enable **Wireless Debugging (Débogage sans fil)** in Developer Options.
4. Open **Shizuku** ➡️ tap **Pairing (Jumelage)** ➡️ tap Developer Options notification ➡️ enter the 6-digit wireless pairing code.
5. In Shizuku, tap **Start (Démarrer)**.
6. Open **VerbAI call** ➡️ navigate to **Paramètres (Settings)** ➡️ tap **Autoriser Shizuku**.

---

### 3. 🔋 Disable Battery Optimization

To ensure Android never pauses background recording during long calls:
1. Long press the **VerbAI call** icon on your home screen ➡️ tap **App Info (ℹ️)**.
2. Go to **Battery (Batterie)** ➡️ select **Unrestricted (Non restreinte)**.

---

## 📊 API Route Verification Matrix

All 35 production routes are tested and validated by [`audit_routes.py`](backend/audit_routes.py):

| Method | Endpoint Path | Function & Scope | Audit Result |
| :--- | :--- | :--- | :---: |
| `GET` | `/health` | Server Healthcheck & Engine Metadata | **PASS** |
| `POST` | `/api/v1/auth/register` | User Registration with Native Bcrypt | **PASS** |
| `POST` | `/api/v1/auth/login` | JWT Access Token Issuance | **PASS** |
| `POST` | `/api/v1/auth/refresh` | Token Refresh Endpoint | **PASS** |
| `GET` | `/api/v1/users/me` | User Profile & Identifiers | **PASS** |
| `PUT` | `/api/v1/users/me` | Profile Information Update | **PASS** |
| `PUT` | `/api/v1/users/me/password` | Secure Password Change | **PASS** |
| `GET` | `/api/v1/voip/token` | Twilio VoIP & Native PSTN Token | **PASS** |
| `GET` | `/api/v1/contacts` | Address Book Synchronization | **PASS** |
| `POST` | `/api/v1/contacts` | Create Contact Record | **PASS** |
| `PATCH` | `/api/v1/contacts/{id}/gdpr-consent` | Update Contact Consent Flag | **PASS** |
| `POST` | `/api/v1/calls` | Initialize Call Record | **PASS** |
| `GET` | `/api/v1/calls/{id}` | Retrieve Single Call Details | **PASS** |
| `GET` | `/api/v1/calls` | User Call History List | **PASS** |
| `POST` | `/api/v1/calls/{id}/consent` | Real-Time Voice Recording Consent | **PASS** |
| `POST` | `/api/v1/calls/{id}/end` | Finalize Call Duration | **PASS** |
| `GET` | `/api/v1/calls/{id}/transcript` | Full Multi-Speaker Transcript | **PASS** |
| `GET` | `/api/v1/calls/{id}/summary` | AI Summary & Extracted RDV | **PASS** |
| `POST` | `/api/v1/calls/{id}/summary/validate` | Confirm AI-Detected Appointment | **PASS** |
| `POST` | `/api/v1/calls/{id}/summary/edit` | Manually Edit Summary Text | **PASS** |
| `GET` | `/api/v1/calls/{id}/ai-status` | AI Processing Status Polling | **PASS** |
| `GET` | `/api/v1/agenda` | Agenda Meetings & Filtered Dates | **PASS** |
| `POST` | `/api/v1/agenda` | Create New Appointment | **PASS** |
| `GET` | `/api/v1/reminders` | Planned Reminders List | **PASS** |
| `GET` | `/api/v1/tasks` | Extracted AI Action Items | **PASS** |
| `POST` | `/api/v1/tasks` | Create Action Item | **PASS** |
| `PUT` | `/api/v1/tasks/{id}` | Update / Complete Task | **PASS** |
| `GET` | `/api/v1/files` | Audio Recordings Vault List | **PASS** |
| `POST` | `/api/v1/files` | Multipart Audio File Upload | **PASS** |
| `GET` | `/api/v1/ai/models` | Active AI Models Discovery | **PASS** |
| `GET` | `/api/v1/me/export` | GDPR Art. 15 Full Data Portability | **PASS** |
| `GET` | `/api/v1/users/me/voice-data/export` | Voice Metadata Export | **PASS** |
| `DELETE`| `/api/v1/users/me/voice-data` | GDPR Art. 17 Atomic Voice Purge | **PASS** |
| `POST` | `/webhooks/twilio/voice` | TwiML Ingestion & Call Bridge | **PASS** |
| `POST` | `/webhooks/vonage/voice` | NCCO Webhook Ingestion | **PASS** |
| `POST` | `/webhooks/recording-complete` | Universal Audio Processing Trigger | **PASS** |
| `WS` | `/api/v1/ws/calls/{id}/live-transcript`| Real-Time Diarization WebSocket | **PASS** |

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.
