package com.atomgo.android

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.atomgo.shared.api.AtomGoApiClient
import kotlinx.coroutines.runBlocking

class AdminHomeActivity : AppCompatActivity() {

    private val apiClient = AtomGoApiClient(BackendConfig.BASE_URL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_home)

        val logoutButton = findViewById<Button>(R.id.logoutButton)
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        val contentText = findViewById<TextView>(R.id.contentText)
        val errorText = findViewById<TextView>(R.id.errorText)

        logoutButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }

        refreshButton.setOnClickListener {
            loadClients(contentText, errorText)
        }

        loadClients(contentText, errorText)
    }

    override fun onDestroy() {
        super.onDestroy()
        apiClient.close()
    }

    private fun loadClients(contentText: TextView, errorText: TextView) {
        val accessToken = intent.getStringExtra(MainActivity.EXTRA_ACCESS_TOKEN).orEmpty()
        if (accessToken.isBlank()) {
            errorText.text = "Не удалось загрузить данные\nНет access token"
            contentText.text = ""
            return
        }

        contentText.text = "Загрузка..."
        errorText.text = ""
        Thread {
            try {
                val clients = runBlocking {
                    apiClient.fetchAdminClients(accessToken = accessToken)
                }
                runOnUiThread {
                    contentText.text = buildString {
                        appendLine("Клиентов: ${clients.size}")
                        appendLine()
                        clients.forEach { client ->
                            appendLine("${client.fullName} (${client.clientId})")
                            appendLine("Велосипед: ${client.bikeModel}")
                            appendLine("Статус: ${client.statusText}")
                            appendLine("Долг: ${client.debtRub} ₽")
                            appendLine("Прибыль: ${client.profitRub} ₽")
                            appendLine("Корректировка: ${client.totalAdjustmentRub} ₽")
                            appendLine("-----")
                        }
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    contentText.text = ""
                    errorText.text = "Не удалось загрузить данные\n${error.message}"
                }
            }
        }.start()
    }
}
