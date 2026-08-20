# 🌐 Complete Hosting, Deployment & Database Architecture Guide
## Intelligent Calls — Production Setup, Database Schemas & Zero-Recompilation Routing

---

## 📑 Table of Contents

1. [Infrastructure Topology & Network Flow](#1-infrastructure-topology--network-flow)
2. [Where is the Data Stored? (Database Details)](#2-where-is-the-data-stored-database-details)
   - [2.1 Server Database (MySQL / PostgreSQL / SQLite)](#21-server-database-mysql--postgresql--sqlite)
   - [2.2 Detailed Backend Table Schemas (SQLAlchemy)](#22-detailed-backend-table-schemas-sqlalchemy)
   - [2.3 Android Local Database (SQLite)](#23-android-local-database-sqlite)
3. [End-to-End Dataflow & Call Lifecycle](#3-end-to-end-dataflow--call-lifecycle)
4. [How to Replace `localhost` (Zero Recompilation)](#4-how-to-replace-localhost-zero-recompilation)
5. [Hosting & Deployment Options for FastAPI Backend](#5-hosting--deployment-options-for-fastapi-backend)
   - [Option A: Free / Managed Cloud (Railway / Render / Fly.io)](#option-a-free--managed-cloud-railway--render--flyio)
   - [Option B: Dedicated VPS (Ubuntu 22.04 / 24.04 + Docker + Nginx SSL)](#option-b-dedicated-vps-ubuntu-2204--2404--docker--nginx-ssl)
   - [Option C: Free Secure Tunnel from Your PC (Cloudflare Tunnel / Ngrok)](#option-c-free-secure-tunnel-from-your-pc-cloudflare-tunnel--ngrok)
6. [Deploying the Web Dashboard (Next.js 16) on Vercel](#6-deploying-the-web-dashboard-nextjs-16-on-vercel)
7. [Production Environment Variables Reference (`.env`)](#7-production-environment-variables-reference-env)

---

## 1. Infrastructure Topology & Network Flow

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   GLOBAL TOPOLOGY                                      │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│   [Android Smartphone]               [Next.js Web Dashboard]                           │
│   (On 4G / 5G / Wi-Fi)               (Chrome / Safari Browser)                         │
│            │                                    │                                      │
│            │ HTTPS / WSS                        │ HTTPS (Vercel)                       │
│            ▼                                    ▼                                      │
│   ┌─────────────────────────────────────────────────────────┐                          │
│   │              NGINX / CLOUDFLARE REVERSE PROXY           │                          │
│   │              (SSL / HTTPS / Port 443 / WSS)             │                          │
│   └────────────────────────────┬────────────────────────────┘                          │
│                                │ Reverse Proxy (Port 8000)                             │
│                                ▼                                                       │
│   ┌─────────────────────────────────────────────────────────┐                          │
│   │             FASTAPI BACKEND (Python 3.12)               │                          │
│   │             (Uvicorn Worker / Async Engine)             │                          │
│   └───────────────┬─────────────────────────┬───────────────┘                          │
│                   │                         │                                          │
│                   ▼                         ▼                                          │
│   ┌───────────────────────────────┐ ┌────────────────────────────────┐                 │
│   │   MYSQL DATABASE              │ │      GROQ INFERENCE CLOUD      │                 │
│   │   (appcall_db / Port 3306)    │ │   - Whisper Large v3 Turbo     │                 │
│   │   - Users & Contacts          │ │   - GPT-OSS 120B / LLaMA 3.3   │                 │
│   │   - Calls, Transcripts, RDVs  │ └────────────────────────────────┘                 │
│   └───────────────────────────────┘                                                    │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Where is the Data Stored? (Database Details)

The architecture maintains **two synchronized database layers**:

### 2.1 Server Database (MySQL / PostgreSQL / SQLite)
- **Location**: On the host running FastAPI (default MySQL on port `3306`, database `appcall_db`).
- **Configuration**: Defined in `backend/.env` via the `DATABASE_URL` parameter.
  - MySQL format: `mysql+pymysql://<user>:<password>@<host>:3306/appcall_db`
  - PostgreSQL format: `postgresql://<user>:<password>@<host>:5432/appcall_db`
  - SQLite format (single file without server): `sqlite:///./appcall_db.sqlite3`

### 2.2 Detailed Backend Table Schemas (SQLAlchemy)

```mermaid
erDiagram
    users ||--o{ calls : makes
    users ||--o{ contacts : owns
    users ||--o{ tasks : assigns
    users ||--o{ agenda_items : organizes
    users ||--o{ files : uploads
    users ||--o{ reminders : configures
    users ||--o{ audit_logs : triggers

    contacts ||--o{ calls : linked_to
    calls ||--o| transcripts : contains
    calls ||--o| call_summaries : generates
    call_summaries ||--o| appointments : extracts

    users {
        VARCHAR(36) id PK "UUID"
        VARCHAR(255) email UK "Login email"
        VARCHAR(255) hashed_password "Bcrypt password hash"
        VARCHAR(255) full_name "Full name"
        BOOLEAN is_active "Active status"
        BOOLEAN is_superuser "Admin flag"
        BOOLEAN gdpr_voice_consent "Global voice GDPR consent"
        DATETIME created_at "Created timestamp"
        DATETIME updated_at "Updated timestamp"
    }

    contacts {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) user_id FK "User relation"
        VARCHAR(100) first_name "First name"
        VARCHAR(100) last_name "Last name"
        VARCHAR(50) phone_number "E.164 phone number"
        VARCHAR(255) email "Email address"
        VARCHAR(255) company "Company name"
        BOOLEAN gdpr_consent "Contact consent flag"
        DATETIME gdpr_consent_date "Consent timestamp"
    }

    calls {
        VARCHAR(36) id PK "UUID or native ID (native-timestamp)"
        VARCHAR(36) user_id FK "User relation"
        VARCHAR(36) contact_id FK "Contact relation"
        VARCHAR(20) direction "INBOUND or OUTBOUND"
        DATETIME started_at "Call start timestamp"
        DATETIME ended_at "Call end timestamp"
        VARCHAR(20) status "INITIATED, RECORDING, COMPLETED"
        BOOLEAN consent_given "Call recording consent flag"
        DATETIME consent_timestamp "Consent recorded timestamp"
        JSON twilio_params "Call metadata (caller_id, contact_name)"
        VARCHAR(512) audio_url "Local path or remote URL to audio"
        VARCHAR(20) ai_status "PENDING, PROCESSING, DONE, FAILED"
    }

    transcripts {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) call_id FK "Call relation (Unique)"
        LONGTEXT raw_text "Full transcript text from Whisper"
        VARCHAR(10) language "Language code (fr, en, etc.)"
        FLOAT confidence_score "Confidence score (e.g., 98.5%)"
        JSON speaker_segments "Diarized timestamped speaker segments"
        DATETIME created_at "Created timestamp"
    }

    call_summaries {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) call_id FK "Call relation (Unique)"
        LONGTEXT summary_text "Contextual synthesis generated by LLM"
        VARCHAR(20) status "PROPOSED, VALIDATED, MODIFIED, CONFIRMED"
        FLOAT confidence_score "Summary confidence score"
        VARCHAR(36) detected_appointment_id "Associated appointment ID"
        DATETIME created_at "Created timestamp"
    }

    appointments {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) call_summary_id FK "Summary relation"
        VARCHAR(255) title "Subject of the meeting / contract"
        DATETIME scheduled_at "ISO-8601 appointment datetime"
        VARCHAR(20) status "PROPOSED, CONFIRMED, CANCELLED, DISMISSED"
        VARCHAR(255) contact_name "Contact name"
        VARCHAR(50) phone_number "Contact phone number"
        TEXT summary_context "Extracted context snippet"
    }

    tasks {
        VARCHAR(36) id PK "UUID or local task_id"
        VARCHAR(36) user_id FK "User relation"
        VARCHAR(255) title "Task description"
        BOOLEAN completed "Completion status (0 or 1)"
        DATETIME due_date "Optional due date"
        VARCHAR(20) priority "LOW, MEDIUM, HIGH"
    }

    agenda_items {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) user_id FK "User relation"
        VARCHAR(255) title "Event title"
        DATETIME scheduled_at "Event datetime"
        VARCHAR(255) contact_name "Contact name"
        VARCHAR(50) phone_number "Associated phone number"
        VARCHAR(20) status "CONFIRMED, TENTATIVE"
    }

    files {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) user_id FK "User relation"
        VARCHAR(255) filename "Original filename"
        VARCHAR(512) stored_path "Path on server storage"
        BIGINT file_size "File size in bytes"
        VARCHAR(100) mime_type "MIME type"
    }

    audit_logs {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) user_id FK "User relation"
        VARCHAR(100) action "Action type (LOGIN, EXPORT, PURGE...)"
        VARCHAR(50) ip_address "IP address"
        TEXT details "Action details"
        DATETIME timestamp "Timestamp"
    }
```

### 2.3 Android Local Database (SQLite)
- **Location**: In the app's sandboxed private storage: `/data/data/com.example.appcall/databases/appcall_local.db`.
- **Role**: Provides zero-latency UI rendering, complete offline mode (airplane mode), and the `sync_queue` table for caching audio files until network reconnection.

---

## 3. End-to-End Dataflow & Call Lifecycle

```mermaid
sequenceDiagram
    autonumber
    actor Client as Phone Contact / Client
    actor User as Sales Agent / Android User
    participant App as Android Mobile App
    participant SQLite as Local SQLite (appcall_local.db)
    participant Backend as FastAPI Server
    participant DB as MySQL Database (appcall_db)
    participant Groq as Groq AI Cloud

    User->>Client: PSTN Phone Call
    App->>App: Intercepts & Records Audio (.m4a)
    User->>Client: Call Ends (Hangup)
    
    App->>SQLite: Instant Local Save (Status: PENDING)
    App->>Backend: POST /api/v1/calls/{id}/audio (Multipart File + Contact Headers)
    
    Backend->>DB: Saves Call Record (ai_status = PROCESSING)
    Backend->>Groq: Audio Stream ➔ Whisper Large v3 Turbo
    Groq-->>Backend: Raw Text + Diarized Segments
    Backend->>DB: INSERT INTO transcripts (raw_text, speaker_segments)
    
    Backend->>Groq: Transcript Prompt ➔ LLM Cascade (GPT-OSS 120B / LLaMA 3.3)
    Groq-->>Backend: Structured JSON (Summary + Extracted Appointment)
    Backend->>DB: INSERT INTO call_summaries & appointments
    Backend->>DB: UPDATE calls SET ai_status = 'DONE'
    
    App->>Backend: Polling GET /api/v1/calls/{id}/summary
    Backend-->>App: 200 OK (Summary + Transcript + Appointment)
    App->>SQLite: Updates Local SQLite Cache
    App->>User: Displays Summary, Dialogue Bubbles & One-Click RDV Validation Card
```

---

## 4. How to Replace `localhost` (Zero Recompilation)

By default, the application is pre-configured to point to `http://127.0.0.1:8000` (for emulator) or `http://192.168.1.12:8000` (for local Wi-Fi testing).

### 📱 1. On the Android App (NO RECOMPILATION NEEDED!)
The app includes a dynamic URL interceptor ([DynamicUrlInterceptor.kt](file:///c:/Users/khali/OneDrive/Bureau/intelligentCall/IntelligentCalls/app/src/main/java/com/example/appcall/data/api/DynamicUrlInterceptor.kt)):

1. Open the Android app on your phone.
2. On the **Login Screen** or in the **Settings Screen**:
   - Tap **"Change Server URL"** (or edit the Server URL field).
   - Enter your public server URL (e.g. `https://api.yourdomain.com` or `https://your-app.up.railway.app`).
   - Tap **Save**.
3. **All Retrofit HTTP calls, audio uploads, and sync workers immediately route to your public server!**

### ⚙️ 2. On the Backend Server (`backend/.env`)
Update the `.env` file on your server:
```env
# Public URL of your server (used for webhooks and audio URLs)
SERVER_BASE_URL=https://api.yourdomain.com

# Listening host (0.0.0.0 accepts public incoming connections)
SERVER_HOST=0.0.0.0
SERVER_PORT=8000

# Allowed CORS origins
CORS_ORIGINS=http://localhost:3000,https://dashboard.yourdomain.com,https://your-app.vercel.app

# Production Database Connection
DATABASE_URL=mysql+pymysql://appcall_user:YourSecurePassword2026!@localhost:3306/appcall_db

# Production Groq API Key
GROQ_API_KEY=gsk_your_production_groq_key
```

### 💻 3. On the Web Dashboard (`dashboard/.env.local`)
```env
NEXT_PUBLIC_API_URL=https://api.yourdomain.com
```

---

## 5. Hosting & Deployment Options for FastAPI Backend

---

### Option A: Free / Managed Cloud (Railway / Render / Fly.io)
> 💡 **Best for deploying in 5 minutes with zero Linux server management and automatic HTTPS.**

#### Deploying on Railway:
1. Create an account on [railway.app](https://railway.app).
2. Click **"New Project"** $\rightarrow$ **"Deploy from GitHub repo"** $\rightarrow$ Select `IntelligentAppCalls`.
3. Set **Root Directory** to `/backend`.
4. Add a **MySQL** (or **PostgreSQL**) database plugin in Railway.
5. In the **Variables** tab, add:
   - `DATABASE_URL` = `${{MySQL.DATABASE_URL}}`
   - `GROQ_API_KEY` = `gsk_...`
   - `JWT_SECRET` = `a_long_random_64_char_secret_key`
   - `CORS_ORIGINS` = `*`
6. Railway generates a public HTTPS URL (e.g. `https://intelligent-calls-production.up.railway.app`).
7. **Paste this URL into your Android app!**

---

### Option B: Dedicated VPS (Ubuntu 22.04 / 24.04 + Docker + Nginx SSL)
> 🛡️ **Best for data sovereignty, GDPR compliance, and maximum performance (Hetzner, OVH, DigitalOcean, AWS EC2).**

#### 1. Server Setup on Ubuntu
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y python3-pip python3-venv git nginx certbot python3-certbot-nginx mysql-server

# Secure MySQL
sudo mysql_secure_installation
```

#### 2. Create MySQL Database
```sql
sudo mysql -u root -p
CREATE DATABASE appcall_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'appcall_user'@'localhost' IDENTIFIED BY 'YourSecurePassword2026!';
GRANT ALL PRIVILEGES ON appcall_db.* TO 'appcall_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

#### 3. Clone & Setup Backend
```bash
cd /var/www
sudo git clone https://github.com/Ysn-Ir/IntelligentAppCalls.git
cd IntelligentAppCalls/backend

# Create virtual environment
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# Configure .env
cp .env.example .env
nano .env
```

#### 4. Configure Systemd Service (Auto-Restart)
Create `/etc/systemd/system/intelligent-calls.service`:
```ini
[Unit]
Description=Intelligent Calls FastAPI Backend
After=network.target mysql.service

[Service]
User=root
WorkingDirectory=/var/www/IntelligentAppCalls/backend
ExecStart=/var/www/IntelligentAppCalls/backend/venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000 --workers 4
Restart=always
RestartSec=5
Environment=PYTHONUNBUFFERED=1

[Install]
WantedBy=multi-user.target
```
Enable and start the service:
```bash
sudo systemctl daemon-reload
sudo systemctl enable intelligent-calls
sudo systemctl start intelligent-calls
sudo systemctl status intelligent-calls
```

#### 5. Nginx & Free Let's Encrypt SSL
Create `/etc/nginx/sites-available/api.yourdomain.com`:
```nginx
server {
    server_name api.yourdomain.com;

    client_max_body_size 50M;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```
Enable site and obtain SSL certificate:
```bash
sudo ln -s /etc/nginx/sites-available/api.yourdomain.com /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d api.yourdomain.com
```

---

### Option C: Free Secure Tunnel from Your PC (Cloudflare Tunnel / Ngrok)
> 🚀 **Best for testing immediately on a physical phone in 4G/5G without paying for a server or opening router ports.**

#### Using Cloudflare Tunnel (100% Free & Unlimited):
1. Install `cloudflared` on Windows:
   ```powershell
   winget install Cloudflare.cloudflared
   ```
2. Start the tunnel targeting your local backend port:
   ```powershell
   cloudflared tunnel --url http://localhost:8000
   ```
3. Cloudflare gives you a secure HTTPS URL (e.g. `https://random-name-1234.trycloudflare.com`).
4. **Type this URL into your Android app: live calls recorded over 4G/5G will sync straight to your PC!**

---

## 6. Deploying the Web Dashboard (Next.js 16) on Vercel

1. Create an account on [vercel.com](https://vercel.com).
2. Import your GitHub repository `IntelligentAppCalls`.
3. Set **Root Directory** to `dashboard`.
4. Add Environment Variable:
   - `NEXT_PUBLIC_API_URL` = `https://api.yourdomain.com` (or your Railway/Cloudflare URL)
5. Click **Deploy**. Your CRM analytics web dashboard is live.

---

## 7. Production Environment Variables Reference (`.env`)

```env
# ==============================================================================
# SERVER AND API CONFIGURATION
# ==============================================================================
ENVIRONMENT=production
SERVER_BASE_URL=https://api.yourdomain.com
SERVER_HOST=0.0.0.0
SERVER_PORT=8000
CORS_ORIGINS=https://dashboard.yourdomain.com,http://localhost:3000

# ==============================================================================
# PRODUCTION MYSQL DATABASE
# ==============================================================================
DATABASE_URL=mysql+pymysql://appcall_user:YourSecurePassword2026!@localhost:3306/appcall_db

# ==============================================================================
# AUTHENTICATION & JWT SECURITY
# ==============================================================================
JWT_SECRET=super_secret_64_character_random_hex_string_for_production
JWT_ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=43200

# ==============================================================================
# GROQ CLOUD AI ENGINES (WHISPER STT & CASCADE LLMS)
# ==============================================================================
GROQ_API_KEY=gsk_your_real_production_groq_key
GROQ_BASE_URL=https://api.groq.com/openai/v1
GROQ_MODEL=openai/gpt-oss-120b
GROQ_FALLBACK_MODEL=llama-3.3-70b-versatile
GROQ_STT_MODEL=whisper-large-v3-turbo

# ==============================================================================
# AUDIO STORAGE & GDPR POLICIES
# ==============================================================================
AUDIO_UPLOAD_DIR=./uploads
GDPR_DATA_RETENTION_DAYS=365
```
