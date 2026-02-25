from fastapi import FastAPI, Depends, Request
from sqlalchemy.orm import Session
from . import models, database

# Create DB tables
models.Base.metadata.create_all(bind=database.engine)

app = FastAPI()

def get_db():
    db = database.SessionLocal()
    try:
        yield db
    finally:
        db.close()

@app.get("/")
def home():
    return {"status": "Suraksha Server Online"}

@app.post("/log-access")
async def log_access(file_id: str, user_id: str, request: Request, db: Session = Depends(get_db)):
    # Check if file is revoked
    file = db.query(models.SecureFile).filter(models.SecureFile.file_id == file_id).first()
    
    status = "GRANTED"
    if file and not file.is_active:
        status = "DENIED"
    
    new_log = models.AccessLog(
        file_id=file_id, 
        user_id=user_id, 
        ip_address=request.client.host, 
        status=status
    )
    db.add(new_log)
    db.commit()
    
    return {"access": status}