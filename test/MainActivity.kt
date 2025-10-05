package com.example.chatapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: implement UI for users and chats
        setContentView(android.R.layout.simple_list_item_1)
    }
}
