import sys
import os

if sys.platform == "win32":
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def run_comprehensive_audit():
    print("=" * 70)
    print("  PRODUCTION READY 1:1 RETROFIT & BACKEND INTEGRITY AUDIT")
    print("=" * 70)

    tests_passed = 0
    total_tests = 0

    def check(endpoint_name, passed, detail=""):
        nonlocal tests_passed, total_tests
        total_tests += 1
        if passed:
            tests_passed += 1
            print(f"  [PASS] {endpoint_name:<45}")
        else:
            print(f"  [FAIL] {endpoint_name:<45} -> {detail}")

    # 1. Health
    r = client.get("/health")
    check("GET /health", r.status_code == 200)

    # 2. Register
    r = client.post("/api/v1/auth/register", json={
        "email": "prod_user@example.com",
        "password": "ProductionPassword123!",
        "first_name": "Jean",
        "last_name": "Dupont"
    })
    check("POST /api/v1/auth/register", r.status_code in [200, 400])

    # 3. Login
    r = client.post("/api/v1/auth/login", json={
        "email": "prod_user@example.com",
        "password": "ProductionPassword123!"
    })
    token = r.json().get("access_token")
    check("POST /api/v1/auth/login", r.status_code == 200 and token is not None)
    headers = {"Authorization": f"Bearer {token}"}

    # 4. Refresh
    r = client.post("/api/v1/auth/refresh", headers=headers)
    check("POST /api/v1/auth/refresh", r.status_code == 200)

    # 5. User Profile (GET & PUT)
    r = client.get("/api/v1/users/me", headers=headers)
    check("GET /api/v1/users/me", r.status_code == 200 and "email" in r.json())

    r = client.put("/api/v1/users/me", json={"first_name": "Jean-Pierre"}, headers=headers)
    check("PUT /api/v1/users/me", r.status_code == 200 and r.json().get("first_name") == "Jean-Pierre")

    # 6. Change Password
    r = client.put("/api/v1/users/me/password", json={"old_password": "ProductionPassword123!", "new_password": "NewProductionPassword123!"}, headers=headers)
    check("PUT /api/v1/users/me/password", r.status_code == 200)

    # Revert password back
    client.put("/api/v1/users/me/password", json={"old_password": "NewProductionPassword123!", "new_password": "ProductionPassword123!"}, headers=headers)

    # 7. VoIP Token (Checks either 200 with JWT or 503 if credentials unconfigured)
    r = client.get("/api/v1/voip/token", headers=headers)
    check("GET /api/v1/voip/token", r.status_code in [200, 503])

    # 8. Contacts (GET, POST, PATCH)
    r = client.get("/api/v1/contacts", headers=headers)
    check("GET /api/v1/contacts", r.status_code == 200 and isinstance(r.json(), list))
    
    contacts = r.json()
    contact_id = contacts[0]["id"] if contacts else None
    if not contact_id:
        r_create = client.post("/api/v1/contacts", json={"first_name": "Paul", "last_name": "Valery", "phone_number": "+33699999999", "email": "paul@valery.com"}, headers=headers)
        contact_id = r_create.json().get("id")

    check("POST /api/v1/contacts", contact_id is not None)

    r = client.patch(f"/api/v1/contacts/{contact_id}/gdpr-consent", json={"consent_given": True}, headers=headers)
    check("PATCH /api/v1/contacts/{id}/gdpr-consent", r.status_code == 200)

    # 9. Calls Lifecycle
    r = client.post("/api/v1/calls", json={"contact_id": contact_id, "direction": "OUTBOUND"}, headers=headers)
    check("POST /api/v1/calls", r.status_code == 200 and "id" in r.json())
    call_id = r.json().get("id")

    r = client.get(f"/api/v1/calls/{call_id}", headers=headers)
    check("GET /api/v1/calls/{id}", r.status_code == 200 and r.json().get("id") == call_id)

    r = client.get("/api/v1/calls", headers=headers)
    check("GET /api/v1/calls (History List)", r.status_code == 200 and isinstance(r.json(), list))

    r = client.post(f"/api/v1/calls/{call_id}/consent", json={"consent_given": True}, headers=headers)
    check("POST /api/v1/calls/{id}/consent", r.status_code == 200)

    r = client.post(f"/api/v1/calls/{call_id}/end", headers=headers)
    check("POST /api/v1/calls/{id}/end", r.status_code == 200 and r.json().get("status") == "COMPLETED")

    r = client.get(f"/api/v1/calls/{call_id}/transcript", headers=headers)
    check("GET /api/v1/calls/{id}/transcript", r.status_code == 200 and "raw_text" in r.json())

    r = client.get(f"/api/v1/calls/{call_id}/summary", headers=headers)
    check("GET /api/v1/calls/{id}/summary", r.status_code == 200 and "summary_text" in r.json())

    r = client.post(f"/api/v1/calls/{call_id}/summary/validate", headers=headers)
    check("POST /api/v1/calls/{id}/summary/validate", r.status_code == 200)

    r = client.post(f"/api/v1/calls/{call_id}/summary/edit", json={"new_text": "Résumé édité avec succès."}, headers=headers)
    check("POST /api/v1/calls/{id}/summary/edit", r.status_code == 200)

    r = client.get(f"/api/v1/calls/{call_id}/ai-status", headers=headers)
    check("GET /api/v1/calls/{id}/ai-status", r.status_code == 200 and "ai_status" in r.json())

    # 10. Agenda & Reminders
    r = client.get("/api/v1/agenda", headers=headers)
    check("GET /api/v1/agenda", r.status_code == 200 and isinstance(r.json(), list))

    r = client.post("/api/v1/agenda", json={"id": "agenda_test_prod_1", "title": "Audit Meeting", "scheduled_at": "2026-09-01T10:00:00Z"}, headers=headers)
    check("POST /api/v1/agenda", r.status_code == 200)

    r = client.get("/api/v1/reminders", headers=headers)
    check("GET /api/v1/reminders", r.status_code == 200 and isinstance(r.json(), list))

    # 11. Tasks
    r = client.get("/api/v1/tasks", headers=headers)
    check("GET /api/v1/tasks", r.status_code == 200 and isinstance(r.json(), list))

    r = client.post("/api/v1/tasks", json={"id": "task_test_prod_1", "title": "Audit Task", "completed": False}, headers=headers)
    check("POST /api/v1/tasks", r.status_code == 200)

    r = client.put("/api/v1/tasks/task_test_prod_1", json={"id": "task_test_prod_1", "title": "Audit Task Updated", "completed": True}, headers=headers)
    check("PUT /api/v1/tasks/{id}", r.status_code == 200 and r.json().get("completed") is True)

    # 12. Files
    r = client.get("/api/v1/files", headers=headers)
    check("GET /api/v1/files", r.status_code == 200 and isinstance(r.json(), list))

    r = client.post("/api/v1/files", json={"id": "file_test_prod_1", "name": "audit_doc.pdf", "path": "/uploads/audit_doc.pdf", "size": "250 KB"}, headers=headers)
    check("POST /api/v1/files", r.status_code == 200)

    # 13. GDPR Suite
    r = client.get("/api/v1/me/export", headers=headers)
    check("GET /api/v1/me/export (Art. 15)", r.status_code == 200)

    r = client.get("/api/v1/users/me/voice-data/export", headers=headers)
    check("GET /api/v1/users/me/voice-data/export", r.status_code == 200)

    # 14. Universal Multi-Provider Webhooks
    r = client.post("/webhooks/twilio/voice", data={"CallSid": "audit_tw_prod", "From": "+331", "To": "+332"})
    check("POST /webhooks/twilio/voice (TwiML)", r.status_code == 200 and "<Dial" in r.text)

    r = client.post("/webhooks/vonage/voice", json={"uuid": "audit_vn_prod", "from": "+331", "to": "+332"})
    check("POST /webhooks/vonage/voice (NCCO)", r.status_code == 200 and "connect" in r.text)

    r = client.post("/webhooks/recording-complete", data={"CallSid": "audit_tw_prod", "RecordingUrl": "http://127.0.0.1:8000/uploads/mock.wav"})
    check("POST /webhooks/recording-complete", r.status_code == 200)

    # 15. WebSocket Live Transcript Pub/Sub
    with client.websocket_connect(f"/api/v1/ws/calls/{call_id}/live-transcript?token={token}") as ws:
        init_event = ws.receive_json()
        ws.send_json({"action": "ping"})
        pong_event = ws.receive_json()
        check("WS /api/v1/ws/calls/{id}/live-transcript", init_event.get("type") == "connected" and pong_event.get("type") == "pong")

    print("\n" + "=" * 70)
    print(f"  FINAL SCORE: {tests_passed}/{total_tests} ENDPOINTS PASSED ({tests_passed/total_tests*100:.1f}%)")
    print("=" * 70)

    if tests_passed == total_tests:
        print("[SUCCESS] ALL PRODUCTION ENDPOINTS & LIVE WEBSOCKET SUITES ARE 100% OPERATIONAL!")
        return 0
    else:
        print("[FAIL] Production audit detected failures.")
        return 1

if __name__ == "__main__":
    sys.exit(run_comprehensive_audit())
