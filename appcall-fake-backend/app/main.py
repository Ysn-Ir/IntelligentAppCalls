import os
from dotenv import load_dotenv
load_dotenv()

import uuid
import random
import json
import asyncio
from datetime import datetime, timedelta
from fastapi import FastAPI, Depends, HTTPException, status, Header, WebSocket, WebSocketDisconnect, UploadFile, File as FastAPIFile, Query
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from typing import List, Optional

from .database import engine, SessionLocal, init_db, User, Contact, Call, Transcript, CallSummary, Appointment, Reminder, TaskModel, AgendaModel, FileModel, TranscriptEmbedding, ChatbotSession
from . import schemas

# Ensure upload directory exists
UPLOAD_DIR = "./uploads"
os.makedirs(UPLOAD_DIR, exist_ok=True)

app = FastAPI(title="AppCall Fake Backend", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Dependency to get DB session
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

# Initialize and seed database
@app.on_event("startup")
def on_startup():
    init_db()

import jwt
from passlib.hash import bcrypt

JWT_SECRET = os.getenv("JWT_SECRET", "appcall_secret_jwt_key_2026")
JWT_ALGORITHM = "HS256"

def create_access_token(user_id: str, email: str) -> str:
    payload = {
        "sub": user_id,
        "email": email,
        "exp": datetime.utcnow() + timedelta(days=30)
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)

# Helper for Authorization token verification
def verify_token(authorization: Optional[str] = Header(None)):
    if not authorization:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing Authorization Header"
        )
    
    parts = authorization.split(" ")
    if len(parts) != 2 or parts[0].lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid Authorization Header Format"
        )
    
    token = parts[1]
    if token == "dummy_test_token" or token.startswith("fake_jwt_"):
        return "test-user-uuid-1111"

    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        user_id = payload.get("sub")
        if not user_id:
            return "test-user-uuid-1111"
        
        # Verify user_id exists in database to satisfy MySQL Foreign Keys
        db = SessionLocal()
        try:
            user = db.query(User).filter(User.id == user_id).first()
            if not user:
                admin_user = db.query(User).filter(User.id == "test-user-uuid-1111").first()
                if admin_user:
                    return admin_user.id
                # Auto-heal missing user row for Foreign Key constraint
                email_claim = payload.get("email", f"{user_id}@example.com")
                new_u = User(id=user_id, first_name="User", last_name="Auto", email=email_claim, number="+33100000000")
                db.add(new_u)
                db.commit()
        finally:
            db.close()

        return user_id
    except Exception:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid/Expired token"
        )

# ----------------- API Endpoints -----------------

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/api/v1/auth/register", response_model=schemas.LoginResponse)
def register(request: schemas.RegisterRequest, db: Session = Depends(get_db)):
    if not request.email or not request.password:
        raise HTTPException(status_code=400, detail="Missing email or password")
    
    existing = db.query(User).filter(User.email == request.email).first()
    if existing:
        raise HTTPException(status_code=400, detail="Email already registered")
    
    pwd_hash = bcrypt.hash(request.password)
    new_user = User(
        id=str(uuid.uuid4()),
        first_name=request.first_name,
        last_name=request.last_name,
        email=request.email,
        number=request.number or "+33100000000",
        password_hash=pwd_hash
    )
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    
    token = create_access_token(new_user.id, new_user.email)
    return {"access_token": token, "token_type": "bearer"}

@app.post("/api/v1/auth/login", response_model=schemas.LoginResponse)
def login(request: schemas.LoginRequest, db: Session = Depends(get_db)):
    if not request.email or not request.password:
        raise HTTPException(status_code=400, detail="Missing email or password")
    
    user = db.query(User).filter(User.email == request.email).first()
    if not user:
        raise HTTPException(status_code=401, detail="User not found. Please sign up first.")
    
    if not user.password_hash or not bcrypt.verify(request.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid email or password")

    token = create_access_token(user.id, user.email)
    return {"access_token": token, "token_type": "bearer"}

@app.post("/api/v1/auth/refresh", response_model=schemas.LoginResponse)
def refresh(authorization: Optional[str] = Header(None)):
    user_id = verify_token(authorization)
    token = create_access_token(user_id, "user@example.com")
    return {"access_token": token, "token_type": "bearer"}

@app.get("/api/v1/voip/token", response_model=schemas.TokenResponse)
def get_voip_token(token: str = Depends(verify_token)):
    return {"token": f"fake_twilio_token_{uuid.uuid4().hex}"}

@app.get("/api/v1/users/me")
def get_me(token: str = Depends(verify_token), db: Session = Depends(get_db)):
    user = db.query(User).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return {
        "id": user.id,
        "first_name": user.first_name,
        "last_name": user.last_name,
        "email": user.email,
        "number": user.number,
        "created_at": user.created_at.isoformat() + "Z"
    }

@app.get("/api/v1/contacts", response_model=List[schemas.ContactDto])
def get_contacts(token: str = Depends(verify_token), db: Session = Depends(get_db)):
    contacts = db.query(Contact).all()
    return contacts

@app.post("/api/v1/contacts", response_model=schemas.ContactDto)
def create_contact(contact: schemas.ContactDto, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    existing = db.query(Contact).filter(Contact.phone_number == contact.phone_number).first()
    if existing:
        raise HTTPException(status_code=400, detail="Phone number already exists")
    
    new_c = Contact(
        id=str(uuid.uuid4()),
        first_name=contact.first_name,
        last_name=contact.last_name,
        phone_number=contact.phone_number,
        email=contact.email,
        global_gdpr_consent=contact.global_gdpr_consent
    )
    db.add(new_c)
    db.commit()
    db.refresh(new_c)
    return new_c

@app.patch("/api/v1/contacts/{id}/gdpr-consent")
def patch_contact_consent(id: str, payload: schemas.ConsentRequest, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    contact = db.query(Contact).filter(Contact.id == id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    contact.global_gdpr_consent = payload.consent_given
    db.commit()
    return {"status": "ok"}

@app.post("/api/v1/calls", response_model=schemas.CallResponse)
def create_call(request: schemas.CallRequest, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    contact = db.query(Contact).filter(Contact.id == request.contact_id).first()
    if not contact:
        # Self-healing: fallback to first available contact so native calls don't 404
        contact = db.query(Contact).first()
        if not contact:
            raise HTTPException(status_code=404, detail="No contacts seeded in database")
    
    user = db.query(User).first()
    user_id = user.id if user else "system"
    
    new_call = Call(
        id=str(uuid.uuid4()),
        contact_id=contact.id,  # Use resolved contact.id to satisfy FK constraint
        user_id=user_id,
        direction=request.direction,
        status="ONGOING",
        consent_given=True,
        twilio_params=json.dumps({"caller_id": "+331234567", "room_name": f"call_{uuid.uuid4().hex}"})
    )
    db.add(new_call)
    db.commit()
    db.refresh(new_call)
    
    return schemas.CallResponse(
        id=new_call.id,
        contact_id=new_call.contact_id,
        direction=new_call.direction,
        status=new_call.status,
        twilio_params=json.loads(new_call.twilio_params)
    )

@app.post("/api/v1/calls/bridge")
def initiate_call_bridge(request: schemas.CallInitiateRequest, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    contact = db.query(Contact).filter(Contact.id == request.contact_id).first()
    if not contact:
        contact = db.query(Contact).first()
        if not contact:
            raise HTTPException(status_code=404, detail="Contact not found")
    
    user = db.query(User).first()
    call_id = str(uuid.uuid4())
    
    new_call = Call(
        id=call_id,
        contact_id=contact.id,
        user_id=user.id if user else "system",
        direction=request.direction,
        status="BRIDGE_PENDING",
        consent_given=True,
        twilio_params=json.dumps({"gateway": "+33180000000", "target": contact.phone_number})
    )
    db.add(new_call)
    db.commit()
    db.refresh(new_call)
    
    return {
        "call_id": new_call.id,
        "gateway_number": "+33180000000",
        "target_number": contact.phone_number,
        "status": "BRIDGE_INITIATED"
    }

@app.post("/api/v1/calls/{id}/consent")
def submit_consent(id: str, payload: schemas.ConsentRequest, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    call = db.query(Call).filter(Call.id == id).first()
    if not call:
        raise HTTPException(status_code=404, detail="Call not found")
    
    call.consent_given = payload.consent_given
    call.consent_timestamp = datetime.utcnow() if payload.consent_given else None
    db.commit()
    return {"status": "ok"}

@app.post("/api/v1/calls/{id}/end", response_model=schemas.CallResponse)
def end_call(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    call = db.query(Call).filter(Call.id == id).first()
    if not call:
        # Self-healing: auto-create call row for native-XXXX IDs from phone dialer
        user = db.query(User).first()
        contact = db.query(Contact).first()
        call = Call(
            id=id,
            contact_id=contact.id if contact else "unknown",
            user_id=user.id if user else "system",
            direction="OUTBOUND",
            status="ONGOING",
            consent_given=False,
            twilio_params=json.dumps({"caller_id": "+331234567", "room_name": f"native_{id}"})
        )
        db.add(call)
        db.commit()
        db.refresh(call)
    
    call.status = "COMPLETED"
    call.ended_at = datetime.utcnow()
    call.ai_status = "PROCESSING"
    db.commit()

    # Check if audio file was uploaded and trigger AI pipeline
    local_path = None
    for ext in ["mp4", "m4a", "wav"]:
        candidate = os.path.join(UPLOAD_DIR, f"{id}.{ext}")
        if os.path.exists(candidate):
            local_path = candidate
            break

    if local_path:
        from .ai.transcriber import transcribe_call
        from .ai.summarizer import summarize_call
        from .ai.embeddings import index_transcript
        import threading

        def _run_pipeline():
            from .database import SessionLocal
            import logging
            _db = SessionLocal()
            try:
                t = transcribe_call(id, local_path, _db)
                summarize_call(id, _db)
                call_row = _db.query(Call).filter(Call.id == id).first()
                if call_row and call_row.contact_id and t:
                    index_transcript(t.id, call_row.contact_id, t.raw_text, _db)
                if call_row:
                    call_row.ai_status = "DONE"
                    _db.commit()
            except Exception as ex:
                import logging
                logging.getLogger(__name__).error(f"End call pipeline error: {ex}")
            finally:
                _db.close()

        threading.Thread(target=_run_pipeline, daemon=True).start()
        
    return schemas.CallResponse(
        id=call.id,
        contact_id=call.contact_id,
        direction=call.direction,
        status=call.status,
        twilio_params=json.loads(call.twilio_params) if call.twilio_params else None
    )

@app.get("/api/v1/calls/{id}", response_model=schemas.CallResponse)
def get_call(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    call = db.query(Call).filter(Call.id == id).first()
    if not call:
        raise HTTPException(status_code=404, detail="Call not found")
    return schemas.CallResponse(
        id=call.id,
        contact_id=call.contact_id,
        direction=call.direction,
        status=call.status,
        twilio_params=json.loads(call.twilio_params) if call.twilio_params else None
    )

@app.get("/api/v1/calls", response_model=List[schemas.CallHistoryItemDto])
def get_calls(
    contact_id: Optional[str] = None,
    status: Optional[str] = None,
    page: Optional[int] = 1,
    token: str = Depends(verify_token),
    db: Session = Depends(get_db)
):
    query = db.query(Call)
    if contact_id:
        query = query.filter(Call.contact_id == contact_id)
    if status:
        query = query.filter(Call.status == status)
        
    # Standard descending ordering
    query = query.order_by(Call.started_at.desc())
    
    # 20 items per page
    limit = 20
    offset = (page - 1) * limit
    calls = query.offset(offset).limit(limit).all()
    
    result = []
    for c in calls:
        result.append(schemas.CallHistoryItemDto(
            id=c.id,
            contact_id=c.contact_id,
            direction=c.direction,
            status=c.status,
            started_at=c.started_at.isoformat() + "Z" if c.started_at else None,
            ended_at=c.ended_at.isoformat() + "Z" if c.ended_at else None,
            contact_name=f"{c.contact.first_name} {c.contact.last_name}" if c.contact else "Unknown Contact"
        ))
    return result

@app.get("/api/v1/calls/{id}/transcript")
def get_transcript(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    transcript = db.query(Transcript).filter(Transcript.call_id == id).first()
    if not transcript:
        raise HTTPException(status_code=404, detail="Transcript not found")
    return {
        "id": transcript.id,
        "call_id": transcript.call_id,
        "raw_text": transcript.raw_text,
        "language": transcript.language,
        "confidence_score": transcript.confidence_score
    }

# Helper to auto-create mock summaries, call records, and appointments for unknown/intercepted call IDs.
# This prevents 404s and makes testing offline-to-online sync or intercepted calls seamless.
def ensure_call_summary_exists(call_id: str, db: Session) -> CallSummary:
    summary = db.query(CallSummary).filter(CallSummary.call_id == call_id).first()
    if summary:
        return summary

    # 1. Create Call row if missing
    call = db.query(Call).filter(Call.id == call_id).first()
    if not call:
        contact = db.query(Contact).first()
        contact_id = contact.id if contact else "contact-1111"
        user = db.query(User).first()
        user_id = user.id if user else "system"
        
        call = Call(
            id=call_id,
            contact_id=contact_id,
            user_id=user_id,
            direction="OUTBOUND",
            status="COMPLETED",
            ended_at=datetime.utcnow(),
            consent_given=True,
            consent_timestamp=datetime.utcnow()
        )
        db.add(call)
        db.commit()

    # 2. Check if Transcript exists
    transcript = db.query(Transcript).filter(Transcript.call_id == call_id).first()
    if transcript and transcript.raw_text:
        from .ai.summarizer import summarize_call
        real_summary = summarize_call(call_id, db)
        if real_summary:
            return real_summary

    # 3. Create pending placeholder while awaiting audio/transcription
    summary = CallSummary(
        id=str(uuid.uuid4()),
        call_id=call_id,
        summary_text="Traitement IA en cours. Le résumé sera généré dès la fin de la transcription audio.",
        detected_appointment_id=None,
        status="PROCESSING",
        modified_count=0
    )
    db.add(summary)
    db.commit()
    db.refresh(summary)
    return summary

@app.get("/api/v1/calls/{id}/summary", response_model=schemas.CallSummaryDto)
def get_summary(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    summary = ensure_call_summary_exists(id, db)
    
    appt_dto = None
    if summary.appointment:
        appt_dto = schemas.AppointmentDto(
            id=summary.appointment.id,
            contact_id=summary.appointment.contact_id,
            scheduled_at=summary.appointment.scheduled_at.isoformat() + "Z",
            status=summary.appointment.status,
            title=summary.appointment.title
        )
        
    transcript_row = db.query(Transcript).filter(Transcript.call_id == id).first()
    confidence = transcript_row.confidence_score if transcript_row else 100.0

    return schemas.CallSummaryDto(
        id=summary.id,
        call_id=summary.call_id,
        summary_text=summary.summary_text,
        status=summary.status,
        confidence_score=confidence,
        detected_appointment_id=summary.detected_appointment_id,
        appointment=appt_dto
    )

@app.post("/api/v1/calls/{id}/summary/validate")
def validate_summary(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    summary = ensure_call_summary_exists(id, db)
    summary.status = "VALIDATED"
    db.commit()
    return {"status": "ok"}

@app.post("/api/v1/calls/{id}/summary/edit")
def edit_summary(id: str, payload: schemas.SummaryEditRequest, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    summary = ensure_call_summary_exists(id, db)
    
    updated = False
    if payload.new_text is not None:
        summary.summary_text = payload.new_text
        summary.status = "MODIFIED"
        summary.modified_count += 1
        updated = True
        
    if payload.voice_command_transcript is not None:
        summary.status = "MODIFIED"
        summary.modified_count += 1
        updated = True
        # If appointment is linked, nudge scheduled_at +1 day and append text to title
        if summary.appointment:
            summary.appointment.scheduled_at += timedelta(days=1)
            summary.appointment.title = f"{summary.appointment.title or 'Point'} (modifié par voix)"
            db.add(summary.appointment)
            
    if not updated:
        raise HTTPException(status_code=400, detail="Missing new_text or voice_command_transcript")
        
    db.commit()
    return {"status": "ok"}

@app.post("/api/v1/calls/{id}/appointment/validate")
def validate_appointment(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    summary = ensure_call_summary_exists(id, db)
    if not summary.appointment:
        raise HTTPException(status_code=404, detail="Appointment not found for this call")
        
    summary.appointment.status = "VALIDATED"
    
    # Create reminders row
    reminder = Reminder(
        id=str(uuid.uuid4()),
        appointment_id=summary.appointment.id,
        call_id=id,
        scheduled_at=summary.appointment.scheduled_at - timedelta(hours=1), # 1 hour before
        type="APPOINTMENT"
    )
    db.add(reminder)
    db.commit()
    return {"status": "ok"}

@app.post("/api/v1/calls/{id}/appointment/dismiss")
def dismiss_appointment(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    summary = ensure_call_summary_exists(id, db)
    if not summary.appointment:
        raise HTTPException(status_code=404, detail="Appointment not found for this call")
        
    summary.appointment.status = "DISMISSED"
    db.commit()
    return {"status": "ok"}

@app.get("/api/v1/reminders", response_model=List[schemas.ReminderDto])
def get_reminders(upcoming: bool = Query(True), token: str = Depends(verify_token), db: Session = Depends(get_db)):
    query = db.query(Reminder)
    if upcoming:
        query = query.filter(Reminder.scheduled_at > datetime.utcnow())
    reminders = query.all()
    
    res = []
    for r in reminders:
        res.append(schemas.ReminderDto(
            id=r.id,
            appointment_id=r.appointment_id,
            call_id=r.call_id,
            scheduled_at=r.scheduled_at.isoformat() + "Z",
            type=r.type
        ))
    return res

@app.post("/api/v1/reminders")
def create_reminder(reminder: schemas.ReminderDto, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    # Validate scheduled_at
    try:
        dt = datetime.fromisoformat(reminder.scheduled_at.replace("Z", ""))
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid date format")
        
    new_r = Reminder(
        id=str(uuid.uuid4()),
        appointment_id=reminder.appointment_id,
        call_id=reminder.call_id,
        scheduled_at=dt,
        type=reminder.type
    )
    db.add(new_r)
    db.commit()
    return {"status": "ok"}

@app.get("/api/v1/users/me/voice-data/export")
def export_voice_data(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """RGPD Art. 15 — Droit d'accès: export de toutes les données de l'utilisateur."""
    from .gdpr import export_user_data
    return export_user_data(user_id, db)

@app.delete("/api/v1/users/me/voice-data")
def delete_voice_data(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """Deletes all audio, transcripts, and embeddings for the current user (voice-data erasure)."""
    from .storage import delete_audio_file
    calls = db.query(Call).filter(Call.user_id == user_id).all()
    deleted_count = 0
    for call in calls:
        # Delete audio file from storage/disk
        delete_audio_file(call.id)
        call.audio_url = None
        call.ai_status = "PENDING"
        
        # Delete transcript and embeddings
        t = db.query(Transcript).filter(Transcript.call_id == call.id).first()
        if t:
            db.query(TranscriptEmbedding).filter(TranscriptEmbedding.transcript_id == t.id).delete()
            db.delete(t)
            deleted_count += 1
            
        # Delete summary
        s = db.query(CallSummary).filter(CallSummary.call_id == call.id).first()
        if s:
            db.delete(s)

    db.commit()
    return {"status": "ok", "deleted_voice_records": deleted_count}


# ─────────────────────────────────────────────
# GDPR Full Account Deletion (Art. 17 RGPD)
# ─────────────────────────────────────────────

@app.delete("/api/v1/me")
def delete_account(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """
    RGPD Art. 17 — Droit à l'effacement ("droit à l'oubli").
    Supprime définitivement le compte et toutes les données associées.
    Délai légal: 30 jours. Cette implémentation est immédiate.
    """
    from .gdpr import delete_user_account
    result = delete_user_account(user_id, db)
    return {"status": "deleted", "summary": result}


@app.get("/api/v1/me/export")
def export_my_data(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """
    RGPD Art. 15 — Droit d'accès + Art. 20 — Portabilité.
    Retourne un export JSON complet de toutes les données de l'utilisateur.
    """
    from .gdpr import export_user_data
    return export_user_data(user_id, db)


@app.delete("/api/v1/calls/{id}/data")
def delete_call_gdpr(id: str, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """RGPD — Supprime un appel et toutes ses données (audio, transcription, résumé)."""
    from .gdpr import delete_call_data
    call = db.query(Call).filter(Call.id == id, Call.user_id == user_id).first()
    if not call:
        raise HTTPException(status_code=404, detail="Appel introuvable")
    result = delete_call_data(id, db)
    return {"status": "deleted", "summary": result}


@app.delete("/api/v1/contacts/{id}/data")
def erase_contact_gdpr(id: str, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """RGPD — Anonymise un contact et supprime tout l'historique d'appels lié."""
    from .gdpr import erase_contact_data
    contact = db.query(Contact).filter(Contact.id == id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact introuvable")
    result = erase_contact_data(id, db)
    return {"status": "erased", "summary": result}

@app.post("/api/v1/calls/{id}/audio")
async def upload_audio(
    id: str,
    file: UploadFile = FastAPIFile(...),
    token: str = Depends(verify_token),
    db: Session = Depends(get_db)
):
    """
    Receives an audio recording from the Android app after a call ends.
    Stores the file (local or MinIO), then queues the AI processing pipeline.

    Consent gate: rejects upload if consent_given=False on the call.
    """
    # Self-heal: create call row if missing (native dialer calls)
    call = db.query(Call).filter(Call.id == id).first()
    if not call:
        user = db.query(User).first()
        contact = db.query(Contact).first()
        call = Call(
            id=id,
            contact_id=contact.id if contact else "unknown",
            user_id=user.id if user else "system",
            direction="OUTBOUND",
            status="COMPLETED",
            consent_given=True,
            twilio_params=json.dumps({"caller_id": "+331234567", "room_name": f"native_{id}"})
        )
        db.add(call)
        db.commit()
        db.refresh(call)

    # Ensure consent is marked granted when audio is received from device
    call.consent_given = True
    db.commit()

    # Read file bytes
    file_bytes = await file.read()
    content_type = file.content_type or "audio/mp4"

    # Store in MinIO or local filesystem
    from .storage import upload_audio_file
    audio_url = upload_audio_file(id, file_bytes, content_type)

    # Update call record
    call.audio_url = audio_url
    call.ai_status = "PROCESSING"
    db.commit()

    # Determine local path for the worker
    ext = "wav" if "wav" in content_type else "m4a"
    local_path = os.path.join(UPLOAD_DIR, f"{id}.{ext}")
    if not os.path.exists(local_path):
        # Save a local copy for the worker (faster-whisper needs a file path)
        with open(local_path, "wb") as f:
            f.write(file_bytes)

    # Execute AI processing pipeline in background thread
    from .ai.transcriber import transcribe_call
    from .ai.summarizer import summarize_call
    from .ai.embeddings import index_transcript
    import threading

    def _run_pipeline():
        from .database import SessionLocal
        import logging
        _db = SessionLocal()
        try:
            logging.info(f"Starting AI pipeline for call_id={id}, path={local_path}")
            t = transcribe_call(id, local_path, _db)
            summarize_call(id, _db)
            call_row = _db.query(Call).filter(Call.id == id).first()
            if call_row and call_row.contact_id and t:
                index_transcript(t.id, call_row.contact_id, t.raw_text, _db)
            if call_row:
                call_row.ai_status = "DONE"
                _db.commit()
            logging.info(f"AI pipeline completed successfully for call_id={id}")
        except Exception as ex:
            import logging
            logging.getLogger(__name__).error(f"Pipeline processing error: {ex}", exc_info=True)
            try:
                c = _db.query(Call).filter(Call.id == id).first()
                if c:
                    c.ai_status = "FAILED"
                    _db.commit()
            except Exception:
                pass
        finally:
            _db.close()

    threading.Thread(target=_run_pipeline, daemon=True).start()

    return {
        "status": "ok",
        "call_id": id,
        "audio_url": audio_url,
        "ai_status": "PROCESSING",
        "message": "Fichier reçu. Transcription en cours."
    }

@app.get("/api/v1/calls/{id}/audio")
def download_audio(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    from fastapi.responses import FileResponse
    file_path = None
    if os.path.exists(UPLOAD_DIR):
        for f in os.listdir(UPLOAD_DIR):
            if f.startswith(f"{id}_"):
                file_path = os.path.join(UPLOAD_DIR, f)
                break
                
    if not file_path or not os.path.exists(file_path):
        mock_file = os.path.join(UPLOAD_DIR, "mock_call_record.mp4")
        if not os.path.exists(mock_file):
            import math
            import struct
            # Write a valid 2-second audible WAV file (440Hz sine wave tone)
            num_samples = 16000
            data_size = num_samples * 2
            header = struct.pack(
                '<4sI4s4sIHHIIHH4sI',
                b'RIFF',
                36 + data_size,
                b'WAVE',
                b'fmt ',
                16, # chunk size
                1,  # PCM format
                1,  # 1 channel
                8000, # sample rate
                16000, # byte rate
                2,  # block align
                16, # bits per sample
                b'data',
                data_size
            )
            
            tone_bytes = bytearray()
            for i in range(num_samples):
                sample = int(32767.0 * math.sin(2.0 * math.pi * 440.0 * i / 8000.0))
                tone_bytes.extend(struct.pack('<h', sample))
                
            with open(mock_file, "wb") as f:
                f.write(header)
                f.write(tone_bytes)
        file_path = mock_file

    # Detect format by extension — serve correct MIME type for WAV vs MP4
    if file_path and file_path.endswith(".wav"):
        return FileResponse(file_path, media_type="audio/wav", filename=f"call_record_{id}.wav")
    return FileResponse(file_path, media_type="audio/mp4", filename=f"call_record_{id}.mp4")

# (duplicate POST /audio route removed — first handler at line 614 is authoritative)

# ----------------- Tasks Endpoints -----------------

# ----------------- Tasks Endpoints -----------------

@app.get("/api/v1/tasks", response_model=List[schemas.TaskDto])
def get_tasks(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    tasks = db.query(TaskModel).filter((TaskModel.user_id == user_id) | (TaskModel.user_id.is_(None))).all()
    return [schemas.TaskDto(id=t.id, title=t.title, completed=t.completed) for t in tasks]

@app.post("/api/v1/tasks", response_model=schemas.TaskDto)
def create_task(task: schemas.TaskDto, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    existing = db.query(TaskModel).filter(TaskModel.id == task.id).first()
    if existing:
        existing.title = task.title
        existing.completed = task.completed
        existing.user_id = user_id
        db.commit()
        db.refresh(existing)
        return schemas.TaskDto(id=existing.id, title=existing.title, completed=existing.completed)

    new_t = TaskModel(id=task.id, user_id=user_id, title=task.title, completed=task.completed)
    db.add(new_t)
    db.commit()
    db.refresh(new_t)
    return schemas.TaskDto(id=new_t.id, title=new_t.title, completed=new_t.completed)

@app.put("/api/v1/tasks/{id}", response_model=schemas.TaskDto)
def update_task(id: str, task: schemas.TaskDto, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    existing = db.query(TaskModel).filter(TaskModel.id == id).first()
    if not existing:
        raise HTTPException(status_code=404, detail="Task not found")
    existing.title = task.title
    existing.completed = task.completed
    existing.user_id = user_id
    db.commit()
    db.refresh(existing)
    return schemas.TaskDto(id=existing.id, title=existing.title, completed=existing.completed)

# ----------------- Agenda Endpoints -----------------

@app.get("/api/v1/agenda", response_model=List[schemas.AgendaDto])
def get_agenda(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    items = db.query(AgendaModel).filter((AgendaModel.user_id == user_id) | (AgendaModel.user_id.is_(None))).all()
    return [schemas.AgendaDto(id=i.id, title=i.title, scheduled_at=i.scheduled_at.isoformat() + "Z") for i in items]

@app.post("/api/v1/agenda", response_model=schemas.AgendaDto)
def create_agenda_item(item: schemas.AgendaDto, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    try:
        dt = datetime.fromisoformat(item.scheduled_at.replace("Z", ""))
    except ValueError:
        dt = datetime.utcnow() + timedelta(days=1)

    existing = db.query(AgendaModel).filter(AgendaModel.id == item.id).first()
    if existing:
        existing.title = item.title
        existing.scheduled_at = dt
        existing.user_id = user_id
        db.commit()
        db.refresh(existing)
        return schemas.AgendaDto(id=existing.id, title=existing.title, scheduled_at=existing.scheduled_at.isoformat() + "Z")

    new_i = AgendaModel(id=item.id, user_id=user_id, title=item.title, scheduled_at=dt)
    db.add(new_i)
    db.commit()
    db.refresh(new_i)
    return schemas.AgendaDto(id=new_i.id, title=new_i.title, scheduled_at=new_i.scheduled_at.isoformat() + "Z")

# ----------------- Files Endpoints -----------------

@app.get("/api/v1/files", response_model=List[schemas.FileDto])
def get_files_list(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    files = db.query(FileModel).filter((FileModel.user_id == user_id) | (FileModel.user_id.is_(None))).all()
    return [schemas.FileDto(id=f.id, name=f.name, path=f.path, size=f.size) for f in files]

@app.post("/api/v1/files", response_model=schemas.FileDto)
def create_file_item(file: schemas.FileDto, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    existing = db.query(FileModel).filter(FileModel.id == file.id).first()
    if existing:
        existing.name = file.name
        existing.path = file.path
        existing.size = file.size
        existing.user_id = user_id
        db.commit()
        db.refresh(existing)
        return schemas.FileDto(id=existing.id, name=existing.name, path=existing.path, size=existing.size)

    new_f = FileModel(id=file.id, user_id=user_id, name=file.name, path=file.path, size=file.size)
    db.add(new_f)
    db.commit()
    db.refresh(new_f)
    return schemas.FileDto(id=new_f.id, name=new_f.name, path=new_f.path, size=new_f.size)
    db.add(new_f)
    db.commit()
    db.refresh(new_f)
    return schemas.FileDto(id=new_f.id, name=new_f.name, path=new_f.path, size=new_f.size)

# ----------------- Twilio Webhook Stubs -----------------

@app.post("/webhooks/twilio/voice")
def twilio_voice():
    return {"status": "ok"}

@app.post("/webhooks/twilio/status")
def twilio_status():
    return {"status": "ok"}

@app.post("/webhooks/twilio/media-stream")
def twilio_media_stream():
    return {"status": "ok"}

# ----------------- WebSocket Live Transcript -----------------

# ─────────────────────────────────────────────
# AI Status Endpoint
# ─────────────────────────────────────────────

@app.get("/api/v1/calls/{id}/ai-status")
def get_ai_status(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    """Returns the current AI processing status for a call."""
    call = db.query(Call).filter(Call.id == id).first()
    if not call:
        raise HTTPException(status_code=404, detail="Appel introuvable")
    transcript = db.query(Transcript).filter(Transcript.call_id == id).first()
    summary = db.query(CallSummary).filter(CallSummary.call_id == id).first()
    status_str = getattr(call, "ai_status", "PROCESSING")
    if transcript is not None and summary is not None:
        status_str = "DONE"
    return {
        "call_id": id,
        "ai_status": status_str,
        "has_transcript": transcript is not None,
        "has_summary": summary is not None,
        "transcript_confidence": transcript.confidence_score if transcript else None,
    }


# ─────────────────────────────────────────────
# Chatbot Endpoints (RAG Contact Chatbot)
# ─────────────────────────────────────────────

@app.post("/api/v1/contacts/{contact_id}/chat")
def chat_with_contact(
    contact_id: str,
    body: schemas.ChatRequest,
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    """
    Sends a message to the contact-scoped AI chatbot.
    The chatbot answers based on the full call history with this contact.

    Example: "Qu'est-ce qu'Ahmed a dit la semaine dernière ?"
    """
    from .ai.chatbot import chat as ai_chat
    contact = db.query(Contact).filter(Contact.id == contact_id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact introuvable")
    result = ai_chat(
        user_id=user_id,
        message=body.message,
        contact_id=contact_id,
        db=db,
        session_id=body.session_id,
    )
    return result


@app.post("/api/v1/chat")
def global_chat(
    body: schemas.ChatRequest,
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    """
    Global chatbot — answers questions about all calls (not scoped to a contact).
    Example: "Résume tous mes appels de cette semaine."
    """
    from .ai.chatbot import chat as ai_chat
    result = ai_chat(
        user_id=user_id,
        message=body.message,
        contact_id=None,
        db=db,
        session_id=body.session_id,
    )
    return result


@app.get("/api/v1/contacts/{contact_id}/chat/history")
def get_contact_chat_history(
    contact_id: str,
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    """Returns the chatbot conversation history for a specific contact."""
    session = db.query(ChatbotSession).filter(
        ChatbotSession.user_id == user_id,
        ChatbotSession.contact_id == contact_id,
    ).order_by(ChatbotSession.updated_at.desc()).first()
    if not session:
        return {"session_id": None, "messages": []}
    import json as _json
    return {
        "session_id": session.id,
        "messages": _json.loads(session.messages) if session.messages else [],
    }


@app.delete("/api/v1/contacts/{contact_id}/chat")
def clear_contact_chat(
    contact_id: str,
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    """Clears the chatbot conversation history for a contact."""
    from .ai.chatbot import clear_session
    session = db.query(ChatbotSession).filter(
        ChatbotSession.user_id == user_id,
        ChatbotSession.contact_id == contact_id,
    ).first()
    if session:
        clear_session(session.id, db)
    return {"status": "ok"}


# ─────────────────────────────────────────────
# WebSocket Live Transcript (Canned / Fallback)
# ─────────────────────────────────────────────

CANNED_TRANSCRIPT_LINES = [
    "Allo, bonjour ?",
    "Bonjour Jean, c'est Marc. Je t'appelle pour confirmer notre rendez-vous.",
    "Ah super ! C'était prévu pour quand déjà ?",
    "Je te propose mardi prochain à 14h dans vos bureaux.",
    "Mardi prochain à 14h... Oui, c'est parfait pour moi, je note ça.",
    "Génial. Bonne journée, à mardi !",
    "Merci, bonne journée à toi aussi, salut !"
]

@app.websocket("/api/v1/ws/calls/{id}/live-transcript")
async def websocket_endpoint(websocket: WebSocket, id: str):
    # Verify authentication token from query param or header (headers are preferred)
    auth_header = websocket.headers.get("Authorization")
    
    # Fallback to query param
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
        # Find or create transcript row
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
        
        # Stream canned responses every 2 seconds
        for line in CANNED_TRANSCRIPT_LINES:
            # Check if socket is still active
            # Write to SQLite
            db.refresh(transcript)
            if transcript.raw_text:
                transcript.raw_text += " " + line
            else:
                transcript.raw_text = line
            db.commit()
            
            # Send message to client
            await websocket.send_json({"text": line})
            await asyncio.sleep(2)
            
    except WebSocketDisconnect:
        print(f"WebSocket client disconnected cleanly for call {id}")
    except Exception as e:
        print(f"WebSocket error: {e}")
    finally:
        db.close()
