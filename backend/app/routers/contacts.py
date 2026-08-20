import uuid
from typing import List
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from ..database import Contact, get_db
from .. import schemas
from .deps import verify_token

router = APIRouter(tags=["Contacts"])

@router.get("/api/v1/contacts", response_model=List[schemas.ContactDto])
def get_contacts(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    return db.query(Contact).all()

@router.post("/api/v1/contacts", response_model=schemas.ContactDto)
def create_contact(contact: schemas.ContactDto, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    existing = db.query(Contact).filter(Contact.phone_number == contact.phone_number).first()
    if existing:
        raise HTTPException(status_code=400, detail="Phone number already exists")
    
    new_c = Contact(
        id=str(uuid.uuid4()),
        first_name=contact.first_name,
        last_name=contact.last_name,
        phone_number=contact.phone_number,
        email=contact.email,
        global_gdpr_consent=contact.global_gdpr_consent
    )
    db.add(new_c)
    db.commit()
    db.refresh(new_c)
    return new_c

@router.patch("/api/v1/contacts/{id}/gdpr-consent")
def patch_contact_consent(id: str, payload: schemas.ConsentRequest, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    contact = db.query(Contact).filter(Contact.id == id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    contact.global_gdpr_consent = payload.consent_given
    db.commit()
    return {"status": "ok"}
