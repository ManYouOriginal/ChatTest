from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime

class UserCreate(BaseModel):
    nickname: str

class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user_id: str

class UserOut(BaseModel):
    id: str
    nickname: str
    online: bool

class ChatCreate(BaseModel):
    name: Optional[str] = None
    participants: List[str]

class ChatOut(BaseModel):
    id: str
    name: Optional[str]
    is_group: bool

class MessageIn(BaseModel):
    type: str  
    target: str
    content: str

class MessageOut(BaseModel):
    id: str
    chat_id: str
    sender_id: str
    content: str
    created_at: datetime
