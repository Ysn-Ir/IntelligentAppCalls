"""
chatbot.py — Contact-Based RAG Chatbot
=======================================

Answers questions about call history using Retrieval-Augmented Generation.
Scoped to a specific contact (or global across all calls).

Example:
  User: "Qu'est-ce qu'Ahmed a dit sur le projet la semaine dernière ?"
  Bot:  "Lors de votre appel du 19 août, Ahmed a confirmé qu'il vous
         enverrait les fichiers du projet avant vendredi et vous a
         demandé de planifier une réunion de revue."
        Sources: [{ call_id: "...", date: "2026-08-19" }]

Environment variables:
  OPENAI_API_KEY   — Required for GPT
  OPENAI_MODEL     — default: "gpt-4o-mini"
  CHATBOT_MAX_HIST — Number of past messages to keep in context (default: 10)
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

CHATBOT_SYSTEM_PROMPT = """Tu es un assistant intelligent pour un centre d'appels.
Tu réponds aux questions de l'agent en te basant UNIQUEMENT sur les transcriptions d'appels fournies.
Tu réponds toujours en français, de façon concise et professionnelle.
Si l'information n'est pas dans les transcriptions, dis-le clairement.
Ne jamais inventer d'informations.

Format de réponse :
- Réponse directe à la question
- Si pertinent, précise la date de l'appel concerné
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


# ─────────────────────────────────────────────
# Core chat function
# ─────────────────────────────────────────────

def chat(
    user_id: str,
    message: str,
    contact_id: Optional[str],
    db,
    session_id: Optional[str] = None,
) -> dict:
    """
    Processes a user message and returns an AI reply with source attribution.

    Steps:
      1. Load or create chatbot session
      2. Retrieve relevant transcript chunks via semantic search
      3. Build context from retrieved chunks
      4. Call GPT-4o-mini with session history + context
      5. Save updated session history
      6. Return reply + sources

    Returns:
    {
        "session_id": "...",
        "reply": "Lors de votre appel du 19 août...",
        "sources": [
            { "call_id": "...", "call_date": "2026-08-19T14:00:00", "chunk": "..." }
        ]
    }
    """
    from ..database import ChatbotSession, Contact

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

    # Parse stored history
    history = json.loads(session.messages) if session.messages else []

    # 2. Semantic search for relevant chunks
    chunks = search_similar_chunks(message, contact_id, db, top_k=4)

    # 3. Build context block
    context_parts = []
    for chunk in chunks:
        date_label = chunk["call_date"][:10] if chunk.get("call_date") else "date inconnue"
        context_parts.append(
            f"[Appel du {date_label}]\n{chunk['chunk_text']}"
        )
    context_text = "\n\n---\n\n".join(context_parts) if context_parts else "(aucun appel trouvé pour ce contact)"

    # 4. Build message list for GPT
    contact_name = "ce contact"
    if contact_id:
        contact = db.query(Contact).filter(Contact.id == contact_id).first()
        if contact:
            contact_name = f"{contact.first_name} {contact.last_name}"

    context_message = {
        "role": "system",
        "content": (
            f"{CHATBOT_SYSTEM_PROMPT}\n\n"
            f"Contact actuel : {contact_name}\n\n"
            f"Extraits d'appels pertinents :\n{context_text}"
        )
    }

    # Keep last N turns of history
    trimmed_history = history[-(MAX_HISTORY * 2):]

    messages = [context_message] + trimmed_history + [
        {"role": "user", "content": message}
    ]

    # 5. Call LLM
    client = _get_openai()
    if client:
        try:
            active_key = os.getenv("GROQ_API_KEY") or os.getenv("OPENAI_API_KEY", "")
            model = os.getenv("GROQ_CHAT_MODEL", "openai/gpt-oss-120b") if active_key.startswith("gsk_") else OPENAI_MODEL
            response = client.chat.completions.create(
                model=model,
                messages=messages,
                temperature=0.3,
                max_tokens=500,
            )
            reply = response.choices[0].message.content.strip()
        except Exception as e:
            logger.error(f"Chatbot LLM error: {e}")
            reply = _offline_reply(chunks, message)
    else:
        logger.warning("No OpenAI client — using offline chatbot reply.")
        reply = _offline_reply(chunks, message)

    # 6. Save updated history
    history.append({"role": "user", "content": message})
    history.append({"role": "assistant", "content": reply})

    session.messages = json.dumps(history, ensure_ascii=False)
    session.updated_at = datetime.utcnow()
    db.commit()

    # Build sources for the frontend
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


def _offline_reply(chunks: List[dict], message: str) -> str:
    """Returns a simple offline reply when OpenAI is not available."""
    if not chunks:
        return "Je n'ai pas trouvé d'informations sur ce contact dans l'historique des appels."
    excerpt = chunks[0]["chunk_text"][:200]
    return f"Voici ce que j'ai trouvé dans les appels :\n\n{excerpt}"


# ─────────────────────────────────────────────
# Session management helpers
# ─────────────────────────────────────────────

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
