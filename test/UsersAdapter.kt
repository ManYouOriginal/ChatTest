package com.example.chatapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class UsersAdapter(private val listener: Listener) : ListAdapter<UserOut, UsersAdapter.UserVH>(DIFF) {

    interface Listener { fun onUserClicked(user: UserOut) }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<UserOut>() {
            override fun areItemsTheSame(oldItem: UserOut, newItem: UserOut) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: UserOut, newItem: UserOut) = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserVH {
        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return UserVH(v)
    }

    override fun onBindViewHolder(holder: UserVH, position: Int) {
        val u = getItem(position)
        holder.bind(u)
        holder.itemView.setOnClickListener { listener.onUserClicked(u) }
    }

    class UserVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val t1 = itemView.findViewById<TextView>(android.R.id.text1)
        private val t2 = itemView.findViewById<TextView>(android.R.id.text2)
        fun bind(u: UserOut) {
            t1.text = u.nickname
            t2.text = if (u.online) "online" else "offline"
        }
    }
}
