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
# System prompt (French only)
# ─────────────────────────────────────────────

SYSTEM_PROMPT = """Tu es un assistant IA pour centre d'appels. 
Tu analyses les transcriptions d'appels téléphoniques et extrais les informations clés.

Tu DOIS répondre UNIQUEMENT en JSON valide, sans texte avant ou après, avec cette structure exacte :
{
  "resume": "Résumé concis de l'appel en 2-3 phrases.",
  "sentiment": "POSITIF" | "NEUTRE" | "NEGATIF",
  "rendez_vous": [
    {
      "titre": "Titre du rendez-vous",
      "date": "YYYY-MM-DD ou null si non précisé",
      "heure": "HH:MM ou null si non précisé",
      "confiance": 0.0 à 1.0
    }
  ],
  "actions": [
    "Action 1 à effectuer",
    "Action 2 à effectuer"
  ]
}

Règles :
- Le résumé doit être en français, factuel et professionnel.
- rendez_vous est une liste vide [] si aucun rendez-vous n'est mentionné.
- actions est une liste vide [] si aucune action n'est requise.
- confiance reflète la certitude qu'il s'agit bien d'un rendez-vous (0.9 = très sûr).
- Si la date/heure n'est pas mentionnée explicitement, utilise null.
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

    api_key = os.getenv("OPENAI_API_KEY", "")
    if not api_key:
        logger.warning("OPENAI_API_KEY not set — using offline fallback summary.")
        return _fallback_summary(raw_text)

    client = OpenAI(api_key=api_key, timeout=OPENAI_TIMEOUT)

    # Format transcript for the model
    transcript_text = _build_transcript_text(speaker_segments) if speaker_segments else raw_text

    user_message = f"""Voici la transcription d'un appel téléphonique :

---
{transcript_text}
---

Analyse cet appel et retourne le JSON demandé."""

    try:
        response = client.chat.completions.create(
            model=OPENAI_MODEL,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_message},
            ],
            temperature=0.2,
            max_tokens=800,
        )
        content = response.choices[0].message.content
        result = json.loads(content)
        logger.info(f"GPT summary generated: sentiment={result.get('sentiment')}, "
                    f"rendez_vous={len(result.get('rendez_vous', []))}")
        return result
    except json.JSONDecodeError as e:
        logger.error(f"GPT returned invalid JSON: {e}")
        return _fallback_summary(raw_text)
    except Exception as e:
        logger.error(f"GPT summarization error: {e}")
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
    from ..database import Transcript, CallSummary, Appointment, Call

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
    contact_id = call.contact_id if call else None
    user_id = call.user_id if call else None

    # Create appointments detected
    appointment_id = None
    for rdv in result.get("rendez_vous", []):
        if rdv.get("confiance", 0) < 0.5:
            continue  # Skip low-confidence detections
        # Parse date/time
        scheduled_at = _parse_datetime(rdv.get("date"), rdv.get("heure"))
        if scheduled_at and contact_id:
            appt = Appointment(
                id=str(uuid.uuid4()),
                contact_id=contact_id,
                user_id=user_id,
                scheduled_at=scheduled_at,
                status="PROPOSED",
                title=rdv.get("titre", "Rendez-vous détecté"),
            )
            db.add(appt)
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
    """Converts date/time strings from GPT output to a datetime object."""
    if not date_str:
        return None
    try:
        date_part = datetime.strptime(date_str, "%Y-%m-%d")
        if time_str:
            try:
                time_part = datetime.strptime(time_str, "%H:%M")
                return date_part.replace(hour=time_part.hour, minute=time_part.minute)
            except ValueError:
                pass
        return date_part
    except ValueError:
        logger.warning(f"Could not parse date: '{date_str}' time: '{time_str}'")
        return None
