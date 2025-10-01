from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
import json
import uuid
from datetime import datetime
import redis.asyncio as redis
from typing import Dict, List, Set
import asyncio

app = FastAPI()

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Redis connection
redis_client = None

async def get_redis():
    global redis_client
    if redis_client is None:
        redis_client = redis.Redis(host='redis', port=6379, decode_responses=True)
    return redis_client

class ConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, WebSocket] = {}
        self.user_groups: Dict[str, Set[str]] = {}  # user_id -> set of group_ids
        self.lock = asyncio.Lock()

    async def connect(self, websocket: WebSocket, user_id: str):
        await websocket.accept()
        
        async with self.lock:
            # Закрываем предыдущее соединение если есть
            if user_id in self.active_connections:
                try:
                    await self.active_connections[user_id].close()
                except Exception:
                    pass
                finally:
                    del self.active_connections[user_id]
            
            self.active_connections[user_id] = websocket
        
        # Сохраняем пользователя онлайн в Redis
        redis_client = await get_redis()
        await redis_client.sadd("online_users", user_id)
        
        # Сохраняем информацию о пользователе для отображения ника
        user_nickname = f"User {user_id}"
        await redis_client.hset(f"user:{user_id}", "nickname", user_nickname)
        
        # Загружаем группы пользователя
        user_groups_key = f"user:{user_id}:groups"
        user_groups = await redis_client.smembers(user_groups_key)
        self.user_groups[user_id] = set(user_groups)
        
        # Уведомляем всех о новом пользователе
        await self.broadcast_users()
        
        print(f"User {user_id} connected. Online users: {len(self.active_connections)}")

    async def disconnect(self, user_id: str):
        async with self.lock:
            if user_id in self.active_connections:
                try:
                    await self.active_connections[user_id].close()
                except Exception:
                    pass
                finally:
                    if user_id in self.active_connections:
                        del self.active_connections[user_id]
        
        # Удаляем из онлайн пользователей
        redis_client = await get_redis()
        await redis_client.srem("online_users", user_id)
        
        # Удаляем из user_groups
        if user_id in self.user_groups:
            del self.user_groups[user_id]
        
        # Уведомляем всех об отключении
        await self.broadcast_users()
        
        print(f"User {user_id} disconnected. Online users: {len(self.active_connections)}")

    async def send_personal_message(self, message: str, user_id: str):
        async with self.lock:
            if user_id not in self.active_connections:
                print(f"User {user_id} not connected, message not delivered")
                return False
            
            try:
                await self.active_connections[user_id].send_text(message)
                print(f"Message delivered to {user_id}")
                return True
            except Exception as e:
                print(f"Error sending message to {user_id}: {e}")
                # Удаляем нерабочее соединение
                if user_id in self.active_connections:
                    try:
                        await self.active_connections[user_id].close()
                    except Exception:
                        pass
                    del self.active_connections[user_id]
                return False

    async def broadcast_to_group(self, message: str, group_id: str):
        """Отправляет сообщение всем участникам группы"""
        redis_client = await get_redis()
        group_members_key = f"group:{group_id}:members"
        members = await redis_client.smembers(group_members_key)
        
        sent_count = 0
        for member_id in members:
            if await self.send_personal_message(message, member_id):
                sent_count += 1
        
        print(f"Broadcasted message to {sent_count}/{len(members)} members of group {group_id}")

    async def broadcast(self, message: str):
        """Отправляет сообщение всем подключенным пользователям"""
        disconnected = []
        async with self.lock:
            for user_id, connection in list(self.active_connections.items()):
                try:
                    await connection.send_text(message)
                except Exception as e:
                    print(f"Error broadcasting to {user_id}: {e}")
                    disconnected.append(user_id)
        
        for user_id in disconnected:
            await self.disconnect(user_id)

    async def broadcast_users(self):
        """Рассылает обновленный список онлайн пользователей ВСЕМ подключенным клиентам"""
        redis_client = await get_redis()
        online_users = await redis_client.smembers("online_users")
        
        # Получаем ники пользователей
        users_with_info = []
        for user_id in online_users:
            user_nickname = await redis_client.hget(f"user:{user_id}", "nickname")
            if not user_nickname:
                user_nickname = f"User {user_id}"
            users_with_info.append({
                "id": user_id,
                "nickname": user_nickname,
                "online": True
            })
        
        users_message = {
            "action": "users_online",
            "users": users_with_info
        }
        
        print(f"Broadcasting users update: {len(online_users)} online users")
        message_json = json.dumps(users_message)
        
        # Отправляем каждому подключенному пользователю
        for user_id in list(self.active_connections.keys()):
            await self.send_personal_message(message_json, user_id)

    async def add_user_to_group(self, user_id: str, group_id: str):
        """Добавляет пользователя в группу"""
        if user_id not in self.user_groups:
            self.user_groups[user_id] = set()
        self.user_groups[user_id].add(group_id)

    async def get_user_groups(self, user_id: str):
        """Возвращает группы пользователя"""
        redis_client = await get_redis()
        user_groups_key = f"user:{user_id}:groups"
        return await redis_client.smembers(user_groups_key)

    async def is_user_connected(self, user_id: str) -> bool:
        """Проверяет, подключен ли пользователь"""
        async with self.lock:
            return user_id in self.active_connections

manager = ConnectionManager()

@app.websocket("/ws/{user_id}")
async def websocket_endpoint(websocket: WebSocket, user_id: str, token: str = Query(...)):
    # Простая проверка токена
    if token != f"token_{user_id}":
        await websocket.close(code=1008)
        return

    try:
        await manager.connect(websocket, user_id)
        redis_client = await get_redis()
        
        try:
            while True:
                data = await websocket.receive_text()
                message_data = json.loads(data)
                action = message_data.get("action")
                
                print(f"Received action '{action}' from user {user_id}")
                
                if action == "send_message":
                    await handle_send_message(user_id, message_data, redis_client)
                    
                elif action == "get_users":
                    await handle_get_users(user_id, redis_client)
                    
                elif action == "create_group":
                    await handle_create_group(user_id, message_data, redis_client)
                    
                elif action == "get_user_groups":
                    await handle_get_user_groups(user_id, redis_client)
                    
                elif action == "get_group_messages":
                    await handle_get_group_messages(user_id, message_data, redis_client)
                    
                elif action == "get_chat_history":
                    await handle_get_chat_history(user_id, message_data, redis_client)
                    
                else:
                    print(f"Unknown action: {action}")
                    
        except WebSocketDisconnect:
            print(f"WebSocket disconnected for user {user_id}")
        except Exception as e:
            print(f"WebSocket error for user {user_id}: {e}")
    except Exception as e:
        print(f"Connection error for user {user_id}: {e}")
    finally:
        await manager.disconnect(user_id)

async def handle_send_message(user_id: str, message_data: dict, redis_client):
    """Обработка отправки сообщения"""
    try:
        chat_type = message_data["payload"].get("chat_type", "private")
        
        if chat_type == "private":
            # ЛИЧНОЕ СООБЩЕНИЕ
            message_id = str(uuid.uuid4())
            target_user_id = message_data["payload"]["target_user_id"]
            
            # Создаем одинаковый chat_id для обоих пользователей
            if user_id < target_user_id:
                chat_id = f"{user_id}_{target_user_id}"
            else:
                chat_id = f"{target_user_id}_{user_id}"
            
            message = {
                "id": message_id,
                "chat_id": chat_id,
                "sender_id": user_id,
                "content": message_data["payload"]["content"],
                "created_at": datetime.now().isoformat(),
                "target_user_id": target_user_id,
                "chat_type": "private"
            }
            
            print(f"Sending private message from {user_id} to {target_user_id}, chat_id: {chat_id}")
            
            # Сохраняем сообщение в Redis для истории
            await redis_client.rpush(f"chat:{chat_id}:messages", json.dumps(message))
            # Сохраняем последние 100 сообщений
            await redis_client.ltrim(f"chat:{chat_id}:messages", -100, -1)
            
            # Получаем ник отправителя
            sender_nickname = await redis_client.hget(f"user:{user_id}", "nickname")
            if not sender_nickname:
                sender_nickname = f"User {user_id}"
            message["sender_nickname"] = sender_nickname
            
            # Отправляем получателю
            target_delivered = await manager.send_personal_message(json.dumps({
                "action": "new_message",
                "payload": message
            }), target_user_id)
            
            # Всегда отправляем обратно отправителю для подтверждения
            await manager.send_personal_message(json.dumps({
                "action": "new_message", 
                "payload": message
            }), user_id)
            
            if target_delivered:
                print(f"✓ Message successfully delivered to {target_user_id}")
            else:
                print(f"✗ Failed to deliver message to {target_user_id} (user offline)")
            
        elif chat_type == "group":
            # ГРУППОВОЕ СООБЩЕНИЕ
            message_id = str(uuid.uuid4())
            group_id = message_data["payload"]["group_id"]
            message = {
                "id": message_id,
                "group_id": group_id,
                "sender_id": user_id,
                "content": message_data["payload"]["content"],
                "created_at": datetime.now().isoformat(),
                "chat_type": "group",
                "sender_nickname": message_data["payload"].get("sender_nickname", f"User {user_id}")
            }
            
            print(f"Sending group message from {user_id} to group {group_id}")
            
            # Сохраняем сообщение в Redis для истории группы
            await redis_client.rpush(f"group:{group_id}:messages", json.dumps(message))
            await redis_client.ltrim(f"group:{group_id}:messages", -100, -1)
            
            # Отправляем сообщение всем участникам группы
            await manager.broadcast_to_group(json.dumps({
                "action": "new_group_message",
                "payload": message
            }), group_id)
    except Exception as e:
        print(f"Error handling send_message for user {user_id}: {e}")

async def handle_get_users(user_id: str, redis_client):
    """Обработка запроса списка пользователей"""
    try:
        online_users = await redis_client.smembers("online_users")
        
        # Получаем информацию о пользователях
        users_with_info = []
        for online_user_id in online_users:
            if online_user_id != user_id:  # Исключаем текущего пользователя
                user_nickname = await redis_client.hget(f"user:{online_user_id}", "nickname")
                if not user_nickname:
                    user_nickname = f"User {online_user_id}"
                users_with_info.append({
                    "id": online_user_id,
                    "nickname": user_nickname,
                    "online": True
                })
        
        users_message = {
            "action": "users_online",
            "users": users_with_info
        }
        
        await manager.send_personal_message(json.dumps(users_message), user_id)
        print(f"Sent users list to {user_id}: {len(users_with_info)} users")
    except Exception as e:
        print(f"Error handling get_users for user {user_id}: {e}")

async def handle_create_group(user_id: str, message_data: dict, redis_client):
    """Обработка создания группы"""
    try:
        group_id = str(uuid.uuid4())
        group_name = message_data["payload"]["group_name"]
        
        # Получаем список участников
        members_dict = message_data["payload"]["members"]
        members = [members_dict[key] for key in members_dict if key in members_dict]
        
        print(f"Creating group: {group_name} with members: {members}")
        
        # Сохраняем информацию о группе
        await redis_client.hset(f"group:{group_id}", mapping={
            "name": group_name,
            "creator": user_id,
            "created_at": datetime.now().isoformat()
        })
        
        # Добавляем участников в группу (включая создателя)
        all_members = set(members) | {user_id}
        for member_id in all_members:
            await redis_client.sadd(f"group:{group_id}:members", member_id)
            await redis_client.sadd(f"user:{member_id}:groups", group_id)
            await manager.add_user_to_group(member_id, group_id)
        
        # Получаем информацию о группе для ответа
        members_list = list(all_members)
        group_info = {
            "group_id": group_id,
            "name": group_name,
            "creator": user_id,
            "members": members_list
        }
        
        print(f"✓ Group created: {group_info}")
        
        # Отправляем информацию о созданной группе создателю
        await manager.send_personal_message(json.dumps({
            "action": "group_created",
            "payload": group_info
        }), user_id)
        
        # Уведомляем участников о добавлении в группу
        for member_id in all_members:
            if member_id != user_id:  # Создателя уже уведомили
                await manager.send_personal_message(json.dumps({
                    "action": "added_to_group",
                    "payload": group_info
                }), member_id)
                print(f"Notified user {member_id} about group")
    except Exception as e:
        print(f"Error handling create_group for user {user_id}: {e}")

async def handle_get_user_groups(user_id: str, redis_client):
    """Обработка запроса групп пользователя"""
    try:
        user_groups_key = f"user:{user_id}:groups"
        group_ids = await redis_client.smembers(user_groups_key)
        
        groups_info = []
        for group_id in group_ids:
            group_data = await redis_client.hgetall(f"group:{group_id}")
            if group_data:
                # Получаем участников группы
                members = await redis_client.smembers(f"group:{group_id}:members")
                group_info = {
                    "group_id": group_id,
                    "name": group_data.get("name", "Unnamed Group"),
                    "creator": group_data.get("creator", ""),
                    "members": list(members)
                }
                groups_info.append(group_info)
        
        await manager.send_personal_message(json.dumps({
            "action": "user_groups",
            "payload": groups_info
        }), user_id)
        print(f"Sent {len(groups_info)} groups to user {user_id}")
    except Exception as e:
        print(f"Error handling get_user_groups for user {user_id}: {e}")

async def handle_get_group_messages(user_id: str, message_data: dict, redis_client):
    """Обработка запроса истории групповых сообщений"""
    try:
        group_id = message_data["payload"]["group_id"]
        messages = await redis_client.lrange(f"group:{group_id}:messages", 0, -1)
        parsed_messages = [json.loads(msg) for msg in messages]
        
        await manager.send_personal_message(json.dumps({
            "action": "group_messages",
            "payload": {
                "group_id": group_id,
                "messages": parsed_messages
            }
        }), user_id)
        print(f"Sent {len(parsed_messages)} group messages to user {user_id}")
    except Exception as e:
        print(f"Error handling get_group_messages for user {user_id}: {e}")

async def handle_get_chat_history(user_id: str, message_data: dict, redis_client):
    """Обработка запроса истории приватного чата"""
    try:
        target_user_id = message_data["payload"]["target_user_id"]
        
        # Создаем одинаковый chat_id для обоих пользователей
        if user_id < target_user_id:
            chat_id = f"{user_id}_{target_user_id}"
        else:
            chat_id = f"{target_user_id}_{user_id}"
        
        # Получаем историю сообщений
        messages = await redis_client.lrange(f"chat:{chat_id}:messages", 0, -1)
        parsed_messages = [json.loads(msg) for msg in messages]
        
        # Добавляем никнеймы к сообщениям
        for message in parsed_messages:
            sender_id = message["sender_id"]
            sender_nickname = await redis_client.hget(f"user:{sender_id}", "nickname")
            if not sender_nickname:
                sender_nickname = f"User {sender_id}"
            message["sender_nickname"] = sender_nickname
        
        await manager.send_personal_message(json.dumps({
            "action": "chat_history",
            "payload": {
                "chat_id": chat_id,
                "messages": parsed_messages
            }
        }), user_id)
        print(f"Sent {len(parsed_messages)} chat history messages to user {user_id}")
    except Exception as e:
        print(f"Error handling get_chat_history for user {user_id}: {e}")

# REST endpoints
@app.get("/")
async def root():
    return {"message": "Chat API is running"}

@app.get("/api/users")
async def get_users():
    redis_client = await get_redis()
    online_users = await redis_client.smembers("online_users")
    
    users = []
    for user_id in online_users:
        user_nickname = await redis_client.hget(f"user:{user_id}", "nickname")
        if not user_nickname:
            user_nickname = f"User {user_id}"
        users.append({
            "id": user_id,
            "nickname": user_nickname,
            "online": True
        })
    
    return users

@app.post("/api/login")
async def login(user_data: dict):
    nickname = user_data.get("nickname", "").strip()
    if not nickname:
        raise HTTPException(status_code=400, detail="Nickname is required")
    
    user_id = str(abs(hash(nickname)))[-8:]
    
    redis_client = await get_redis()
    await redis_client.hset(f"user:{user_id}", "nickname", nickname)
    
    return {
        "access_token": f"token_{user_id}",
        "token_type": "bearer", 
        "user_id": user_id
    }

@app.get("/health")
async def health_check():
    try:
        redis_client = await get_redis()
        await redis_client.ping()
        return {"status": "healthy", "redis": "connected"}
    except Exception as e:
        return {"status": "unhealthy", "redis": "disconnected", "error": str(e)}