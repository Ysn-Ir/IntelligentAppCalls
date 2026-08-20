import uuid
from datetime import datetime, timedelta
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from ..database import Appointment, Agenda as AgendaModel, Contact, CallSummary, Reminder, get_db
from .. import schemas
from .deps import verify_token

router = APIRouter(tags=["Agenda & Reminders"])

@router.get("/api/v1/agenda", response_model=List[schemas.AgendaDto])
def get_agenda(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    results = []
    seen_ids = set()

    # 1. Appointments detected from calls
    appts = db.query(Appointment).all()
    for a in appts:
        c_name = None
        p_num = None
        contact_obj = db.query(Contact).filter(Contact.id == a.contact_id).first() if a.contact_id else None
        if contact_obj:
            c_name = f"{contact_obj.first_name} {contact_obj.last_name}".strip()
            p_num = contact_obj.phone_number

        summary_obj = db.query(CallSummary).filter(CallSummary.detected_appointment_id == a.id).first()
        call_id_val = summary_obj.call_id if summary_obj else None

        results.append(schemas.AgendaDto(
            id=a.id,
            title=a.title or f"Rendez-vous avec {c_name or 'Contact'}",
            scheduled_at=a.scheduled_at.isoformat() + "Z" if a.scheduled_at else datetime.utcnow().isoformat() + "Z",
            contact_name=c_name,
            phone_number=p_num,
            call_id=call_id_val,
            status=a.status or "SCHEDULED"
        ))
        seen_ids.add(a.id)

    # 2. Agenda backend items
    items = db.query(AgendaModel).filter((AgendaModel.user_id == user_id) | (AgendaModel.user_id.is_(None))).all()
    for i in items:
        if i.id not in seen_ids:
            results.append(schemas.AgendaDto(
                id=i.id,
                title=i.title,
                scheduled_at=i.scheduled_at.isoformat() + "Z" if i.scheduled_at else datetime.utcnow().isoformat() + "Z",
                status="SCHEDULED"
            ))
            seen_ids.add(i.id)

    return results

@router.post("/api/v1/agenda", response_model=schemas.AgendaDto)
def create_agenda_item(item: schemas.AgendaDto, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    try:
        dt = datetime.fromisoformat(item.scheduled_at.replace("Z", ""))
    except ValueError:
        dt = datetime.utcnow() + timedelta(days=1)

    existing = db.query(AgendaModel).filter(AgendaModel.id == item.id).first()
    if existing:
        existing.title = item.title
        existing.scheduled_at = dt
        existing.user_id = user_id
        db.commit()
        db.refresh(existing)
        return schemas.AgendaDto(id=existing.id, title=existing.title, scheduled_at=existing.scheduled_at.isoformat() + "Z")

    new_i = AgendaModel(id=item.id, user_id=user_id, title=item.title, scheduled_at=dt)
    db.add(new_i)
    db.commit()
    db.refresh(new_i)
    return schemas.AgendaDto(id=new_i.id, title=new_i.title, scheduled_at=new_i.scheduled_at.isoformat() + "Z")

@router.get("/api/v1/reminders", response_model=List[schemas.ReminderDto])
def get_reminders(upcoming: bool = Query(True), token: str = Depends(verify_token), db: Session = Depends(get_db)):
    query = db.query(Reminder)
    if upcoming:
        query = query.filter(Reminder.scheduled_at > datetime.utcnow())
    reminders = query.all()
    
    res = []
    for r in reminders:
        res.append(schemas.ReminderDto(
            id=r.id,
            appointment_id=r.appointment_id,
            call_id=r.call_id,
            scheduled_at=r.scheduled_at.isoformat() + "Z" if r.scheduled_at else None,
            type=r.type
        ))
    return res

@router.post("/api/v1/reminders")
def create_reminder(reminder: schemas.ReminderDto, token: str = Depends(verify_token), db: Session = Depends(get_db)):
    try:
        dt = datetime.fromisoformat(reminder.scheduled_at.replace("Z", "")) if reminder.scheduled_at else datetime.utcnow()
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid date format")
        
    new_r = Reminder(
        id=str(uuid.uuid4()),
        appointment_id=reminder.appointment_id,
        call_id=reminder.call_id,
        scheduled_at=dt,
        type=reminder.type
    )
    db.add(new_r)
    db.commit()
    return {"status": "ok", "reminder_id": new_r.id}
