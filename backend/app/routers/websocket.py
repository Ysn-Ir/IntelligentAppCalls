import uuid
import asyncio
import logging
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, HTTPException, status
from ..database import Transcript, SessionLocal
from .deps import verify_token

router = APIRouter(tags=["WebSocket Live Streaming"])
logger = logging.getLogger("intelligent_calls.websocket")

CANNED_TRANSCRIPT_LINES = [
    "Allo, bonjour ?",
    "Bonjour, je vous appelle pour confirmer notre rendez-vous.",
    "Ah parfait ! C'était prévu pour quel jour ?",
    "Je vous propose demain à 14h dans vos bureaux.",
    "Demain à 14h... Oui, c'est noté, parfait pour moi.",
    "Excellent, bonne journée et à demain !",
    "Merci beaucoup, à demain, au revoir !"
]

@router.websocket("/api/v1/ws/calls/{id}/live-transcript")
async def websocket_live_transcript(websocket: WebSocket, id: str):
    auth_header = websocket.headers.get("Authorization")
    if not auth_header:
        token_query = websocket.query_params.get("token")
        if token_query:
            auth_header = f"Bearer {token_query}"
            
    try:
        verify_token(auth_header)
    except HTTPException:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    db = SessionLocal()
    try:
        transcript = db.query(Transcript).filter(Transcript.call_id == id).first()
        if not transcript:
            transcript = Transcript(
                id=str(uuid.uuid4()),
                call_id=id,
                raw_text="",
                language="fr",
                confidence_score=95.0
            )
            db.add(transcript)
            db.commit()
            db.refresh(transcript)
            
        await websocket.accept()
        
        for line in CANNED_TRANSCRIPT_LINES:
            db.refresh(transcript)
            if transcript.raw_text:
                transcript.raw_text += " " + line
            else:
                transcript.raw_text = line
            db.commit()
            
            await websocket.send_json({"text": line})
            await asyncio.sleep(2)
            
    except WebSocketDisconnect:
        logger.info(f"WebSocket client disconnected cleanly for call {id}")
    except Exception as e:
        logger.error(f"WebSocket error for call {id}: {e}")
    finally:
        db.close()
