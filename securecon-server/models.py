from sqlalchemy import Column, Integer, String, Boolean, DateTime
from .database import Base
import datetime

class SecureFile(Base):
    __tablename__ = "secure_files"
    file_id = Column(String, primary_key=True, index=True)
    owner_id = Column(String)
    is_active = Column(Boolean, default=True) # Used for Remote Revoke

class AccessLog(Base):
    __tablename__ = "access_logs"
    id = Column(Integer, primary_key=True, index=True)
    file_id = Column(String)
    user_id = Column(String)
    ip_address = Column(String)
    timestamp = Column(DateTime, default=datetime.datetime.utcnow)
    status = Column(String) # "GRANTED" or "DENIED"