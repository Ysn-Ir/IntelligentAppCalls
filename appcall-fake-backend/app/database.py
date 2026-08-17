import os
import uuid
from datetime import datetime, timedelta
from sqlalchemy import create_engine, Column, String, DateTime, Boolean, ForeignKey, Text, Float, Integer, JSON
from sqlalchemy.orm import declarative_base, sessionmaker, relationship

# pgvector support (optional — falls back gracefully if not installed)
try:
    from pgvector.sqlalchemy import Vector
    PGVECTOR_AVAILABLE = True
except ImportError:
    PGVECTOR_AVAILABLE = False
    Vector = None

DATABASE_URL = os.getenv("DATABASE_URL", "mysql+pymysql://root:@localhost:3306/appcall_db")

connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}
engine = create_engine(DATABASE_URL, connect_args=connect_args)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

class User(Base):
    __tablename__ = "users"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    first_name = Column(String(50))
    last_name = Column(String(50))
    email = Column(String(100), unique=True, index=True)
    number = Column(String(20))
    password_hash = Column(String(255), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

class Contact(Base):
    __tablename__ = "contacts"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id"), nullable=True)
    first_name = Column(String(50))
    last_name = Column(String(50))
    phone_number = Column(String(20))
    email = Column(String(100))
    global_gdpr_consent = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)

class Call(Base):
    __tablename__ = "calls"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    contact_id = Column(String(36), ForeignKey("contacts.id"))
    user_id = Column(String(36), ForeignKey("users.id"))
    direction = Column(String(10))  # INBOUND, OUTBOUND
    started_at = Column(DateTime, default=datetime.utcnow)
    ended_at = Column(DateTime, nullable=True)
    status = Column(String(15))  # COMPLETED, MISSED, FAILED, ONGOING
    consent_given = Column(Boolean, default=False)
    consent_timestamp = Column(DateTime, nullable=True)
    twilio_params = Column(Text, nullable=True)
    audio_url = Column(String(512), nullable=True)  # S3/MinIO URL after upload
    ai_status = Column(String(20), default="PENDING")  # PENDING, PROCESSING, DONE, FAILED
    created_at = Column(DateTime, default=datetime.utcnow)

    contact = relationship("Contact")

class Transcript(Base):
    __tablename__ = "transcripts"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    call_id = Column(String(36), ForeignKey("calls.id"), unique=True)
    raw_text = Column(Text, default="")
    language = Column(String(10), default="fr")
    confidence_score = Column(Float, default=0.0)
    speaker_segments = Column(Text, nullable=True)  # JSON array of {speaker, start, end, text}
    created_at = Column(DateTime, default=datetime.utcnow)

class CallSummary(Base):
    __tablename__ = "call_summaries"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    call_id = Column(String(36), ForeignKey("calls.id"), unique=True)
    summary_text = Column(Text)
    detected_appointment_id = Column(String(36), ForeignKey("appointments.id"), nullable=True)
    status = Column(String(15), default="PROPOSED")
    modified_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    appointment = relationship("Appointment", foreign_keys=[detected_appointment_id])

class Appointment(Base):
    __tablename__ = "appointments"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    contact_id = Column(String(36), ForeignKey("contacts.id"))
    user_id = Column(String(36), ForeignKey("users.id"))
    scheduled_at = Column(DateTime)
    status = Column(String(15), default="SCHEDULED")
    title = Column(String(255))
    created_at = Column(DateTime, default=datetime.utcnow)

class Reminder(Base):
    __tablename__ = "reminders"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    appointment_id = Column(String(36), ForeignKey("appointments.id"), nullable=True)
    call_id = Column(String(36), ForeignKey("calls.id"), nullable=True)
    scheduled_at = Column(DateTime)
    type = Column(String(20))
    created_at = Column(DateTime, default=datetime.utcnow)

class TaskModel(Base):
    __tablename__ = "tasks_backend"
    id = Column(String(36), primary_key=True)
    user_id = Column(String(36), ForeignKey("users.id"), nullable=True)
    title = Column(String(255))
    completed = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)

class AgendaModel(Base):
    __tablename__ = "agenda_backend"
    id = Column(String(36), primary_key=True)
    user_id = Column(String(36), ForeignKey("users.id"), nullable=True)
    title = Column(String(255))
    scheduled_at = Column(DateTime)
    created_at = Column(DateTime, default=datetime.utcnow)

class FileModel(Base):
    __tablename__ = "files_backend"
    id = Column(String(36), primary_key=True)
    user_id = Column(String(36), ForeignKey("users.id"), nullable=True)
    name = Column(String(255))
    path = Column(String(512))
    size = Column(String(50))
    created_at = Column(DateTime, default=datetime.utcnow)


class TranscriptEmbedding(Base):
    """
    Stores vector embeddings of transcript chunks for RAG chatbot.
    Uses pgvector if available, otherwise stores as JSON text.
    """
    __tablename__ = "transcript_embeddings"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    transcript_id = Column(String(36), ForeignKey("transcripts.id"), nullable=False)
    contact_id = Column(String(36), ForeignKey("contacts.id"), nullable=True)
    chunk_text = Column(Text, nullable=False)
    # Store embedding as Text (JSON array) for SQLite/MySQL compat.
    # On PostgreSQL with pgvector, this can be cast to vector(1536).
    embedding = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    transcript = relationship("Transcript")


class ChatbotSession(Base):
    """
    Stores conversation history for the contact-based AI chatbot.
    One session per (user, contact) pair — or (user, None) for global chat.
    """
    __tablename__ = "chatbot_sessions"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id"), nullable=False)
    contact_id = Column(String(36), ForeignKey("contacts.id"), nullable=True)  # NULL = global
    messages = Column(Text, default="[]")  # JSON array of {role, content}
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

def init_db():
    Base.metadata.create_all(bind=engine)
    
    db = SessionLocal()
    try:
        # Seed 1 fake user if empty
        if db.query(User).count() == 0:
            from passlib.hash import bcrypt
            user = User(
                id="test-user-uuid-1111",
                first_name="Admin",
                last_name="AppCall",
                email="test@example.com",
                number="+33100000000",
                password_hash=bcrypt.hash("password")
            )
            db.add(user)
            db.commit()
            print("Seeded user test@example.com with password 'password'")

        # Seed 3 fake contacts if empty
        if db.query(Contact).count() == 0:
            contacts = [
                Contact(id="contact-1111", first_name="Jean", last_name="Dupont", phone_number="+33612345678", email="jean.dupont@example.com", global_gdpr_consent=True),
                Contact(id="contact-2222", first_name="Marie", last_name="Martin", phone_number="+33687654321", email="marie.martin@example.com", global_gdpr_consent=True),
                Contact(id="contact-3333", first_name="Ahmed", last_name="Bensaid", phone_number="+33699887766", email="ahmed.bensaid@example.com", global_gdpr_consent=False)
            ]
            db.add_all(contacts)
            db.commit()
            print("Seeded 3 fake contacts")

        # Seed initial fake calls if empty
        if db.query(Call).count() == 0:
            user_id = "test-user-uuid-1111"
            
            # Call 1 (Completed with Summary & Appointment)
            call1 = Call(
                id="call-uuid-1111",
                contact_id="contact-1111",
                user_id=user_id,
                direction="OUTBOUND",
                started_at=datetime.utcnow() - timedelta(hours=24),
                ended_at=datetime.utcnow() - timedelta(hours=23, minutes=55),
                status="COMPLETED",
                consent_given=True,
                consent_timestamp=datetime.utcnow() - timedelta(hours=24)
            )
            
            # Call 2 (Missed Call)
            call2 = Call(
                id="call-uuid-2222",
                contact_id="contact-2222",
                user_id=user_id,
                direction="INBOUND",
                started_at=datetime.utcnow() - timedelta(hours=5),
                status="MISSED",
                consent_given=False
            )
            
            db.add_all([call1, call2])
            db.commit()
            print("Seeded call records")

            # Seed Transcript for Call 1
            transcript = Transcript(
                id="trans-uuid-1111",
                call_id="call-uuid-1111",
                raw_text="Bonjour Jean, c'est Marc. Je t'appelle pour confirmer notre rendez-vous. Je te propose mardi prochain à 14h dans vos bureaux. Parfait, c'est noté. Bonne journée !",
                language="fr",
                confidence_score=94.5
            )
            db.add(transcript)
            db.commit()
            print("Seeded transcript records")

            # Seed Appointment for Call 1
            appt = Appointment(
                id="appt-uuid-1111",
                contact_id="contact-1111",
                user_id=user_id,
                scheduled_at=datetime.utcnow() + timedelta(days=4, hours=2),
                status="PROPOSED",
                title="Point commercial"
            )
            db.add(appt)
            db.commit()
            print("Seeded appointment records")

            # Seed Summary for Call 1
            summary = CallSummary(
                id="sum-uuid-1111",
                call_id="call-uuid-1111",
                summary_text="Marc a proposé un rendez-vous mardi prochain à 14h dans les bureaux. Jean a accepté.",
                detected_appointment_id="appt-uuid-1111",
                status="PROPOSED",
                modified_count=0
            )
            db.add(summary)
            db.commit()
            print("Seeded summary records")

            # Seed Reminder for Call 1 Appointment
            reminder = Reminder(
                id="rem-uuid-1111",
                appointment_id="appt-uuid-1111",
                call_id="call-uuid-1111",
                scheduled_at=appt.scheduled_at - timedelta(hours=1),
                type="APPOINTMENT"
            )
            db.add(reminder)
            db.commit()
            print("Seeded reminder records")

            # Seed Tasks
            tasks = [
                TaskModel(id="task-uuid-1", title="Appeler le client pour validation", completed=False),
                TaskModel(id="task-uuid-2", title="Préparer la présentation commerciale", completed=True)
            ]
            db.add_all(tasks)
            db.commit()
            print("Seeded tasks records")

            # Seed Agenda Calendar
            agenda_items = [
                AgendaModel(id="agenda-uuid-1", title="Réunion d'équipe hebdomadaire", scheduled_at=datetime.utcnow() + timedelta(days=1)),
                AgendaModel(id="agenda-uuid-2", title="Point d'avancement technique", scheduled_at=datetime.utcnow() + timedelta(days=2))
            ]
            db.add_all(agenda_items)
            db.commit()
            print("Seeded agenda records")

            # Seed Files Browser
            files = [
                FileModel(id="file-uuid-1", name="contrat_client.pdf", path="/documents/contrat_client.pdf", size="1.2 MB"),
                FileModel(id="file-uuid-2", name="presentation_commerciale.pptx", path="/documents/presentation_commerciale.pptx", size="4.5 MB")
            ]
            db.add_all(files)
            db.commit()
            print("Seeded files records")
    finally:
        db.close()
