import asyncio
import json
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException
from sqlalchemy import (create_engine, MetaData, Table, Column, Integer, String,
                        ForeignKey, select, insert, event)
from sqlalchemy.orm import sessionmaker
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession


DATABASE_URL = "sqlite+aiosqlite:///./chat.db"
engine = create_async_engine(DATABASE_URL, echo=True)
metadata = MetaData()


messages = Table(
    'messages', metadata,
    Column('id', Integer, primary_key=True),
    Column('sender', String, nullable=False),
    Column('recipient', String, nullable=True), 
    Column('group_name', String, nullable=True), 
    Column('content', String, nullable=False)
)

groups = Table(
    'groups', metadata,
    Column('id', Integer, primary_key=True),
    Column('name', String, unique=True, nullable=False)
)

group_members = Table(
    'group_members', metadata,
    Column('id', Integer, primary_key=True),
    Column('group_name', String, ForeignKey('groups.name')),
    Column('username', String)
)


async_session = sessionmaker(
    engine, expire_on_commit=False, class_=AsyncSession
)

app = FastAPI()


connected_users = {}


@app.on_event("startup")
async def startup():
    
    async with engine.begin() as conn:
        await conn.run_sync(metadata.create_all)


async def broadcast_user_list():
  
    user_list = list(connected_users.keys())
    for ws in connected_users.values():
        await ws.send_json({"type": "user_list", "users": user_list})


@app.websocket("/ws/{username}")
async def websocket_endpoint(websocket: WebSocket, username: str):
   
    if username in connected_users:
       
        await websocket.close(code=1008, reason=f"Username '{username}' is already taken.")
        return

    await websocket.accept()
    connected_users[username] = websocket
    print(f"Пользователь {username} подключился.")

   
    await broadcast_user_list()
   
    await send_user_groups(username)

    try:
        while True:
            data = await websocket.receive_text()
            print(f"Сообщение от {username}: {data}")
            try:
                message_data = json.loads(data)
                msg_type = message_data.get("type")

                if msg_type == "personal_message":
                    recipient = message_data.get("recipient")
                    message = message_data.get("message")
                    if recipient and message:
                        await save_and_send_personal_message(username, recipient, message)

                elif msg_type == "group_message":
                    group_name = message_data.get("group_name")
                    message = message_data.get("message")
                    if group_name and message:
                        await save_and_send_group_message(username, group_name, message)

            except json.JSONDecodeError:
                print(f"Некорректный JSON от {username}")
            except Exception as e:
                print(f"Ошибка при обработке сообщения: {e}")

    except WebSocketDisconnect:
        print(f"Пользователь {username} отключился.")
    finally:
        if username in connected_users:
            del connected_users[username]
       
        await broadcast_user_list()


async def save_and_send_personal_message(sender: str, recipient: str, message: str):
    
    async with async_session() as session:
        async with session.begin():
            stmt = insert(messages).values(sender=sender, recipient=recipient, content=message)
            await session.execute(stmt)

     
        if recipient in connected_users:
            recipient_ws = connected_users[recipient]
            await recipient_ws.send_json({
                "type": "personal_message",
                "sender": sender,
                "message": message
            })
        
       
        if sender in connected_users:
            sender_ws = connected_users[sender]
            await sender_ws.send_json({
                "type": "personal_message",
                "sender": sender,
                "recipient": recipient, 
                "message": message
            })


async def save_and_send_group_message(sender: str, group_name: str, message: str):
    
    async with async_session() as session:
        async with session.begin():
            
            stmt_msg = insert(messages).values(sender=sender, group_name=group_name, content=message)
            await session.execute(stmt_msg)

          
            stmt_members = select(group_members.c.username).where(group_members.c.group_name == group_name)
            result = await session.execute(stmt_members)
            members = [row[0] for row in result]

      
        for member in members:
            if member in connected_users:
                await connected_users[member].send_json({
                    "type": "group_message",
                    "group_name": group_name,
                    "sender": sender,
                    "message": message
                })


@app.get("/history/personal/{user1}/{user2}")
async def get_personal_chat_history(user1: str, user2: str):
    
    async with async_session() as session:
        stmt = select(messages).where(
            ((messages.c.sender == user1) & (messages.c.recipient == user2)) |
            ((messages.c.sender == user2) & (messages.c.recipient == user1))
        ).order_by(messages.c.id)
        result = await session.execute(stmt)
        history = [
            {"sender": row.sender, "recipient": row.recipient, "message": row.content}
            for row in result
        ]
        return history


@app.get("/history/group/{group_name}")
async def get_group_chat_history(group_name: str):
    
    async with async_session() as session:
        stmt = select(messages).where(messages.c.group_name == group_name).order_by(messages.c.id)
        result = await session.execute(stmt)
        history = [
            {"sender": row.sender, "group": row.group_name, "message": row.content}
            for row in result
        ]
        return history

@app.post("/groups/create")
async def create_group(group_data: dict):
    
    group_name = group_data.get("group_name")
    members = group_data.get("members") 
    if not group_name or not members:
        raise HTTPException(status_code=400, detail="Group name and members are required.")

    async with async_session() as session:
        async with session.begin():
            
            stmt_exist = select(groups).where(groups.c.name == group_name)
            if (await session.execute(stmt_exist)).first():
                raise HTTPException(status_code=400, detail="Group with this name already exists.")
            
            
            stmt_group = insert(groups).values(name=group_name)
            await session.execute(stmt_group)
            
            
            for member in members:
                stmt_member = insert(group_members).values(group_name=group_name, username=member)
                await session.execute(stmt_member)

 
    for member in members:
        if member in connected_users:
            await connected_users[member].send_json({"type": "group_update"})

    return {"status": "success", "message": f"Group '{group_name}' created."}


@app.get("/groups/{username}")
async def get_user_groups(username: str):
   
    async with async_session() as session:
        stmt = select(group_members.c.group_name).where(group_members.c.username == username)
        result = await session.execute(stmt)
        user_groups = [row[0] for row in result]
        return {"groups": user_groups}

async def send_user_groups(username: str):
    
    if username in connected_users:
        groups_data = await get_user_groups(username)
        await connected_users[username].send_json({
            "type": "group_list",
            "groups": groups_data.get("groups", [])
        })