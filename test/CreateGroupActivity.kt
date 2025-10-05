package com.example.chatapp

import android.os.Bundle
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class CreateGroupActivity : AppCompatActivity() {
    private val groupViewModel: GroupViewModel by viewModels()
    private val usersViewModel: UsersViewModel by viewModels()

    private lateinit var selectedMembers: MutableSet<String>
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_group)

        selectedMembers = mutableSetOf()

        val token = intent.getStringExtra("token") ?: ""
        val userId = intent.getStringExtra("userId") ?: ""
        val userNickname = intent.getStringExtra("userNickname") ?: "User $userId"

        groupViewModel.token = token
        groupViewModel.userId = userId
        groupViewModel.userNickname = userNickname

        usersViewModel.token = token
        usersViewModel.userId = userId


        val groupNameInput = findViewById<EditText>(R.id.groupNameInput)
        val membersListView = findViewById<ListView>(R.id.membersListView)
        val createButton = findViewById<Button>(R.id.createGroupButton)
        val selectedMembersText = findViewById<TextView>(R.id.selectedMembersText)


        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice)
        membersListView.adapter = adapter
        membersListView.choiceMode = ListView.CHOICE_MODE_MULTIPLE


        membersListView.setOnItemClickListener { _, _, position, _ ->
            val userString = adapter.getItem(position)
            if (userString != null) {

                val userId = userString.substringAfter("(ID: ").substringBefore(")")
                if (membersListView.isItemChecked(position)) {
                    selectedMembers.add(userId)
                } else {
                    selectedMembers.remove(userId)
                }
                updateSelectedMembersText(selectedMembersText)
            }
        }


        createButton.setOnClickListener {
            val groupName = groupNameInput.text.toString().trim()
            if (groupName.isEmpty()) {
                Toast.makeText(this, "Введите название группы", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedMembers.isEmpty()) {
                Toast.makeText(this, "Выберите участников группы", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                groupViewModel.createGroup(groupName, selectedMembers.toList())
            }
        }


        groupViewModel.createdGroup.observe(this) { group ->
            if (group != null) {
                Toast.makeText(this, "Группа \"${group.name}\" создана!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }


        usersViewModel.onlineUsers.observe(this) { users ->
            adapter.clear()

            users.forEach { user ->
                adapter.add("${user.nickname} (ID: ${user.id})")
            }
        }


        lifecycleScope.launch {
            usersViewModel.connectWebSocket()
            groupViewModel.connectWebSocket()
        }
    }

    private fun updateSelectedMembersText(textView: TextView) {
        textView.text = "Выбранные участники: ${selectedMembers.size}"
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            groupViewModel.close()
            usersViewModel.close()
        }
    }
}