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
    """Deletes all calls, audio, transcripts, summaries, embeddings, and chatbot sessions for the current user."""
    from ..database import ChatbotSession
    calls = db.query(Call).filter((Call.user_id == user_id) | (Call.user_id.is_(None))).all()
    deleted_count = 0
    for call in calls:
        delete_call_data(call.id, db)
        deleted_count += 1

    # Also wipe AI chatbot sessions so past discussed summaries are purged
    db.query(ChatbotSession).filter(
        (ChatbotSession.user_id == user_id) | (ChatbotSession.user_id.is_(None))
    ).delete(synchronize_session=False)

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
    call = db.query(Call).filter(
        (Call.id == id) & ((Call.user_id == user_id) | (Call.user_id.is_(None)))
    ).first()
    if not call:
        call = db.query(Call).filter(Call.id.like(f"%{id}%")).first()
    if not call:
        return {"status": "already_deleted", "summary": {"call_id": id}}
    result = delete_call_data(call.id, db)
    return {"status": "deleted", "summary": result}


@router.delete("/api/v1/contacts/{id}/data")
def erase_contact_gdpr(id: str, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    """RGPD — Anonymise un contact et supprime tout l'historique d'appels lié."""
    contact = db.query(Contact).filter(Contact.id == id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact introuvable")
    result = erase_contact_data(id, db)
    return {"status": "erased", "summary": result}
