from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from ..database import Call, Contact, Transcript, TranscriptEmbedding, CallSummary, get_db
from ..gdpr import export_user_data, delete_user_account, delete_call_data, erase_contact_data
from ..storage import delete_audio_file
from .deps import verify_token

router = APIRouter(tags=["GDPR & Privacy"])

@router.get("/api/v1/users/me/voice-data/export")
def export_voice_data(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """RGPD Art. 15 — Droit d'accès: export de toutes les données de l'utilisateur."""
    return export_user_data(user_id, db)

@router.delete("/api/v1/users/me/voice-data")
def delete_voice_data(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """Deletes all audio, transcripts, and embeddings for the current user (voice-data erasure)."""
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

@router.delete("/api/v1/me")
def delete_account(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """
    RGPD Art. 17 — Droit à l'effacement ("droit à l'oubli").
    Supprime définitivement le compte et toutes les données associées.
    Délai légal: 30 jours. Cette implémentation est immédiate.
    """
    result = delete_user_account(user_id, db)
    return {"status": "deleted", "summary": result}


@router.get("/api/v1/me/export")
def export_my_data(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """
    RGPD Art. 15 — Droit d'accès + Art. 20 — Portabilité.
    Retourne un export JSON complet de toutes les données de l'utilisateur.
    """
    return export_user_data(user_id, db)


@router.delete("/api/v1/calls/{id}/data")
def delete_call_gdpr(id: str, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """RGPD — Supprime un appel et toutes ses données (audio, transcription, résumé)."""
    call = db.query(Call).filter(Call.id == id, Call.user_id == user_id).first()
    if not call:
        raise HTTPException(status_code=404, detail="Appel introuvable")
    result = delete_call_data(id, db)
    return {"status": "deleted", "summary": result}


@router.delete("/api/v1/contacts/{id}/data")
def erase_contact_gdpr(id: str, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """RGPD — Anonymise un contact et supprime tout l'historique d'appels lié."""
    contact = db.query(Contact).filter(Contact.id == id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact introuvable")
    result = erase_contact_data(id, db)
    return {"status": "erased", "summary": result}
