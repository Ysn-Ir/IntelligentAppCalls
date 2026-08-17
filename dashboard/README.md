# IntelligentAppCalls — Web Dashboard 💻

A modern, high-performance Next.js 16 (React 19 + TypeScript) web dashboard for **IntelligentAppCalls**. Provides real-time CRM analytics, call recording audio playback, AI conversation inspector, and a comprehensive GDPR compliance portal.

---

## ✨ Features

- **📊 CRM Analytics Dashboard**: Live metrics for completed calls, missed calls, active tasks, and scheduled appointments.
- **👥 Contact Management**: Browse CRM contacts, view conversation histories, and start targeted AI queries.
- **📞 Call History & Audio Inspector**: Search call records, inspect Whisper transcriptions with speaker segments, and view AI summaries.
- **🤖 Assistant IA (RAG Chatbot)**: Ask natural language questions about conversations, client agreements, and meeting schedules.
- **🛡️ GDPR Compliance Center**: 
  - **Art. 15**: 1-click JSON full data export & portability.
  - **Art. 17**: Right to erasure with immediate account and audio file deletion.

---

## 🚀 Getting Started

### 1. Installation
```bash
cd dashboard
npm install
```

### 2. Configure Environment (`.env.local`)
```env
NEXT_PUBLIC_API_URL=http://localhost:8000
```

### 3. Run Development Server
```bash
npm run dev
```

Open **[http://localhost:3000](http://localhost:3000)** in your browser.

---

## 📁 App Router Routes

- `/` — Main dashboard with overview statistics and recent calls.
- `/contacts` — Contact directory with direct AI chat shortcuts.
- `/calls` — Comprehensive call history with full transcripts and summaries.
- `/chat` — Interactive RAG AI assistant.
- `/gdpr` — Privacy compliance, data portability, and account deletion.
