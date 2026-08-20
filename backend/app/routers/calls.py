import os
import json
import uuid
import logging
import threading
from datetime import datetime, timedelta
from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, Header, UploadFile, File as FastAPIFile, Query
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session
from ..database import Call, Contact, Transcript, TranscriptEmbedding, CallSummary, Appointment, Reminder, User, SessionLocal, get_db
from .. import schemas
from .deps import verify_token, UPLOAD_DIR

router = APIRouter(tags=["Calls & Telephony"])
logger = logging.getLogger("intelligent_calls.calls")

@router.post("/api/v1/calls", response_model=schemas.CallResponse)
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
        twilio_params=json.loads(new_call.twilio_params) if new_call.twilio_params else None,
        provider="SIM"
    )

@router.post("/api/v1/calls/cloud-outbound")
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

@router.post("/api/v1/calls/twilio-outbound")
def initiate_twilio_outbound_call(request: schemas.CallRequest, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """Legacy alias for initiate_cloud_outbound_call with provider=TWILIO."""
    request.provider = "TWILIO"
    return initiate_cloud_outbound_call(request, user_id, db)

@router.post("/api/v1/calls/bridge")
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

@router.post("/api/v1/calls/{id}/consent")
def submit_consent(id: str, payload: schemas.ConsentRequest, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    call = db.query(Call).filter(Call.id == id).first()
    if not call:
        raise HTTPException(status_code=404, detail="Call not found")
    
    call.consent_given = payload.consent_given
    call.consent_timestamp = datetime.utcnow() if payload.consent_given else None
    db.commit()
    return {"status": "ok"}

@router.post("/api/v1/calls/{id}/end", response_model=schemas.CallResponse)
def end_call(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    call = db.query(Call).filter(Call.id == id).first()
    if not call:
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

    local_path = None
    for ext in ["mp4", "m4a", "wav"]:
        candidate = os.path.join(UPLOAD_DIR, f"{id}.{ext}")
        if os.path.exists(candidate):
            local_path = candidate
            break

    if local_path:
        from ..ai.transcriber import transcribe_call
        from ..ai.summarizer import summarize_call
        from ..ai.embeddings import index_transcript

        def _run_pipeline():
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
                logger.error(f"End call pipeline error: {ex}")
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

@router.get("/api/v1/calls/{id}", response_model=schemas.CallResponse)
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

@router.get("/api/v1/calls", response_model=List[schemas.CallHistoryItemDto])
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
        
    query = query.order_by(Call.started_at.desc())
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
                raw_name = p.get("contact_name")
                if raw_name and raw_name not in ["Anon", "Appel", "Appel Téléphonique", "Unknown Contact"]:
                    name = raw_name
            except Exception:
                pass

        if c.contact:
            contact_full = f"{c.contact.first_name or ''} {c.contact.last_name or ''}".strip()
            is_contact_name_phone = contact_full.startswith("+") or any(char.isdigit() for char in contact_full)
            if contact_full and not is_contact_name_phone and contact_full not in ["Anon", "Appel", "Appel Téléphonique", "Unknown Contact"]:
                name = contact_full
            elif not name and contact_full:
                name = contact_full
            if not phone and c.contact.phone_number:
                phone = c.contact.phone_number

        if not name or name in ["Anon", "Appel", "Appel Téléphonique", "Unknown Contact"]:
            name = phone or "Appel Téléphonique"

        # If name is a phone number and phone is not set or placeholder, unify them
        if name and (name.startswith("+") or (len(name) >= 6 and name.replace(" ", "").isdigit())):
            if not phone or phone == "+331234567":
                phone = name

        summary_row = db.query(CallSummary).filter(CallSummary.call_id == c.id).first()
        summary_prev = summary_row.summary_text if summary_row and not summary_row.summary_text.startswith("Traitement IA") else None

        tags_list = []
        if summary_row and summary_row.tags:
            try:
                tags_list = json.loads(summary_row.tags)
            except Exception:
                tags_list = [t.strip() for t in summary_row.tags.split(",") if t.strip()]

        from ..ai.summarizer import _refine_sentiment_and_intent
        refined = _refine_sentiment_and_intent(
            summary_prev or "",
            {
                "sentiment": (summary_row.sentiment if summary_row else None) or "NEUTRAL",
                "intent": (summary_row.intent if summary_row else None) or "General Call",
                "tags": tags_list
            }
        )

        result.append(schemas.CallHistoryItemDto(
            id=c.id,
            contact_id=c.contact_id or "unknown",
            direction=c.direction or "OUTBOUND",
            status=c.status or "COMPLETED",
            started_at=c.started_at.isoformat() + "Z" if c.started_at else None,
            ended_at=c.ended_at.isoformat() + "Z" if c.ended_at else None,
            contact_name=name,
            phone_number=phone,
            summary_preview=summary_prev,
            sentiment=refined["sentiment"],
            intent=refined["intent"],
            tags=refined["tags"]
        ))
    return result

@router.get("/api/v1/calls/{id}/transcript")
def get_transcript(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    transcript = db.query(Transcript).filter(Transcript.call_id == id).first()
    if not transcript or not transcript.raw_text:
        # Check if local audio recording file exists and trigger on-demand transcription
        for ext in ["wav", "m4a", "mp4"]:
            candidate = os.path.join(UPLOAD_DIR, f"{id}.{ext}")
            if os.path.exists(candidate):
                try:
                    from ..ai.transcriber import transcribe_call
                    transcript = transcribe_call(id, candidate, db)
                    break
                except Exception as e:
                    logger.warning(f"On-demand transcript failed for {candidate}: {e}")

    raw_text = transcript.raw_text if (transcript and transcript.raw_text) else ""

    segments = []
    if transcript and transcript.speaker_segments:
        try:
            segments = json.loads(transcript.speaker_segments)
        except Exception:
            pass

    if not segments and raw_text:
        segments = [{"speaker": "agent", "start": 0.0, "end": 5.0, "text": raw_text}]

    return {
        "id": transcript.id if transcript else f"t-{id}",
        "call_id": id,
        "raw_text": raw_text,
        "language": transcript.language if transcript else "en",
        "confidence_score": transcript.confidence_score if transcript else 0.0,
        "speaker_segments": segments
    }

def ensure_call_summary_exists(call_id: str, db: Session) -> CallSummary:
    summary = db.query(CallSummary).filter(CallSummary.call_id == call_id).first()
    transcript = db.query(Transcript).filter(Transcript.call_id == call_id).first()

    if transcript and transcript.raw_text:
        if not summary or summary.status == "PROCESSING" or "Traitement IA" in (summary.summary_text or ""):
            from ..ai.summarizer import summarize_call
            real_summary = summarize_call(call_id, db)
            if real_summary:
                return real_summary

    if summary and summary.status != "PROCESSING":
        return summary

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

    summary = CallSummary(
        id=str(uuid.uuid4()),
        call_id=call_id,
        summary_text="Traitement IA en cours. Le résumé sera généré dès la fin de la transcription audio.",
        sentiment="NEUTRAL",
        intent="Pending AI Analysis",
        tags="[]",
        detected_appointment_id=None,
        status="PROCESSING",
        modified_count=0
    )
    db.add(summary)
    db.commit()
    db.refresh(summary)
    return summary

@router.get("/api/v1/calls/{id}/summary", response_model=schemas.CallSummaryDto)
def get_summary(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    summary = ensure_call_summary_exists(id, db)
    appt = summary.appointment
    if not appt and summary.detected_appointment_id:
        appt = db.query(Appointment).filter(Appointment.id == summary.detected_appointment_id).first()
    
    call_row = db.query(Call).filter(Call.id == id).first()
    appt_dto = None
    contact_name = None
    phone_num = None

    if appt:
        contact_obj = db.query(Contact).filter(Contact.id == appt.contact_id).first() if appt.contact_id else None
        if contact_obj:
            contact_name = f"{contact_obj.first_name} {contact_obj.last_name}".strip()
            phone_num = contact_obj.phone_number
        if (not contact_name or not phone_num) and call_row and call_row.twilio_params:
            try:
                tp = json.loads(call_row.twilio_params)
                contact_name = contact_name or tp.get("contact_name")
                phone_num = phone_num or tp.get("caller_id") or tp.get("contact_phone")
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

    if not contact_name or not phone_num:
        if call_row:
            if call_row.contact_id:
                c_obj = db.query(Contact).filter(Contact.id == call_row.contact_id).first()
                if c_obj:
                    contact_name = contact_name or f"{c_obj.first_name} {c_obj.last_name}".strip()
                    phone_num = phone_num or c_obj.phone_number
            if (not contact_name or not phone_num) and call_row.twilio_params:
                try:
                    tp = json.loads(call_row.twilio_params)
                    contact_name = contact_name or tp.get("contact_name")
                    phone_num = phone_num or tp.get("caller_id") or tp.get("contact_phone")
                except Exception:
                    pass
        
    transcript_row = db.query(Transcript).filter(Transcript.call_id == id).first()
    confidence = transcript_row.confidence_score if transcript_row else (summary.modified_count and 95.0 or 90.0)

    tags_list = []
    if summary.tags:
        try:
            tags_list = json.loads(summary.tags)
        except Exception:
            tags_list = [t.strip() for t in summary.tags.split(",") if t.strip()]

    # Dynamic Intent & Tags validation engine
    raw_content = (transcript_row.raw_text if transcript_row and transcript_row.raw_text else "") or summary.summary_text or ""
    from ..ai.summarizer import _refine_sentiment_and_intent
    refined = _refine_sentiment_and_intent(
        raw_content,
        {
            "sentiment": summary.sentiment or "NEUTRAL",
            "intent": summary.intent or "General Call",
            "tags": tags_list
        },
        language=transcript_row.language if transcript_row and transcript_row.language else "en"
    )

    final_sentiment = refined["sentiment"]
    final_intent = refined["intent"]
    final_tags = refined["tags"]

    return schemas.CallSummaryDto(
        id=summary.id,
        call_id=summary.call_id,
        summary_text=summary.summary_text,
        status=summary.status,
        sentiment=final_sentiment,
        intent=final_intent,
        tags=final_tags,
        confidence_score=confidence,
        detected_appointment_id=summary.detected_appointment_id or (appt.id if appt else None),
        appointment=appt_dto,
        contact_name=contact_name or "Appel Enregistré",
        phone_number=phone_num or ""
    )

@router.post("/api/v1/calls/{id}/summary/validate")
def validate_summary(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    summary = ensure_call_summary_exists(id, db)
    summary.status = "VALIDATED"
    db.commit()
    return {"status": "ok"}

@router.post("/api/v1/calls/{id}/summary/edit")
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
        if summary.appointment:
            summary.appointment.scheduled_at += timedelta(days=1)
            summary.appointment.title = f"{summary.appointment.title or 'Point'} (modifié par voix)"
            db.add(summary.appointment)
            
    if not updated:
        raise HTTPException(status_code=400, detail="Missing new_text or voice_command_transcript")
        
    db.commit()
    return {"status": "ok"}

@router.post("/api/v1/calls/{id}/appointment/validate")
def validate_appointment(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    summary = ensure_call_summary_exists(id, db)
    if not summary.appointment:
        raise HTTPException(status_code=404, detail="Appointment not found for this call")
        
    summary.appointment.status = "VALIDATED"
    reminder = Reminder(
        id=str(uuid.uuid4()),
        appointment_id=summary.appointment.id,
        call_id=id,
        scheduled_at=summary.appointment.scheduled_at - timedelta(hours=1),
        type="APPOINTMENT"
    )
    db.add(reminder)
    db.commit()
    return {"status": "ok"}

@router.post("/api/v1/calls/{id}/appointment/dismiss")
def dismiss_appointment(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    summary = ensure_call_summary_exists(id, db)
    if not summary.appointment:
        raise HTTPException(status_code=404, detail="Appointment not found for this call")
        
    summary.appointment.status = "DISMISSED"
    db.commit()
    return {"status": "ok"}

@router.post("/api/v1/calls/{id}/audio")
async def upload_audio(
    id: str,
    file: UploadFile = FastAPIFile(...),
    x_contact_name: Optional[str] = Header(None),
    x_phone_number: Optional[str] = Header(None),
    x_app_language: Optional[str] = Header(None),
    token: str = Depends(verify_token),
    db: Session = Depends(get_db)
):
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
        if contact_id:
            call.contact_id = contact_id
        if x_phone_number or x_contact_name:
            call.twilio_params = json.dumps({
                "caller_id": x_phone_number or (c.phone_number if c else "+331234567"),
                "contact_name": x_contact_name or (c.first_name if c else "Appel")
            })
        db.commit()

    call.consent_given = True
    db.commit()

    file_bytes = await file.read()
    content_type = file.content_type or "audio/mp4"

    from ..storage import upload_audio_file
    audio_url = upload_audio_file(id, file_bytes, content_type)

    call.audio_url = audio_url
    call.ai_status = "PROCESSING"
    db.commit()

    ext = "wav" if "wav" in content_type else "m4a"
    local_path = os.path.join(UPLOAD_DIR, f"{id}.{ext}")
    if not os.path.exists(local_path):
        with open(local_path, "wb") as f:
            f.write(file_bytes)

    from ..ai.transcriber import transcribe_call
    from ..ai.summarizer import summarize_call
    from ..ai.embeddings import index_transcript

    def _run_pipeline():
        _db = SessionLocal()
        try:
            logger.info(f"Starting AI pipeline for call_id={id}, path={local_path}, lang={x_app_language or 'auto'}")
            t = transcribe_call(id, local_path, _db, language=x_app_language)
            summarize_call(id, _db, language=x_app_language)
            call_row = _db.query(Call).filter(Call.id == id).first()
            if call_row and call_row.contact_id and t:
                try:
                    index_transcript(t.id, call_row.contact_id, t.raw_text, _db)
                except Exception as e_idx:
                    logger.warning(f"Embedding index error: {e_idx}")
            if call_row:
                call_row.ai_status = "DONE"
                _db.commit()
            logger.info(f"AI pipeline completed for call_id={id}")
        except Exception as ex:
            logger.error(f"Pipeline error: {ex}", exc_info=True)
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

@router.get("/api/v1/calls/{id}/audio")
def download_audio(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    """Downloads or streams the recorded audio file for a completed call."""
    file_path = None
    if os.path.exists(UPLOAD_DIR):
        for f in os.listdir(UPLOAD_DIR):
            if f.startswith(id) or f.endswith(f"_{id}.wav") or f.endswith(f"_{id}.mp4"):
                file_path = os.path.join(UPLOAD_DIR, f)
                break
                
    if not file_path or not os.path.exists(file_path):
        # Check call row in db for audio_url
        call = db.query(Call).filter(Call.id == id).first()
        if call and call.audio_url:
            candidate = os.path.join(UPLOAD_DIR, os.path.basename(call.audio_url))
            if os.path.exists(candidate):
                file_path = candidate

    if not file_path or not os.path.exists(file_path):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Enregistrement audio introuvable pour l'appel '{id}'"
        )

    if file_path.endswith(".wav"):
        return FileResponse(file_path, media_type="audio/wav", filename=f"call_record_{id}.wav")
    return FileResponse(file_path, media_type="audio/mp4", filename=f"call_record_{id}.mp4")

@router.get("/api/v1/calls/{id}/ai-status")
def get_ai_status(id: str, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    call = db.query(Call).filter(Call.id == id).first()
    if not call:
        raise HTTPException(status_code=404, detail="Appel introuvable")
    transcript = db.query(Transcript).filter(Transcript.call_id == id).first()
    summary = db.query(CallSummary).filter(CallSummary.call_id == id).first()
    
    is_ready = (
        transcript is not None 
        and bool(transcript.raw_text) 
        and summary is not None 
        and summary.status in ["CONFIRMED", "VALIDATED", "MODIFIED"]
        and not (summary.summary_text or "").startswith("Traitement IA")
    )
    
    status_str = "DONE" if is_ready else getattr(call, "ai_status", "PROCESSING")
    if not is_ready and status_str == "DONE":
        status_str = "PROCESSING"

    return {
        "call_id": id,
        "ai_status": status_str,
        "has_transcript": transcript is not None and bool(transcript.raw_text),
        "has_summary": summary is not None and not (summary.summary_text or "").startswith("Traitement IA"),
        "transcript_confidence": transcript.confidence_score if transcript else None,
    }

@router.post("/api/v1/integrations/crm-webhook/test")
def test_crm_webhook(payload: dict, token: str = Depends(verify_token)):
    """Tests a user-provided CRM or Zapier webhook URL."""
    webhook_url = payload.get("webhook_url") or os.getenv("CRM_WEBHOOK_URL")
    if not webhook_url:
        raise HTTPException(status_code=400, detail="Webhook URL required")
    try:
        import httpx
        test_data = {
            "event": "test.ping",
            "message": "IntelligentCalls CRM Webhook connection successful",
            "timestamp": datetime.utcnow().isoformat()
        }
        with httpx.Client(timeout=10.0) as client:
            resp = client.post(webhook_url, json=test_data)
            return {"status": "ok", "status_code": resp.status_code, "url": webhook_url}
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Webhook connection error: {str(e)}")

@router.post("/api/v1/calls/{id}/dispatch-webhook")
def dispatch_call_webhook(id: str, payload: dict = {}, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    """Dispatches a structured JSON call summary to a CRM / Zapier endpoint."""
    webhook_url = payload.get("webhook_url") or os.getenv("CRM_WEBHOOK_URL") or os.getenv("ZAPIER_WEBHOOK_URL")
    if not webhook_url:
        raise HTTPException(status_code=400, detail="No CRM webhook URL configured")
    try:
        import httpx
        call = db.query(Call).filter(Call.id == id).first()
        if not call:
            raise HTTPException(status_code=404, detail="Call not found")
        contact = db.query(Contact).filter(Contact.id == call.contact_id).first() if call else None
        summary = db.query(CallSummary).filter(CallSummary.call_id == id).first()
        appointment = db.query(Appointment).filter(Appointment.call_id == id).first()
        reminders = db.query(Reminder).filter(Reminder.call_id == id).all()

        data = {
            "event": "call.summary.ready",
            "call_id": id,
            "timestamp": datetime.utcnow().isoformat(),
            "contact": {
                "id": contact.id if contact else None,
                "name": f"{contact.first_name or ''} {contact.last_name or ''}".strip() if contact else None,
                "phone": contact.phone_number if contact else None,
                "email": contact.email if contact else None
            },
            "summary": summary.summary_text if summary else None,
            "appointment": {
                "title": appointment.title if appointment else None,
                "scheduled_at": appointment.scheduled_at.isoformat() if appointment and appointment.scheduled_at else None,
                "status": appointment.status if appointment else None
            } if appointment else None,
            "tasks": [
                {"title": r.title, "priority": r.priority, "status": r.status}
                for r in reminders
            ]
        }
        with httpx.Client(timeout=10.0) as client:
            resp = client.post(webhook_url, json=data)
            return {"status": "ok", "delivered": True, "http_status": resp.status_code}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to dispatch: {str(e)}")

