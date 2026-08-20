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

### 1. 📞 Appels & Contacts
- **Top Tab Switcher**: Seamlessly switch between `📞 Historique` and `👥 Contacts`.
- **Search & Filter**: Live contact search and call filter tabs (`Tous`, `Manqués`, `Avec résumé`).
- **100% Automatic Background Recording**: Intercepts PSTN calls automatically in the background without blocking modal dialogs.
- **Sleek UI Badges**: Clean typographic pills with status dots (`● Audio HD`, `● Positif`, `#RDV`, `#IA`).
- **Direct Calling**: One-tap native SIM call (`📱`) or VoIP call.

### 2. 📝 Call Analysis & Summary
- **Interactive Audio Engine**: Real `MediaPlayer` with dynamic waveform progress, seek-on-tap, elapsed/total timers, speed adjustment (`1×`, `1.5×`, `2×`), and native audio export (`⬇`).
- **Live AI Summary Card**: Formatted bullet points extracted from conversation context.
- **Appointment Extraction**: 2x2 grid displaying Date, Time, Title, and Contact with `Valider RDV` and `Modifier` options.
- **Dual-Speaker Transcript**: Live speaker bubbles with timestamps, confidence scores, and real-time backend updates.

### 3. 🔔 Native Notifications Engine (`AppNotificationManager`)
- **Agenda Reminders**: Native high-priority Android notifications for confirmed & upcoming appointments (`📅 Rappel RDV: [Titre] à [Heure] avec [Contact]`).
- **Task Alerts**: Instant notifications when new action items or tasks are created.
- **Post-Call AI Summary Notification**: One-tap notification arriving as soon as a call is recorded and transcribed, opening directly into the call's AI summary screen.

### 4. 📅 Smart Agenda with Dynamic Day-Filtering
- **Dynamic Weekday Calculator**: Automatically generates current week cards (`LUN 18`, `MAR 19`, `MER 20`...) with live appointment counters.
- **Day-by-Day Filter**: Filter appointments by clicking any day card or select `"TOUS"` to view everything.
- **`＋ Nouveau RDV` Creator**: Collapsible drawer with quick day chips (`Aujourd'hui`, `Demain`, `+1 sem`), native DatePicker, quick time chips (`09:00`, `14:00`, `18:00`), and native TimePicker with instant notification dispatch.

### 5. 📋 Tasks & Audio Vault
- **Tâches IA & Création Manuelle**: Extracted action items with checkboxes, source call tags, and `＋ Ajouter` button with local notification alerts.
- **Coffre-fort Audio**: Local voice recordings list with in-app play/pause (`▶ / ❚❚`) and direct file sharing (`⬇`).
- **RGPD Privacy**: Live JSON data export (`Article 15`) and local storage voice data purge with confirmation (`Droit à l'oubli`).

### 6. 🤖 AI Assistant RAG Chat
- Query all call transcripts and CRM data using Groq LLaMA / GPT-4o.
- Filter chat context by specific contact or search across all calls.
- Sources citation badge indicating which call the answer was derived from.

### 7. ⚙️ Settings (Paramètres, Profil & Multi-Fournisseur VoIP)
- **Mon Profil & Identifiants**: View active account avatar/initials, edit user information (Prénom, Nom, Email, Téléphone), and change account password securely with old password validation.
- **Universal Cloud Telephony & Multi-VoIP**: Full support for any cloud VoIP provider with dynamic switching:
  - 🔵 **Twilio** (TwiML Dual-Channel HD + REST Call Bridge)
  - 🟢 **Telnyx** (TeXML + Call Control v2 API)
  - 🟣 **Plivo** (Plivo XML + Voice API)
  - 🟠 **Vonage / Nexmo** (NCCO JSON + Voice Application API)
  - ⚪ **SignalWire** (SWML / TwiML)
  - 🏢 **SIP Trunk / PBX Gateway** (Asterisk, FreePBX, 3CX, Cisco Webhook Ingestion)
- **Shizuku API Elevation**: Live Shizuku ADB service connection detector and permission granter.
- **Server URL & Connectivity**: USB / Wi-Fi mode selector with instant connection tester (`/health`).
- **GDPR Privacy Suite**: Article 15 JSON data export and Article 17 full account & audio records purge (`Droit à l'oubli`).

---

## 📄 License
Distributed under the MIT License.
