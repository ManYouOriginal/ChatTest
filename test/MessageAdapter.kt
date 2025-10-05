package com.example.chatapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(private val listener: Listener) : ListAdapter<MessageOut, MessageAdapter.ViewHolder>(DiffCallback) {

    interface Listener {
        fun onMessageClicked(id: String)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val content: TextView = itemView.findViewById(android.R.id.text1)
    }

    object DiffCallback : DiffUtil.ItemCallback<MessageOut>() {
        override fun areItemsTheSame(oldItem: MessageOut, newItem: MessageOut): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MessageOut, newItem: MessageOut): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = getItem(position)
        holder.content.text = "${message.sender_id}: ${message.content}"
        holder.itemView.setOnClickListener {
            listener.onMessageClicked(message.id)
        }
    }
}