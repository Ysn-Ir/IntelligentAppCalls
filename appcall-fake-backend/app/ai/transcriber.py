"""
transcriber.py — Local Whisper Transcription + Speaker Diarization
===================================================================

Uses faster-whisper (free, runs on CPU or GPU) to transcribe call recordings.
Optionally uses pyannote.audio to identify who is speaking (agent vs. contact).

Environment variables:
  WHISPER_MODEL      = "base" | "small" | "medium" | "large-v3" (default: "small")
  USE_DIARIZATION    = "true" | "false" (default: "false", requires HF_TOKEN)
  HF_TOKEN           = your HuggingFace token (for pyannote — free account needed)
  WHISPER_LANGUAGE   = "fr" (default: French)
  WHISPER_DEVICE     = "cpu" | "cuda" (default: "cpu")
  WHISPER_COMPUTE    = "int8" | "float16" | "float32" (default: "int8")

Model size vs. speed (on CPU, ~1 min audio):
  tiny   → ~5s  (low accuracy)
  base   → ~12s (acceptable)
  small  → ~25s (good)  ← recommended default
  medium → ~60s (great)
  large  → ~3min (best, needs >8GB RAM)
"""

import os
import json
import logging
import tempfile
from typing import Optional

logger = logging.getLogger(__name__)

WHISPER_MODEL = os.getenv("WHISPER_MODEL", "small")
WHISPER_LANGUAGE = os.getenv("WHISPER_LANGUAGE", "fr")
WHISPER_DEVICE = os.getenv("WHISPER_DEVICE", "cpu")
WHISPER_COMPUTE = os.getenv("WHISPER_COMPUTE", "int8")
USE_DIARIZATION = os.getenv("USE_DIARIZATION", "false").lower() == "true"
HF_TOKEN = os.getenv("HF_TOKEN", "")

# Lazy-loaded models
_whisper_model = None
_diarization_pipeline = None


def _get_whisper():
    global _whisper_model
    if _whisper_model is None:
        try:
            from faster_whisper import WhisperModel
            logger.info(f"Loading faster-whisper model '{WHISPER_MODEL}' on {WHISPER_DEVICE}/{WHISPER_COMPUTE}...")
            _whisper_model = WhisperModel(
                WHISPER_MODEL,
                device=WHISPER_DEVICE,
                compute_type=WHISPER_COMPUTE,
            )
            logger.info("faster-whisper model loaded.")
        except ImportError:
            logger.error("faster-whisper not installed. Run: pip install faster-whisper")
            raise
    return _whisper_model


def _get_diarization():
    global _diarization_pipeline
    if not USE_DIARIZATION:
        return None
    if _diarization_pipeline is None:
        try:
            from pyannote.audio import Pipeline
            logger.info("Loading pyannote speaker diarization pipeline...")
            _diarization_pipeline = Pipeline.from_pretrained(
                "pyannote/speaker-diarization-3.1",
                use_auth_token=HF_TOKEN,
            )
            logger.info("Diarization pipeline loaded.")
        except ImportError:
            logger.warning("pyannote.audio not installed. Diarization disabled.")
            return None
        except Exception as e:
            logger.warning(f"Could not load diarization: {e}. Diarization disabled.")
            return None
    return _diarization_pipeline


# ─────────────────────────────────────────────
# Core transcription function
# ─────────────────────────────────────────────

def transcribe(audio_path: str) -> dict:
    """
    Transcribes an audio file using faster-whisper.

    Returns:
    {
        "raw_text": "Bonjour, je voudrais prendre rendez-vous...",
        "language": "fr",
        "confidence_score": 0.92,
        "speaker_segments": [
            {"speaker": "agent",   "start": 0.0, "end": 2.5, "text": "Bonjour !"},
            {"speaker": "contact", "start": 3.0, "end": 7.2, "text": "Je voudrais..."},
        ]
    }
    """
    model = _get_whisper()
    logger.info(f"Transcribing: {audio_path}")

    # Run Whisper
    segments_iter, info = model.transcribe(
        audio_path,
        language=WHISPER_LANGUAGE,
        beam_size=5,
        vad_filter=True,  # skip silent parts — faster
    )
    segments = list(segments_iter)

    raw_text = " ".join(s.text.strip() for s in segments)
    avg_confidence = (
        sum(s.avg_logprob for s in segments) / len(segments)
        if segments else -1.0
    )
    # Convert log probability to 0-100 confidence score
    # avg_logprob is typically -2 to 0; 0 = perfect
    confidence_score = max(0.0, min(100.0, (avg_confidence + 1.0) * 100.0))

    logger.info(
        f"Transcription done: {len(segments)} segments, "
        f"language={info.language}, confidence={confidence_score:.1f}%"
    )

    # Build speaker segments (with or without diarization)
    speaker_segments = _build_speaker_segments(audio_path, segments)

    return {
        "raw_text": raw_text,
        "language": info.language or WHISPER_LANGUAGE,
        "confidence_score": round(confidence_score, 2),
        "speaker_segments": speaker_segments,
    }


def _build_speaker_segments(audio_path: str, whisper_segments: list) -> list:
    """
    Combines Whisper word timestamps with pyannote diarization.
    Falls back to alternating speaker assignment if diarization is unavailable.
    """
    diarization = _get_diarization()

    if diarization is not None:
        return _diarize(audio_path, whisper_segments, diarization)

    # Fallback: simple alternating heuristic
    # First speaker = agent (the app user), second = contact
    # Works well for typical call recordings where agent speaks first.
    segments = []
    current_speaker = "agent"
    prev_end = 0.0
    SILENCE_THRESHOLD = 1.5  # seconds of silence = speaker change

    for seg in whisper_segments:
        if seg.start - prev_end > SILENCE_THRESHOLD and segments:
            current_speaker = "contact" if current_speaker == "agent" else "agent"
        segments.append({
            "speaker": current_speaker,
            "start": round(seg.start, 2),
            "end": round(seg.end, 2),
            "text": seg.text.strip(),
        })
        prev_end = seg.end

    return segments


def _diarize(audio_path: str, whisper_segments: list, pipeline) -> list:
    """
    Runs pyannote diarization and aligns with Whisper segments.
    """
    try:
        import torch
        diarization = pipeline(audio_path)

        # Build a timeline: (start, end, speaker_label)
        speaker_timeline = []
        for turn, _, speaker in diarization.itertracks(yield_label=True):
            speaker_timeline.append((turn.start, turn.end, speaker))

        # Map pyannote labels to agent/contact based on first occurrence
        label_map = {}

        def resolve_speaker(t_start: float) -> str:
            for (s, e, label) in speaker_timeline:
                if s <= t_start <= e:
                    if label not in label_map:
                        label_map[label] = "agent" if not label_map else "contact"
                    return label_map[label]
            return "unknown"

        segments = []
        for seg in whisper_segments:
            midpoint = (seg.start + seg.end) / 2
            speaker = resolve_speaker(midpoint)
            segments.append({
                "speaker": speaker,
                "start": round(seg.start, 2),
                "end": round(seg.end, 2),
                "text": seg.text.strip(),
            })
        return segments
    except Exception as e:
        logger.warning(f"Diarization failed: {e}. Falling back to alternating assignment.")
        return _build_speaker_segments(audio_path, whisper_segments)


# ─────────────────────────────────────────────
# High-level entry point (called by worker.py)
# ─────────────────────────────────────────────

def transcribe_call(call_id: str, audio_path: str, db) -> "Transcript":
    """
    Transcribes a call and saves the result to the database.
    Creates or updates the Transcript row for the given call_id.
    """
    from ..database import Transcript
    import uuid

    result = transcribe(audio_path)

    transcript = db.query(Transcript).filter(Transcript.call_id == call_id).first()
    if transcript:
        # Update existing row
        transcript.raw_text = result["raw_text"]
        transcript.language = result["language"]
        transcript.confidence_score = result["confidence_score"]
        transcript.speaker_segments = json.dumps(result["speaker_segments"], ensure_ascii=False)
    else:
        transcript = Transcript(
            id=str(uuid.uuid4()),
            call_id=call_id,
            raw_text=result["raw_text"],
            language=result["language"],
            confidence_score=result["confidence_score"],
            speaker_segments=json.dumps(result["speaker_segments"], ensure_ascii=False),
        )
        db.add(transcript)

    db.commit()
    db.refresh(transcript)
    logger.info(f"Transcript saved for call_id={call_id}")
    return transcript
