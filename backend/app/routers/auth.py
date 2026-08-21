import os
import uuid
import logging
import bcrypt
from typing import Optional
from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException, Header, status
from sqlalchemy.orm import Session
from ..database import User, get_db
from .. import schemas
from .deps import verify_token, create_access_token

router = APIRouter(tags=["Auth & Users"])
logger = logging.getLogger("intelligent_calls.auth")

def hash_password(password: str) -> str:
    """Hashes a password securely using direct native bcrypt."""
    salt = bcrypt.gensalt()
    return bcrypt.hashpw(password.encode("utf-8"), salt).decode("utf-8")

def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verifies a plaintext password against a stored bcrypt hash."""
    if not plain_password or not hashed_password:
        return False
    try:
        return bcrypt.checkpw(plain_password.encode("utf-8"), hashed_password.encode("utf-8"))
    except Exception:
        try:
            from passlib.hash import bcrypt as pb_bcrypt
            return pb_bcrypt.verify(plain_password, hashed_password)
        except Exception:
            return False

@router.post("/api/v1/auth/register", response_model=schemas.LoginResponse)
def register(request: schemas.RegisterRequest, db: Session = Depends(get_db)):
    if not request.email or not request.password:
        raise HTTPException(status_code=400, detail="Missing email or password")
    
    clean_email = request.email.strip().lower()
    try:
        existing = db.query(User).filter(User.email.ilike(clean_email)).first()
        if existing:
            # If user already exists, update credentials and seamlessly log in
            existing.password_hash = hash_password(request.password)
            if request.first_name:
                existing.first_name = request.first_name
            if request.last_name:
                existing.last_name = request.last_name
            if hasattr(request, "number") and request.number:
                existing.number = request.number
            db.commit()
            db.refresh(existing)
            token = create_access_token(existing.id, existing.email)
            return {"access_token": token, "token_type": "bearer"}
        
        pwd_hash = hash_password(request.password)
        new_user = User(
            id=str(uuid.uuid4()),
            first_name=request.first_name or "User",
            last_name=request.last_name or "",
            email=clean_email,
            number=getattr(request, "number", None) or getattr(request, "phone_number", None) or "+33100000000",
            password_hash=pwd_hash
        )
        db.add(new_user)
        db.commit()
        db.refresh(new_user)
        
        token = create_access_token(new_user.id, new_user.email)
        return {"access_token": token, "token_type": "bearer"}
    except HTTPException:
        db.rollback()
        raise
    except Exception as e:
        db.rollback()
        logger.exception(f"Error during registration for {clean_email}: {e}")
        raise HTTPException(status_code=500, detail=f"Registration failed: {str(e)}")

@router.post("/api/v1/auth/login", response_model=schemas.LoginResponse)
def login(request: schemas.LoginRequest, db: Session = Depends(get_db)):
    if not request.email or not request.password:
        raise HTTPException(status_code=400, detail="Missing email or password")
    
    clean_email = request.email.strip().lower()
    user = db.query(User).filter(User.email.ilike(clean_email)).first()
    if not user:
        raise HTTPException(status_code=401, detail="User not found. Please sign up first.")
    
    if not user.password_hash or not verify_password(request.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid email or password")

    token = create_access_token(user.id, user.email)
    return {"access_token": token, "token_type": "bearer"}

@router.post("/api/v1/auth/refresh", response_model=schemas.LoginResponse)
def refresh(authorization: Optional[str] = Header(None)):
    user_id = verify_token(authorization)
    token = create_access_token(user_id, "user@example.com")
    return {"access_token": token, "token_type": "bearer"}

@router.get("/api/v1/voip/token", response_model=schemas.TokenResponse)
def get_voip_token(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    account_sid = os.getenv("TWILIO_ACCOUNT_SID")
    auth_token = os.getenv("TWILIO_AUTH_TOKEN")
    twiml_app_sid = os.getenv("TWILIO_TWIML_APP_SID")
    api_key = os.getenv("TWILIO_API_KEY") or account_sid
    api_secret = os.getenv("TWILIO_API_SECRET") or auth_token

    if not (account_sid and auth_token and twiml_app_sid):
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="VoIP provider credentials not configured on server. Please configure TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, and TWILIO_TWIML_APP_SID."
        )

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
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to generate VoIP access token: {str(e)}"
        )

@router.get("/api/v1/users/me")
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
        "created_at": user.created_at.isoformat() + "Z" if getattr(user, "created_at", None) else datetime.utcnow().isoformat() + "Z"
    }

@router.put("/api/v1/users/me")
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

@router.put("/api/v1/users/me/password")
def change_password(req: schemas.PasswordChangeRequest, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        user = db.query(User).first()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")
    if user.password_hash:
        if not verify_password(req.old_password, user.password_hash):
            raise HTTPException(status_code=400, detail="Ancien mot de passe incorrect")
    user.password_hash = hash_password(req.new_password)
    db.commit()
    return {"message": "Mot de passe mis à jour avec succès"}
