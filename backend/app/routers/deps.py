import os
import jwt
import logging
from datetime import datetime, timedelta
from typing import Optional
from fastapi import Header, HTTPException, status, Depends
from sqlalchemy.orm import Session
from ..database import get_db, SessionLocal, User

logger = logging.getLogger("intelligent_calls.deps")

# Minimum 32-byte secret key for RFC 7518 HMAC-SHA256 compliance
JWT_SECRET = os.getenv("JWT_SECRET", "appcall_secret_jwt_key_2026_production_secure_32bytes")
JWT_ALGORITHM = "HS256"

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPLOAD_DIR = os.path.join(BASE_DIR, "uploads")
os.makedirs(UPLOAD_DIR, exist_ok=True)

def create_access_token(user_id: str, email: str) -> str:
    """Generates a secure cryptographically signed JWT access token."""
    payload = {
        "sub": user_id,
        "email": email,
        "exp": datetime.utcnow() + timedelta(days=30),
        "iat": datetime.utcnow()
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)

def verify_token(authorization: Optional[str] = Header(None)) -> str:
    """
    Strict Authorization Bearer token validator.
    Decodes the JWT, validates claims & expiration, and verifies user exists in database.
    """
    if not authorization:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing Authorization Header"
        )
    
    parts = authorization.split(" ")
    if len(parts) != 2 or parts[0].lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid Authorization Header Format. Expected 'Bearer <token>'"
        )
    
    token = parts[1]

    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        user_id = payload.get("sub")
        if not user_id:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid token payload (missing subject)"
            )
        
        db = SessionLocal()
        try:
            user = db.query(User).filter(User.id == user_id).first()
            if not user:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="User account no longer exists"
                )
        finally:
            db.close()

        return user_id
    except HTTPException:
        raise
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token has expired. Please log in again."
        )
    except Exception as e:
        logger.warning(f"Token verification error: {e}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or malformed authentication token"
        )
