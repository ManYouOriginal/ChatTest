import asyncio
import json
import aioredis

REDIS_URL = "redis://redis:6379/0"  

async def publisher(channel: str, message: dict):
    redis = await aioredis.from_url(REDIS_URL)
    await redis.publish(channel, json.dumps(message))
    await redis.close()

async def subscriber(channel: str):
    redis = await aioredis.from_url(REDIS_URL)
    pubsub = redis.pubsub()
    await pubsub.subscribe(channel)
    print(f"Subscribed to {channel}")
    async for msg in pubsub.listen():
        if msg is None:
            continue
        if msg['type'] == 'message':
            data = json.loads(msg['data'])
           
            print("Received via Redis:", data)

if __name__ == '__main__':
    import sys
    ch = sys.argv[1] if len(sys.argv) > 1 else 'chat_messages'
    asyncio.run(subscriber(ch))
