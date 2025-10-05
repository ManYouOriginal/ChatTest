package com.example.chatapp

import okhttp3.*
import okio.ByteString
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val baseUrl: String, // e.g. ws://192.168.0.186:8000/ws
    private val token: String,
    private val listener: Listener
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var ws: WebSocket? = null
    private val moshi = Moshi.Builder().build()

    interface Listener {
        fun onMessage(json: String)
        fun onOpen()
        fun onClose()
    }

    fun connect() {
        val urlWithToken = "$baseUrl?token=$token"
        val req = Request.Builder().url(urlWithToken).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onOpen()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onMessage(text)
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                listener.onMessage(bytes.utf8())
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClose()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onClose()
            }
        })
    }

    fun sendMessage(type: String, target: String, content: String) {
        val payload = mapOf(
            "action" to "send_message",
            "payload" to mapOf(
                "type" to type,
                "target" to target,
                "content" to content
            )
        )
        val json = moshi.adapter(Map::class.java).toJson(payload)
        ws?.send(json)
    }

    fun close() {
        ws?.close(1000, "bye")
    }
}
