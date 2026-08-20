from typing import List
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from ..database import Task as TaskModel, get_db
from .. import schemas
from .deps import verify_token

router = APIRouter(tags=["Tasks"])

@router.get("/api/v1/tasks", response_model=List[schemas.TaskDto])
def get_tasks(user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    tasks = db.query(TaskModel).filter((TaskModel.user_id == user_id) | (TaskModel.user_id.is_(None))).all()
    return [schemas.TaskDto(id=t.id, title=t.title, completed=t.completed) for t in tasks]

@router.post("/api/v1/tasks", response_model=schemas.TaskDto)
def create_task(task: schemas.TaskDto, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    existing = db.query(TaskModel).filter(TaskModel.id == task.id).first()
    if existing:
        existing.title = task.title
        existing.completed = task.completed
        existing.user_id = user_id
        db.commit()
        db.refresh(existing)
        return schemas.TaskDto(id=existing.id, title=existing.title, completed=existing.completed)

    new_t = TaskModel(id=task.id, user_id=user_id, title=task.title, completed=task.completed)
    db.add(new_t)
    db.commit()
    db.refresh(new_t)
    return schemas.TaskDto(id=new_t.id, title=new_t.title, completed=new_t.completed)

@router.put("/api/v1/tasks/{id}", response_model=schemas.TaskDto)
def update_task(id: str, task: schemas.TaskDto, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    existing = db.query(TaskModel).filter(TaskModel.id == id).first()
    if not existing:
        raise HTTPException(status_code=404, detail="Task not found")
    existing.title = task.title
    existing.completed = task.completed
    existing.user_id = user_id
    db.commit()
    db.refresh(existing)
    return schemas.TaskDto(id=existing.id, title=existing.title, completed=existing.completed)

@router.delete("/api/v1/tasks/{id}")
def delete_task(id: str, user_id: str = Depends(verify_token), db: Session = Depends(get_db)):
    existing = db.query(TaskModel).filter(TaskModel.id == id).first()
    if not existing:
        raise HTTPException(status_code=404, detail="Task not found")
    db.delete(existing)
    db.commit()
    return {"status": "ok"}
