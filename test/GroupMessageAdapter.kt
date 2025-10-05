package com.example.chatapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class GroupMessageAdapter(private val currentUserId: String) : ListAdapter<GroupMessage, GroupMessageAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderName: TextView = itemView.findViewById(R.id.senderName)
        val content: TextView = itemView.findViewById(R.id.messageContent)
        val timestamp: TextView = itemView.findViewById(R.id.timestamp)
    }

    object DiffCallback : DiffUtil.ItemCallback<GroupMessage>() {
        override fun areItemsTheSame(oldItem: GroupMessage, newItem: GroupMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GroupMessage, newItem: GroupMessage): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = getItem(position)

        if (message.sender_id == currentUserId) {
            // Сообщение текущего пользователя
            holder.senderName.text = "Вы"
            holder.senderName.setTextColor(holder.itemView.context.getColor(android.R.color.holo_blue_dark))
        } else {
            // Сообщение другого пользователя
            holder.senderName.text = message.sender_nickname
            holder.senderName.setTextColor(holder.itemView.context.getColor(android.R.color.darker_gray))
        }

        holder.content.text = message.content
        holder.timestamp.text = formatTimestamp(message.created_at)
    }

    private fun formatTimestamp(timestamp: String): String {
        return try {
            val time = timestamp.toLong()
            android.text.format.DateFormat.format("HH:mm", time).toString()
        } catch (e: Exception) {
            "now"
        }
    }
}