"""
chatbot.py — Full-Context RAG Assistant & Chatbot
=================================================

Answers questions about:
  - Tasks & To-Dos
  - Agenda & Appointments
  - Contacts & Phone Numbers
  - Call Transcriptions & Intelligent Call Summaries

Scoped to a specific contact or global across all user data.
"""

import os
import json
import uuid
import logging
from datetime import datetime
from typing import Optional, List

from .embeddings import search_similar_chunks

logger = logging.getLogger(__name__)

OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
MAX_HISTORY = int(os.getenv("CHATBOT_MAX_HIST", "10"))

LANGUAGE_LABELS = {
    "en": "English",
    "fr": "French",
    "ar": "Arabic",
    "es": "Spanish",
    "de": "German",
    "zh": "Chinese",
    "ja": "Japanese",
}

def get_chatbot_system_prompt(language: str = "en") -> str:
    lang_name = LANGUAGE_LABELS.get(language, "English")
    return f"""You are an advanced enterprise AI assistant for the Intelligent Calls telephony platform.
You have real-time access to the user's authentic CRM data:
1. Tasks & To-Do list (with completion statuses)
2. Agenda & Scheduled Appointments (dates, times, titles)
3. Address Book & Contacts (names, verified phone numbers, emails, companies)
4. Recent Call Summaries & Verbatim Transcriptions

Strict Operating Rules:
- Respond fluently, professionally, and concisely in {lang_name} (or match the user's inquiry language).
- Base your answers STRICTLY on the authentic context provided below.
- NEVER fabricate phone numbers, fake contacts, or hallucinated meetings.
- If the user asks about their tasks, accurately list their tasks with their completed/pending status.
- If the user asks about their appointments or agenda, list the real scheduled events with date and time.
- If the user asks for a contact's info, provide their exact phone number or email from the context.
- If the requested information is absent from the provided context, state clearly and honestly that the record was not found.
"""


def _get_openai():
    try:
        from openai import OpenAI
        api_key = os.getenv("GROQ_API_KEY") or os.getenv("OPENAI_API_KEY", "")
        if not api_key:
            return None
        base_url = os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1") if api_key.startswith("gsk_") else None
        return OpenAI(api_key=api_key, base_url=base_url)
    except ImportError:
        return None


def chat(
    user_id: str,
    message: str,
    contact_id: Optional[str],
    db,
    session_id: Optional[str] = None,
    language: Optional[str] = None,
) -> dict:
    """
    Processes a user message and returns an AI reply with source attribution.
    """
    from ..database import ChatbotSession, Contact, Task, Agenda, Appointment, Call, CallSummary

    # 1. Load or create session
    session = None
    if session_id:
        session = db.query(ChatbotSession).filter(ChatbotSession.id == session_id).first()

    if not session:
        valid_contact_id = None
        if contact_id:
            c_exists = db.query(Contact).filter(Contact.id == contact_id).first()
            if c_exists:
                valid_contact_id = contact_id
            else:
                # Auto-create the contact or leave valid_contact_id as None to avoid FK violation
                try:
                    new_c = Contact(
                        id=contact_id,
                        user_id=user_id,
                        first_name="Contact",
                        last_name=contact_id,
                        phone_number=contact_id if (contact_id.startswith("+") or contact_id.isdigit()) else "+33000000000",
                        global_gdpr_consent=True
                    )
                    db.add(new_c)
                    db.commit()
                    valid_contact_id = contact_id
                except Exception:
                    db.rollback()
                    valid_contact_id = None

        session = ChatbotSession(
            id=session_id if session_id else str(uuid.uuid4()),
            user_id=user_id,
            contact_id=valid_contact_id,
            messages=json.dumps([]),
        )
        db.add(session)
        try:
            db.commit()
            db.refresh(session)
        except Exception:
            db.rollback()
            session.contact_id = None
            db.add(session)
            db.commit()
            db.refresh(session)

    history = json.loads(session.messages) if session.messages else []

    # 2. Fetch User Real Data Context: Tasks
    tasks = db.query(Task).filter(Task.user_id == user_id).all()
    tasks_text = "\n".join([
        f"- [{'Terminée' if t.completed else 'En cours'}] {t.title}" for t in tasks
    ]) if tasks else "(Aucune tâche enregistrée)"

    # 3. Fetch User Real Data Context: Agenda & Appointments
    agenda_items = db.query(Agenda).filter(Agenda.user_id == user_id).all()
    user_contacts = db.query(Contact).filter(Contact.user_id == user_id).all()
    contact_map = {c.id: f"{c.first_name} {c.last_name}" for c in user_contacts}

    appointments = db.query(Appointment).filter(Appointment.user_id == user_id).all()

    agenda_parts = [f"- {a.title} prévu le {a.scheduled_at}" for a in agenda_items]
    for app in appointments:
        c_name = contact_map.get(app.contact_id, "Contact")
        agenda_parts.append(f"- Rendez-vous '{app.title or 'RDV'}' avec {c_name} le {app.scheduled_at} [Statut: {app.status}]")
    agenda_text = "\n".join(agenda_parts) if agenda_parts else "(Aucun événement d'agenda)"

    # 4. Fetch User Real Data Context: Contacts
    contacts_parts = []
    for c in user_contacts:
        email_txt = f", Email: {c.email}" if c.email else ""
        contacts_parts.append(f"- {c.first_name} {c.last_name}: Téléphone {c.phone_number}{email_txt}")
    contacts_text = "\n".join(contacts_parts) if contacts_parts else "(Aucun contact enregistré)"

    # 5. Fetch User Real Data Context: Recent Calls & Summaries
    recent_calls = db.query(Call).filter(Call.user_id == user_id).order_by(Call.started_at.desc()).limit(8).all()
    call_summaries_parts = []
    for call in recent_calls:
        sum_obj = db.query(CallSummary).filter(CallSummary.call_id == call.id).first()
        c_label = f"{call.contact.first_name} {call.contact.last_name} ({call.contact.phone_number})" if call.contact else "Numéro non répertorié"
        s_text = sum_obj.summary_text if sum_obj else "En attente de transcription"
        call_summaries_parts.append(f"- Appel du {call.started_at} avec {c_label} : {s_text}")
    calls_text = "\n".join(call_summaries_parts) if call_summaries_parts else "(Aucun appel récent)"

    # 6. Semantic search for relevant chunks
    chunks = search_similar_chunks(message, contact_id, db, top_k=4, user_id=user_id)
    chunks_parts = []
    for chunk in chunks:
        date_label = chunk["call_date"][:10] if chunk.get("call_date") else "date inconnue"
        chunks_parts.append(f"[Appel du {date_label}]\n{chunk['chunk_text']}")
    chunks_text = "\n\n---\n\n".join(chunks_parts) if chunks_parts else "(Aucun extrait textuel supplémentaire)"

    # 7. Build Full System Context
    target_lang = language if language and language != "auto" else "en"
    target_contact_label = "All Contacts"
    if contact_id:
        c_found = db.query(Contact).filter(Contact.id == contact_id).first()
        if c_found:
            target_contact_label = f"{c_found.first_name} {c_found.last_name} ({c_found.phone_number})"

    system_instructions = get_chatbot_system_prompt(target_lang)
    full_context = (
        f"{system_instructions}\n\n"
        f"=== CURRENT USER ACCOUNT CONTEXT ===\n"
        f"Active Contact Scope : {target_contact_label}\n\n"
        f"--- USER TASKS ---\n{tasks_text}\n\n"
        f"--- AGENDA & APPOINTMENTS ---\n{agenda_text}\n\n"
        f"--- CONTACTS BOOK ---\n{contacts_text}\n\n"
        f"--- RECENT CALLS & SUMMARIES ---\n{calls_text}\n\n"
        f"--- RELEVANT TRANSCRIPT EXCERPTS ---\n{chunks_text}\n"
    )

    context_message = {"role": "system", "content": full_context}
    trimmed_history = history[-(MAX_HISTORY * 2):]

    messages = [context_message] + trimmed_history + [
        {"role": "user", "content": message}
    ]

    # 8. Call LLM with Multi-Model Fallback
    client = _get_openai()
    reply = None
    if client:
        active_key = os.getenv("GROQ_API_KEY") or os.getenv("OPENAI_API_KEY", "")
        if active_key.startswith("gsk_"):
            primary_model = os.getenv("GROQ_CHAT_MODEL", "llama-3.3-70b-versatile")
            candidates = [primary_model, "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768", "gemma2-9b-it"]
        else:
            primary_model = OPENAI_MODEL
            candidates = [primary_model, "gpt-4o-mini", "gpt-4o"]

        # Deduplicate candidates while preserving order
        seen = set()
        unique_candidates = [c for c in candidates if c and not (c in seen or seen.add(c))]

        for model in unique_candidates:
            try:
                response = client.chat.completions.create(
                    model=model,
                    messages=messages,
                    temperature=0.2,
                    max_tokens=600,
                )
                reply = response.choices[0].message.content.strip()
                logger.info(f"Chatbot response generated successfully with model: {model}")
                break
            except Exception as e:
                logger.warning(f"AI model {model} attempt failed: {e}")
                continue

    if not reply:
        logger.info("Generating factual offline database reply.")
        reply = _smart_offline_reply(message, tasks, agenda_items, appointments, user_contacts, recent_calls, chunks)

    # 9. Save updated history
    history.append({"role": "user", "content": message})
    history.append({"role": "assistant", "content": reply})

    session.messages = json.dumps(history, ensure_ascii=False)
    session.updated_at = datetime.utcnow()
    db.commit()

    sources = [
        {
            "call_id": c.get("call_id"),
            "call_date": c.get("call_date"),
            "excerpt": c["chunk_text"][:120] + "..." if len(c["chunk_text"]) > 120 else c["chunk_text"],
        }
        for c in chunks if c.get("call_id")
    ]

    return {
        "session_id": session.id,
        "reply": reply,
        "sources": sources,
    }


def _smart_offline_reply(
    message: str,
    tasks: list,
    agenda_items: list,
    appointments: list,
    contacts: list,
    recent_calls: list,
    chunks: list
) -> str:
    """
    Generates a truthful, factual response directly from the database
    when the AI LLM service is offline or not configured. Never returns placeholders.
    """
    msg_lower = message.lower()

    # Query about tasks
    if any(w in msg_lower for w in ["tâche", "tache", "todo", "à faire", "a faire"]):
        if not tasks:
            return "Vous n'avez actuellement aucune tâche enregistrée dans votre liste."
        lines = ["Voici vos tâches enregistrées :"]
        for t in tasks:
            status = "Terminée" if t.completed else "En cours"
            lines.append(f"• [{status}] {t.title}")
        return "\n".join(lines)

    # Query about agenda / appointments
    if any(w in msg_lower for w in ["agenda", "rendez-vous", "rdv", "calendrier", "planning"]):
        all_events = []
        for a in agenda_items:
            all_events.append(f"• {a.title} prévu le {a.scheduled_at}")
        for app in appointments:
            all_events.append(f"• {app.title or 'Rendez-vous'} le {app.scheduled_at} ({app.status})")
        if not all_events:
            return "Vous n'avez aucun rendez-vous ou événement prévu dans votre agenda."
        return "Voici vos rendez-vous et événements planifiés :\n" + "\n".join(all_events)

    # Query about contacts / phone numbers
    if any(w in msg_lower for w in ["contact", "numéro", "numero", "téléphone", "telephone", "coordonnées", "joindre"]):
        matched = []
        for c in contacts:
            full_name = f"{c.first_name} {c.last_name}".lower()
            if any(part in msg_lower for part in full_name.split()):
                matched.append(c)
        if matched:
            lines = ["Voici les coordonnées trouvées dans vos contacts :"]
            for c in matched:
                lines.append(f"• {c.first_name} {c.last_name} : {c.phone_number} (Email: {c.email or 'N/A'})")
            return "\n".join(lines)
        if contacts:
            lines = ["Voici vos contacts enregistrés :"]
            for c in contacts[:5]:
                lines.append(f"• {c.first_name} {c.last_name} : {c.phone_number}")
            return "\n".join(lines)
        return "Aucun contact correspondant n'a été trouvé dans votre répertoire."

    # Query about call summaries / recordings
    if any(w in msg_lower for w in ["appel", "résumé", "resume", "enregistrement", "conversation", "dernier"]):
        if recent_calls:
            lines = ["Voici le récapitulatif de vos derniers appels enregistrés :"]
            for call in recent_calls[:4]:
                c_name = f"{call.contact.first_name} {call.contact.last_name}" if call.contact else "Numéro non répertorié"
                lines.append(f"• Appel avec {c_name} ({call.started_at}) : {call.status}")
            return "\n".join(lines)
        return "Aucun appel récent n'est enregistré pour le moment."

    if chunks:
        excerpt = chunks[0]["chunk_text"][:250]
        return f"D'après les transcriptions de vos appels :\n\n{excerpt}"

    return "Je suis votre assistant Intelligent Calls. Vous pouvez me demander vos tâches, votre agenda, les coordonnées de vos contacts ou les résumés de vos appels."


def get_session_history(session_id: str, db) -> List[dict]:
    """Returns the message history for a chatbot session."""
    from ..database import ChatbotSession
    session = db.query(ChatbotSession).filter(ChatbotSession.id == session_id).first()
    if not session:
        return []
    return json.loads(session.messages) if session.messages else []


def clear_session(session_id: str, db) -> bool:
    """Resets the message history for a chatbot session."""
    from ..database import ChatbotSession
    session = db.query(ChatbotSession).filter(ChatbotSession.id == session_id).first()
    if not session:
        return False
    session.messages = json.dumps([])
    db.commit()
    return True
