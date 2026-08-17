from pydantic import BaseModel
from typing import Optional, List, Dict

class RegisterRequest(BaseModel):
    first_name: str
    last_name: str
    email: str
    password: str
    number: Optional[str] = None

class LoginRequest(BaseModel):
    email: str
    password: str

class LoginResponse(BaseModel):
    access_token: str
    token_type: str

class TokenResponse(BaseModel):
    token: str

class CallRequest(BaseModel):
    contact_id: str
    direction: str = "OUTBOUND"

class CallResponse(BaseModel):
    id: str
    contact_id: str
    direction: str
    status: str
    twilio_params: Optional[Dict[str, str]] = None

class ConsentRequest(BaseModel):
    consent_given: bool

class ContactDto(BaseModel):
    id: str
    first_name: str
    last_name: str
    phone_number: str
    email: str
    global_gdpr_consent: bool

    class Config:
        from_attributes = True

class AppointmentDto(BaseModel):
    id: str
    contact_id: str
    scheduled_at: str
    status: str
    title: Optional[str] = None
    summary_context: Optional[str] = None
    phone_number: Optional[str] = None
    contact_name: Optional[str] = None

class CallSummaryDto(BaseModel):
    id: str
    call_id: str
    summary_text: str
    status: str
    confidence_score: Optional[float] = None
    detected_appointment_id: Optional[str] = None
    appointment: Optional[AppointmentDto] = None

class SummaryEditRequest(BaseModel):
    new_text: Optional[str] = None
    voice_command_transcript: Optional[str] = None

class CallHistoryItemDto(BaseModel):
    id: str
    contact_id: str
    direction: str
    status: str
    started_at: Optional[str] = None
    ended_at: Optional[str] = None
    contact_name: Optional[str] = None
    phone_number: Optional[str] = None
    summary_preview: Optional[str] = None

class ReminderDto(BaseModel):
    id: str
    appointment_id: Optional[str] = None
    call_id: Optional[str] = None
    scheduled_at: str
    type: str

class TaskDto(BaseModel):
    id: str
    title: str
    completed: bool

class AgendaDto(BaseModel):
    id: str
    title: str
    scheduled_at: str
    contact_name: Optional[str] = None
    phone_number: Optional[str] = None
    call_id: Optional[str] = None
    status: Optional[str] = "SCHEDULED"

class FileDto(BaseModel):
    id: str
    name: str
    path: str
    size: str

class CallInitiateRequest(BaseModel):
    contact_id: str
    direction: str = "OUTBOUND"

class ChatRequest(BaseModel):
    message: str
    session_id: Optional[str] = None  # If provided, continues existing session

class ChatSourceDto(BaseModel):
    call_id: Optional[str] = None
    call_date: Optional[str] = None
    excerpt: Optional[str] = None

class ChatResponse(BaseModel):
    session_id: str
    reply: str
    sources: List[ChatSourceDto] = []
