"""
storage.py — Object Storage Client (MinIO / S3)
================================================

Provides:
  - upload_audio_file(call_id, file_bytes, content_type) -> str (URL)
  - download_audio_file(call_id) -> bytes
  - get_signed_url(call_id) -> str  (time-limited download link)
  - delete_audio_file(call_id) -> bool

Uses MinIO in local/dev mode (Docker Compose) and falls back to
local filesystem when STORAGE_MODE=local (for offline development).

Environment variables:
  STORAGE_MODE       = "minio" | "local" (default: "local")
  MINIO_ENDPOINT     = "localhost:9000"
  MINIO_ACCESS_KEY   = "minioadmin"
  MINIO_SECRET_KEY   = "minioadmin"
  MINIO_BUCKET       = "appcall-audio"
  MINIO_SECURE       = "false"
  UPLOAD_DIR         = "./uploads"  (used when STORAGE_MODE=local)
"""

import os
import logging
from pathlib import Path

logger = logging.getLogger(__name__)

STORAGE_MODE = os.getenv("STORAGE_MODE", "local")
UPLOAD_DIR = os.getenv("UPLOAD_DIR", "./uploads")
MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "localhost:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minioadmin")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minioadmin")
MINIO_BUCKET = os.getenv("MINIO_BUCKET", "appcall-audio")
MINIO_SECURE = os.getenv("MINIO_SECURE", "false").lower() == "true"

# Lazy-init MinIO client
_minio_client = None


def _get_minio():
    global _minio_client
    if _minio_client is not None:
        return _minio_client
    try:
        from minio import Minio
        _minio_client = Minio(
            MINIO_ENDPOINT,
            access_key=MINIO_ACCESS_KEY,
            secret_key=MINIO_SECRET_KEY,
            secure=MINIO_SECURE,
        )
        # Ensure bucket exists
        if not _minio_client.bucket_exists(MINIO_BUCKET):
            _minio_client.make_bucket(MINIO_BUCKET)
            logger.info(f"Created MinIO bucket: {MINIO_BUCKET}")
        return _minio_client
    except ImportError:
        logger.warning("minio package not installed — falling back to local storage")
        return None
    except Exception as e:
        logger.warning(f"MinIO connection failed ({e}) — falling back to local storage")
        return None


def _object_key(call_id: str, ext: str = "m4a") -> str:
    return f"calls/{call_id}.{ext}"


def _local_path(call_id: str, ext: str = "m4a") -> str:
    os.makedirs(UPLOAD_DIR, exist_ok=True)
    return os.path.join(UPLOAD_DIR, f"{call_id}.{ext}")


# ─────────────────────────────────────────────
# Upload
# ─────────────────────────────────────────────

def upload_audio_file(call_id: str, file_bytes: bytes, content_type: str = "audio/mp4") -> str:
    """
    Stores audio bytes for a given call_id.
    Returns the storage URL/path string.
    """
    ext = "wav" if "wav" in content_type else "m4a"

    if STORAGE_MODE == "minio":
        client = _get_minio()
        if client:
            import io
            key = _object_key(call_id, ext)
            client.put_object(
                MINIO_BUCKET,
                key,
                io.BytesIO(file_bytes),
                length=len(file_bytes),
                content_type=content_type,
            )
            url = f"minio://{MINIO_BUCKET}/{key}"
            logger.info(f"Uploaded audio to MinIO: {url}")
            return url

    # Fallback — local filesystem
    path = _local_path(call_id, ext)
    with open(path, "wb") as f:
        f.write(file_bytes)
    logger.info(f"Saved audio locally: {path}")
    return f"local://{path}"


# ─────────────────────────────────────────────
# Download (for transcription worker)
# ─────────────────────────────────────────────

def download_audio_file(call_id: str) -> tuple[bytes, str]:
    """
    Returns (audio_bytes, local_file_path).
    Downloads from MinIO to a temp file if needed.
    """
    import tempfile

    # Try MinIO first
    if STORAGE_MODE == "minio":
        client = _get_minio()
        if client:
            for ext in ("m4a", "wav", "mp4"):
                try:
                    key = _object_key(call_id, ext)
                    response = client.get_object(MINIO_BUCKET, key)
                    data = response.read()
                    response.close()
                    # Write to temp file so faster-whisper can read it
                    suffix = f".{ext}"
                    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
                        tmp.write(data)
                        tmp_path = tmp.name
                    logger.info(f"Downloaded from MinIO: {key} -> {tmp_path}")
                    return data, tmp_path
                except Exception:
                    continue

    # Try local filesystem
    for ext in ("m4a", "wav", "mp4"):
        path = _local_path(call_id, ext)
        if os.path.exists(path):
            with open(path, "rb") as f:
                data = f.read()
            logger.info(f"Loaded audio from local: {path}")
            return data, path

    raise FileNotFoundError(f"Audio not found for call_id={call_id}")


# ─────────────────────────────────────────────
# Signed URL (for playback in the app)
# ─────────────────────────────────────────────

def get_signed_url(call_id: str, expires_hours: int = 1) -> str | None:
    """
    Returns a time-limited signed URL for audio playback.
    Returns None if not using MinIO (local mode falls back to /api endpoint).
    """
    if STORAGE_MODE == "minio":
        client = _get_minio()
        if client:
            from datetime import timedelta
            for ext in ("m4a", "wav", "mp4"):
                try:
                    key = _object_key(call_id, ext)
                    url = client.presigned_get_object(
                        MINIO_BUCKET, key,
                        expires=timedelta(hours=expires_hours)
                    )
                    return url
                except Exception:
                    continue
    return None  # Caller falls back to /api/v1/calls/{id}/audio endpoint


# ─────────────────────────────────────────────
# Delete (GDPR)
# ─────────────────────────────────────────────

def delete_audio_file(call_id: str) -> bool:
    """
    Permanently deletes the audio file for a call.
    Called by the GDPR engine.
    Returns True if file was found and deleted.
    """
    deleted = False

    # Try MinIO
    if STORAGE_MODE == "minio":
        client = _get_minio()
        if client:
            for ext in ("m4a", "wav", "mp4"):
                try:
                    key = _object_key(call_id, ext)
                    client.remove_object(MINIO_BUCKET, key)
                    logger.info(f"Deleted from MinIO: {key}")
                    deleted = True
                except Exception:
                    pass

    # Try local filesystem
    for ext in ("m4a", "wav", "mp4"):
        path = _local_path(call_id, ext)
        if os.path.exists(path):
            os.remove(path)
            logger.info(f"Deleted local audio: {path}")
            deleted = True

    return deleted
