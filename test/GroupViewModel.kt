package com.example.chatapp

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroupViewModel : ViewModel() {
    val userGroups = MutableLiveData<List<Group>>(emptyList())
    val groupMessages = MutableLiveData<List<GroupMessage>>(emptyList())
    val createdGroup = MutableLiveData<Group?>(null)
    val connectionStatus = MutableLiveData<Boolean>(false)

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    var token: String = ""
    var userId: String = ""
    var userNickname: String = ""

    suspend fun connectWebSocket() {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("ws://192.168.0.186:8000/ws/$userId?token=$token")
                    .build()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        connectionStatus.postValue(true)
                        // Загружаем группы при подключении
                        loadUserGroups()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        println("GroupViewModel received: $text")
                        try {
                            val jsonObject = JSONObject(text)
                            when (jsonObject.getString("action")) {
                                "user_groups" -> {
                                    val groupsArray = jsonObject.getJSONArray("payload")
                                    val groupsList = mutableListOf<Group>()
                                    for (i in 0 until groupsArray.length()) {
                                        val groupJson = groupsArray.getJSONObject(i)
                                        val membersArray = groupJson.getJSONArray("members")
                                        val membersList = mutableListOf<String>()
                                        for (j in 0 until membersArray.length()) {
                                            membersList.add(membersArray.getString(j))
                                        }
                                        val group = Group(
                                            group_id = groupJson.getString("group_id"),
                                            name = groupJson.getString("name"),
                                            creator = groupJson.getString("creator"),
                                            members = membersList
                                        )
                                        groupsList.add(group)
                                    }
                                    userGroups.postValue(groupsList)
                                    println("Groups updated: ${groupsList.size} groups")
                                }
                                "group_created" -> {
                                    val groupJson = jsonObject.getJSONObject("payload")
                                    val membersArray = groupJson.getJSONArray("members")
                                    val membersList = mutableListOf<String>()
                                    for (i in 0 until membersArray.length()) {
                                        membersList.add(membersArray.getString(i))
                                    }
                                    val group = Group(
                                        group_id = groupJson.getString("group_id"),
                                        name = groupJson.getString("name"),
                                        creator = groupJson.getString("creator"),
                                        members = membersList
                                    )
                                    createdGroup.postValue(group)
                                    println("Group created: ${group.name}")
                                    // Обновляем список групп
                                    loadUserGroups()
                                }
                                "added_to_group" -> {
                                    println("Added to new group, reloading groups...")
                                    loadUserGroups()
                                }
                                "new_group_message" -> {
                                    val messageJson = jsonObject.getJSONObject("payload")
                                    val message = GroupMessage(
                                        id = messageJson.getString("id"),
                                        group_id = messageJson.getString("group_id"),
                                        sender_id = messageJson.getString("sender_id"),
                                        sender_nickname = messageJson.getString("sender_nickname"),
                                        content = messageJson.getString("content"),
                                        created_at = messageJson.getString("created_at")
                                    )

                                    val currentMessages = groupMessages.value?.toMutableList() ?: mutableListOf()
                                    currentMessages.add(message)
                                    groupMessages.postValue(currentMessages)
                                    println("New group message: ${message.content}")
                                }
                                "group_messages" -> {
                                    val payload = jsonObject.getJSONObject("payload")
                                    val messagesArray = payload.getJSONArray("messages")
                                    val messagesList = mutableListOf<GroupMessage>()
                                    for (i in 0 until messagesArray.length()) {
                                        val messageJson = messagesArray.getJSONObject(i)
                                        val message = GroupMessage(
                                            id = messageJson.getString("id"),
                                            group_id = messageJson.getString("group_id"),
                                            sender_id = messageJson.getString("sender_id"),
                                            sender_nickname = messageJson.getString("sender_nickname"),
                                            content = messageJson.getString("content"),
                                            created_at = messageJson.getString("created_at")
                                        )
                                        messagesList.add(message)
                                    }
                                    groupMessages.postValue(messagesList)
                                    println("Loaded ${messagesList.size} group messages")
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            println("Error parsing message: ${e.message}")
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        connectionStatus.postValue(false)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        connectionStatus.postValue(false)
                        println("WebSocket failure: ${t.message}")
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadUserGroups() {
        val message = JSONObject().apply {
            put("action", "get_user_groups")
        }
        webSocket?.send(message.toString())
        println("GroupViewModel: Requested user groups")
    }

    fun loadGroupMessages(groupId: String) {
        val message = JSONObject().apply {
            put("action", "get_group_messages")
            put("payload", JSONObject().apply {
                put("group_id", groupId)
            })
        }
        webSocket?.send(message.toString())
        println("Requested messages for group: $groupId")
    }

    suspend fun createGroup(groupName: String, members: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                // Создаем JSON массив для members
                val membersArray = JSONObject()
                members.forEachIndexed { index, member ->
                    membersArray.put(index.toString(), member)
                }

                val message = JSONObject().apply {
                    put("action", "create_group")
                    put("payload", JSONObject().apply {
                        put("group_name", groupName)
                        put("members", membersArray)
                    })
                }
                webSocket?.send(message.toString())
                println("Sent create group request: $groupName with ${members.size} members")
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error creating group: ${e.message}")
            }
        }
    }

    suspend fun sendGroupMessage(groupId: String, content: String) {
        withContext(Dispatchers.IO) {
            try {
                val message = JSONObject().apply {
                    put("action", "send_message")
                    put("payload", JSONObject().apply {
                        put("chat_type", "group")
                        put("group_id", groupId)
                        put("content", content)
                        put("sender_nickname", userNickname)
                    })
                }
                webSocket?.send(message.toString())
                println("Sent group message to $groupId: $content")

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