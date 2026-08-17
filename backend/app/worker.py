"""
worker.py — Celery Background Task Worker
==========================================

Defines async tasks for the AI processing pipeline:
  1. process_call_audio  — transcribe + summarize + index after upload
  2. purge_expired_data  — daily GDPR auto-purge (scheduled by Celery Beat)

To run the worker locally:
  celery -A app.worker worker --loglevel=info

To run with Beat (scheduled tasks):
  celery -A app.worker beat --loglevel=info

Environment variables:
  CELERY_BROKER_URL    = "redis://localhost:6379/0"
  CELERY_BACKEND_URL   = "redis://localhost:6379/1"
"""

import os
import logging
from celery import Celery
from celery.schedules import crontab

logger = logging.getLogger(__name__)

BROKER_URL = os.getenv("CELERY_BROKER_URL", "redis://localhost:6379/0")
BACKEND_URL = os.getenv("CELERY_BACKEND_URL", "redis://localhost:6379/1")

celery_app = Celery(
    "appcall",
    broker=BROKER_URL,
    backend=BACKEND_URL,
)

celery_app.conf.update(
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    timezone="Europe/Paris",
    enable_utc=True,
    # Retry policy
    task_acks_late=True,
    task_reject_on_worker_lost=True,
    # Daily GDPR purge at 02:00 Paris time
    beat_schedule={
        "gdpr-auto-purge-daily": {
            "task": "app.worker.purge_expired_data",
            "schedule": crontab(hour=2, minute=0),
        }
    },
)


# ─────────────────────────────────────────────
# Task 1: Full AI pipeline after call upload
# ─────────────────────────────────────────────

@celery_app.task(
    bind=True,
    max_retries=3,
    default_retry_delay=30,
    name="app.worker.process_call_audio",
)
def process_call_audio(self, call_id: str, audio_path: str):
    """
    Background task triggered after a call recording is uploaded.

    Pipeline:
      1. Transcribe audio with faster-whisper
      2. Summarize transcript with GPT-4o-mini
      3. Index transcript chunks in pgvector (for chatbot RAG)
      4. Update call status to PROCESSED

    This task is queued by the /api/v1/calls/{id}/audio endpoint.
    """
    from .database import SessionLocal, Call, Transcript
    from .ai.transcriber import transcribe_call
    from .ai.summarizer import summarize_call
    from .ai.embeddings import index_transcript

    db = SessionLocal()
    try:
        logger.info(f"[Worker] Starting AI pipeline for call_id={call_id}")

        # Step 1: Transcribe
        transcript = transcribe_call(call_id, audio_path, db)
        logger.info(f"[Worker] Transcription complete: {len(transcript.raw_text)} chars")

        # Step 2: Summarize
        summary = summarize_call(call_id, db)
        if summary:
            logger.info(f"[Worker] Summary saved: {summary.summary_text[:60]}...")

        # Step 3: Index for chatbot
        call = db.query(Call).filter(Call.id == call_id).first()
        contact_id = call.contact_id if call else None
        if contact_id:
            n_chunks = index_transcript(transcript.id, contact_id, transcript.raw_text, db)
            logger.info(f"[Worker] Indexed {n_chunks} chunks for chatbot RAG")

        logger.info(f"[Worker] AI pipeline complete for call_id={call_id}")
        return {"status": "ok", "call_id": call_id}

    except Exception as exc:
        logger.error(f"[Worker] Pipeline error for call_id={call_id}: {exc}")
        try:
            raise self.retry(exc=exc)
        except self.MaxRetriesExceededError:
            logger.error(f"[Worker] Max retries exceeded for call_id={call_id}")
            return {"status": "error", "call_id": call_id, "error": str(exc)}
    finally:
        db.close()


# ─────────────────────────────────────────────
# Task 2: Daily GDPR auto-purge
# ─────────────────────────────────────────────

@celery_app.task(name="app.worker.purge_expired_data")
def purge_expired_data():
    """
    Runs every day at 02:00 Paris time (configured in beat_schedule).
    Deletes data exceeding its legal retention period.
    """
    from .gdpr import run_auto_purge
    logger.info("[Worker] Starting daily GDPR auto-purge...")
    result = run_auto_purge()
    logger.info(f"[Worker] GDPR auto-purge complete: {result}")
    return result
