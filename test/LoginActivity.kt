package com.example.chatapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

// Data classes moved to separate Models.kt file
// data class UserCreate(val nickname: String)
// data class TokenResponse(val access_token: String, val token_type: String, val user_id: String)
// data class UserOut(val id: String, val nickname: String, val online: Boolean)

interface ApiService {
    @retrofit2.http.POST("/api/login")
    fun login(@retrofit2.http.Body user: UserCreate): Call<TokenResponse>
}

class LoginActivity : AppCompatActivity() {
    private lateinit var api: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.0.186:8000")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        api = retrofit.create(ApiService::class.java)

        val nicknameInput = findViewById<EditText>(R.id.nickname)
        val btn = findViewById<Button>(R.id.loginBtn)

        btn.setOnClickListener {
            val nick = nicknameInput.text.toString().trim()
            if (nick.isNotEmpty()) {
                // Show loading state
                btn.isEnabled = false
                btn.text = "Подключение..."

                api.login(UserCreate(nick)).enqueue(object: Callback<TokenResponse> {
                    override fun onResponse(call: Call<TokenResponse>, response: Response<TokenResponse>) {
                        btn.isEnabled = true
                        btn.text = "Войти"

                        if (response.isSuccessful) {
                            val token = response.body()
                            if (token != null) {
                                // Successfully logged in - navigate to UsersActivity
                                val intent = Intent(this@LoginActivity, UsersActivity::class.java)
                                intent.putExtra("token", token.access_token)
                                intent.putExtra("userId", token.user_id)
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this@LoginActivity, "Ошибка: пустой ответ от сервера", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            // Handle HTTP error codes
                            val errorMessage = when (response.code()) {
                                404 -> "Сервер не найден"
                                500 -> "Ошибка сервера"
                                401 -> "Неавторизованный доступ"
                                else -> "Ошибка подключения: ${response.code()}"
                            }
                            Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<TokenResponse>, t: Throwable) {
                        btn.isEnabled = true
                        btn.text = "Войти"

                        // Handle network failures
                        val errorMessage = when {
                            t.message?.contains("Unable to resolve host") == true -> "Сервер не доступен. Проверьте подключение"
                            t.message?.contains("Failed to connect") == true -> "Не удалось подключиться к серверу"
                            else -> "Сетевая ошибка: ${t.message}"
                        }
                        Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                        t.printStackTrace() // For debugging in Logcat
                    }
                })
            } else {
                Toast.makeText(this, "Введите никнейм", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset button state when returning to login screen
        val btn = findViewById<Button>(R.id.loginBtn)
        btn.isEnabled = true
        btn.text = "Войти"
    }
}