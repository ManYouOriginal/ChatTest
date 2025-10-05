package com.example.chatapp

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatViewModel : ViewModel() {
    val messages = MutableLiveData<List<MessageOut>>(emptyList())
    val connectionStatus = MutableLiveData<Boolean>(false)

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    var token: String = ""
    var userId: String = ""
    var targetUserId: String = ""
    var chatId: String? = null

    fun init(token: String, userId: String, targetUserId: String) {
        this.token = token
        this.userId = userId
        this.targetUserId = targetUserId
        this.chatId = if (userId < targetUserId) "${userId}_${targetUserId}" else "${targetUserId}_${userId}"
    }

    suspend fun loadChatHistory() {
        withContext(Dispatchers.IO) {
            try {
                val message = JSONObject().apply {
                    put("action", "get_chat_history")
                    put("payload", JSONObject().apply {
                        put("target_user_id", targetUserId)
                    })
                }
                webSocket?.send(message.toString())
                println("Requested chat history with $targetUserId")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun connectWebSocket() {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("ws://192.168.0.186:8000/ws/$userId?token=$token")
                    .build()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        connectionStatus.postValue(true)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val jsonObject = JSONObject(text)
                            when (jsonObject.getString("action")) {
                                "new_message" -> {
                                    val messageJson = jsonObject.getJSONObject("payload")
                                    val message = MessageOut(
                                        id = messageJson.getString("id"),
                                        chat_id = messageJson.getString("chat_id"),
                                        sender_id = messageJson.getString("sender_id"),
                                        content = messageJson.getString("content"),
                                        created_at = messageJson.getString("created_at")
                                    )

                                    // Добавляем сообщение в список
                                    val currentMessages = messages.value?.toMutableList() ?: mutableListOf()
                                    // Проверяем, нет ли уже такого сообщения (избегаем дублирования)
                                    if (currentMessages.none { it.id == message.id }) {
                                        currentMessages.add(message)
                                        messages.postValue(currentMessages)
                                    }
                                }
                                "chat_history" -> {
                                    val payload = jsonObject.getJSONObject("payload")
                                    val messagesArray = payload.getJSONArray("messages")
                                    val messagesList = mutableListOf<MessageOut>()
                                    for (i in 0 until messagesArray.length()) {
                                        val messageJson = messagesArray.getJSONObject(i)
                                        val message = MessageOut(
                                            id = messageJson.getString("id"),
                                            chat_id = messageJson.getString("chat_id"),
                                            sender_id = messageJson.getString("sender_id"),
                                            content = messageJson.getString("content"),
                                            created_at = messageJson.getString("created_at")
                                        )
                                        messagesList.add(message)
                                    }
                                    messages.postValue(messagesList)
                                    println("Loaded chat history: ${messagesList.size} messages")
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        connectionStatus.postValue(false)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        connectionStatus.postValue(false)
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun sendMessage(text: String) {
        withContext(Dispatchers.IO) {
            try {
                val message = JSONObject().apply {
                    put("action", "send_message")
                    put("payload", JSONObject().apply {
                        put("chat_id", chatId)
                        put("content", text)
                        put("target_user_id", targetUserId)
                    })
                }
                webSocket?.send(message.toString())
                // Локально добавляем сообщение для мгновенного отображения
                val tempMessage = MessageOut(
                    id = "temp_${System.currentTimeMillis()}",
                    chat_id = chatId ?: "",
                    sender_id = userId,
                    content = text,
                    created_at = System.currentTimeMillis().toString()
                )
                val currentMessages = messages.value?.toMutableList() ?: mutableListOf()
                currentMessages.add(tempMessage)
                messages.postValue(currentMessages)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun close() {
        withContext(Dispatchers.IO) {
            webSocket?.close(1000, "Normal closure")
        }
    }
}