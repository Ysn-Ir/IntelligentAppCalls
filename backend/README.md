# IntelligentAppCalls — AI Telephony Backend 🚀

The **IntelligentAppCalls Backend** is a high-performance FastAPI service designed for call recording ingestion, speech-to-text transcription (via Whisper), intelligent French appointment extraction (via Groq / OpenAI), vector embeddings search (RAG), and GDPR data management.

---

## 🛠️ Architecture & Modules

```
backend/
├── app/
│   ├── ai/
│   │   ├── summarizer.py     # Dynamic date-aware French AI appointment extraction & summaries
│   │   ├── transcriber.py    # Whisper speech-to-text pipeline
│   │   ├── embeddings.py     # Vector embeddings generation & semantic search
│   │   └── rag.py            # Retrieval-Augmented Generation for assistant chatbot
│   ├── database.py           # SQLAlchemy database layer (MySQL / SQLite)
│   ├── gdpr.py               # GDPR export (Art. 15) & right to erasure (Art. 17)
│   ├── schemas.py            # Pydantic validation DTOs
│   ├── storage.py            # Audio file storage (local disk & MinIO)
│   └── main.py               # FastAPI routers & WebSocket live transcription
├── asterisk/                 # Asterisk PBX bridging configurations (optional)
├── requirements.txt          # Python dependencies
├── docker-compose.yml        # Multi-container orchestration (MySQL + Redis + FastAPI)
└── .env                      # Environment variables
```

---

## ⚡ Setup & Run

### 1. Prerequisites
- Python 3.10+
- Groq API Key or OpenAI API Key (configured in `.env`)

### 2. Installation
```bash
# Create and activate virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt
```

### 3. Environment Variables (`.env`)
```env
DATABASE_URL=sqlite:///./appcall.db
GROQ_API_KEY=gsk_your_groq_api_key_here
JWT_SECRET=appcall_secret_jwt_key_2026
UPLOAD_DIR=./uploads
```

### 4. Start the Server
```bash
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```
Interactive Swagger docs: **`http://localhost:8000/docs`**

---

## 📡 Key REST API Endpoints

### 🔐 Authentication & Profile
- `POST /api/v1/auth/register` — Register a new account.
- `POST /api/v1/auth/login` — Login & receive JWT bearer token.
- `GET /api/v1/users/me` — Current user profile.

### 📞 Call Management & Audio Ingestion
- `GET /api/v1/calls` — List call history items with real caller names, numbers, and summary previews.
- `POST /api/v1/calls` — Initiate/record an outgoing or incoming call session.
- `POST /api/v1/calls/{id}/audio` — Upload MP4 call recording with `X-Contact-Name` and `X-Phone-Number` headers.
- `GET /api/v1/calls/{id}/summary` — Fetch AI generated summary and extracted appointment.
- `GET /api/v1/calls/{id}/transcript` — Fetch call transcript and speaker diarization.

### 📅 Agenda & Appointments
- `GET /api/v1/agenda` — List all agenda items (including AI detected appointments).
- `POST /api/v1/agenda` — Create a new agenda event.
- `DELETE /api/v1/agenda/{id}` — Delete an agenda item.
- `POST /api/v1/calls/{id}/validate-appointment` — Confirm an AI-proposed appointment.

### 📋 Task Management
- `GET /api/v1/tasks` — List tasks.
- `POST /api/v1/tasks` — Create a task (supports categories like `[📞 Appel]`, `[📅 RDV]`).
- `PUT /api/v1/tasks/{id}` — Toggle task completion status.
- `DELETE /api/v1/tasks/{id}` — Delete a task.

### 🤖 AI Assistant & RAG Chatbot
- `POST /api/v1/chat` — Global conversational query across all recorded calls and CRM data.
- `POST /api/v1/contacts/{id}/chat` — Targeted conversation querying specific call history with a contact.

### 🛡️ GDPR Compliance
- `GET /api/v1/me/export` — Full Art. 15 JSON export of all calls, summaries, transcripts, and tasks.
- `DELETE /api/v1/me` — Art. 17 complete account and personal data erasure.
- `DELETE /api/v1/calls/{id}/data` — Purge all recordings, transcripts, and summaries for a specific call.

---

## 📱 Connecting Android Device

For physical Android devices over USB:
```bash
adb reverse tcp:8000 tcp:8000
```
Then configure the app to connect to `http://localhost:8000` or `http://10.0.2.2:8000` (for emulator).
