import json as _json
from typing import Optional, List, Dict, Any
from pydantic import BaseModel
from fastapi import APIRouter, Depends, HTTPException, Header
from sqlalchemy.orm import Session
from ..database import Contact, ChatbotSession, get_db
from ..ai.chatbot import chat as ai_chat, clear_session
from .. import schemas
from .deps import verify_token

router = APIRouter(tags=["AI Assistant & Chatbot"])

@router.post("/api/v1/assistant/chat")
def chat_with_assistant(
    body: schemas.ChatRequest,
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    """RAG Assistant Chat endpoint."""
    return ai_chat(
        user_id=user_id,
        message=body.message,
        contact_id=body.contact_id if hasattr(body, "contact_id") else None,
        db=db,
        session_id=body.session_id,
    )

@router.post("/api/v1/contacts/{contact_id}/chat")
def chat_with_contact(
    contact_id: str,
    body: schemas.ChatRequest,
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    contact = db.query(Contact).filter(Contact.id == contact_id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact introuvable")
    return ai_chat(
        user_id=user_id,
        message=body.message,
        contact_id=contact_id,
        db=db,
        session_id=body.session_id,
        language=x_app_language,
    )

@router.post("/api/v1/chat")
def global_chat(
    body: schemas.ChatRequest,
    x_app_language: Optional[str] = Header(None),
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    return ai_chat(
        user_id=user_id,
        message=body.message,
        contact_id=None,
        db=db,
        session_id=body.session_id,
        language=x_app_language,
    )

@router.get("/api/v1/contacts/{contact_id}/chat/history")
def get_contact_chat_history(
    contact_id: str,
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    session = db.query(ChatbotSession).filter(
        ChatbotSession.user_id == user_id,
        ChatbotSession.contact_id == contact_id,
    ).order_by(ChatbotSession.updated_at.desc()).first()
    if not session:
        return {"session_id": None, "messages": []}
    return {
        "session_id": session.id,
        "messages": _json.loads(session.messages) if session.messages else [],
    }

@router.delete("/api/v1/contacts/{contact_id}/chat")
def clear_contact_chat(
    contact_id: str,
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    session = db.query(ChatbotSession).filter(
        ChatbotSession.user_id == user_id,
        ChatbotSession.contact_id == contact_id,
    ).first()
    if session:
        clear_session(session.id, db)
    return {"status": "ok"}

@router.get("/api/v1/chat/history")
def get_global_chat_history(
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    session = db.query(ChatbotSession).filter(
        ChatbotSession.user_id == user_id,
        ChatbotSession.contact_id.is_(None),
    ).order_by(ChatbotSession.updated_at.desc()).first()
    if not session:
        return {"session_id": None, "messages": []}
    return {
        "session_id": session.id,
        "messages": _json.loads(session.messages) if session.messages else [],
    }

@router.delete("/api/v1/chat/history")
def clear_global_chat(
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    session = db.query(ChatbotSession).filter(
        ChatbotSession.user_id == user_id,
        ChatbotSession.contact_id.is_(None),
    ).first()
    if session:
        clear_session(session.id, db)
    return {"status": "ok"}
