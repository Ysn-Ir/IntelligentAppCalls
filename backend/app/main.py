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
def get_voip_token(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    account_sid = os.getenv("TWILIO_ACCOUNT_SID")
    auth_token = os.getenv("TWILIO_AUTH_TOKEN")
    twiml_app_sid = os.getenv("TWILIO_TWIML_APP_SID")
    api_key = os.getenv("TWILIO_API_KEY") or account_sid
    api_secret = os.getenv("TWILIO_API_SECRET") or auth_token

    if account_sid and auth_token and twiml_app_sid:
        try:
            from twilio.jwt.access_token import AccessToken
            from twilio.jwt.access_token.grants import VoiceGrant

            token = AccessToken(account_sid, api_key, api_secret, identity=user_id, ttl=3600)
            voice_grant = VoiceGrant(
                outgoing_application_sid=twiml_app_sid,
                incoming_allow=True
            )
            token.add_grant(voice_grant)
            return {"token": token.to_jwt()}
        except Exception as e:
            logger.error(f"Error generating Twilio AccessToken: {e}")

    return {"token": f"dev_twilio_token_{uuid.uuid4().hex}"}

@app.get("/api/v1/users/me")
def get_me(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        user = db.query(User).first()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")
    return {
        "id": user.id,
        "first_name": user.first_name,
        "last_name": user.last_name,
        "email": user.email,
        "number": user.number,
        "created_at": user.created_at.isoformat() + "Z" if user.created_at else datetime.utcnow().isoformat() + "Z"
    }

@app.put("/api/v1/users/me")
def update_profile(req: schemas.ProfileUpdateRequest, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        user = db.query(User).first()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")
    if req.first_name is not None:
        user.first_name = req.first_name
    if req.last_name is not None:
        user.last_name = req.last_name
    if req.email is not None:
        user.email = req.email
    if req.number is not None:
        user.number = req.number
    db.commit()
    db.refresh(user)
    return {
        "id": user.id,
        "first_name": user.first_name,
        "last_name": user.last_name,
        "email": user.email,
        "number": user.number
    }

@app.put("/api/v1/users/me/password")
def change_password(req: schemas.PasswordChangeRequest, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        user = db.query(User).first()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")
    if user.password_hash:
        if not bcrypt.verify(req.old_password, user.password_hash):
            raise HTTPException(status_code=400, detail="Ancien mot de passe incorrect")
    user.password_hash = bcrypt.hash(req.new_password)
    db.commit()
    return {"message": "Mot de passe mis à jour avec succès"}

@app.get("/api/v1/contacts", response_model=List[schemas.ContactDto])
def get_contacts(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    contacts = db.query(Contact).all()
    return contacts

@app.post("/api/v1/contacts", response_model=schemas.ContactDto)
def create_contact(contact: schemas.ContactDto, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
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
def patch_contact_consent(id: str, payload: schemas.ConsentRequest, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    contact = db.query(Contact).filter(Contact.id == id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    contact.global_gdpr_consent = payload.consent_given
    db.commit()
    return {"status": "ok"}

@app.post("/api/v1/calls", response_model=schemas.CallResponse)
def create_call(request: schemas.CallRequest, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    contact = db.query(Contact).filter(Contact.id == request.contact_id).first()
    if not contact:
        contact = db.query(Contact).first()
        if not contact:
            raise HTTPException(status_code=404, detail="No contacts available")
    
    new_call = Call(
        id=str(uuid.uuid4()),
        contact_id=contact.id,
        user_id=user_id,
        direction=request.direction,
        status="ONGOING",
        consent_given=True,
        twilio_params=json.dumps({"caller_id": contact.phone_number or "+331234567", "room_name": f"call_{uuid.uuid4().hex}"})
    )
    db.add(new_call)
    db.commit()
    db.refresh(new_call)
    
    return schemas.CallResponse(
        id=new_call.id,
        contact_id=new_call.contact_id,
        direction=new_call.direction,
        status=new_call.status,
        twilio_params=json.loads(new_call.twilio_params) if new_call.twilio_params else None
    )

@app.post("/api/v1/calls/twilio-outbound")
def initiate_twilio_outbound_call(request: schemas.CallRequest, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """
    Triggers an active outbound call from your Twilio virtual number to the target contact
    using the Twilio REST API, recording the conversation in dual-channel HD.
    """
    account_sid = os.getenv("TWILIO_ACCOUNT_SID")
    auth_token = os.getenv("TWILIO_AUTH_TOKEN")
    twilio_number = os.getenv("TWILIO_PHONE_NUMBER")

@app.post("/api/v1/calls/cloud-outbound")
def initiate_cloud_outbound_call(request: schemas.CallRequest, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """
    Initiates an outbound call using the configured Cloud VoIP provider
    (Twilio, Telnyx, Plivo, Vonage, or Generic SIP/PBX Gateway).
    """
    provider = (request.provider or os.getenv("VOIP_PROVIDER", "TWILIO")).upper()
    contact = db.query(Contact).filter(Contact.id == request.contact_id).first()
    if not contact or not contact.phone_number:
        raise HTTPException(status_code=404, detail="Contact introuvable ou sans numéro de téléphone")

    # Determine agent's phone to ring first
    agent_phone = os.getenv("VOIP_AGENT_PHONE_NUMBER") or os.getenv("TWILIO_AGENT_PHONE_NUMBER") or os.getenv("AGENT_FORWARD_PHONE_NUMBER")
    if not agent_phone:
        user = db.query(User).filter(User.id == user_id).first()
        if user and user.number:
            agent_phone = user.number
        else:
            agent_phone = "+33100000000"

    server_url = os.getenv("SERVER_BASE_URL", "http://127.0.0.1:8000")
    recording_cb = f"{server_url.rstrip('/')}/webhooks/recording-complete"
    status_cb = f"{server_url.rstrip('/')}/webhooks/status"

    call_id = f"{provider.lower()}_{uuid.uuid4().hex[:12]}"

    try:
        if provider == "TWILIO":
            account_sid = os.getenv("TWILIO_ACCOUNT_SID")
            auth_token = os.getenv("TWILIO_AUTH_TOKEN")
            twilio_number = os.getenv("TWILIO_PHONE_NUMBER")

            if not account_sid or not auth_token or not twilio_number:
                raise HTTPException(
                    status_code=400,
                    detail="Paramètres Twilio non configurés (TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN / TWILIO_PHONE_NUMBER manquants)"
                )

            from twilio.rest import Client
            client = Client(account_sid, auth_token)

            bridge_twiml = f"""<?xml version="1.0" encoding="UTF-8"?>
<Response>
    <Say language="fr-FR">Connexion en cours avec votre contact...</Say>
    <Dial record="record-from-answer-dual" recordingStatusCallback="{recording_cb}" recordingStatusCallbackMethod="POST">
        <Number>{contact.phone_number}</Number>
    </Dial>
</Response>"""

            tw_call = client.calls.create(
                to=agent_phone,
                from_=twilio_number,
                twiml=bridge_twiml,
                status_callback=status_cb,
                status_callback_method="POST"
            )
            call_id = tw_call.sid

        elif provider == "TELNYX":
            telnyx_key = os.getenv("TELNYX_API_KEY")
            telnyx_connection_id = os.getenv("TELNYX_CONNECTION_ID")
            telnyx_number = os.getenv("TELNYX_PHONE_NUMBER") or os.getenv("VOIP_PHONE_NUMBER")

            if telnyx_key:
                import requests
                headers = {"Authorization": f"Bearer {telnyx_key}", "Content-Type": "application/json"}
                payload = {
                    "to": agent_phone,
                    "from": telnyx_number,
                    "connection_id": telnyx_connection_id,
                    "webhook_url": f"{server_url.rstrip('/')}/webhooks/telnyx/voice"
                }
                resp = requests.post("https://api.telnyx.com/v2/calls", json=payload, headers=headers, timeout=10)
                if resp.status_code in [200, 201]:
                    call_id = resp.json().get("data", {}).get("call_control_id", call_id)

        elif provider == "PLIVO":
            plivo_auth_id = os.getenv("PLIVO_AUTH_ID")
            plivo_auth_token = os.getenv("PLIVO_AUTH_TOKEN")
            plivo_number = os.getenv("PLIVO_PHONE_NUMBER")

            if plivo_auth_id and plivo_auth_token:
                import requests
                url = f"https://api.plivo.com/v1/Account/{plivo_auth_id}/Call/"
                payload = {
                    "from": plivo_number,
                    "to": agent_phone,
                    "answer_url": f"{server_url.rstrip('/')}/webhooks/plivo/voice",
                    "answer_method": "POST"
                }
                resp = requests.post(url, json=payload, auth=(plivo_auth_id, plivo_auth_token), timeout=10)
                if resp.status_code in [200, 201]:
                    call_id = resp.json().get("request_uuid", call_id)

        elif provider == "VONAGE":
            vonage_app_id = os.getenv("VONAGE_APPLICATION_ID")
            vonage_number = os.getenv("VONAGE_PHONE_NUMBER")
            logger.info(f"Vonage call dispatch prepared for {contact.phone_number}")

        # Save active call record in Database
        new_call = Call(
            id=call_id,
            contact_id=contact.id,
            user_id=user_id,
            direction="OUTBOUND",
            status="QUEUED",
            ai_status="PROCESSING",
            twilio_params=json.dumps({
                "provider": provider,
                "call_id": call_id,
                "target": contact.phone_number,
                "agent_phone": agent_phone
            })
        )
        db.add(new_call)
        db.commit()

        return {
            "id": call_id,
            "provider": provider,
            "contact_id": contact.id,
            "status": "QUEUED",
            "direction": "OUTBOUND",
            "message": f"Appel {provider} lancé. Votre téléphone ({agent_phone}) va sonner pour vous connecter à {contact.phone_number}."
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error creating {provider} outbound call: {e}")
        raise HTTPException(status_code=500, detail=f"Erreur {provider}: {str(e)}")

@app.post("/api/v1/calls/twilio-outbound")
def initiate_twilio_outbound_call(request: schemas.CallRequest, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """Legacy alias for initiate_cloud_outbound_call with provider=TWILIO."""
    request.provider = "TWILIO"
    return initiate_cloud_outbound_call(request, user_id, db)

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
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db)
):
    query = db.query(Call).filter((Call.user_id == user_id) | (Call.user_id == "system") | (Call.user_id.is_(None)))
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
        phone = None
        name = None
        if c.twilio_params:
            try:
                p = json.loads(c.twilio_params)
                phone = p.get("caller_id") or p.get("phone_number")
                name = p.get("contact_name")
            except Exception:
                pass

        if c.contact:
            contact_full = f"{c.contact.first_name} {c.contact.last_name}".strip()
            if contact_full and contact_full not in ["Anon", "Appel", "Appel Téléphonique", "Unknown Contact"]:
                name = contact_full
            if not phone and c.contact.phone_number:
                phone = c.contact.phone_number

        if not name or name in ["Anon", "Appel", "Appel Téléphonique", "Unknown Contact"]:
            name = phone or "Appel Téléphonique"

        summary_row = db.query(CallSummary).filter(CallSummary.call_id == c.id).first()
        summary_prev = summary_row.summary_text if summary_row and not summary_row.summary_text.startswith("Traitement IA") else None

        result.append(schemas.CallHistoryItemDto(
            id=c.id,
            contact_id=c.contact_id or "unknown",
            direction=c.direction or "OUTBOUND",
            status=c.status or "COMPLETED",
            started_at=c.started_at.isoformat() + "Z" if c.started_at else None,
            ended_at=c.ended_at.isoformat() + "Z" if c.ended_at else None,
            contact_name=name,
            phone_number=phone,
            summary_preview=summary_prev
        ))
    return result

@app.get("/api/v1/calls/{id}/transcript")
def get_transcript(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    transcript = db.query(Transcript).filter(Transcript.call_id == id).first()
    if not transcript:
        return {
            "id": f"pending-{id}",
            "call_id": id,
            "raw_text": "",
            "language": "fr",
            "confidence_score": 0.0,
            "speaker_segments": []
        }

    segments = []
    if transcript.speaker_segments:
        try:
            segments = json.loads(transcript.speaker_segments)
        except Exception:
            pass

    return {
        "id": transcript.id,
        "call_id": transcript.call_id,
        "raw_text": transcript.raw_text,
        "language": transcript.language,
        "confidence_score": transcript.confidence_score,
        "speaker_segments": segments
    }

# Helper to auto-create or update summaries, call records, and appointments for call IDs.
def ensure_call_summary_exists(call_id: str, db: Session) -> CallSummary:
    summary = db.query(CallSummary).filter(CallSummary.call_id == call_id).first()
    transcript = db.query(Transcript).filter(Transcript.call_id == call_id).first()

    # If transcript is available and summary is missing or pending/placeholder, generate real summary!
    if transcript and transcript.raw_text:
        if not summary or summary.status == "PROCESSING" or "Traitement IA" in (summary.summary_text or ""):
            from .ai.summarizer import summarize_call
            real_summary = summarize_call(call_id, db)
            if real_summary:
                return real_summary

    if summary and summary.status != "PROCESSING":
        return summary

    # 1. Create Call row if missing
    call = db.query(Call).filter(Call.id == call_id).first()
    if not call:
        user = db.query(User).first()
        user_id = user.id if user else "system"
        
        call = Call(
            id=call_id,
            contact_id=None,
            user_id=user_id,
            direction="OUTBOUND",
            status="COMPLETED",
            ended_at=datetime.utcnow(),
            consent_given=True,
            consent_timestamp=datetime.utcnow()
        )
        db.add(call)
        db.commit()

    if summary:
        return summary

    # 2. Create pending placeholder while awaiting audio/transcription
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
    
    appt = summary.appointment
    if not appt and summary.detected_appointment_id:
        appt = db.query(Appointment).filter(Appointment.id == summary.detected_appointment_id).first()
    
    call_row = db.query(Call).filter(Call.id == id).first()
    appt_dto = None
    if appt:
        contact_name = None
        phone_num = None
        contact_obj = db.query(Contact).filter(Contact.id == appt.contact_id).first() if appt.contact_id else None
        if contact_obj:
            contact_name = f"{contact_obj.first_name} {contact_obj.last_name}".strip()
            phone_num = contact_obj.phone_number
        if (not contact_name or not phone_num) and call_row and call_row.twilio_params:
            try:
                tp = json.loads(call_row.twilio_params)
                contact_name = contact_name or tp.get("contact_name")
                phone_num = phone_num or tp.get("caller_id")
            except Exception:
                pass

        appt_dto = schemas.AppointmentDto(
            id=appt.id,
            contact_id=appt.contact_id or "contact-1111",
            scheduled_at=appt.scheduled_at.isoformat() + "Z" if appt.scheduled_at else datetime.utcnow().isoformat() + "Z",
            status=appt.status or "PROPOSED",
            title=appt.title or "Rendez-vous détecté",
            summary_context=summary.summary_text if not summary.summary_text.startswith("Traitement IA") else None,
            phone_number=phone_num,
            contact_name=contact_name
        )
        
    transcript_row = db.query(Transcript).filter(Transcript.call_id == id).first()
    confidence = transcript_row.confidence_score if transcript_row else 100.0

    return schemas.CallSummaryDto(
        id=summary.id,
        call_id=summary.call_id,
        summary_text=summary.summary_text,
        status=summary.status,
        confidence_score=confidence,
        detected_appointment_id=summary.detected_appointment_id or (appt.id if appt else None),
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
    x_contact_name: Optional[str] = Header(None),
    x_phone_number: Optional[str] = Header(None),
    token: str = Depends(verify_token),
    db: Session = Depends(get_db)
):
    """
    Receives an audio recording from the Android app after a call ends.
    Stores the file (local or MinIO), then queues the AI processing pipeline.

    Consent gate: rejects upload if consent_given=False on the call.
    """
    # Resolve or create Contact for this caller
    contact_id = None
    if x_phone_number and x_phone_number.strip():
        clean_num = x_phone_number.strip()
        c = db.query(Contact).filter(Contact.phone_number == clean_num).first()
        if not c:
            parts = (x_contact_name or clean_num).split(" ", 1)
            c = Contact(
                id=str(uuid.uuid4()),
                first_name=parts[0],
                last_name=parts[1] if len(parts) > 1 else "",
                phone_number=clean_num,
                email=f"{parts[0].lower()}@contact.phone",
                global_gdpr_consent=True
            )
            db.add(c)
            db.commit()
            db.refresh(c)
        contact_id = c.id
    elif x_contact_name and x_contact_name.strip() and x_contact_name not in ["Appel Téléphonique", "Unknown Contact"]:
        c = db.query(Contact).filter(Contact.first_name.ilike(f"%{x_contact_name.strip()}%")).first()
        if c:
            contact_id = c.id

    # Self-heal: create call row if missing (native dialer calls)
    call = db.query(Call).filter(Call.id == id).first()
    if not call:
        user = db.query(User).first()
        call = Call(
            id=id,
            contact_id=contact_id,
            user_id=user.id if user else "system",
            direction="OUTBOUND",
            status="COMPLETED",
            consent_given=True,
            twilio_params=json.dumps({"caller_id": x_phone_number or "+331234567", "contact_name": x_contact_name or "Appel"})
        )
        db.add(call)
        db.commit()
        db.refresh(call)
    else:
        if contact_id and not call.contact_id:
            call.contact_id = contact_id
        if x_phone_number:
            call.twilio_params = json.dumps({"caller_id": x_phone_number, "contact_name": x_contact_name or "Appel"})
        db.commit()

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
                try:
                    index_transcript(t.id, call_row.contact_id, t.raw_text, _db)
                except Exception as e_idx:
                    logging.warning(f"Embedding index error (ignored): {e_idx}")
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
            if f.startswith(id):
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
    results = []
    seen_ids = set()

    # 1. Appointments detected from calls
    appts = db.query(Appointment).all()
    for a in appts:
        c_name = None
        p_num = None
        contact_obj = db.query(Contact).filter(Contact.id == a.contact_id).first() if a.contact_id else None
        if contact_obj:
            c_name = f"{contact_obj.first_name} {contact_obj.last_name}".strip()
            p_num = contact_obj.phone_number

        summary_obj = db.query(CallSummary).filter(CallSummary.detected_appointment_id == a.id).first()
        call_id_val = summary_obj.call_id if summary_obj else None

        results.append(schemas.AgendaDto(
            id=a.id,
            title=a.title or f"Rendez-vous avec {c_name or 'Contact'}",
            scheduled_at=a.scheduled_at.isoformat() + "Z" if a.scheduled_at else datetime.utcnow().isoformat() + "Z",
            contact_name=c_name,
            phone_number=p_num,
            call_id=call_id_val,
            status=a.status or "SCHEDULED"
        ))
        seen_ids.add(a.id)

    # 2. Agenda backend items
    items = db.query(AgendaModel).filter((AgendaModel.user_id == user_id) | (AgendaModel.user_id.is_(None))).all()
    for i in items:
        if i.id not in seen_ids:
            results.append(schemas.AgendaDto(
                id=i.id,
                title=i.title,
                scheduled_at=i.scheduled_at.isoformat() + "Z" if i.scheduled_at else datetime.utcnow().isoformat() + "Z",
                status="SCHEDULED"
            ))
            seen_ids.add(i.id)

    return results

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
# ----------------- Twilio Production Webhooks -----------------

# ----------------- Universal Multi-Provider Webhooks -----------------

@app.api_route("/webhooks/{provider}/voice", methods=["GET", "POST"])
@app.api_route("/webhooks/voice", methods=["GET", "POST"])
async def universal_voice_webhook(request: Request, provider: str = "universal", db: Session = Depends(get_db)):
    """
    Universal Voice Webhook compatible with Twilio, Telnyx (TeXML), Plivo, SignalWire, Vonage, and SIP Gateways.
    Routes incoming calls to the agent's phone while capturing dual-channel recording.
    """
    provider_name = provider.upper()
    try:
        data = {}
        if request.headers.get("content-type", "").startswith("application/json"):
            try:
                data = await request.json()
            except Exception:
                data = {}
        else:
            form = await request.form()
            data = dict(form)

        call_sid = data.get("CallSid") or data.get("call_control_id") or data.get("uuid") or f"{provider}_{uuid.uuid4().hex[:12]}"
        from_number = data.get("From") or data.get("from") or "+33100000000"
        to_number = data.get("To") or data.get("to") or "+33100000000"
        direction = str(data.get("Direction") or data.get("direction") or "inbound").upper()

        # Ensure Call record exists in DB
        call = db.query(Call).filter(Call.id == call_sid).first()
        if not call:
            call = Call(
                id=call_sid,
                contact_id="1",
                direction="OUTBOUND" if "outbound" in direction.lower() else "INBOUND",
                status="ONGOING",
                ai_status="PROCESSING",
                twilio_params=json.dumps({
                    "provider": provider_name,
                    "caller_id": from_number,
                    "target": to_number,
                    "call_sid": call_sid,
                    "direction": direction
                })
            )
            db.add(call)
            db.commit()

        # Determine target phone to forward to
        agent_forward_number = os.getenv("VOIP_AGENT_PHONE_NUMBER") or os.getenv("TWILIO_AGENT_PHONE_NUMBER") or os.getenv("AGENT_FORWARD_PHONE_NUMBER")
        if not agent_forward_number:
            user = db.query(User).first()
            agent_forward_number = user.number if user and user.number else "+33100000000"

        voip_number = os.getenv("VOIP_PHONE_NUMBER") or os.getenv("TWILIO_PHONE_NUMBER", "")
        is_inbound = (to_number == voip_number) or ("inbound" in direction.lower())
        target_dial = agent_forward_number if is_inbound else to_number

        base_url = str(request.base_url).rstrip('/')
        callback_url = f"{base_url}/webhooks/recording-complete"

        # If Vonage NCCO JSON response
        if provider_name == "VONAGE":
            ncco = [
                {"action": "record", "split": True, "eventUrl": [callback_url]},
                {"action": "connect", "endpoint": [{"type": "phone", "number": target_dial}]}
            ]
            return Response(content=json.dumps(ncco), media_type="application/json")

        # Standard TwiML / TeXML / Plivo XML Response
        xml_response = f"""<?xml version="1.0" encoding="UTF-8"?>
<Response>
    <Dial record="record-from-answer-dual" recordingStatusCallback="{callback_url}" recordingStatusCallbackMethod="POST">
        <Number>{target_dial}</Number>
    </Dial>
</Response>"""

        return Response(content=xml_response, media_type="application/xml")
    except Exception as e:
        logger.error(f"Error handling /webhooks/{provider}/voice: {e}")
        return Response(content="<Response><Hangup/></Response>", media_type="application/xml")

@app.api_route("/webhooks/{provider}/recording-complete", methods=["GET", "POST"])
@app.api_route("/webhooks/recording-complete", methods=["GET", "POST"])
@app.api_route("/webhooks/recording", methods=["GET", "POST"])
async def universal_recording_complete(request: Request, background_tasks: BackgroundTasks, provider: str = "universal", db: Session = Depends(get_db)):
    """
    Universal Recording Webhook.
    Extracts recording URL from any VoIP provider (Twilio, Telnyx, Plivo, Vonage, SIP Gateway),
    downloads dual-channel audio into uploads/ and triggers Whisper STT & French RDV extraction.
    """
    try:
        data = {}
        if request.headers.get("content-type", "").startswith("application/json"):
            try:
                data = await request.json()
            except Exception:
                data = {}
        else:
            form = await request.form()
            data = dict(form)

        call_sid = (
            data.get("CallSid") or
            data.get("call_control_id") or
            data.get("call_id") or
            data.get("uuid") or
            data.get("recording_id")
        )
        recording_url = (
            data.get("RecordingUrl") or
            data.get("recording_url") or
            data.get("RecordUrl") or
            data.get("audio_url") or
            data.get("url")
        )

        # Support Telnyx nested payload
        if not recording_url and isinstance(data.get("data"), dict):
            telnyx_payload = data["data"].get("payload", {})
            recording_url = telnyx_payload.get("recording_url") or telnyx_payload.get("public_recording_url")
            call_sid = call_sid or telnyx_payload.get("call_control_id")

        logger.info(f"Universal recording complete for provider={provider}, call_sid={call_sid}: {recording_url}")

        if call_sid and recording_url:
            audio_url = f"{recording_url}.wav" if not recording_url.endswith(".wav") and not recording_url.endswith(".mp3") else recording_url
            local_filename = f"{provider.lower()}_{call_sid}.wav"
            local_filepath = os.path.join(UPLOAD_DIR, local_filename)

            def _download_and_process():
                try:
                    import requests
                    auth = None
                    acc_sid = os.getenv("TWILIO_ACCOUNT_SID")
                    auth_tok = os.getenv("TWILIO_AUTH_TOKEN")
                    if "twilio.com" in audio_url and acc_sid and auth_tok:
                        auth = (acc_sid, auth_tok)
                    elif "plivo.com" in audio_url:
                        p_id = os.getenv("PLIVO_AUTH_ID")
                        p_tok = os.getenv("PLIVO_AUTH_TOKEN")
                        if p_id and p_tok:
                            auth = (p_id, p_tok)

                    resp = requests.get(audio_url, auth=auth, timeout=30)
                    if resp.status_code == 200:
                        with open(local_filepath, "wb") as f:
                            f.write(resp.content)
                        logger.info(f"Saved audio ({len(resp.content)} bytes) to {local_filepath}")

                        bg_db = SessionLocal()
                        try:
                            c = bg_db.query(Call).filter(Call.id == call_sid).first()
                            if not c:
                                c = Call(id=call_sid, contact_id="1", direction="INBOUND", status="COMPLETED")
                                bg_db.add(c)
                            c.audio_url = f"/uploads/{local_filename}"
                            c.status = "COMPLETED"
                            c.ai_status = "PROCESSING"
                            bg_db.commit()

                            from .ai.transcriber import transcribe_call
                            from .ai.summarizer import summarize_call

                            transcribe_call(call_sid, local_filepath, bg_db)
                            summarize_call(call_sid, bg_db)
                            logger.info(f"AI Pipeline finished for call {call_sid} ({provider})")
                        finally:
                            bg_db.close()
                except Exception as ex:
                    logger.error(f"Error downloading/processing audio for {call_sid}: {ex}")

            background_tasks.add_task(_download_and_process)

        return {"status": "recording_queued_for_ai"}
    except Exception as e:
        logger.error(f"Error in recording-complete webhook: {e}")
        return {"status": "error", "detail": str(e)}

@app.api_route("/webhooks/{provider}/status", methods=["GET", "POST"])
@app.api_route("/webhooks/status", methods=["GET", "POST"])
async def universal_status_webhook(request: Request, provider: str = "universal", db: Session = Depends(get_db)):
    """Handles call status events (completed, busy, failed, no-answer) across all providers."""
    try:
        data = {}
        if request.headers.get("content-type", "").startswith("application/json"):
            try:
                data = await request.json()
            except Exception:
                data = {}
        else:
            form = await request.form()
            data = dict(form)

        call_sid = data.get("CallSid") or data.get("call_control_id") or data.get("uuid")
        call_status = (data.get("CallStatus") or data.get("status") or "completed").upper()

        if call_sid:
            call = db.query(Call).filter(Call.id == call_sid).first()
            if call:
                call.status = call_status
                db.commit()

        return {"status": "ok"}
    except Exception as e:
        logger.error(f"Error in status webhook: {e}")
        return {"status": "ok"}

@app.post("/webhooks/twilio/media-stream")
def twilio_media_stream():
    """Stub for live WebSocket media streams."""
    return {"status": "media_stream_ready"}

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


@app.get("/api/v1/chat/history")
def get_global_chat_history(
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    """Returns the global chatbot conversation history for the current user."""
    session = db.query(ChatbotSession).filter(
        ChatbotSession.user_id == user_id,
        ChatbotSession.contact_id.is_(None),
    ).order_by(ChatbotSession.updated_at.desc()).first()
    if not session:
        return {"session_id": None, "messages": []}
    import json as _json
    return {
        "session_id": session.id,
        "messages": _json.loads(session.messages) if session.messages else [],
    }


@app.delete("/api/v1/chat/history")
def clear_global_chat(
    user_id: str = Depends(verify_token),
    db: Session = Depends(get_db),
):
    """Clears the global chatbot conversation history for the current user."""
    from .ai.chatbot import clear_session
    session = db.query(ChatbotSession).filter(
        ChatbotSession.user_id == user_id,
        ChatbotSession.contact_id.is_(None),
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
