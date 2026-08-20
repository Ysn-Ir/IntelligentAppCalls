#!/usr/bin/env python3
"""
Test Telephony Providers Script
Simulate and validate incoming/outgoing calls and AI pipelines for all VoIP providers:
- Twilio
- Telnyx
- Plivo
- Vonage
- SIP PBX / Asterisk
"""

import sys
import os
import time
import json
import uuid
from fastapi.testclient import TestClient
from app.main import app

# Fix Windows console UTF-8 output
if sys.platform == "win32":
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

client = TestClient(app)

def print_header(title):
    print("\n" + "=" * 60)
    print(f"  [*] {title}")
    print("=" * 60)

def test_voice_webhook(provider="twilio"):
    print_header(f"TEST 1: Inbound Voice Webhook for {provider.upper()}")
    url = f"/webhooks/{provider.lower()}/voice"
    print(f"Sending mock incoming call to: {url}")

    call_id = f"test_{provider.lower()}_{uuid.uuid4().hex[:8]}"
    if provider.lower() == "vonage":
        payload = {
            "uuid": call_id,
            "from": "+33612345678",
            "to": "+33180000000",
            "direction": "inbound"
        }
        resp = client.post(url, json=payload)
    else:
        payload = {
            "CallSid": call_id,
            "From": "+33612345678",
            "To": "+33180000000",
            "Direction": "inbound"
        }
        resp = client.post(url, data=payload)

    print(f"Status Code: {resp.status_code}")
    print(f"Content-Type: {resp.headers.get('content-type')}")
    print(f"Response Body:\n{resp.text[:300]}...")
    if resp.status_code == 200:
        print("✅ Voice Webhook responded successfully!")
    else:
        print("❌ Voice Webhook failed!")
    return call_id

def test_recording_and_ai_pipeline(provider="twilio", sample_filename="c13deb58-10e6-4bbf-a026-ef5e1e55adaa_call_record_c13deb58-10e6-4bbf-a026-ef5e1e55adaa.wav"):
    print_header(f"TEST 2: Recording Ingestion for {provider.upper()}")
    call_id = f"test_{provider.lower()}_{uuid.uuid4().hex[:8]}"
    url = f"/webhooks/{provider.lower()}/recording-complete"

    sample_audio_url = f"http://127.0.0.1:8000/uploads/{sample_filename}"
    print(f"Simulating recording completed from {provider.upper()}:")
    print(f"- Call SID: {call_id}")
    print(f"- Audio URL: {sample_audio_url}")

    if provider.lower() == "telnyx":
        payload = {
            "data": {
                "payload": {
                    "call_control_id": call_id,
                    "recording_url": sample_audio_url
                }
            }
        }
        resp = client.post(url, json=payload)
    elif provider.lower() == "vonage":
        payload = {
            "uuid": call_id,
            "recording_url": sample_audio_url
        }
        resp = client.post(url, json=payload)
    else:
        payload = {
            "CallSid": call_id,
            "RecordingUrl": sample_audio_url,
            "RecordingDuration": "30"
        }
        resp = client.post(url, data=payload)

    print(f"Webhook response: {resp.status_code} {resp.text[:200]}")
    if resp.status_code in [200, 202]:
        print(f"✅ {provider.upper()} recording webhook processed successfully!")
        return True
    return False

def main():
    print("=" * 60)
    print("    INTELLIGENT CALLS — MULTI-PROVIDER TEST SUITE")
    print("=" * 60)

    choice = sys.argv[1] if len(sys.argv) > 1 else "1"
    providers = {
        "1": "twilio",
        "2": "telnyx",
        "3": "plivo",
        "4": "vonage",
        "5": "sip"
    }

    if choice in providers:
        p = providers[choice]
        test_voice_webhook(p)
        test_recording_and_ai_pipeline(p)
    else:
        for p in ["twilio", "telnyx", "plivo", "vonage", "sip"]:
            test_voice_webhook(p)
            test_recording_and_ai_pipeline(p)

    print("\n✅ All provider simulation tests finished!")

if __name__ == "__main__":
    main()
