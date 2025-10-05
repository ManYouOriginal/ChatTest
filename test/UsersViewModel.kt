package com.example.chatapp

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UsersViewModel : ViewModel() {
    // Изменяем на List<UserOut> вместо List<String>
    val onlineUsers = MutableLiveData<List<UserOut>>(emptyList())
    val connectionStatus = MutableLiveData<Boolean>(false)

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    var token: String = ""
    var userId: String = ""

    suspend fun connectWebSocket() {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("ws://192.168.0.186:8000/ws/$userId?token=$token")
                    .build()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        connectionStatus.postValue(true)
                        // Запрашиваем список пользователей при подключении
                        val message = JSONObject().apply {
                            put("action", "get_users")
                        }
                        webSocket.send(message.toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val jsonObject = JSONObject(text)
                            when (jsonObject.getString("action")) {
                                "users_online" -> {
                                    val usersArray = jsonObject.getJSONArray("users")
                                    val usersList = mutableListOf<UserOut>()
                                    for (i in 0 until usersArray.length()) {
                                        val userJson = usersArray.getJSONObject(i)
                                        val user = UserOut(
                                            id = userJson.getString("id"),
                                            nickname = userJson.getString("nickname"),
                                            online = userJson.getBoolean("online")
                                        )
                                        if (user.id != userId) {
                                            usersList.add(user)
                                        }
                                    }
                                    onlineUsers.postValue(usersList)
                                    println("UsersViewModel: Users updated - ${usersList.size} users")
                                }
                                "added_to_group" -> {
                                    // При получении уведомления о добавлении в группу - обновляем список групп
                                    println("UsersViewModel: Added to new group, reloading groups...")

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

    suspend fun close() {
        withContext(Dispatchers.IO) {
            webSocket?.close(1000, "Normal closure")
        }
    }
}