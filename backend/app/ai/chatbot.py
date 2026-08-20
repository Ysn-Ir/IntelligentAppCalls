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

CHATBOT_SYSTEM_PROMPT = """Tu es un assistant IA professionnel pour l'application Intelligent Calls.
Tu as accès en temps réel aux données exactes de l'utilisateur :
1. Ses Tâches (To-Do list)
2. Son Agenda & Rendez-vous
3. Son Carnet de Contacts (noms, numéros de téléphone, emails, entreprises)
4. Ses Résumés d'Appels récents & Transcriptions

Règles strictes :
- Réponds toujours en français, de manière claire, concise et professionnelle.
- Réponds UNIQUEMENT sur la base des données réelles fournies dans le contexte ci-dessous.
- Ne JAMAIS inventer de numéros de téléphone, de faux contacts ou de faux rendez-vous.
- Si l'utilisateur demande ses tâches, liste-les fidèlement avec leur statut (terminée ou en cours).
- Si l'utilisateur demande ses rendez-vous ou son agenda, liste les événements réels avec date et heure.
- Si l'utilisateur demande les coordonnées d'un contact, donne son numéro de téléphone exact présent dans la liste de contacts.
- Si une information demandée n'existe pas dans les données fournies, réponds honnêtement que l'information n'a pas été trouvée.
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
) -> dict:
    """
    Processes a user message and returns an AI reply with source attribution.
    """
    from ..database import ChatbotSession, Contact, Task, AgendaItem, Appointment, Call, CallSummary

    # 1. Load or create session
    session = None
    if session_id:
        session = db.query(ChatbotSession).filter(ChatbotSession.id == session_id).first()

    if not session:
        session = ChatbotSession(
            id=session_id if session_id else str(uuid.uuid4()),
            user_id=user_id,
            contact_id=contact_id,
            messages=json.dumps([]),
        )
        db.add(session)
        db.commit()
        db.refresh(session)

    history = json.loads(session.messages) if session.messages else []

    # 2. Fetch User Real Data Context: Tasks
    tasks = db.query(Task).filter((Task.user_id == user_id) | (Task.user_id.is_(None))).all()
    tasks_text = "\n".join([
        f"- [{'Terminée' if t.completed else 'En cours'}] {t.title}" for t in tasks
    ]) if tasks else "(Aucune tâche enregistrée)"

    # 3. Fetch User Real Data Context: Agenda & Appointments
    agenda_items = db.query(AgendaItem).filter((AgendaItem.user_id == user_id) | (AgendaItem.user_id.is_(None))).all()
    appointments = db.query(Appointment).filter(
        (Appointment.contact_id.in_([c.id for c in db.query(Contact.id).filter(Contact.user_id == user_id).all()])) |
        (Appointment.contact_id.is_(None))
    ).all()

    agenda_parts = [f"- {a.title} à {a.time}" for a in agenda_items]
    for app in appointments:
        c_name = app.contact_name or "Contact"
        phone_txt = f" ({app.phone_number})" if app.phone_number else ""
        agenda_parts.append(f"- Rendez-vous '{app.title or 'RDV'}' avec {c_name}{phone_txt} le {app.scheduled_at} [Statut: {app.status}]")
    agenda_text = "\n".join(agenda_parts) if agenda_parts else "(Aucun événement d'agenda)"

    # 4. Fetch User Real Data Context: Contacts
    contacts = db.query(Contact).filter(Contact.user_id == user_id).all()
    contacts_parts = []
    for c in contacts:
        company_txt = f", Entreprise: {c.company}" if c.company else ""
        email_txt = f", Email: {c.email}" if c.email else ""
        contacts_parts.append(f"- {c.first_name} {c.last_name}: Téléphone {c.phone}{company_txt}{email_txt}")
    contacts_text = "\n".join(contacts_parts) if contacts_parts else "(Aucun contact enregistré)"

    # 5. Fetch User Real Data Context: Recent Calls & Summaries
    recent_calls = db.query(Call).filter(Call.user_id == user_id).order_by(Call.started_at.desc()).limit(8).all()
    call_summaries_parts = []
    for call in recent_calls:
        sum_obj = db.query(CallSummary).filter(CallSummary.call_id == call.id).first()
        c_label = f"{call.contact.first_name} {call.contact.last_name} ({call.contact.phone})" if call.contact else "Numéro non répertorié"
        s_text = sum_obj.summary_text if sum_obj else "En attente de transcription"
        call_summaries_parts.append(f"- Appel du {call.started_at} avec {c_label} : {s_text}")
    calls_text = "\n".join(call_summaries_parts) if call_summaries_parts else "(Aucun appel récent)"

    # 6. Semantic search for relevant chunks
    chunks = search_similar_chunks(message, contact_id, db, top_k=4)
    chunks_parts = []
    for chunk in chunks:
        date_label = chunk["call_date"][:10] if chunk.get("call_date") else "date inconnue"
        chunks_parts.append(f"[Appel du {date_label}]\n{chunk['chunk_text']}")
    chunks_text = "\n\n---\n\n".join(chunks_parts) if chunks_parts else "(Aucun extrait textuel supplémentaire)"

    # 7. Build Full System Context
    target_contact_label = "Tous les contacts"
    if contact_id:
        c_found = db.query(Contact).filter(Contact.id == contact_id).first()
        if c_found:
            target_contact_label = f"{c_found.first_name} {c_found.last_name} ({c_found.phone})"

    full_context = (
        f"{CHATBOT_SYSTEM_PROMPT}\n\n"
        f"=== CONTEXTE ACTUEL DU COMPTE ===\n"
        f"Filtre de contact actif : {target_contact_label}\n\n"
        f"--- TÂCHES DE L'UTILISATEUR ---\n{tasks_text}\n\n"
        f"--- AGENDA & RENDEZ-VOUS ---\n{agenda_text}\n\n"
        f"--- CARNET DE CONTACTS ---\n{contacts_text}\n\n"
        f"--- DERNIERS APPELS & RÉSUMÉS ---\n{calls_text}\n\n"
        f"--- EXTRAITS DE TRANSCRIPTION PERTINENTS ---\n{chunks_text}\n"
    )

    context_message = {"role": "system", "content": full_context}
    trimmed_history = history[-(MAX_HISTORY * 2):]

    messages = [context_message] + trimmed_history + [
        {"role": "user", "content": message}
    ]

    # 8. Call LLM
    client = _get_openai()
    if client:
        try:
            active_key = os.getenv("GROQ_API_KEY") or os.getenv("OPENAI_API_KEY", "")
            model = os.getenv("GROQ_CHAT_MODEL", "llama-3.3-70b-versatile") if active_key.startswith("gsk_") else OPENAI_MODEL
            response = client.chat.completions.create(
                model=model,
                messages=messages,
                temperature=0.2,
                max_tokens=600,
            )
            reply = response.choices[0].message.content.strip()
        except Exception as e:
            logger.error(f"Chatbot LLM error: {e}")
            reply = _smart_offline_reply(message, tasks, agenda_items, appointments, contacts, recent_calls, chunks)
    else:
        logger.warning("No LLM client configured — generating factual offline database reply.")
        reply = _smart_offline_reply(message, tasks, agenda_items, appointments, contacts, recent_calls, chunks)

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
            all_events.append(f"• {a.title} à {a.time}")
        for app in appointments:
            c_name = app.contact_name or "Contact"
            all_events.append(f"• {app.title or 'Rendez-vous'} avec {c_name} le {app.scheduled_at} ({app.status})")
        if not all_events:
            return "Vous n'avez aucun rendez-vous ou événement prévu dans votre agenda."
        return "Voici vos rendez-vous et événements planifiés :\n" + "\n".join(all_events)

    # Query about contacts / phone numbers
    if any(w in msg_lower for w in ["contact", "numéro", "numero", "téléphone", "telephone", "coordonnées", "joindre"]):
        matched = []
        for c in contacts:
            full_name = f"{c.first_name} {c.last_name}".lower()
            if any(part in msg_lower for part in full_name.split()) or (c.company and c.company.lower() in msg_lower):
                matched.append(c)
        if matched:
            lines = ["Voici les coordonnées trouvées dans vos contacts :"]
            for c in matched:
                lines.append(f"• {c.first_name} {c.last_name} : {c.phone} (Email: {c.email or 'N/A'})")
            return "\n".join(lines)
        if contacts:
            lines = ["Voici vos contacts enregistrés :"]
            for c in contacts[:5]:
                lines.append(f"• {c.first_name} {c.last_name} : {c.phone}")
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

    return "Je suis prêt à vous aider. Vous pouvez me demander vos tâches, vos rendez-vous d'agenda, les coordonnées de vos contacts ou les résumés de vos appels récents."


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
