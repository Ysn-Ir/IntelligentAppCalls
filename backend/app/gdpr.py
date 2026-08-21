"""
gdpr.py — GDPR & French Law Compliance Engine
==============================================

Implements:
- Right to Erasure (Art. 17 RGPD): full cascade deletion of user data
- Right of Access (Art. 15 RGPD): full data export as JSON
- Single-call deletion with S3 audio purge
- Per-contact data anonymization
- Auto-purge scheduler helpers (called from Celery Beat)

French legal references:
  - RGPD (Règlement UE 2016/679)
  - Loi Informatique et Libertés (modifiée 2018)
  - Art. L226-1 Code Pénal (enregistrement illicite)
"""

import json
import logging
from datetime import datetime, timedelta
from sqlalchemy.orm import Session

from .database import (
    SessionLocal, User, Contact, Call, Transcript, CallSummary,
    Appointment, Reminder, TaskModel, AgendaModel, FileModel,
    ChatbotSession, TranscriptEmbedding
)
from .storage import delete_audio_file

logger = logging.getLogger(__name__)


# ─────────────────────────────────────────────
# Retention policy (configurable)
# ─────────────────────────────────────────────

AUDIO_RETENTION_DAYS = 30          # Raw audio files
TRANSCRIPT_RETENTION_DAYS = 365    # Transcripts & summaries
CALL_RECORD_RETENTION_DAYS = 730   # Call metadata rows


# ─────────────────────────────────────────────
# Single-call deletion
# ─────────────────────────────────────────────

def delete_call_data(call_id: str, db: Session) -> dict:
    """
    Deletes all data associated with a single call.
    Order matters — FK children before parents.

    Returns a dict summarising what was deleted.
    """
    deleted = {
        "call_id": call_id,
        "audio_deleted": False,
        "transcript_deleted": False,
        "summary_deleted": False,
        "embeddings_deleted": 0,
        "reminders_deleted": 0,
    }

    # 1. Delete audio from object store
    try:
        audio_deleted = delete_audio_file(call_id)
        deleted["audio_deleted"] = audio_deleted
    except Exception as e:
        logger.warning(f"Could not delete audio for call {call_id}: {e}")

    # 2. Delete vector embeddings (references transcripts)
    transcript_ids = [t.id for t in db.query(Transcript.id).filter(Transcript.call_id == call_id).all()]
    if transcript_ids:
        n = db.query(TranscriptEmbedding).filter(TranscriptEmbedding.transcript_id.in_(transcript_ids)).delete(synchronize_session=False)
        deleted["embeddings_deleted"] = n
        db.query(Transcript).filter(Transcript.call_id == call_id).delete(synchronize_session=False)
        deleted["transcript_deleted"] = True

    # 3. Delete reminders tied to this call
    n = db.query(Reminder).filter(Reminder.call_id == call_id).delete(synchronize_session=False)
    deleted["reminders_deleted"] = n

    # 4. Delete summaries tied to this call
    db.query(CallSummary).filter(CallSummary.call_id == call_id).delete(synchronize_session=False)
    deleted["summary_deleted"] = True

    # 5. Delete the call row itself
    db.query(Call).filter(Call.id == call_id).delete(synchronize_session=False)

    db.commit()
    logger.info(f"GDPR: Deleted call data for call_id={call_id} | {deleted}")
    return deleted


# ─────────────────────────────────────────────
# Per-contact data erasure
# ─────────────────────────────────────────────

def erase_contact_data(contact_id: str, db: Session) -> dict:
    """
    Erases all call data linked to a contact, then anonymizes the contact row.
    The contact ID is kept (for referential integrity in archived appointments)
    but all PII is wiped.

    RGPD Art. 17: Right to erasure ("right to be forgotten").
    """
    calls = db.query(Call).filter(Call.contact_id == contact_id).all()
    call_ids = [c.id for c in calls]

    deleted_calls = 0
    for call_id in call_ids:
        delete_call_data(call_id, db)
        deleted_calls += 1

    # Anonymize appointments (keep the slot, remove contact link info)
    db.query(Appointment).filter(Appointment.contact_id == contact_id).update({
        "title": "Rendez-vous supprimé",
        "status": "CANCELLED"
    })

    # Delete chatbot sessions for this contact
    db.query(ChatbotSession).filter(
        ChatbotSession.contact_id == contact_id
    ).delete()

    # Anonymize the contact row — wipe PII but keep the UUID
    contact = db.query(Contact).filter(Contact.id == contact_id).first()
    if contact:
        contact.first_name = "Utilisateur"
        contact.last_name = "Supprimé"
        contact.phone_number = "XXXX"
        contact.email = f"deleted_{contact_id[:8]}@supprime.invalid"
        contact.global_gdpr_consent = False

    db.commit()

    result = {
        "contact_id": contact_id,
        "calls_deleted": deleted_calls,
        "contact_anonymized": True,
    }
    logger.info(f"GDPR: Erased contact data for contact_id={contact_id} | {result}")
    return result


# ─────────────────────────────────────────────
# Full account deletion (Right to Erasure)
# ─────────────────────────────────────────────

def delete_user_account(user_id: str, db: Session) -> dict:
    """
    Complete erasure of a user account and all associated data.

    Cascade order:
      1. Audio files (S3/MinIO)
      2. Transcript embeddings
      3. Transcripts
      4. Call summaries
      5. Reminders
      6. Calls
      7. Appointments
      8. Chatbot sessions
      9. Tasks & agenda items
      10. File references
      11. Contacts (anonymized, not deleted, for audit trail)
      12. User row

    RGPD Art. 17 + CNIL guidance: deletion must be effective within 30 days.
    """
    summary = {
        "user_id": user_id,
        "calls_deleted": 0,
        "audio_deleted": 0,
        "transcripts_deleted": 0,
        "summaries_deleted": 0,
        "embeddings_deleted": 0,
        "chatbot_sessions_deleted": 0,
        "tasks_deleted": 0,
        "agenda_deleted": 0,
        "files_deleted": 0,
        "contacts_anonymized": 0,
    }

    # 1–6: Delete all calls and their children
    calls = db.query(Call).filter(Call.user_id == user_id).all()
    for call in calls:
        result = delete_call_data(call.id, db)
        summary["calls_deleted"] += 1
        summary["audio_deleted"] += 1 if result["audio_deleted"] else 0
        summary["transcripts_deleted"] += 1 if result["transcript_deleted"] else 0
        summary["summaries_deleted"] += 1 if result["summary_deleted"] else 0
        summary["embeddings_deleted"] += result["embeddings_deleted"]

    # 7. Delete appointments
    db.query(Appointment).filter(Appointment.user_id == user_id).delete()

    # 8. Delete chatbot sessions
    n = db.query(ChatbotSession).filter(ChatbotSession.user_id == user_id).delete()
    summary["chatbot_sessions_deleted"] = n

    # 9. Tasks and agenda
    n_tasks = db.query(TaskModel).filter(TaskModel.user_id == user_id).delete()
    n_agenda = db.query(AgendaModel).filter(AgendaModel.user_id == user_id).delete()
    summary["tasks_deleted"] = n_tasks
    summary["agenda_deleted"] = n_agenda

    # 10. File references
    n_files = db.query(FileModel).filter(FileModel.user_id == user_id).delete()
    summary["files_deleted"] = n_files

    # 11. Anonymize contacts (preserve ID for audit trail, wipe PII)
    contacts = db.query(Contact).filter(Contact.user_id == user_id).all()
    for contact in contacts:
        contact.first_name = "Utilisateur"
        contact.last_name = "Supprimé"
        contact.phone_number = "XXXX"
        contact.email = f"deleted_{contact.id[:8]}@supprime.invalid"
        contact.global_gdpr_consent = False
        summary["contacts_anonymized"] += 1

    db.commit()

    # 12. Delete user row
    user = db.query(User).filter(User.id == user_id).first()
    if user:
        db.delete(user)
        db.commit()

    logger.info(f"GDPR: Account deleted for user_id={user_id} | {summary}")
    return summary


# ─────────────────────────────────────────────
# Data Export (Right of Access — Art. 15 RGPD)
# ─────────────────────────────────────────────

def export_user_data(user_id: str, db: Session) -> dict:
    """
    Generates a complete JSON export of all data held for a user.
    Must be provided within 30 days of request (CNIL guidance).
    """
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        return {"error": "Utilisateur introuvable"}

    calls = db.query(Call).filter(Call.user_id == user_id).all()
    call_data = []
    for call in calls:
        transcript = db.query(Transcript).filter(Transcript.call_id == call.id).first()
        summary = db.query(CallSummary).filter(CallSummary.call_id == call.id).first()
        call_data.append({
            "call_id": call.id,
            "contact_id": call.contact_id,
            "direction": call.direction,
            "started_at": call.started_at.isoformat() if call.started_at else None,
            "ended_at": call.ended_at.isoformat() if call.ended_at else None,
            "status": call.status,
            "consent_given": call.consent_given,
            "transcript": {
                "raw_text": transcript.raw_text if transcript else None,
                "language": transcript.language if transcript else None,
                "speaker_segments": json.loads(transcript.speaker_segments)
                    if transcript and transcript.speaker_segments else [],
            },
            "summary": summary.summary_text if summary else None,
        })

    contacts = db.query(Contact).filter(Contact.user_id == user_id).all()
    appointments = db.query(Appointment).filter(Appointment.user_id == user_id).all()

    export = {
        "exported_at": datetime.utcnow().isoformat() + "Z",
        "user": {
            "id": user.id,
            "first_name": user.first_name,
            "last_name": user.last_name,
            "email": user.email,
            "number": user.number,
            "created_at": user.created_at.isoformat() if user.created_at else None,
        },
        "contacts": [
            {
                "id": c.id,
                "first_name": c.first_name,
                "last_name": c.last_name,
                "phone_number": c.phone_number,
                "email": c.email,
                "gdpr_consent": c.global_gdpr_consent,
            }
            for c in contacts
        ],
        "calls": call_data,
        "appointments": [
            {
                "id": a.id,
                "contact_id": a.contact_id,
                "scheduled_at": a.scheduled_at.isoformat() if a.scheduled_at else None,
                "title": a.title,
                "status": a.status,
            }
            for a in appointments
        ],
        "legal_notice": (
            "Ces données vous appartiennent. "
            "Conformément au RGPD (Art. 15, 17, 20), vous pouvez "
            "demander leur suppression à tout moment via DELETE /api/v1/me."
        ),
    }

    logger.info(f"GDPR: Data export generated for user_id={user_id}")
    return export


# ─────────────────────────────────────────────
# Auto-Purge Scheduler (called by Celery Beat)
# ─────────────────────────────────────────────

def run_auto_purge() -> dict:
    """
    Deletes data that has exceeded its legal retention period.
    Intended to be called by a daily Celery Beat task (02:00 local time).

    Retention policy:
      - Audio files  : 30 days  (deleted from S3 by storage.py TTL policy)
      - Transcripts  : 365 days
      - Call records : 730 days
    """
    db = SessionLocal()
    summary = {"transcripts_purged": 0, "calls_purged": 0}

    try:
        now = datetime.utcnow()

        # Purge old transcripts
        transcript_cutoff = now - timedelta(days=TRANSCRIPT_RETENTION_DAYS)
        old_transcripts = db.query(Transcript).filter(
            Transcript.created_at < transcript_cutoff
        ).all()
        for t in old_transcripts:
            # Delete embeddings first
            db.query(TranscriptEmbedding).filter(
                TranscriptEmbedding.transcript_id == t.id
            ).delete()
            # Delete summary
            db.query(CallSummary).filter(CallSummary.call_id == t.call_id).delete()
            db.delete(t)
            summary["transcripts_purged"] += 1

        db.commit()

        # Purge old call records
        call_cutoff = now - timedelta(days=CALL_RECORD_RETENTION_DAYS)
        old_calls = db.query(Call).filter(
            Call.started_at < call_cutoff
        ).all()
        for call in old_calls:
            delete_call_data(call.id, db)
            summary["calls_purged"] += 1

        logger.info(f"GDPR auto-purge completed: {summary}")
    except Exception as e:
        logger.error(f"GDPR auto-purge error: {e}")
        db.rollback()
    finally:
        db.close()

    return summary
