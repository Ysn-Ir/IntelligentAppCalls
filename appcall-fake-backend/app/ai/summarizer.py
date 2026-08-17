"""
summarizer.py — GPT-4o-mini Call Summarization
===============================================

Takes a transcript with speaker segments and produces:
  - A concise French summary of the call
  - Detected appointments (date, time, title, confidence)
  - Action items for the agent
  - Call sentiment (POSITIVE / NEUTRAL / NEGATIVE)

Environment variables:
  OPENAI_API_KEY  — Required for GPT calls
  OPENAI_MODEL    — default: "gpt-4o-mini"
  OPENAI_TIMEOUT  — default: 30 seconds
"""

import os
import json
import logging
import uuid
from datetime import datetime
from typing import Optional

logger = logging.getLogger(__name__)

OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
OPENAI_TIMEOUT = int(os.getenv("OPENAI_TIMEOUT", "30"))

# ─────────────────────────────────────────────
# Dynamic System prompt
# ─────────────────────────────────────────────

def get_system_prompt() -> str:
    now = datetime.utcnow()
    date_str = now.strftime("%A %d %B %Y")
    iso_date = now.strftime("%Y-%m-%d")
    return f"""Tu es un assistant IA pour centre d'appels téléphoniques.
Aujourd'hui nous sommes le {date_str} (Date ISO: {iso_date}).

Tu DOIS analyser la transcription et répondre UNIQUEMENT en JSON valide, sans texte additionnel :
{{
  "resume": "Résumé clair et professionnel de l'appel en 2-3 phrases.",
  "sentiment": "POSITIF" | "NEUTRE" | "NEGATIF",
  "rendez_vous": [
    {{
      "titre": "Titre explicite du rendez-vous (ex: Point commercial avec Marc)",
      "date": "YYYY-MM-DD (Calcule la date exacte selon la date d'aujourd'hui {iso_date})",
      "heure": "HH:MM (ex: 14:00, 09:30, 16:00)",
      "confiance": 0.95
    }}
  ],
  "actions": [
    "Action concrète à réaliser"
  ]
}}

Règles pour la détection de rendez-vous :
- Si un rendez-vous, une réunion, un rappel, un appel de suivi ou une entrevue est convenu ou proposé, EXTRAIS-LE dans "rendez_vous".
- Si l'interlocuteur mentionne "demain", "mardi", "vendredi prochain", calcule la date exacte YYYY-MM-DD.
- Si une heure est mentionnée (ex: "14h", "14h30", "à 10 heures"), formate en HH:MM.
- Mets "rendez_vous": [] uniquement si aucun rendez-vous/réunion/rappel n'est mentionné.
"""


def _build_transcript_text(speaker_segments: list) -> str:
    """Formats speaker segments into a readable transcript for the AI."""
    if not speaker_segments:
        return "(transcription vide)"
    lines = []
    for seg in speaker_segments:
        speaker_label = "Agent" if seg.get("speaker") == "agent" else "Contact"
        lines.append(f"{speaker_label}: {seg.get('text', '').strip()}")
    return "\n".join(lines)


# ─────────────────────────────────────────────
# Core summarization
# ─────────────────────────────────────────────

def summarize_transcript(raw_text: str, speaker_segments: list) -> dict:
    """
    Calls GPT-4o-mini to summarize a call transcript.

    Returns:
    {
        "resume": "...",
        "sentiment": "POSITIF",
        "rendez_vous": [...],
        "actions": [...]
    }
    """
    try:
        from openai import OpenAI
    except ImportError:
        logger.error("openai package not installed. Run: pip install openai")
        return _fallback_summary(raw_text)

    api_key = os.getenv("GROQ_API_KEY") or os.getenv("OPENAI_API_KEY", "")
    if not api_key:
        logger.warning("No API key set — using offline fallback summary.")
        return _fallback_summary(raw_text)

    base_url = os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1") if api_key.startswith("gsk_") else None
    model = os.getenv("GROQ_CHAT_MODEL", "openai/gpt-oss-120b") if api_key.startswith("gsk_") else OPENAI_MODEL

    client = OpenAI(api_key=api_key, base_url=base_url, timeout=OPENAI_TIMEOUT)

    # Format transcript for the model
    transcript_text = _build_transcript_text(speaker_segments) if speaker_segments else raw_text

    user_message = f"""Voici la transcription d'un appel téléphonique :

---
{transcript_text}
---

Analyse cet appel et retourne le JSON demandé."""

    try:
        response = client.chat.completions.create(
            model=model,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": get_system_prompt()},
                {"role": "user", "content": user_message},
            ],
            temperature=0.2,
            max_tokens=800,
        )
        content = response.choices[0].message.content
        result = json.loads(content)
        logger.info(f"AI summary generated with {model}: sentiment={result.get('sentiment')}, "
                    f"rendez_vous={len(result.get('rendez_vous', []))}")
        return result
    except json.JSONDecodeError as e:
        logger.error(f"AI returned invalid JSON: {e}")
        return _fallback_summary(raw_text)
    except Exception as e:
        logger.error(f"AI summarization error: {e}")
        return _fallback_summary(raw_text)


def _fallback_summary(raw_text: str) -> dict:
    """Returns a minimal fallback when AI is unavailable."""
    return {
        "resume": raw_text[:300] + "..." if len(raw_text) > 300 else raw_text,
        "sentiment": "NEUTRE",
        "rendez_vous": [],
        "actions": [],
    }


# ─────────────────────────────────────────────
# High-level entry point (called by worker.py)
# ─────────────────────────────────────────────

def summarize_call(call_id: str, db) -> "CallSummary":
    """
    Reads the transcript for call_id, runs GPT summarization,
    creates/updates CallSummary and Appointment rows in the DB.
    """
    from ..database import Transcript, CallSummary, Appointment, Call, Contact, AgendaModel

    # Load transcript
    transcript = db.query(Transcript).filter(Transcript.call_id == call_id).first()
    if not transcript:
        logger.warning(f"No transcript found for call_id={call_id}, cannot summarize.")
        return None

    speaker_segments = []
    if transcript.speaker_segments:
        try:
            speaker_segments = json.loads(transcript.speaker_segments)
        except json.JSONDecodeError:
            pass

    # Run AI
    result = summarize_transcript(transcript.raw_text, speaker_segments)

    # Resolve contact_id and user_id from the Call row
    call = db.query(Call).filter(Call.id == call_id).first()
    contact_id = call.contact_id if call and call.contact_id else None
    if not contact_id:
        c = db.query(Contact).first()
        contact_id = c.id if c else "contact-1111"
    user_id = call.user_id if call and call.user_id else "system"

    # Create appointments detected
    appointment_id = None
    for rdv in result.get("rendez_vous", []):
        # Parse date/time
        scheduled_at = _parse_datetime(rdv.get("date"), rdv.get("heure"))
        if scheduled_at and contact_id:
            appt = Appointment(
                id=str(uuid.uuid4()),
                contact_id=contact_id,
                user_id=user_id,
                scheduled_at=scheduled_at,
                status="PROPOSED",
                title=rdv.get("titre", "Rendez-vous détecté par IA"),
            )
            db.add(appt)

            # Sync with agenda_backend
            try:
                agenda_item = AgendaModel(
                    id=appt.id,
                    user_id=user_id,
                    title=appt.title,
                    scheduled_at=scheduled_at
                )
                db.add(agenda_item)
            except Exception as e_ag:
                logger.warning(f"Could not add to agenda_backend: {e_ag}")

            db.commit()
            db.refresh(appt)
            appointment_id = appt.id  # Use first/best appointment
            logger.info(f"Appointment created: {appt.title} at {scheduled_at}")
            break  # One appointment per call for now

    # Save/update CallSummary
    existing_summary = db.query(CallSummary).filter(CallSummary.call_id == call_id).first()
    if existing_summary:
        existing_summary.summary_text = result["resume"]
        existing_summary.detected_appointment_id = appointment_id
        existing_summary.status = "CONFIRMED"
        db.commit()
        db.refresh(existing_summary)
        return existing_summary
    else:
        summary = CallSummary(
            id=str(uuid.uuid4()),
            call_id=call_id,
            summary_text=result["resume"],
            detected_appointment_id=appointment_id,
            status="CONFIRMED",
            modified_count=0,
        )
        db.add(summary)
        db.commit()
        db.refresh(summary)
        logger.info(f"Summary saved for call_id={call_id}")
        return summary


def _parse_datetime(date_str: Optional[str], time_str: Optional[str]) -> Optional[datetime]:
    """Converts date/time strings from GPT output to a datetime object, handling relative dates and French terms."""
    from datetime import timedelta
    now = datetime.utcnow()
    date_part = None

    if date_str:
        d_lower = date_str.lower().strip()
        if "demain" in d_lower:
            date_part = now + timedelta(days=1)
        elif "après-demain" in d_lower or "apres-demain" in d_lower:
            date_part = now + timedelta(days=2)
        elif "aujourd" in d_lower:
            date_part = now
        elif any(day in d_lower for day in ["lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"]):
            day_names = ["lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"]
            target_idx = 0
            for idx, name in enumerate(day_names):
                if name in d_lower:
                    target_idx = idx
                    break
            current_idx = now.weekday()
            days_ahead = (target_idx - current_idx) % 7
            if days_ahead == 0:
                days_ahead = 7
            date_part = now + timedelta(days=days_ahead)
        else:
            for fmt in ("%Y-%m-%d", "%d/%m/%Y", "%Y/%m/%d", "%d-%m-%Y"):
                try:
                    date_part = datetime.strptime(date_str, fmt)
                    break
                except ValueError:
                    pass

    if date_part is None:
        # Default to tomorrow if a time is specified
        if time_str:
            date_part = now + timedelta(days=1)
        else:
            return None

    # Parse time
    hour = 14
    minute = 0
    if time_str:
        t_clean = time_str.lower().replace("h", ":").replace("min", "").strip()
        if ":" in t_clean:
            parts = t_clean.split(":")
            try:
                hour = int(parts[0].strip())
                minute = int(parts[1].strip()) if len(parts) > 1 and parts[1].strip() else 0
            except ValueError:
                pass
        else:
            try:
                hour = int(t_clean)
            except ValueError:
                pass

    return date_part.replace(hour=hour, minute=minute, second=0, microsecond=0)
