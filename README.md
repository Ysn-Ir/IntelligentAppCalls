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

## 🚀 Complete Startup & Connection Guide

### Step 1: Find Your Computer's Wi-Fi IP Address

Open a terminal (PowerShell or Command Prompt) on your PC and run:
```powershell
ipconfig
```
Look for **`Wireless LAN adapter Wi-Fi`** ➡️ **`IPv4 Address`** (for example: `192.168.1.12`).

---

### Step 2: Start the FastAPI AI Backend

Navigate to the `backend/` folder:
```powershell
cd backend
```

Activate your Python virtual environment (or create one if needed):
```powershell
# Create virtualenv (first time only)
python -m venv venv

# Activate on Windows PowerShell / CMD:
.\venv\Scripts\activate
# (On Linux / macOS: source venv/bin/activate)

# Install dependencies
pip install -r requirements.txt
```

Start the backend server on **`0.0.0.0`** so that devices on your local network can reach it:
```powershell
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

> [!IMPORTANT]
> **Why `--host 0.0.0.0` is required:**
> If you run `uvicorn` without `--host 0.0.0.0`, it binds only to `127.0.0.1` (localhost) and refuses connections from your mobile phone.
>
> When Windows Defender Firewall prompts you, click **"Allow access" / "Autoriser"** for Private networks.

*Interactive API Swagger Documentation is available at:* **`http://localhost:8000/docs`**

---

### Step 3: Configure the Android App

Open the **IntelligentCalls** app on your phone:

#### Option A: Wi-Fi Mode (Recommended)
1. Tap the **⚙️ Paramètres** tab at the bottom right.
2. In **URL du serveur**, enter your computer's IP address and port:
   ```text
   http://192.168.1.12:8000
   ```
   *(Replace `192.168.1.12` with your actual Wi-Fi IPv4 address from Step 1).*
3. Tap **Enregistrer** (Save).
4. Tap **🔄 Tester**. You should see:
   ```text
   ✅ Backend connecté (HTTP 200)
   ```

#### Option B: USB Cable Mode (ADB Reverse)
If your phone is plugged in via USB with USB Debugging enabled:
1. On your PC terminal, run:
   ```powershell
   adb reverse tcp:8000 tcp:8000
   ```
2. In the app's **⚙️ Paramètres** tab, tap **🔌 Mode USB** (`http://127.0.0.1:8000`).
3. Tap **Enregistrer** (Save), then **🔄 Tester**.

---

### Step 4: Build & Install the Android App (If updating)

From the project root:
```powershell
cd IntelligentCalls
.\gradlew.bat :app:assembleDebug
```
To install on your connected device:
```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

---

### Step 5: Start the Next.js Web Dashboard

Open a new terminal and navigate to `dashboard/`:
```powershell
cd dashboard
npm install
npm run dev
```
*Open your browser at:* **`http://localhost:3000`**

---

## 🔒 Samsung S21 / Android 15 Knox 2-Way Recording Configuration

To ensure crystal-clear 2-way call audio on Samsung Galaxy devices running Android 14 / 15:

1. Go to **Settings (Paramètres téléphone)** ➡️ **Accessibility (Accessibilité)** ➡️ **Installed Apps (Applications installées)**.
2. Tap **IntelligentCalls** and toggle **ON** (`CallAccessibilityService`).
3. Ensure **Wi-Fi Calling (Appels Wi-Fi)** is **OFF** in your quick settings panel.
4. Make or receive any phone call. The app will automatically record the audio into `Appel_<Contact>_<YYYY-MM-DD_HH-mm>.mp4` and send it to the AI pipeline for instant transcription and appointment extraction.

---

## 📱 Features Overview

### 1. 📞 Appels & Contacts (Screen 1)
- **Top Tab Switcher**: Seamlessly switch between `📞 Historique` and `👥 Contacts`.
- **Search & Filter**: Live contact search and call filter tabs (`Tous`, `Manqués`, `Avec résumé`).
- **AI Transcription Consent Toggle**: Instant per-user switch to enable or disable AI voice analysis.
- **Direct Calling**: One-tap native SIM call (`📱`) or VoIP call (`Appeler`).
- **AI Badges**: Waveform indicators, audio HD tags, and sentiment pills (`😃 Positif`).

### 2. 📝 Call Analysis & Summary (Screen 2)
- **Interactive Audio Scrubber**: Dynamic waveform visualization, duration tracking, and playback speed pills (`1×`, `1.5×`, `2×`).
- **AI Summary Card**: Formatted bullet points extracted from conversation context.
- **Appointment Extraction**: 2x2 grid displaying Date, Time, Title, and Contact with `Valider RDV` and `Modifier` options.
- **Dual-Speaker Transcript**: Left bubble for Caller, right bubble for You with timestamps and confidence scores.

### 3. 🤖 AI Assistant RAG Chat (Screen 3)
- Query all call transcripts and CRM data using Groq LLaMA / GPT-4o.
- Filter chat context by specific contact or search across all calls.
- Sources citation badge indicating which call the answer was derived from.

### 4. 📅 Smart Agenda (Screen 4)
- Real-time digital clock (`HH:mm:ss`) and Monday-to-Sunday day strip picker.
- **`＋ Nouveau RDV` Creator**: Collapsible drawer with quick day chips (`Aujourd'hui`, `Demain`, `+1 sem`), native DatePicker, quick time chips (`09:00`, `14:00`, `18:00`), and native TimePicker.
- Direct `Rappeler` dialer button and `Valider` sync button.

### 5. 📋 Tasks & Audio Vault (Screen 5)
- **Tâches IA**: Extracted action items with checkboxes and source call tags.
- **Coffre-fort Audio**: Local voice recordings scanner with in-app audio playback and download.
- **RGPD Privacy**: Data export and right-to-be-forgotten deletion.

### 6. ⚙️ Settings (Screen 6)
- Shizuku API elevation manager.
- Server URL configuration & connectivity tester.
- Recording engine selector (Bluetooth SCO / PBX bridge).
- GDPR Article 15 JSON data export and Article 17 full account deletion.

---

## 📄 License
Distributed under the MIT License.
