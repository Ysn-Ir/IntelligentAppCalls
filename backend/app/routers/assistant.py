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
    x_app_language: Optional[str] = Header(None),
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
        language=x_app_language,
    )

@router.post("/api/v1/contacts/{contact_id}/chat")
def chat_with_contact(
    contact_id: str,
    body: schemas.ChatRequest,
    x_app_language: Optional[str] = Header(None),
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    # Lookup contact by ID, phone number or email gracefully
    contact = db.query(Contact).filter(
        (Contact.id == contact_id) | 
        (Contact.phone_number == contact_id) |
        (Contact.email == contact_id)
    ).first()

    real_contact_id = contact.id if contact else contact_id
    return ai_chat(
        user_id=user_id,
        message=body.message,
        contact_id=real_contact_id,
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
    contact = db.query(Contact).filter(
        (Contact.id == contact_id) | 
        (Contact.phone_number == contact_id)
    ).first()
    real_contact_id = contact.id if contact else contact_id

    session = db.query(ChatbotSession).filter(
        ChatbotSession.user_id == user_id,
        (ChatbotSession.contact_id == real_contact_id) | (ChatbotSession.contact_id == contact_id),
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
    contact = db.query(Contact).filter(
        (Contact.id == contact_id) | 
        (Contact.phone_number == contact_id)
    ).first()
    real_contact_id = contact.id if contact else contact_id

    session = db.query(ChatbotSession).filter(
        ChatbotSession.user_id == user_id,
        (ChatbotSession.contact_id == real_contact_id) | (ChatbotSession.contact_id == contact_id),
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


@router.get("/api/v1/ai/models")
def get_ai_models():
    """Returns available Groq, Ollama, and OpenAI models and current active configuration."""
    import os
    provider = os.getenv("LLM_PROVIDER", "groq")
    groq_model = os.getenv("GROQ_CHAT_MODEL", "llama-3.3-70b-versatile")
    ollama_model = os.getenv("OLLAMA_MODEL", "llama3.3")
    whisper_model = os.getenv("GROQ_WHISPER_MODEL", "whisper-large-v3-turbo")

    groq_options = [
        {"id": "llama-3.3-70b-versatile", "name": "LLaMA 3.3 70B Versatile", "type": "cloud", "recommended": True, "speed": "Ultra-Fast (~120 t/s)"},
        {"id": "llama-3.1-8b-instant", "name": "LLaMA 3.1 8B Instant", "type": "cloud", "speed": "Instant (~300 t/s)"},
        {"id": "deepseek-r1-distill-llama-70b", "name": "DeepSeek R1 Distill 70B", "type": "cloud", "speed": "Advanced Reasoning"},
        {"id": "mixtral-8x7b-32768", "name": "Mixtral 8x7B MoE", "type": "cloud", "speed": "High Context (32k)"},
        {"id": "gemma2-9b-it", "name": "Gemma 2 9B IT", "type": "cloud", "speed": "Fast"}
    ]

    ollama_options = [
        {"id": "llama3.3", "name": "Ollama LLaMA 3.3 (70B/8B)", "type": "local", "recommended": True, "description": "Top reasoning & French appointment extraction"},
        {"id": "llama3.1:8b", "name": "Ollama LLaMA 3.1 (8B)", "type": "local", "description": "Fast & lightweight on consumer laptops"},
        {"id": "mistral", "name": "Ollama Mistral (7B / 12B Nemo)", "type": "local", "description": "Excellent European French fluency"},
        {"id": "qwen2.5:7b", "name": "Ollama Qwen 2.5 (7B / 14B)", "type": "local", "description": "Superior 7-language multilingual support"},
        {"id": "deepseek-r1:8b", "name": "Ollama DeepSeek-R1 (8B / 14B)", "type": "local", "description": "Step-by-step reasoning on local hardware"},
        {"id": "phi4", "name": "Ollama Phi-4 (14B)", "type": "local", "description": "Microsoft dense reasoning model"}
    ]

    return {
        "current_provider": provider,
        "current_groq_model": groq_model,
        "current_ollama_model": ollama_model,
        "current_whisper_model": whisper_model,
        "groq_models": groq_options,
        "ollama_models": ollama_options
    }
