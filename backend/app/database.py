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
    sentiment = Column(String(30), default="NEUTRAL")
    intent = Column(String(100), nullable=True)
    tags = Column(String(255), nullable=True)
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

Task = TaskModel
Agenda = AgendaModel
File = FileModel


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

def _ensure_schema(eng):
    """Automatically adds new columns to existing tables if missing."""
    from sqlalchemy import text
    columns_to_ensure = [
        ("calls", "audio_url", "VARCHAR(500) NULL"),
        ("calls", "ai_status", "VARCHAR(20) DEFAULT 'PENDING'"),
        ("transcripts", "speaker_segments", "TEXT NULL"),
        ("appointments", "title", "VARCHAR(255) NULL"),
        ("call_summaries", "sentiment", "VARCHAR(30) DEFAULT 'NEUTRAL'"),
        ("call_summaries", "intent", "VARCHAR(100) NULL"),
        ("call_summaries", "tags", "VARCHAR(255) NULL"),
    ]
    with eng.connect() as conn:
        for table, col, col_type in columns_to_ensure:
            try:
                if eng.dialect.name == "mysql":
                    res = conn.execute(text(f"SHOW COLUMNS FROM `{table}` LIKE '{col}'")).fetchall()
                    if not res:
                        conn.execute(text(f"ALTER TABLE `{table}` ADD COLUMN `{col}` {col_type}"))
                        conn.commit()
                elif eng.dialect.name == "sqlite":
                    cols = [row[1] for row in conn.execute(text(f"PRAGMA table_info({table})")).fetchall()]
                    if col not in cols:
                        conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {col} {col_type}"))
                        conn.commit()
                elif eng.dialect.name == "postgresql":
                    conn.execute(text(f"ALTER TABLE {table} ADD COLUMN IF NOT EXISTS {col} {col_type}"))
                    conn.commit()
            except Exception as ex:
                pass

def init_db():
    Base.metadata.create_all(bind=engine)
    _ensure_schema(engine)
    
    # In production, do not seed mock data and purge any legacy fake records
    db = SessionLocal()
    try:
        # Clean up any legacy fake contacts, tasks, agenda, files
        fake_contact_ids = ["contact-1111", "contact-2222", "contact-3333"]
        db.query(Contact).filter(Contact.id.in_(fake_contact_ids)).delete(synchronize_session=False)

        fake_task_ids = ["task-uuid-1", "task-uuid-2"]
        db.query(TaskModel).filter(TaskModel.id.in_(fake_task_ids)).delete(synchronize_session=False)

        fake_agenda_ids = ["agenda-uuid-1", "agenda-uuid-2"]
        db.query(AgendaModel).filter(AgendaModel.id.in_(fake_agenda_ids)).delete(synchronize_session=False)

        fake_file_ids = ["file-uuid-1", "file-uuid-2"]
        db.query(FileModel).filter(FileModel.id.in_(fake_file_ids)).delete(synchronize_session=False)

        # Clean up legacy mock calls if present
        try:
            from .gdpr import delete_call_data
            for legacy_id in ["call-uuid-1111", "call-uuid-2222"]:
                call = db.query(Call).filter(Call.id == legacy_id).first()
                if call:
                    delete_call_data(legacy_id, db)
        except Exception:
            pass

        db.commit()
    except Exception as e:
        db.rollback()
    finally:
        db.close()

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
