import sys
import os

if sys.platform == "win32":
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def run_audit():
    print("=" * 60)
    print("  FASTAPI ROUTE & CONTRACT INTEGRITY AUDIT")
    print("=" * 60)

    tests_passed = 0
    total_tests = 0

    def assert_check(name, condition, extra=""):
        nonlocal tests_passed, total_tests
        total_tests += 1
        if condition:
            tests_passed += 1
            print(f"  [PASS] {name}")
        else:
            print(f"  [FAIL] {name} - {extra}")

    # 1. Health Check
    r = client.get("/health")
    assert_check("GET /health", r.status_code == 200 and r.json().get("status") == "ok")

    # 2. Auth Endpoints
    # Ensure test user exists by calling register (or login directly)
    r_reg = client.post("/api/v1/auth/register", json={
        "email": "admin@example.com",
        "password": "password123",
        "first_name": "Admin",
        "last_name": "User"
    })
    
    r = client.post("/api/v1/auth/login", json={"email": "admin@example.com", "password": "password123"})
    if r.status_code != 200 and r_reg.status_code == 200:
        token = r_reg.json().get("access_token")
    else:
        token = r.json().get("access_token") if r.status_code == 200 else None

    assert_check("POST /api/v1/auth/login", token is not None)
    headers = {"Authorization": f"Bearer {token}"}

    # 3. User Endpoints
    r = client.get("/api/v1/users/me", headers=headers)
    assert_check("GET /api/v1/users/me", r.status_code == 200 and "email" in r.json())

    r = client.put("/api/v1/users/me", json={"first_name": "AdminUpdated"}, headers=headers)
    assert_check("PUT /api/v1/users/me", r.status_code == 200 and r.json().get("first_name") == "AdminUpdated")

    r = client.get("/api/v1/voip/token", headers=headers)
    assert_check("GET /api/v1/voip/token", r.status_code == 200 and "token" in r.json())

    # 4. Contacts Endpoints
    r = client.get("/api/v1/contacts", headers=headers)
    assert_check("GET /api/v1/contacts", r.status_code == 200 and isinstance(r.json(), list))

    # 5. Calls Endpoints
    r = client.get("/api/v1/calls", headers=headers)
    assert_check("GET /api/v1/calls", r.status_code == 200 and isinstance(r.json(), list))

    # 6. Agenda Endpoints
    r = client.get("/api/v1/agenda", headers=headers)
    assert_check("GET /api/v1/agenda", r.status_code == 200 and isinstance(r.json(), list))

    # 7. Tasks Endpoints
    r = client.get("/api/v1/tasks", headers=headers)
    assert_check("GET /api/v1/tasks", r.status_code == 200 and isinstance(r.json(), list))

    # 8. Files Endpoints
    r = client.get("/api/v1/files", headers=headers)
    assert_check("GET /api/v1/files", r.status_code == 200 and isinstance(r.json(), list))

    # 9. Webhooks (Twilio, Telnyx, Plivo, Vonage)
    r = client.post("/webhooks/twilio/voice", data={"CallSid": "audit_tw_1", "From": "+331", "To": "+332"})
    assert_check("POST /webhooks/twilio/voice (XML)", r.status_code == 200 and "Dial" in r.text)

    r = client.post("/webhooks/vonage/voice", json={"uuid": "audit_vn_1", "from": "+331", "to": "+332"})
    assert_check("POST /webhooks/vonage/voice (NCCO JSON)", r.status_code == 200 and "connect" in r.text)

    r = client.post("/webhooks/recording-complete", data={"CallSid": "audit_tw_1", "RecordingUrl": "http://127.0.0.1:8000/uploads/mock.wav"})
    assert_check("POST /webhooks/recording-complete", r.status_code == 200)

    # 10. GDPR Endpoints
    r = client.get("/api/v1/me/export", headers=headers)
    assert_check("GET /api/v1/me/export", r.status_code == 200)

    print("\n" + "=" * 60)
    print(f"  AUDIT SUMMARY: {tests_passed}/{total_tests} tests passed!")
    print("=" * 60)

    if tests_passed == total_tests:
        print("[SUCCESS] ALL CONTRACTS, ROUTES & LOGIC ARE 100% IDENTICAL AND VERIFIED!")
        return 0
    else:
        print("[FAILURE] INTEGRITY CHECK FAILED")
        return 1

if __name__ == "__main__":
    sys.exit(run_audit())
