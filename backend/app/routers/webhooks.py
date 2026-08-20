import os
import json
import uuid
import logging
from fastapi import APIRouter, Request, Response, BackgroundTasks, Depends
from sqlalchemy.orm import Session
from ..database import Call, SessionLocal, User, get_db
from .deps import UPLOAD_DIR

router = APIRouter(tags=["Multi-Provider Webhooks"])
logger = logging.getLogger("intelligent_calls.webhooks")

@router.api_route("/webhooks/{provider}/voice", methods=["GET", "POST"])
@router.api_route("/webhooks/voice", methods=["GET", "POST"], include_in_schema=False)
async def universal_voice_webhook(request: Request, provider: str = "universal", db: Session = Depends(get_db)):
    """
    Universal Voice Webhook compatible with Twilio, Telnyx (TeXML), Plivo, SignalWire, Vonage, and SIP Gateways.
    Routes incoming calls to the agent's phone while capturing dual-channel recording.
    """
    provider_name = provider.upper()
    try:
        data = {}
        data.update(dict(request.query_params))
        content_type = request.headers.get("content-type", "")
        if "application/json" in content_type:
            try:
                body_json = await request.json()
                if isinstance(body_json, dict):
                    data.update(body_json)
            except Exception:
                pass
        elif "application/x-www-form-urlencoded" in content_type or "multipart/form-data" in content_type:
            try:
                form = await request.form()
                data.update(dict(form))
            except Exception:
                pass

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

@router.api_route("/webhooks/{provider}/recording-complete", methods=["GET", "POST"])
@router.api_route("/webhooks/recording-complete", methods=["GET", "POST"], include_in_schema=False)
@router.api_route("/webhooks/recording", methods=["GET", "POST"], include_in_schema=False)
async def universal_recording_complete(request: Request, background_tasks: BackgroundTasks, provider: str = "universal", db: Session = Depends(get_db)):
    """
    Universal Recording Webhook.
    Extracts recording URL from any VoIP provider (Twilio, Telnyx, Plivo, Vonage, SIP Gateway),
    downloads dual-channel audio into uploads/ and triggers Whisper STT & French RDV extraction.
    """
    try:
        data = {}
        data.update(dict(request.query_params))
        content_type = request.headers.get("content-type", "")
        if "application/json" in content_type:
            try:
                body_json = await request.json()
                if isinstance(body_json, dict):
                    data.update(body_json)
            except Exception:
                pass
        elif "application/x-www-form-urlencoded" in content_type or "multipart/form-data" in content_type:
            try:
                form = await request.form()
                data.update(dict(form))
            except Exception:
                pass

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

                            from ..ai.transcriber import transcribe_call
                            from ..ai.summarizer import summarize_call

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

@router.api_route("/webhooks/{provider}/status", methods=["GET", "POST"])
@router.api_route("/webhooks/status", methods=["GET", "POST"], include_in_schema=False)
async def universal_status_webhook(request: Request, provider: str = "universal", db: Session = Depends(get_db)):
    """Handles call status events (completed, busy, failed, no-answer) across all providers."""
    try:
        data = {}
        data.update(dict(request.query_params))
        content_type = request.headers.get("content-type", "")
        if "application/json" in content_type:
            try:
                body_json = await request.json()
                if isinstance(body_json, dict):
                    data.update(body_json)
            except Exception:
                pass
        elif "application/x-www-form-urlencoded" in content_type or "multipart/form-data" in content_type:
            try:
                form = await request.form()
                data.update(dict(form))
            except Exception:
                pass

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

@router.post("/webhooks/twilio/media-stream")
def twilio_media_stream():
    """Stub for live WebSocket media streams."""
    return {"status": "media_stream_ready"}
