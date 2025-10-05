package com.example.chatapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class UsersActivity : AppCompatActivity(), UsersAdapter.Listener, GroupsAdapter.Listener {
    private val usersViewModel: UsersViewModel by viewModels()
    private val groupViewModel: GroupViewModel by viewModels()

    private lateinit var usersAdapter: UsersAdapter
    private lateinit var groupsAdapter: GroupsAdapter
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var groupsRecyclerView: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var emptyUsersText: TextView
    private lateinit var emptyGroupsText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)

        // Инициализация UI элементов
        initViews()

        // Получение данных из Intent
        val token = intent.getStringExtra("token") ?: ""
        val userId = intent.getStringExtra("userId") ?: ""
        val userNickname = "User $userId"

        // Инициализация ViewModels
        usersViewModel.token = token
        usersViewModel.userId = userId

        groupViewModel.token = token
        groupViewModel.userId = userId
        groupViewModel.userNickname = userNickname

        // Настройка адаптеров
        setupAdapters()

        // Наблюдатели LiveData
        setupObservers()

        // Подключение WebSocket
        lifecycleScope.launch {
            usersViewModel.connectWebSocket()
            groupViewModel.connectWebSocket()
        }

        // Обработчик кнопки создания группы
        val fabCreateGroup = findViewById<FloatingActionButton>(R.id.fabCreateGroup)
        fabCreateGroup.setOnClickListener {
            val intent = Intent(this, CreateGroupActivity::class.java).apply {
                putExtra("token", token)
                putExtra("userId", userId)
                putExtra("userNickname", userNickname)
            }
            startActivity(intent)
        }
    }

    private fun initViews() {
        usersRecyclerView = findViewById(R.id.recyclerUsers)
        groupsRecyclerView = findViewById(R.id.recyclerGroups)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        emptyUsersText = findViewById(R.id.emptyUsersText)
        emptyGroupsText = findViewById(R.id.emptyGroupsText)

        // Скрываем пустые сообщения по умолчанию
        emptyUsersText.visibility = View.GONE
        emptyGroupsText.visibility = View.GONE
    }

    private fun setupAdapters() {
        // Адаптер для пользователей
        usersAdapter = UsersAdapter(this)
        usersRecyclerView.layoutManager = LinearLayoutManager(this)
        usersRecyclerView.adapter = usersAdapter

        // Адаптер для групп
        groupsAdapter = GroupsAdapter(this)
        groupsRecyclerView.layoutManager = LinearLayoutManager(this)
        groupsRecyclerView.adapter = groupsAdapter
    }

    private fun setupObservers() {
        // Наблюдатель за онлайн пользователями
        usersViewModel.onlineUsers.observe(this) { users ->
            Log.d("UsersActivity", "Online users updated: ${users.size} users")

            if (users.isNotEmpty()) {
                // Теперь users уже List<UserOut>, не нужно преобразовывать
                usersAdapter.submitList(users)
                emptyUsersText.visibility = View.GONE
                usersRecyclerView.visibility = View.VISIBLE
            } else {
                usersAdapter.submitList(emptyList())
                emptyUsersText.visibility = View.VISIBLE
                usersRecyclerView.visibility = View.GONE
            }

            loadingIndicator.visibility = View.GONE
        }

        // Наблюдатель за группами пользователя
        groupViewModel.userGroups.observe(this) { groups ->
            println("UsersActivity: Received ${groups.size} groups")
            if (groups.isNotEmpty()) {
                groupsAdapter.submitList(groups)
                emptyGroupsText.visibility = View.GONE
                groupsRecyclerView.visibility = View.VISIBLE
            } else {
                groupsAdapter.submitList(emptyList())
                emptyGroupsText.visibility = View.VISIBLE
                groupsRecyclerView.visibility = View.GONE
            }
        }

        // Наблюдатель за статусом подключения
        usersViewModel.connectionStatus.observe(this) { isConnected ->
            if (isConnected) {
                Log.d("UsersActivity", "WebSocket connected")
            } else {
                Log.d("UsersActivity", "WebSocket disconnected")
            }
        }
    }

    // Обработчик клика по пользователю (приватный чат)
    override fun onUserClicked(user: UserOut) {
        Log.d("UsersActivity", "User clicked: ${user.id}")

        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("token", usersViewModel.token)
            putExtra("userId", usersViewModel.userId)
            putExtra("targetUserId", user.id)
        }
        startActivity(intent)
    }

    // Обработчик клика по группе (групповой чат)
    override fun onGroupClicked(group: Group) {
        Log.d("UsersActivity", "Group clicked: ${group.name}")

        val intent = Intent(this, GroupChatActivity::class.java).apply {
            putExtra("token", groupViewModel.token)
            putExtra("userId", groupViewModel.userId)
            putExtra("userNickname", groupViewModel.userNickname)
            putExtra("group", group)  // Group должен быть Serializable
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // При возвращении на экран обновляем списки
        lifecycleScope.launch {
            usersViewModel.connectWebSocket()
            groupViewModel.connectWebSocket()
            // Загружаем группы после небольшой задержки для установки соединения
            
            groupViewModel.loadUserGroups()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            usersViewModel.close()
            groupViewModel.close()
        }
    }


}
