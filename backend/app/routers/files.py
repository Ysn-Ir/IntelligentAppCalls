from typing import List
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from ..database import File as FileModel, get_db
from .. import schemas
from .deps import verify_token

router = APIRouter(tags=["Files & Audio Vault"])

@router.get("/api/v1/files", response_model=List[schemas.FileDto])
def get_files_list(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    files = db.query(FileModel).filter((FileModel.user_id == user_id) | (FileModel.user_id.is_(None))).all()
    return [schemas.FileDto(id=f.id, name=f.name, path=f.path, size=f.size) for f in files]

@router.post("/api/v1/files", response_model=schemas.FileDto)
def create_file_item(file: schemas.FileDto, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    existing = db.query(FileModel).filter(FileModel.id == file.id).first()
    if existing:
        existing.name = file.name
        existing.path = file.path
        existing.size = file.size
        existing.user_id = user_id
        db.commit()
        db.refresh(existing)
        return schemas.FileDto(id=existing.id, name=existing.name, path=existing.path, size=existing.size)

    new_f = FileModel(id=file.id, user_id=user_id, name=file.name, path=file.path, size=file.size)
    db.add(new_f)
    db.commit()
    db.refresh(new_f)
    return schemas.FileDto(id=new_f.id, name=new_f.name, path=new_f.path, size=new_f.size)
