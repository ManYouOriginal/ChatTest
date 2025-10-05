package com.example.chatapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class GroupsAdapter(private val listener: Listener) : ListAdapter<Group, GroupsAdapter.ViewHolder>(DiffCallback) {

    interface Listener {
        fun onGroupClicked(group: Group)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val groupName: TextView = itemView.findViewById(R.id.groupName)
        val memberCount: TextView = itemView.findViewById(R.id.memberCount)
        val creatorInfo: TextView = itemView.findViewById(R.id.creatorInfo)
    }

    object DiffCallback : DiffUtil.ItemCallback<Group>() {
        override fun areItemsTheSame(oldItem: Group, newItem: Group): Boolean {
            return oldItem.group_id == newItem.group_id
        }

        override fun areContentsTheSame(oldItem: Group, newItem: Group): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = getItem(position)
        holder.groupName.text = group.name
        holder.memberCount.text = "Участников: ${group.members.size}"
        holder.creatorInfo.text = "Создатель: User ${group.creator}"

        holder.itemView.setOnClickListener {
            listener.onGroupClicked(group)
        }
    }
}