import sys, io, json
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
from app.database import SessionLocal, Call, CallSummary, Transcript

db = SessionLocal()
calls = db.query(Call).order_by(Call.started_at.desc()).limit(10).all()
print(f"Total calls inspected: {len(calls)}")
for c in calls:
    cs = db.query(CallSummary).filter(CallSummary.call_id == c.id).first()
    tr = db.query(Transcript).filter(Transcript.call_id == c.id).first()
    print("=" * 60)
    print(f"CALL: {c.id} | Started: {c.started_at} | Audio: {c.audio_url} | AI: {c.ai_status}")
    if tr:
        print(f"  TRANSCRIPT: raw_len={len(tr.raw_text or '')} | raw=\"{tr.raw_text}\"")
        print(f"  SEGMENTS: {tr.speaker_segments}")
    else:
        print("  TRANSCRIPT: None")
    if cs:
        print(f"  SUMMARY: \"{cs.summary_text}\"")
        print(f"  SENTIMENT: {cs.sentiment} | INTENT: {cs.intent} | TAGS: {cs.tags}")
    else:
        print("  SUMMARY: None")
db.close()
