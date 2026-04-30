package com.atomgo.android

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val backendBaseUrl = "http://10.0.2.2:8080/api/v1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val loginInput = findViewById<EditText>(R.id.loginInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val statusText = findViewById<TextView>(R.id.statusText)

        loginButton.setOnClickListener {
            val login = loginInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (login.isBlank() || password.isBlank()) {
                statusText.text = "Статус: введите логин и пароль"
                return@setOnClickListener
            }

            statusText.text = "Статус: выполняю вход..."

            Thread {
                try {
                    val (role, token) = doLogin(login, password)
                    runOnUiThread {
                        statusText.text = "Статус: вход выполнен, роль: $role\nToken: ${token.take(12)}..."
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        statusText.text = "Статус: ошибка входа: ${error.message}"
                    }
                }
            }.start()
        }
    }

    private fun doLogin(login: String, password: String): Pair<String, String> {
        val body = JSONObject().apply {
            put("login", login)
            put("password", password)
        }.toString()

        val url = URL("$backendBaseUrl/auth/login")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7000
            readTimeout = 7000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(body)
            writer.flush()
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use(BufferedReader::readText)

        if (status !in 200..299) {
            throw IllegalStateException("HTTP $status: $response")
        }

        val json = JSONObject(response)
        val role = json.optString("role")
        val token = json.optString("access_token")
        if (role.isBlank() || token.isBlank()) {
            throw IllegalStateException("Некорректный ответ backend")
        }
        return role to token
    }
}
