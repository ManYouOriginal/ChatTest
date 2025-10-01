import uuid
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey, Boolean, Text, UniqueConstraint
from datetime import datetime
from sqlalchemy.orm import relationship
from db import Base

def gen_uuid():
    return str(uuid.uuid4())

class User(Base):
    __tablename__ = "users"
    id = Column(String, primary_key=True, default=gen_uuid)
    nickname = Column(String, unique=True, index=True)  
    online = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)

class Chat(Base):
    __tablename__ = "chats"
    id = Column(String, primary_key=True, default=gen_uuid)
    name = Column(String, nullable=True)
    is_group = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)

class ChatParticipant(Base):
    __tablename__ = "chat_participants"
    id = Column(Integer, primary_key=True, autoincrement=True)
    chat_id = Column(String, ForeignKey("chats.id"))
    user_id = Column(String, ForeignKey("users.id"))

    __table_args__ = (UniqueConstraint('chat_id', 'user_id', name='_chat_user_uc'),)

class Message(Base):
    __tablename__ = "messages"
    id = Column(String, primary_key=True, default=gen_uuid)
    chat_id = Column(String, ForeignKey("chats.id"))
    sender_id = Column(String, ForeignKey("users.id"))
    content = Column(Text)
    created_at = Column(DateTime, default=datetime.utcnow)
