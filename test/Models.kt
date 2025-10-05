package com.example.chatapp

import java.io.Serializable
data class MessageOut(
    val id: String,
    val chat_id: String,
    val sender_id: String,
    val content: String,
    val created_at: String
)

data class UserOut(
    val id: String,
    val nickname: String,
    val online: Boolean
)

data class UserCreate(val nickname: String)

data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val user_id: String
)

data class Group(
    val group_id: String,
    val name: String,
    val creator: String,
    val members: List<String>
) : Serializable

data class GroupMessage(
    val id: String,
    val group_id: String,
    val sender_id: String,
    val sender_nickname: String,
    val content: String,
    val created_at: String,
    val chat_type: String = "group"
)

