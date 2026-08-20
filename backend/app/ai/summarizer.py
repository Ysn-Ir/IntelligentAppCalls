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
# Multilingual Dynamic System prompt
# ─────────────────────────────────────────────

LANGUAGE_LABELS = {
    "en": "English",
    "fr": "French",
    "ar": "Arabic",
    "es": "Spanish",
    "de": "German",
    "zh": "Chinese",
    "ja": "Japanese",
}

def get_system_prompt(language: str = "en") -> str:
    lang_name = LANGUAGE_LABELS.get(language, "English")
    now = datetime.utcnow()
    date_str = now.strftime("%A %d %B %Y")
    iso_date = now.strftime("%Y-%m-%d")
    return f"""You are an advanced enterprise AI assistant specialized in real-time telephony call analysis, sentiment detection, intent classification, and appointment extraction.
Today is {date_str} (ISO Date: {iso_date}).

You MUST analyze the call transcript and respond ONLY with valid JSON in {lang_name} language, with NO surrounding markdown, code blocks, or extra text:
{{
  "resume": "Clear, objective, and professional call summary in 2-3 sentences written in {lang_name}.",
  "sentiment": "POSITIVE" | "NEUTRAL" | "NEGATIVE" | "HOSTILE",
  "intent": "Concise call intent (e.g. Appointment Scheduling, Sales Inquiry, Technical Support, Threat / Violent Conflict, Angry Complaint, General Follow-up)",
  "tags": [
    "#Tag1",
    "#Tag2"
  ],
  "confidence_score": 95.0,
  "rendez_vous": [
    {{
      "titre": "Explicit title/subject of the appointment or follow-up (in {lang_name})",
      "date": "YYYY-MM-DD (Calculate exact date based on today's reference date {iso_date})",
      "heure": "HH:MM (24h format, e.g. 14:00, 09:30, 16:00)",
      "confiance": 0.95
    }}
  ],
  "actions": [
    "Concrete action item to perform (in {lang_name})"
  ]
}}

CRITICAL RULES FOR SENTIMENT & INTENT CLASSIFICATION:
1. HOSTILE / THREATENING / ANGRY SPEECH:
   - If the transcript contains threats, violence, physical aggression, shouting, insults, vulgarities, or severe disputes:
     * "sentiment" MUST be set to "HOSTILE" or "NEGATIVE".
     * "intent" MUST be set to "Threat / Severe Dispute" or "Angry Complaint".
     * "tags" MUST include specific dynamic tags like ["#Threat", "#SecurityAlert", "#Conflict"] or ["#UrgentEscalation", "#Dispute"].
     * NEVER classify hostile, abusive, or threatening speech as "POSITIVE" or "NEUTRAL".
2. CONSTRUCTIVE / FRIENDLY SPEECH:
   - If the conversation is polite, satisfied, agreeing, closed deal, or positive, set "sentiment": "POSITIVE" and appropriate tags like ["#SatisfiedClient", "#DealClosed", "#Agreement"].
3. NEUTRAL / STANDARD INQUIRIES:
   - If routine information is asked without strong emotion, set "sentiment": "NEUTRAL" and tags like ["#Information", "#Support"].
4. DYNAMIC HASHTAGS:
   - Generate 2 to 4 contextual, meaningful hashtags directly derived from the transcript topics.
   - Do NOT use generic or static placeholders like "#SuiviClient" unless it is genuinely a customer follow-up.
5. APPOINTMENT DETECTION:
   - Set "rendez_vous": [] ONLY if no meeting or callback is agreed or requested.
   - If a meeting/appointment is mentioned, extract exact YYYY-MM-DD date and HH:MM time.
"""


def _build_transcript_text(speaker_segments: list) -> str:
    """Formats speaker segments into a readable transcript for the AI."""
    if not speaker_segments:
        return "(empty transcript)"
    lines = []
    for seg in speaker_segments:
        speaker_label = "Agent" if seg.get("speaker") == "agent" else "Contact"
        lines.append(f"{speaker_label}: {seg.get('text', '').strip()}")
    return "\n".join(lines)


# ─────────────────────────────────────────────
# Core summarization
# ─────────────────────────────────────────────

def summarize_transcript(raw_text: str, speaker_segments: list, language: Optional[str] = None) -> dict:
    """
    Calls Groq LLMs to summarize a call transcript in the specified language.
    Supported: 'en', 'fr', 'ar', 'es', 'de', 'zh', 'ja'.
    """
    target_lang = language if language and language != "auto" else "en"

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
    client = OpenAI(api_key=api_key, base_url=base_url, timeout=OPENAI_TIMEOUT)

    # Format transcript for the model
    transcript_text = _build_transcript_text(speaker_segments) if speaker_segments else raw_text

    user_message = f"""Here is the transcription of a phone call:

---
{transcript_text}
---

Analyze this call transcript carefully. Detect the true sentiment, intent, key actions, appointments, and hashtags. Return the required JSON in {LANGUAGE_LABELS.get(target_lang, 'English')}."""

    active_key = api_key
    primary_model = os.getenv("GROQ_CHAT_MODEL", "openai/gpt-oss-120b") if active_key.startswith("gsk_") else OPENAI_MODEL
    candidates = [primary_model, "openai/gpt-oss-120b", "openai/gpt-oss-20b", "llama-3.3-70b-versatile", "qwen/qwen3.6-27b"]

    for model in candidates:
        try:
            response = client.chat.completions.create(
                model=model,
                response_format={"type": "json_object"},
                messages=[
                    {"role": "system", "content": get_system_prompt(target_lang)},
                    {"role": "user", "content": user_message},
                ],
                temperature=0.1,
                max_tokens=800,
            )
            content = response.choices[0].message.content
            result = json.loads(content)
            logger.info(f"AI summary generated with {model} (lang={target_lang}): sentiment={result.get('sentiment')}, "
                        f"intent={result.get('intent')}, tags={result.get('tags')}, rendez_vous={len(result.get('rendez_vous', []))}")
            return result
        except json.JSONDecodeError as e:
            logger.error(f"AI returned invalid JSON with model {model}: {e}")
            continue
        except Exception as e:
            logger.warning(f"Groq model {model} summarization attempt failed: {e}")
            continue

    logger.error("All candidate LLM models failed — using fallback summary.")
    return _fallback_summary(raw_text)


def _fallback_summary(raw_text: str) -> dict:
    """Returns an intelligent offline fallback analyzing key sentiment tokens when LLM is offline."""
    lower = raw_text.lower()
    hostile_tokens = ["kill", "beat", "tuer", "frapper", "menace", "threat", "police", "die", "meurtre", "attack", "agression"]
    negative_tokens = ["angry", "problem", "cancel", "mauvais", "nul", "déçu", "horrible", "remboursement", "issue", "scam"]
    positive_tokens = ["merci", "thank", "great", "excellent", "parfait", "super", "agree", "d'accord", "oui", "awesome"]

    if any(tok in lower for tok in hostile_tokens):
        sentiment = "HOSTILE"
        intent = "Threat / Urgent Conflict"
        tags = ["#Threat", "#SecurityAlert", "#UrgentEscalation"]
    elif any(tok in lower for tok in negative_tokens):
        sentiment = "NEGATIVE"
        intent = "Complaint / Conflict"
        tags = ["#Complaint", "#IssueReport"]
    elif any(tok in lower for tok in positive_tokens):
        sentiment = "POSITIVE"
        intent = "Positive Agreement / Collaboration"
        tags = ["#Positive", "#Collaboration"]
    else:
        sentiment = "NEUTRAL"
        intent = "General Call"
        tags = ["#GeneralCall"]

    return {
        "resume": raw_text[:300] + "..." if len(raw_text) > 300 else (raw_text or "Call summary not available."),
        "sentiment": sentiment,
        "intent": intent,
        "tags": tags,
        "confidence_score": 85.0,
        "rendez_vous": [],
        "actions": [],
    }


# ─────────────────────────────────────────────
# High-level entry point (called by worker.py)
# ─────────────────────────────────────────────

def summarize_call(call_id: str, db, language: Optional[str] = None) -> Optional["CallSummary"]:
    """
    High-level function: finds the transcript for call_id, runs summarize_transcript,
    creates Appointment and CallSummary rows, commits to DB.
    """
    from ..database import CallSummary, Appointment, Transcript, Call, Contact, AgendaModel

    transcript = db.query(Transcript).filter(Transcript.call_id == call_id).first()
    if not transcript or not transcript.raw_text:
        logger.warning(f"No transcript found for call_id={call_id}")
        return None

    speaker_segments = []
    if transcript.speaker_segments:
        try:
            speaker_segments = json.loads(transcript.speaker_segments)
        except json.JSONDecodeError:
            pass

    target_lang = language or transcript.language or "en"
    result = summarize_transcript(transcript.raw_text, speaker_segments, language=target_lang)

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
    tags_str = json.dumps(result.get("tags", []))
    existing_summary = db.query(CallSummary).filter(CallSummary.call_id == call_id).first()
    if existing_summary:
        existing_summary.summary_text = result.get("resume", "")
        existing_summary.sentiment = result.get("sentiment", "NEUTRAL")
        existing_summary.intent = result.get("intent", "General Call")
        existing_summary.tags = tags_str
        existing_summary.detected_appointment_id = appointment_id
        existing_summary.status = "CONFIRMED"
        db.commit()
        db.refresh(existing_summary)
        return existing_summary
    else:
        summary = CallSummary(
            id=str(uuid.uuid4()),
            call_id=call_id,
            summary_text=result.get("resume", ""),
            sentiment=result.get("sentiment", "NEUTRAL"),
            intent=result.get("intent", "General Call"),
            tags=tags_str,
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
