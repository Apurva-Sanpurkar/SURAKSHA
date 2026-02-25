from fastapi import FastAPI, Depends, HTTPException, Request
from sqlalchemy.orm import Session
from . import models, database
from datetime import datetime

# This command creates the database tables automatically based on models.py
models.Base.metadata.create_all(bind=database.engine)

app = FastAPI(title="SURAKSHA-BACKEND")

# Dependency to get a database connection for each request
def get_db():
    db = database.SessionLocal()
    try:
        yield db
    finally:
        db.close()

@app.get("/")
def health_check():
    return {"status": "Suraksha Server Online", "version": "1.0.0"}

# STEP 1: Register a new file (called after the Android app captures a photo)
@app.post("/register-file")
async def register_file(file_id: str, owner_id: str, db: Session = Depends(get_db)):
    db_file = models.SecureFile(file_id=file_id, owner_id=owner_id, is_active=True)
    db.add(db_file)
    db.commit()
    return {"message": f"File {file_id} is now registered and active."}

# STEP 2: Validate Access (called when someone tries to view the .sec file)
@app.post("/validate-access")
async def validate_access(file_id: str, user_id: str, request: Request, db: Session = Depends(get_db)):
    # Check if the file exists and is active
    file_record = db.query(models.SecureFile).filter(models.SecureFile.file_id == file_id).first()
    
    if not file_record:
        raise HTTPException(status_code=404, detail="File not found in Suraksha ecosystem")
    
    # If the owner clicked 'Revoke' on the dashboard, is_active will be False
    if not file_record.is_active:
        # Log the denied attempt for the owner to see
        denied_log = models.AccessLog(
            file_id=file_id, 
            user_id=user_id, 
            ip_address=request.client.host, 
            status="DENIED"
        )
        db.add(denied_log)
        db.commit()
        return {"access": "DENIED", "reason": "Access has been remotely revoked by the owner."}

    # Log the successful attempt (Tracking)
    success_log = models.AccessLog(
        file_id=file_id, 
        user_id=user_id, 
        ip_address=request.client.host, 
        status="GRANTED"
    )
    db.add(success_log)
    db.commit()
    
    return {"access": "GRANTED", "timestamp": datetime.utcnow()}