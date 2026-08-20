import uuid
import json
import asyncio
import logging
from typing import Dict, List
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, HTTPException, status
from ..database import Transcript, SessionLocal
from .deps import verify_token

router = APIRouter(tags=["WebSocket Live Streaming"])
logger = logging.getLogger("intelligent_calls.websocket")

class LiveTranscriptManager:
    """Manages active live WebSocket subscribers for real-time call transcription pub/sub."""
    def __init__(self):
        self.active_connections: Dict[str, List[WebSocket]] = {}

    async def connect(self, call_id: str, websocket: WebSocket):
        await websocket.accept()
        if call_id not in self.active_connections:
            self.active_connections[call_id] = []
        self.active_connections[call_id].append(websocket)
        logger.info(f"WebSocket client connected to live-transcript for call_id={call_id}")

    def disconnect(self, call_id: str, websocket: WebSocket):
        if call_id in self.active_connections:
            if websocket in self.active_connections[call_id]:
                self.active_connections[call_id].remove(websocket)
            if not self.active_connections[call_id]:
                del self.active_connections[call_id]
        logger.info(f"WebSocket client disconnected from call_id={call_id}")

    async def broadcast_transcript(self, call_id: str, text: str, speaker: str = "caller", is_final: bool = True):
        """Broadcasts live transcription fragment to all connected WebSockets for this call."""
        if call_id in self.active_connections:
            message = {
                "type": "transcript",
                "call_id": call_id,
                "text": text,
                "speaker": speaker,
                "is_final": is_final
            }
            dead_sockets = []
            for ws in self.active_connections[call_id]:
                try:
                    await ws.send_json(message)
                except Exception:
                    dead_sockets.append(ws)
            for ws in dead_sockets:
                self.disconnect(call_id, ws)

manager = LiveTranscriptManager()

@router.websocket("/api/v1/ws/calls/{id}/live-transcript")
async def websocket_live_transcript(websocket: WebSocket, id: str):
    """
    Live streaming WebSocket endpoint for active calls.
    Clients receive real-time transcript events broadcasted by speech recognition workers,
    and can also push client audio fragments or live notes.
    """
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
        # Ensure transcript row exists in database
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
    finally:
        db.close()

    await manager.connect(id, websocket)

    try:
        # Send initial confirmation event
        await websocket.send_json({
            "type": "connected",
            "call_id": id,
            "status": "LISTENING_LIVE"
        })

        while True:
            # Receive client messages (live text injection or audio chunk metadata)
            data = await websocket.receive_text()
            try:
                msg = json.loads(data)
                if msg.get("action") == "ping":
                    await websocket.send_json({"type": "pong"})
                elif msg.get("action") == "transcription_chunk":
                    chunk_text = msg.get("text", "").strip()
                    if chunk_text:
                        # Append to DB transcript
                        bg_db = SessionLocal()
                        try:
                            t_row = bg_db.query(Transcript).filter(Transcript.call_id == id).first()
                            if t_row:
                                t_row.raw_text = (t_row.raw_text + " " + chunk_text).strip()
                                bg_db.commit()
                        finally:
                            bg_db.close()
                        # Broadcast to all listeners
                        await manager.broadcast_transcript(id, chunk_text, speaker=msg.get("speaker", "agent"), is_final=True)
            except json.JSONDecodeError:
                pass
    except WebSocketDisconnect:
        manager.disconnect(id, websocket)
    except Exception as e:
        logger.error(f"WebSocket error for call {id}: {e}")
        manager.disconnect(id, websocket)
