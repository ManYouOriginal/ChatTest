package com.example.chatapp

import android.content.Context

object SharedPrefs {
    private const val PREFS = "chat_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"

    fun saveToken(context: Context, token: String, userId: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putString(KEY_TOKEN, token).putString(KEY_USER_ID, userId).apply()
    }

    fun loadToken(context: Context): Pair<String?, String?> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Pair(p.getString(KEY_TOKEN, null), p.getString(KEY_USER_ID, null))
    }
}
