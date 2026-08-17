# IntelligentAppCalls 📞🤖

**IntelligentAppCalls** is a state-of-the-art, end-to-end AI telephony intelligence platform. It seamlessly combines:
1. **Android Client (Kotlin + Jetpack Compose)**: Native PSTN SIM-card call recording (with Samsung Knox process elevation), live speech transcription, offline SQLite cache, task manager, and dynamic agenda calendar.
2. **AI Telephony Backend (FastAPI + Groq / OpenAI)**: Audio processing, Whisper speech-to-text, French temporal reasoning for automatic appointment scheduling, LLM summarization, and vector RAG search.
3. **Next.js Web Dashboard (React 19 + TypeScript)**: Modern CRM analytics, call history playback, AI conversation inspector, and full GDPR Art. 15–20 compliance portal.

---

## 🌟 System Architecture

```
                               ┌────────────────────────────────────────────────────────┐
                               │                    ANDROID DEVICE                      │
                               │  (Samsung Galaxy S21 / Android 15 / One UI 7)         │
                               │                                                        │
                               │   ┌──────────────────┐   ┌──────────────────────────┐  │
                               │   │  Jetpack Compose │   │ CallAccessibilityService │  │
                               │   │    Dark UI       │   │   (Knox Mic Elevation)   │  │
                               │   └────────┬─────────┘   └────────────┬─────────────┘  │
                               │            │                          │                │
                               │   ┌────────▼─────────┐   ┌────────────▼─────────────┐  │
                               │   │ CallingManager & │   │ PhoneCallRecorderService │  │
                               │   │ Local SQLite DB  │──▶│  (Auto 2-Way Recording)  │  │
                               │   └────────┬─────────┘   └────────────┬─────────────┘  │
                               └────────────┼──────────────────────────┼────────────────┘
                                            │                          │
                                   HTTP API │        Multipart Audio   │
                                   Payloads │        Upload (.mp4)     │
                                            ▼                          ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 FASTAPI PYTHON BACKEND                                 │
│                                                                                        │
│   ┌───────────────────────────────┐               ┌────────────────────────────────┐   │
│   │    FastAPI Endpoints (v1)     │               │        AI Engine (Groq)        │   │
│   │  /calls, /contacts, /agenda   │──────────────▶│  - Whisper STT (Transcription) │   │
│   │  /tasks, /summary, /chat      │               │  - French Date & RDV Extraction│   │
│   └───────────────┬───────────────┘               │  - Call Summarization & RAG    │   │
│                   │                               └────────────────────────────────┘   │
│   ┌───────────────▼───────────────┐                                                    │
│   │    MySQL / SQLite Database    │◀───────────────────────────────────────────────────┘   │
│   │  Calls, Agenda, Tasks, RGPD   │                                                    │
│   └───────────────▲───────────────┘                                                    │
└───────────────────┼────────────────────────────────────────────────────────────────────┘
                    │ REST API / WebSocket
┌───────────────────┴────────────────────────────────────────────────────────────────────┐
│                             NEXT.JS 16 WEB DASHBOARD                                   │
│            CRM Analytics • Call Playback • AI Chatbot • GDPR Compliance                │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 📁 Repository Structure

```
IntelligentAppCalls/
├── IntelligentCalls/            # Native Android Client (Kotlin / Jetpack Compose / Hilt)
│   ├── app/
│   │   ├── src/main/java/com/example/appcall/
│   │   │   ├── data/
│   │   │   │   ├── calling/    # CallingManager, PhoneCallRecorderService, Accessibility
│   │   │   │   ├── local/      # AppLocalDatabase (SQLite v6), SyncWorker
│   │   │   │   └── repository/ # VoipRepositoryImpl (Device contacts, API sync)
│   │   │   ├── presentation/
│   │   │   │   ├── calling/    # CallScreen, CallHistoryScreen (Real contacts, no mocks)
│   │   │   │   ├── dashboard/  # TasksSection (Compact), AgendaSection (Compact dynamic RDV)
│   │   │   │   └── summary/    # SummaryScreen (AI Appointment card, French actions)
│   │   │   └── di/             # Hilt Dependency Injection Modules
│   │   └── AndroidManifest.xml # Permissions (READ_CONTACTS, RECORD_AUDIO, ACCESSIBILITY)
│   └── build.gradle.kts
│
├── backend/                     # FastAPI Python Backend (AI Telephony Server)
│   ├── app/
│   │   ├── ai/
│   │   │   ├── summarizer.py   # Groq dynamic date-aware French appointment extractor
│   │   │   ├── transcriber.py  # Audio transcription engine
│   │   │   └── embeddings.py   # Vector embeddings & RAG search
│   │   ├── database.py         # SQLAlchemy models (Calls, Agenda, Tasks, Transcripts)
│   │   ├── gdpr.py             # GDPR Art. 15 (Export), Art. 17 (Right to Erasure)
│   │   ├── schemas.py          # Pydantic DTOs
│   │   └── main.py             # FastAPI router & audio ingestion
│   ├── requirements.txt
│   └── .env
│
└── dashboard/                   # Web Dashboard (Next.js 16 / React 19 / TypeScript)
    ├── src/
    │   ├── app/                # App Router (/contacts, /calls, /chat, /gdpr)
    │   ├── lib/api.ts          # REST client with automatic dev bearer authorization
    │   └── globals.css         # Dark glassmorphic design tokens
    └── package.json
```

---

## 🚀 Quick Start Guide

### 1. Start the Backend (FastAPI)

```bash
cd backend

# Create & activate virtual environment
python -m venv venv
# On Windows:
venv\Scripts\activate
# On Linux/macOS:
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Run the server with hot reload
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```
*API Documentation will be available at:* **`http://localhost:8000/docs`**

---

### 2. Forward ADB Ports (For Physical USB Android Devices)

```bash
adb reverse tcp:8000 tcp:8000
```

---

### 3. Build & Install the Android App

Connect your Android device (e.g. Samsung Galaxy S21 / Android 15) and run:
```bash
cd IntelligentCalls
.\gradlew.bat :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

---

### 4. Start the Web Dashboard (Next.js)

```bash
cd dashboard
npm install
npm run dev
```
*Open your browser at:* **`http://localhost:3000`**

---

## 🔒 Samsung S21 / Android 15 Knox 2-Way Recording Configuration

To ensure crystal-clear 2-way call audio on Samsung Galaxy devices running Android 14 / 15:

1. Go to **Settings** ➡️ **Accessibility** ➡️ **Installed Apps**.
2. Tap **IntelligentCalls** and toggle **ON** (`CallAccessibilityService`).
3. Ensure **Wi-Fi Calling** is **OFF** in your quick settings panel.
4. Make or receive any phone call. The app will automatically record the audio into `Appel_<Contact>_<YYYY-MM-DD_HH-mm>.mp4` and send it to the AI pipeline for instant transcription and appointment extraction.

---

## 🎯 Key Features Breakdown

### 🤖 French AI Appointment Detection & Agenda Sync
- Dynamically resolves French relative expressions (*"demain 14h"*, *"mardi prochain"*, *"vendredi après-midi"*) into strict calendar timestamps (`YYYY-MM-DDTHH:MM:SS`).
- Synchronizes automatically to **Mon Agenda** on your phone and the backend CRM calendar.

### 👥 Real Device Contacts Integration
- Direct phone book resolution via `ContactsContract`.
- Replaces generic placeholder names with your actual caller identities, phone numbers, and avatars.

### 📋 High-Density Compact Tasks & Agenda UI
- Collapsible **`+ Ajouter`** creation drawers that preserve 100% of the screen for your tasks and appointments.
- Category badges (`📞 Appel`, `📅 RDV`, `⚡ Urgent`, `📝 Suivi`).

### 🛡️ Full GDPR Compliance
- **Art. 15 (Right of Access & Portability)**: 1-click JSON export of all transcripts, calls, and summaries.
- **Art. 17 (Right to Erasure)**: Anonymize contacts and purge audio recordings.

---

## 📄 License
Distributed under the MIT License.
