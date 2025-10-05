package com.example.chatapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity(), MessageAdapter.Listener {
    private val viewModel: ChatViewModel by viewModels<ChatViewModel>()
    private lateinit var adapter: MessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val rv = findViewById<RecyclerView>(R.id.recyclerMessages)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(this)
        rv.adapter = adapter

        val token = intent.getStringExtra("token") ?: ""
        val userId = intent.getStringExtra("userId") ?: ""
        val targetUserId = intent.getStringExtra("targetUserId") ?: ""
        viewModel.init(token, userId, targetUserId)
        lifecycleScope.launch {
            viewModel.connectWebSocket()
            // Загружаем историю чата после подключения delay(500)
            viewModel.loadChatHistory()
        }

        lifecycleScope.launch {
            viewModel.connectWebSocket()
        }

        viewModel.messages.observe(this) { list ->
            adapter.submitList(list)
            if (list.isNotEmpty()) {
                rv.scrollToPosition(list.size - 1)
            }
        }

        val input = findViewById<EditText>(R.id.messageInput)
        val sendBtn = findViewById<Button>(R.id.sendBtn)
        sendBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                lifecycleScope.launch {
                    viewModel.sendMessage(text)
                }
                input.setText("")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            viewModel.close()
        }
    }

    override fun onMessageClicked(id: String) {
        // Обработка клика по сообщению (если нужно)
    }
}