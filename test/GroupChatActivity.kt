package com.example.chatapp

import android.os.Bundle
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class GroupChatActivity : AppCompatActivity() {
    private val groupViewModel: GroupViewModel by viewModels()
    private lateinit var adapter: GroupMessageAdapter
    private var currentGroupId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_chat)

        val group = intent.getSerializableExtra("group") as? Group
        if (group == null) {
            Toast.makeText(this, "Ошибка: группа не найдена", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentGroupId = group.group_id

        val token = intent.getStringExtra("token") ?: ""
        val userId = intent.getStringExtra("userId") ?: ""
        val userNickname = intent.getStringExtra("userNickname") ?: "User $userId"

        groupViewModel.token = token
        groupViewModel.userId = userId
        groupViewModel.userNickname = userNickname

        // Инициализация UI
        val rv = findViewById<RecyclerView>(R.id.recyclerGroupMessages)
        val groupNameText = findViewById<TextView>(R.id.groupNameText)
        val messageInput = findViewById<EditText>(R.id.messageInput)
        val sendButton = findViewById<Button>(R.id.sendButton)

        rv.layoutManager = LinearLayoutManager(this)
        adapter = GroupMessageAdapter(userId)
        rv.adapter = adapter

        groupNameText.text = group.name

        // Наблюдаем за сообщениями группы
        groupViewModel.groupMessages.observe(this) { messages ->
            val groupMessages = messages.filter { it.group_id == currentGroupId }
            adapter.submitList(groupMessages)
            if (groupMessages.isNotEmpty()) {
                rv.scrollToPosition(groupMessages.size - 1)
            }
        }

        // Кнопка отправки сообщения
        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                lifecycleScope.launch {
                    groupViewModel.sendGroupMessage(currentGroupId, text)
                }
                messageInput.setText("")
            }
        }

        // Подключаем WebSocket и загружаем сообщения
        lifecycleScope.launch {
            groupViewModel.connectWebSocket()
            groupViewModel.loadGroupMessages(currentGroupId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            groupViewModel.close()
        }
    }
}