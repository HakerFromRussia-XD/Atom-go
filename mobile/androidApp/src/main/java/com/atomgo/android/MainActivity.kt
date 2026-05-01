package com.atomgo.android

import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.atomgo.shared.api.AtomGoApiClient
import com.atomgo.shared.api.UserRole
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    private val apiClient = AtomGoApiClient(BackendConfig.BASE_URL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val loginInput = findViewById<EditText>(R.id.loginInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val statusText = findViewById<TextView>(R.id.statusText)

        loginInput.setText(BackendConfig.DEFAULT_CLIENT_LOGIN)
        passwordInput.setText(BackendConfig.DEFAULT_CLIENT_PASSWORD)

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
                    val session = runBlocking {
                        apiClient.login(login, password)
                    }
                    runOnUiThread {
                        statusText.text = "Статус: вход выполнен, роль: ${session.role.name.lowercase()}\nToken: ${session.accessToken.take(12)}..."
                        routeByRole(session.role, session.accessToken)
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        statusText.text = "Статус: ошибка входа: ${error.message}"
                    }
                }
            }.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        apiClient.close()
    }

    private fun routeByRole(role: UserRole, accessToken: String) {
        val target = when (role) {
            UserRole.CLIENT -> Intent(this, ClientHomeActivity::class.java)
            UserRole.ADMIN -> Intent(this, AdminHomeActivity::class.java)
        }

        target.putExtra(EXTRA_ACCESS_TOKEN, accessToken)
        startActivity(target)
    }

    companion object {
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}
