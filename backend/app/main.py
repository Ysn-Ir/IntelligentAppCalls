import os
import logging
import bcrypt

# Fix passlib compatibility with bcrypt >= 4.0.0 on Python 3.12
if not hasattr(bcrypt, "__about__"):
    class _BcryptAbout:
        __version__ = getattr(bcrypt, "__version__", "4.0.1")
    bcrypt.__about__ = _BcryptAbout()

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from .database import init_db
from .routers.deps import UPLOAD_DIR
from .routers import (
    auth_router,
    contacts_router,
    calls_router,
    agenda_router,
    tasks_router,
    files_router,
    assistant_router,
    gdpr_router,
    webhooks_router,
    websocket_router
)

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("intelligent_calls.main")

# FastAPI App
app = FastAPI(
    title="Intelligent Calls API",
    description="Multi-Provider VoIP, RAG AI Assistant, Real-time Whisper Transcription & GDPR Suite",
    version="2.0.0"
)

# CORS Configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Static files for audio storage & vault
app.mount("/uploads", StaticFiles(directory=UPLOAD_DIR), name="uploads")

# Initialize and seed database on startup
@app.on_event("startup")
def on_startup():
    logger.info("Initializing database...")
    init_db()
    logger.info("Database initialized successfully.")

# Health check
@app.get("/health")
def health_check():
    return {"status": "ok", "version": "2.0.0", "engine": "Universal Multi-Provider VoIP & RAG AI"}

# Link Modular Routers
app.include_router(auth_router)
app.include_router(contacts_router)
app.include_router(calls_router)
app.include_router(agenda_router)
app.include_router(tasks_router)
app.include_router(files_router)
app.include_router(assistant_router)
app.include_router(gdpr_router)
app.include_router(webhooks_router)
app.include_router(websocket_router)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
