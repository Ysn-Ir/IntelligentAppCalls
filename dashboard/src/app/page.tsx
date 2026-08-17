"use client";
import { useState, useEffect } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { api, Contact, CallHistoryItem } from "@/lib/api";

function Sidebar({ active }: { active: string }) {
  const nav = [
    { href: "/", icon: "📊", label: "Tableau de bord" },
    { href: "/contacts", icon: "👥", label: "Contacts" },
    { href: "/calls", icon: "📞", label: "Historique appels" },
    { href: "/chat", icon: "🤖", label: "Assistant IA" },
  ];
  const gdpr = [
    { href: "/gdpr", icon: "🛡️", label: "RGPD & Données" },
  ];

  return (
    <nav className="sidebar">
      <div className="sidebar-logo">
        <div className="logo-icon">📱</div>
        <div>
          <div className="logo-text">IntelligentCalls</div>
          <div className="logo-sub">Tableau de bord IA</div>
        </div>
      </div>

      <div className="nav-section-label">Navigation</div>
      {nav.map((item) => (
        <Link key={item.href} href={item.href} className={`nav-item ${active === item.href ? "active" : ""}`}>
          <span className="nav-icon">{item.icon}</span>
          {item.label}
        </Link>
      ))}

      <div className="nav-section-label" style={{ marginTop: 16 }}>Conformité</div>
      {gdpr.map((item) => (
        <Link key={item.href} href={item.href} className={`nav-item ${active === item.href ? "active" : ""}`}>
          <span className="nav-icon">{item.icon}</span>
          {item.label}
        </Link>
      ))}
    </nav>
  );
}

// ── Dashboard Home ────────────────────────────────────────────────────
function DashboardHome() {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [calls, setCalls] = useState<CallHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([api.contacts(), api.calls()])
      .then(([c, ca]) => { setContacts(c); setCalls(ca); })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const completed = calls.filter((c) => c.status === "COMPLETED").length;
  const missed = calls.filter((c) => c.status === "MISSED").length;

  return (
    <div className="fade-in">
      <div className="page-header">
        <h1>Tableau de bord</h1>
        <p>Vue d'ensemble de vos appels et analyses IA</p>
      </div>

      <div className="grid-3" style={{ marginBottom: 24 }}>
        <div className="stat-tile">
          <div className="stat-icon">👥</div>
          <div className="stat-label">Contacts</div>
          <div className="stat-value">{loading ? "—" : contacts.length}</div>
          <div className="stat-sub">dans votre CRM</div>
        </div>
        <div className="stat-tile">
          <div className="stat-icon">✅</div>
          <div className="stat-label">Appels terminés</div>
          <div className="stat-value">{loading ? "—" : completed}</div>
          <div className="stat-sub">avec transcription IA</div>
        </div>
        <div className="stat-tile">
          <div className="stat-icon">📵</div>
          <div className="stat-label">Appels manqués</div>
          <div className="stat-value" style={{ color: "var(--warning)" }}>{loading ? "—" : missed}</div>
          <div className="stat-sub">à rappeler</div>
        </div>
      </div>

      <div className="card">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <h2 style={{ fontSize: 16, fontWeight: 600 }}>Derniers appels</h2>
          <Link href="/calls" className="btn btn-ghost" style={{ fontSize: 12 }}>Voir tout →</Link>
        </div>
        {loading ? (
          <div className="empty-state"><div className="spinner" /></div>
        ) : calls.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">📞</div>
            <div className="empty-title">Aucun appel enregistré</div>
            <div className="empty-desc">Démarrez un appel depuis l'application Android</div>
          </div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Contact</th><th>Direction</th><th>Statut</th><th>Date</th></tr>
              </thead>
              <tbody>
                {calls.slice(0, 8).map((call) => (
                  <tr key={call.id}>
                    <td style={{ color: "var(--text-primary)", fontWeight: 500 }}>{call.contact_name || "Inconnu"}</td>
                    <td>
                      <span className={`badge ${call.direction === "INBOUND" ? "badge-blue" : "badge-gray"}`}>
                        {call.direction === "INBOUND" ? "📥 Entrant" : "📤 Sortant"}
                      </span>
                    </td>
                    <td>
                      <span className={`badge ${call.status === "COMPLETED" ? "badge-green" : call.status === "MISSED" ? "badge-yellow" : "badge-gray"}`}>
                        {call.status}
                      </span>
                    </td>
                    <td>{call.started_at ? new Date(call.started_at).toLocaleString("fr-FR") : "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Contacts Page ─────────────────────────────────────────────────────
function ContactsPage() {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");

  useEffect(() => {
    api.contacts().then(setContacts).catch(console.error).finally(() => setLoading(false));
  }, []);

  const filtered = contacts.filter((c) =>
    `${c.first_name} ${c.last_name} ${c.phone_number} ${c.email}`.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="fade-in">
      <div className="page-header">
        <h1>Contacts</h1>
        <p>{contacts.length} contact{contacts.length !== 1 ? "s" : ""} dans votre CRM</p>
      </div>

      <div style={{ marginBottom: 20 }}>
        <input className="input" placeholder="🔍 Rechercher un contact..." value={search} onChange={(e) => setSearch(e.target.value)} />
      </div>

      {loading ? (
        <div className="empty-state"><div className="spinner" /></div>
      ) : (
        <div className="grid-auto">
          {filtered.map((c) => (
            <div key={c.id} className="card card-sm" style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div>
                  <div style={{ fontWeight: 600, fontSize: 15, color: "var(--text-primary)" }}>
                    {c.first_name} {c.last_name}
                  </div>
                  <div style={{ fontSize: 12, color: "var(--text-muted)", marginTop: 2 }}>{c.phone_number}</div>
                </div>
                <span className={`badge ${c.global_gdpr_consent ? "badge-green" : "badge-yellow"}`}>
                  {c.global_gdpr_consent ? "✓ Consentement" : "⚠ Sans consentement"}
                </span>
              </div>
              <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>{c.email}</div>
              <div style={{ display: "flex", gap: 8, marginTop: 4 }}>
                <Link href={`/chat?contact=${c.id}&name=${encodeURIComponent(c.first_name + " " + c.last_name)}`} className="btn btn-primary" style={{ fontSize: 12, padding: "6px 12px" }}>
                  🤖 Chat IA
                </Link>
                <Link href={`/calls?contact=${c.id}`} className="btn btn-ghost" style={{ fontSize: 12, padding: "6px 12px" }}>
                  📞 Appels
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Calls Page ─────────────────────────────────────────────────────────
function CallsPage({ contactId }: { contactId?: string }) {
  const [calls, setCalls] = useState<CallHistoryItem[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [transcript, setTranscript] = useState<{ raw_text: string; confidence_score: number; speaker_segments?: Array<{ speaker: string; start: number; text: string }> } | null>(null);
  const [summary, setSummary] = useState<{ summary_text: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);

  useEffect(() => {
    api.calls(contactId).then(setCalls).catch(console.error).finally(() => setLoading(false));
  }, [contactId]);

  async function loadDetail(callId: string) {
    setSelected(callId);
    setTranscript(null); setSummary(null);
    setLoadingDetail(true);
    try {
      const [t, s] = await Promise.allSettled([api.transcript(callId), api.summary(callId)]);
      if (t.status === "fulfilled") setTranscript(t.value as typeof transcript);
      if (s.status === "fulfilled") setSummary(s.value as typeof summary);
    } finally {
      setLoadingDetail(false);
    }
  }

  return (
    <div className="fade-in">
      <div className="page-header">
        <h1>Historique des appels</h1>
        <p>Cliquez sur un appel pour voir la transcription et le résumé IA</p>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1.2fr", gap: 20, height: "calc(100vh - 200px)" }}>
        {/* Call list */}
        <div className="card" style={{ overflow: "auto", padding: 0 }}>
          {loading ? (
            <div className="empty-state"><div className="spinner" /></div>
          ) : calls.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">📞</div>
              <div className="empty-title">Aucun appel</div>
            </div>
          ) : (
            <table>
              <thead><tr><th>Contact</th><th>Statut</th><th>Date</th></tr></thead>
              <tbody>
                {calls.map((call) => (
                  <tr key={call.id} onClick={() => loadDetail(call.id)} style={{ cursor: "pointer", background: selected === call.id ? "var(--accent-glow)" : undefined }}>
                    <td style={{ color: "var(--text-primary)", fontWeight: 500 }}>
                      {call.direction === "INBOUND" ? "📥" : "📤"} {call.contact_name || "Inconnu"}
                    </td>
                    <td><span className={`badge ${call.status === "COMPLETED" ? "badge-green" : "badge-yellow"}`}>{call.status}</span></td>
                    <td style={{ fontSize: 12 }}>{call.started_at ? new Date(call.started_at).toLocaleString("fr-FR") : "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Detail panel */}
        <div className="card" style={{ overflow: "auto" }}>
          {!selected ? (
            <div className="empty-state">
              <div className="empty-icon">👈</div>
              <div className="empty-title">Sélectionnez un appel</div>
              <div className="empty-desc">La transcription et le résumé IA s'afficheront ici</div>
            </div>
          ) : loadingDetail ? (
            <div className="empty-state"><div className="spinner" /></div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
              {summary && (
                <div>
                  <div style={{ fontSize: 12, fontWeight: 700, color: "var(--accent-light)", marginBottom: 8, textTransform: "uppercase", letterSpacing: "0.05em" }}>
                    🤖 Résumé IA
                  </div>
                  <div style={{ fontSize: 14, color: "var(--text-primary)", lineHeight: 1.7, background: "var(--bg-secondary)", padding: "12px 16px", borderRadius: 10 }}>
                    {summary.summary_text}
                  </div>
                </div>
              )}
              {transcript && (
                <div>
                  <div style={{ fontSize: 12, fontWeight: 700, color: "var(--success)", marginBottom: 8, textTransform: "uppercase", letterSpacing: "0.05em" }}>
                    📝 Transcription ({transcript.confidence_score.toFixed(0)}% de confiance)
                  </div>
                  {transcript.speaker_segments && transcript.speaker_segments.length > 0 ? (
                    transcript.speaker_segments.map((seg, i) => (
                      <div key={i} className="transcript-line">
                        <span className={`transcript-speaker ${seg.speaker}`}>{seg.speaker === "agent" ? "Moi" : "Contact"}</span>
                        <span className="transcript-text">{seg.text}</span>
                        <span className="transcript-time">{Math.floor(seg.start)}s</span>
                      </div>
                    ))
                  ) : (
                    <div style={{ fontSize: 14, color: "var(--text-primary)", lineHeight: 1.7 }}>{transcript.raw_text}</div>
                  )}
                </div>
              )}
              {!transcript && !summary && (
                <div className="empty-state">
                  <div className="empty-icon">⏳</div>
                  <div className="empty-title">Pas encore de données IA</div>
                  <div className="empty-desc">La transcription sera disponible après le traitement de l'enregistrement</div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ── Chat Page ─────────────────────────────────────────────────────────
function ChatPage({ contactId, contactName }: { contactId?: string; contactName?: string }) {
  const [messages, setMessages] = useState<Array<{ role: "user" | "assistant"; content: string; sources?: Array<{ call_id: string | null; call_date: string | null; excerpt: string | null }> }>>([]);
  const [input, setInput] = useState("");
  const [sessionId, setSessionId] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [selectedContact, setSelectedContact] = useState<string | undefined>(contactId);

  useEffect(() => {
    api.contacts().then(setContacts).catch(console.error);
  }, []);

  // Load persistent conversation history for the selected scope
  useEffect(() => {
    setLoading(true);
    const fetchHistory = selectedContact
      ? api.chatHistory(selectedContact)
      : api.globalChatHistory();

    fetchHistory
      .then((data) => {
        if (data && data.messages && data.messages.length > 0) {
          setSessionId(data.session_id || undefined);
          setMessages(
            data.messages.map((m) => ({
              role: m.role as "user" | "assistant",
              content: m.content,
            }))
          );
        } else {
          setMessages([]);
          setSessionId(undefined);
        }
      })
      .catch((err) => {
        console.error("Failed to load chat history:", err);
        setMessages([]);
      })
      .finally(() => setLoading(false));
  }, [selectedContact]);

  async function clearHistory() {
    if (!confirm("Effacer tout l'historique de cette conversation ?")) return;
    try {
      if (selectedContact) {
        await api.clearContactChat?.(selectedContact);
      } else {
        await api.clearGlobalChat();
      }
      setMessages([]);
      setSessionId(undefined);
    } catch (err) {
      console.error("Failed to clear chat history:", err);
    }
  }

  async function send() {
    if (!input.trim()) return;
    const msg = input.trim();
    setInput("");
    setMessages((prev) => [...prev, { role: "user", content: msg }]);
    setLoading(true);
    try {
      const res = selectedContact
        ? await api.chat(selectedContact, msg, sessionId)
        : await api.globalChat(msg, sessionId);
      setSessionId(res.session_id);
      setMessages((prev) => [...prev, { role: "assistant", content: res.reply, sources: res.sources }]);
    } catch (e: unknown) {
      const error = e as Error;
      setMessages((prev) => [...prev, { role: "assistant", content: `Erreur: ${error.message}` }]);
    } finally {
      setLoading(false);
    }
  }

  const displayName = contactName || contacts.find((c) => c.id === selectedContact)?.first_name || "Tous vos contacts";

  return (
    <div className="fade-in" style={{ height: "calc(100vh - 64px)", display: "flex", flexDirection: "column" }}>
      <div className="page-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
        <div>
          <h1>Assistant IA</h1>
          <p>Posez des questions sur vos appels avec {displayName}</p>
        </div>
        {messages.length > 0 && (
          <button className="btn btn-secondary" onClick={clearHistory} style={{ fontSize: 12, padding: "6px 12px" }}>
            🗑️ Effacer l'historique
          </button>
        )}
      </div>

      {/* Contact selector */}
      <div style={{ marginBottom: 16 }}>
        <select
          className="input"
          style={{ maxWidth: 300 }}
          value={selectedContact || ""}
          onChange={(e) => setSelectedContact(e.target.value || undefined)}
        >
          <option value="">🌍 Tous les contacts (chatbot global)</option>
          {contacts.map((c) => (
            <option key={c.id} value={c.id}>{c.first_name} {c.last_name}</option>
          ))}
        </select>
      </div>

      <div className="card" style={{ flex: 1, display: "flex", flexDirection: "column", minHeight: 0 }}>
        <div className="chat-messages" style={{ flex: 1, overflowY: "auto" }}>
          {messages.length === 0 && (
            <div className="empty-state">
              <div className="empty-icon">🤖</div>
              <div className="empty-title">Posez une question</div>
              <div className="empty-desc">Ex: "Qu'est-ce que Jean a dit lors du dernier appel ?" ou "Résume mes appels de cette semaine"</div>
            </div>
          )}
          {messages.map((msg, i) => (
            <div key={i} style={{ display: "flex", flexDirection: "column", alignItems: msg.role === "user" ? "flex-end" : "flex-start", gap: 4 }}>
              <div className={`chat-bubble ${msg.role}`}>{msg.content}</div>
              {msg.sources && msg.sources.length > 0 && (
                <div className="chat-sources">
                  {msg.sources.map((s, j) => (
                    <span key={j} className="chat-source-chip">
                      📞 {s.call_date ? new Date(s.call_date).toLocaleDateString("fr-FR") : "Appel"} {s.call_id ? `#${s.call_id.slice(0, 6)}` : ""}
                    </span>
                  ))}
                </div>
              )}
            </div>
          ))}
          {loading && (
            <div style={{ alignSelf: "flex-start" }}>
              <div className="chat-bubble assistant" style={{ display: "flex", gap: 6, alignItems: "center" }}>
                <div className="spinner" style={{ width: 14, height: 14 }} />
                <span style={{ color: "var(--text-muted)", fontSize: 13 }}>L'assistant réfléchit...</span>
              </div>
            </div>
          )}
        </div>
        <div className="chat-input-row">
          <input
            className="input"
            placeholder="Votre question..."
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && !e.shiftKey && send()}
          />
          <button className="btn btn-primary" onClick={send} disabled={loading || !input.trim()}>
            Envoyer
          </button>
        </div>
      </div>
    </div>
  );
}

// ── GDPR Page ─────────────────────────────────────────────────────────
function GdprPage() {
  const [exporting, setExporting] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [done, setDone] = useState("");

  async function handleExport() {
    setExporting(true);
    try {
      const data = await api.exportData();
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a"); a.href = url; a.download = "mes_donnees_appcall.json"; a.click();
      setDone("Export téléchargé ✓");
    } catch (e: unknown) {
      const error = e as Error;
      setDone(`Erreur: ${error.message}`);
    } finally {
      setExporting(false);
    }
  }

  async function handleDelete() {
    if (!confirm("Supprimer définitivement votre compte et toutes vos données ? Cette action est irréversible.")) return;
    setDeleting(true);
    try {
      await api.deleteAccount();
      setDone("Compte supprimé. Toutes vos données ont été effacées conformément au RGPD.");
      localStorage.removeItem("token");
    } catch (e: unknown) {
      const error = e as Error;
      setDone(`Erreur: ${error.message}`);
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="fade-in">
      <div className="page-header">
        <h1>RGPD & Gestion des données</h1>
        <p>Vos droits conformément au Règlement Général sur la Protection des Données</p>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        {done && (
          <div style={{ background: "var(--bg-card)", border: "1px solid var(--success)", borderRadius: 12, padding: "12px 16px", color: "var(--success)", fontSize: 14 }}>
            {done}
          </div>
        )}

        <div className="card">
          <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>📥 Droit d'accès (Art. 15 RGPD)</h2>
          <p style={{ fontSize: 14, color: "var(--text-secondary)", marginBottom: 16, lineHeight: 1.6 }}>
            Vous avez le droit d'obtenir une copie de toutes vos données personnelles traitées par IntelligentCalls.
            Cet export contient : votre profil, vos contacts, l'historique de vos appels, les transcriptions et les résumés IA.
          </p>
          <button className="btn btn-primary" onClick={handleExport} disabled={exporting}>
            {exporting ? "Export en cours..." : "📥 Télécharger mes données"}
          </button>
        </div>

        <div className="card">
          <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>🔒 Consentement aux enregistrements</h2>
          <p style={{ fontSize: 14, color: "var(--text-secondary)", lineHeight: 1.6 }}>
            Conformément à l'<strong style={{ color: "var(--text-primary)" }}>Art. L226-1 du Code Pénal</strong> français,
            tout enregistrement téléphonique nécessite le consentement explicite des deux parties.
            L'application ne stocke aucun enregistrement si le consentement n'a pas été enregistré.
          </p>
          <div style={{ marginTop: 12, display: "flex", gap: 8, flexWrap: "wrap" }}>
            <span className="badge badge-green">✓ Consentement requis avant upload</span>
            <span className="badge badge-green">✓ Enregistré avec horodatage</span>
            <span className="badge badge-green">✓ Révocable à tout moment</span>
          </div>
        </div>

        <div className="card">
          <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>⏱️ Politique de rétention des données</h2>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Type de donnée</th><th>Durée de conservation</th><th>Suppression automatique</th></tr></thead>
              <tbody>
                <tr><td>Fichiers audio</td><td>30 jours</td><td><span className="badge badge-green">✓ Auto</span></td></tr>
                <tr><td>Transcriptions</td><td>1 an</td><td><span className="badge badge-green">✓ Auto</span></td></tr>
                <tr><td>Résumés IA & rendez-vous</td><td>2 ans</td><td><span className="badge badge-green">✓ Auto</span></td></tr>
                <tr><td>Données de compte</td><td>Jusqu'à suppression</td><td><span className="badge badge-blue">Sur demande</span></td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <div className="card" style={{ border: "1px solid rgba(239,68,68,0.3)" }}>
          <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 8, color: "var(--danger)" }}>🗑️ Droit à l'effacement (Art. 17 RGPD)</h2>
          <p style={{ fontSize: 14, color: "var(--text-secondary)", marginBottom: 16, lineHeight: 1.6 }}>
            Vous pouvez demander la suppression définitive de votre compte et de toutes vos données.
            Cette action est <strong style={{ color: "var(--danger)" }}>irréversible</strong> et prend effet immédiatement.
            Vos contacts seront anonymisés (nom remplacé par "Utilisateur Supprimé").
          </p>
          <button className="btn btn-danger" onClick={handleDelete} disabled={deleting}>
            {deleting ? "Suppression..." : "🗑️ Supprimer mon compte et mes données"}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Main App with routing ─────────────────────────────────────────────
export default function App() {
  const pathname = usePathname();
  const [searchParams, setSearchParams] = useState<URLSearchParams | null>(null);

  useEffect(() => {
    setSearchParams(new URLSearchParams(window.location.search));
  }, [pathname]);

  const contactId = searchParams?.get("contact") || undefined;
  const contactName = searchParams?.get("name") ? decodeURIComponent(searchParams.get("name")!) : undefined;

  function renderPage() {
    if (pathname === "/contacts") return <ContactsPage />;
    if (pathname === "/calls") return <CallsPage contactId={contactId} />;
    if (pathname === "/chat") return <ChatPage contactId={contactId} contactName={contactName} />;
    if (pathname === "/gdpr") return <GdprPage />;
    return <DashboardHome />;
  }

  return (
    <div className="app-shell">
      <Sidebar active={pathname} />
      <main className="main-content">{renderPage()}</main>
    </div>
  );
}
