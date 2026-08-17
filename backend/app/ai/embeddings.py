"""
embeddings.py — pgvector Embedding Store
=========================================

Generates text embeddings for transcript chunks and stores them
in the `transcript_embeddings` PostgreSQL table using pgvector.

Used by chatbot.py for semantic similarity search (RAG).

Environment variables:
  OPENAI_API_KEY       — Required for text-embedding-3-small
  EMBEDDING_MODEL      — default: "text-embedding-3-small"
  EMBEDDING_CHUNK_SIZE — default: 400 characters per chunk
"""

import os
import json
import uuid
import logging
from typing import List, Optional

logger = logging.getLogger(__name__)

EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")
CHUNK_SIZE = int(os.getenv("EMBEDDING_CHUNK_SIZE", "400"))

# Lazy-init OpenAI client
_openai_client = None


def _get_openai():
    global _openai_client
    if _openai_client is not None:
        return _openai_client
    try:
        from openai import OpenAI
        api_key = os.getenv("OPENAI_API_KEY", "")
        if not api_key:
            return None
        _openai_client = OpenAI(api_key=api_key)
        return _openai_client
    except ImportError:
        return None


# ─────────────────────────────────────────────
# Chunking
# ─────────────────────────────────────────────

def _chunk_text(text: str, chunk_size: int = CHUNK_SIZE) -> List[str]:
    """
    Splits text into overlapping chunks for better retrieval.
    Each chunk is ~chunk_size characters with a 50-char overlap.
    """
    if not text:
        return []
    overlap = 50
    chunks = []
    start = 0
    while start < len(text):
        end = min(start + chunk_size, len(text))
        chunks.append(text[start:end].strip())
        start += chunk_size - overlap
    return [c for c in chunks if len(c) > 20]  # Filter tiny chunks


# ─────────────────────────────────────────────
# Embedding generation
# ─────────────────────────────────────────────

def _embed(texts: List[str]) -> List[List[float]]:
    """
    Calls OpenAI text-embedding-3-small to embed a list of texts.
    Returns a list of 1536-dimensional float vectors.
    """
    client = _get_openai()
    if not client:
        logger.warning("No OpenAI client — returning zero vectors for embeddings.")
        return [[0.0] * 1536 for _ in texts]

    try:
        response = client.embeddings.create(
            model=EMBEDDING_MODEL,
            input=texts,
        )
        return [item.embedding for item in response.data]
    except Exception as e:
        logger.error(f"Embedding API error: {e}")
        return [[0.0] * 1536 for _ in texts]


# ─────────────────────────────────────────────
# Store embeddings for a transcript
# ─────────────────────────────────────────────

def index_transcript(transcript_id: str, contact_id: str, raw_text: str, db) -> int:
    """
    Chunks the transcript, generates embeddings, and stores them in
    the transcript_embeddings table.

    Returns the number of chunks stored.
    """
    from ..database import TranscriptEmbedding

    # Delete existing embeddings for this transcript (re-index on update)
    db.query(TranscriptEmbedding).filter(
        TranscriptEmbedding.transcript_id == transcript_id
    ).delete()
    db.commit()

    chunks = _chunk_text(raw_text)
    if not chunks:
        logger.warning(f"No chunks to embed for transcript_id={transcript_id}")
        return 0

    vectors = _embed(chunks)

    for chunk_text, vector in zip(chunks, vectors):
        embedding_row = TranscriptEmbedding(
            id=str(uuid.uuid4()),
            transcript_id=transcript_id,
            contact_id=contact_id,
            chunk_text=chunk_text,
            embedding=json.dumps(vector),
        )
        db.add(embedding_row)

    db.commit()
    logger.info(f"Indexed {len(chunks)} chunks for transcript_id={transcript_id}")
    return len(chunks)


# ─────────────────────────────────────────────
# Semantic search
# ─────────────────────────────────────────────

def search_similar_chunks(
    query: str,
    contact_id: Optional[str],
    db,
    top_k: int = 5
) -> List[dict]:
    """
    Finds the most semantically similar transcript chunks for a query.

    If contact_id is provided, search is scoped to that contact's calls.
    Otherwise, searches across all transcripts for the user.

    Returns a list of dicts:
    [
        {
            "chunk_text": "...",
            "transcript_id": "...",
            "contact_id": "...",
            "similarity": 0.92,
        },
        ...
    ]
    """
    from ..database import TranscriptEmbedding, Transcript, Call

    # Embed the query
    query_vector = _embed([query])[0]

    # Use pgvector cosine similarity operator (<=>)
    # Fallback to Python-side dot product if pgvector is not available
    try:
        from pgvector.sqlalchemy import Vector
        from sqlalchemy import func, cast

        base_query = db.query(TranscriptEmbedding)
        if contact_id:
            base_query = base_query.filter(TranscriptEmbedding.contact_id == contact_id)

        results = (
            base_query
            .order_by(
                TranscriptEmbedding.embedding.cosine_distance(query_vector)
            )
            .limit(top_k)
            .all()
        )
    except Exception:
        # Fallback: load all and compute dot product in Python
        all_rows = db.query(TranscriptEmbedding)
        if contact_id:
            all_rows = all_rows.filter(TranscriptEmbedding.contact_id == contact_id)
        all_rows = all_rows.all()

        def dot(a, b):
            if not a or not b:
                return 0.0
            return sum(x * y for x, y in zip(a, b))

        scored = []
        for row in all_rows:
            try:
                vec = json.loads(row.embedding) if isinstance(row.embedding, str) else row.embedding
                score = dot(query_vector, vec)
                scored.append((score, row))
            except Exception:
                continue
        scored.sort(key=lambda x: x[0], reverse=True)
        results = [row for _, row in scored[:top_k]]

    output = []
    for row in results:
        # Get call date from transcript → call
        transcript = db.query(Transcript).filter(Transcript.id == row.transcript_id).first()
        call_date = None
        call_id = None
        if transcript:
            call = db.query(Call).filter(Call.id == transcript.call_id).first()
            if call:
                call_id = call.id
                call_date = call.started_at.isoformat() if call.started_at else None

        output.append({
            "chunk_text": row.chunk_text,
            "transcript_id": row.transcript_id,
            "contact_id": row.contact_id,
            "call_id": call_id,
            "call_date": call_date,
        })

    # If no vector chunks found, fallback to direct search on Transcript table
    if not output:
        q = db.query(Transcript).join(Call, Transcript.call_id == Call.id)
        if contact_id:
            q = q.filter(Call.contact_id == contact_id)
        all_transcripts = q.order_by(Call.started_at.desc()).all()
        for t in all_transcripts[:top_k]:
            call = db.query(Call).filter(Call.id == t.call_id).first()
            output.append({
                "chunk_text": t.raw_text,
                "transcript_id": t.id,
                "contact_id": call.contact_id if call else contact_id,
                "call_id": t.call_id,
                "call_date": call.started_at.isoformat() if call and call.started_at else None,
            })

    return output
