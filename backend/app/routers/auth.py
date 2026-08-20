import os
import uuid
import logging
from typing import Optional
from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException, Header, status
from sqlalchemy.orm import Session
from passlib.hash import bcrypt
from ..database import User, get_db
from .. import schemas
from .deps import verify_token, create_access_token

router = APIRouter(tags=["Auth & Users"])
logger = logging.getLogger("intelligent_calls.auth")

@router.post("/api/v1/auth/register", response_model=schemas.LoginResponse)
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
        number=getattr(request, "number", None) or getattr(request, "phone_number", None) or "+33100000000",
        password_hash=pwd_hash
    )
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    
    token = create_access_token(new_user.id, new_user.email)
    return {"access_token": token, "token_type": "bearer"}

@router.post("/api/v1/auth/login", response_model=schemas.LoginResponse)
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
        if not bcrypt.verify(req.old_password, user.password_hash):
            raise HTTPException(status_code=400, detail="Ancien mot de passe incorrect")
    user.password_hash = bcrypt.hash(req.new_password)
    db.commit()
    return {"message": "Mot de passe mis à jour avec succès"}
