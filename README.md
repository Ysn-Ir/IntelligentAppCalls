# IntelligentAppCalls 📞🤖

**IntelligentAppCalls** is an end-to-end AI-powered phone calling & 2-way call recording platform built for Android (Kotlin + Jetpack Compose) and supported by a FastAPI Python backend pipeline.

Designed specifically to operate seamlessly on modern Android devices (including **Samsung Galaxy S21 / Android 15 / One UI 7** with Knox security policies), it captures 2-way PSTN (SIM card) conversations, provides live UI playback, and automatically generates AI summaries, transcriptions, and CRM task insights.

---

## 🌟 Key Features

* **Native SIM Card Calling & 2-Way Recording**: Place calls over your regular SIM card with 2-way audio recording (both local and remote caller voices).
* **Samsung Knox Bypass Strategy**: Utilizes process elevation via `CallAccessibilityService` to bypass Knox hardware-level mic muting on Android 14 & 15 devices.
* **Shizuku & PBX Bridge Integration**: Built-in optional support for Shizuku ADB binder integration and server-side PBX Call Bridging.
* **Modern Jetpack Compose UI**: Vibrant, responsive Dark Mode dashboard featuring Contacts, Live Call View, Audio Player (Files Section), Agenda, and AI Assistant tasks.
* **Automated AI Pipeline**: Automatic background synchronization (`SyncWorker` + Retrofit) that uploads call recordings (`.mp4` / `.wav`) to the backend for transcription and summarization.

---

## 📁 Repository Structure

```
IntelligentAppCalls/
├── IntelligentCalls/            # Native Android Client (Kotlin / Jetpack Compose / Hilt)
│   ├── app/
│   │   ├── src/main/java/com/example/appcall/
│   │   │   ├── data/calling/   # CallingManager, PhoneCallRecorderService, CallAccessibilityService, ShizukuManager
│   │   │   ├── presentation/  # Jetpack Compose Screens (CallScreen, FilesSection, AgendaSection, TasksSection)
│   │   │   └── di/            # Hilt Dependency Injection Modules
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
└── appcall-fake-backend/        # FastAPI Python Backend (AI Call Analysis Server)
    ├── app/
    │   ├── main.py              # FastAPI endpoints (/api/v1/calls, upload audio, bridge)
    │   ├── models.py            # SQLAlchemy Database models
    │   └── schemas.py           # Pydantic request/response DTOs
    └── requirements.txt
```

---

## 🚀 Setup & Installation

### 1. Backend Setup (FastAPI)

```bash
cd appcall-fake-backend

# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Start the server
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 2. ADB Port Forwarding (For Local Device Testing)

If testing on a physical device connected via USB:
```bash
adb reverse tcp:8000 tcp:8000
```

### 3. Android App Build (Kotlin)

Open `IntelligentCalls` in Android Studio or build via command line:
```bash
cd IntelligentCalls
.\gradlew.bat installDebug
```

---

## 🔒 Samsung S21 / Android 15 Call Recording Guide

To ensure 2-way call audio is recorded cleanly on Samsung Galaxy S21 running Android 14/15:

1. Open **Settings** on your phone -> **Accessibility** -> **Installed Apps**.
2. Enable **IntelligentCalls** (`CallAccessibilityService`).
3. Make sure **Wi-Fi Calling** is toggled **OFF** in your phone quick settings.
4. Place calls from **IntelligentCalls** and enjoy 2-way call recordings in the **Files** section!

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for details.
