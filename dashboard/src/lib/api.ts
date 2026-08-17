// lib/api.ts — API client for the backend

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("token");
}

function headers(): Record<string, string> {
  const token = getToken();
  const h: Record<string, string> = { "Content-Type": "application/json" };
  h["Authorization"] = token ? `Bearer ${token}` : "Bearer dummy_test_token";
  return h;
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: { ...headers(), ...(options?.headers || {}) },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ detail: res.statusText }));
    throw new Error(err.detail || "Erreur réseau");
  }
  return res.json();
}

// ── Auth ──────────────────────────────────────────────────────────────
export const api = {
  login: (email: string, password: string) =>
    request<{ access_token: string; token_type: string }>("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),

  me: () => request<{ id: string; first_name: string; last_name: string; email: string }>("/api/v1/users/me"),

  // ── Contacts ──────────────────────────────────────────────────────
  contacts: () => request<Contact[]>("/api/v1/contacts"),

  // ── Calls ──────────────────────────────────────────────────────────
  calls: (contactId?: string) =>
    request<CallHistoryItem[]>(`/api/v1/calls${contactId ? `?contact_id=${contactId}` : ""}`),

  transcript: (callId: string) => request<Transcript>(`/api/v1/calls/${callId}/transcript`),

  summary: (callId: string) => request<Summary>(`/api/v1/calls/${callId}/summary`),

  aiStatus: (callId: string) => request<AiStatus>(`/api/v1/calls/${callId}/ai-status`),

  // ── Chatbot ──────────────────────────────────────────────────────
  chat: (contactId: string, message: string, sessionId?: string) =>
    request<ChatResponse>(`/api/v1/contacts/${contactId}/chat`, {
      method: "POST",
      body: JSON.stringify({ message, session_id: sessionId }),
    }),

  globalChat: (message: string, sessionId?: string) =>
    request<ChatResponse>("/api/v1/chat", {
      method: "POST",
      body: JSON.stringify({ message, session_id: sessionId }),
    }),

  chatHistory: (contactId: string) =>
    request<{ session_id: string | null; messages: ChatMessage[] }>(`/api/v1/contacts/${contactId}/chat/history`),

  globalChatHistory: () =>
    request<{ session_id: string | null; messages: ChatMessage[] }>("/api/v1/chat/history"),

  clearGlobalChat: () =>
    request<{ status: string }>("/api/v1/chat/history", { method: "DELETE" }),

  // ── GDPR ──────────────────────────────────────────────────────────
  exportData: () => request<object>("/api/v1/me/export"),

  deleteAccount: () =>
    request<{ status: string }>("/api/v1/me", { method: "DELETE" }),

  deleteCallData: (callId: string) =>
    request<{ status: string }>(`/api/v1/calls/${callId}/data`, { method: "DELETE" }),

  eraseContactData: (contactId: string) =>
    request<{ status: string }>(`/api/v1/contacts/${contactId}/data`, { method: "DELETE" }),
};

// ── Types ──────────────────────────────────────────────────────────────
export interface Contact {
  id: string;
  first_name: string;
  last_name: string;
  phone_number: string;
  email: string;
  global_gdpr_consent: boolean;
}

export interface CallHistoryItem {
  id: string;
  contact_id: string;
  direction: "INBOUND" | "OUTBOUND";
  status: "COMPLETED" | "MISSED" | "FAILED" | "ONGOING";
  started_at: string | null;
  ended_at: string | null;
  contact_name: string | null;
}

export interface Transcript {
  id: string;
  call_id: string;
  raw_text: string;
  language: string;
  confidence_score: number;
  speaker_segments?: SpeakerSegment[];
}

export interface SpeakerSegment {
  speaker: "agent" | "contact";
  start: number;
  end: number;
  text: string;
}

export interface Summary {
  id: string;
  call_id: string;
  summary_text: string;
  status: string;
  confidence_score: number | null;
}

export interface AiStatus {
  call_id: string;
  ai_status: string;
  has_transcript: boolean;
  has_summary: boolean;
  transcript_confidence: number | null;
}

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

export interface ChatSource {
  call_id: string | null;
  call_date: string | null;
  excerpt: string | null;
}

export interface ChatResponse {
  session_id: string;
  reply: string;
  sources: ChatSource[];
}
