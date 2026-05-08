package com.atomgo.android

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.atomgo.shared.api.AtomGoApiClient
import kotlinx.coroutines.runBlocking

class ClientHomeActivity : AppCompatActivity() {

    private val apiClient = AtomGoApiClient(BackendConfig.BASE_URL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_home)

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
            loadDashboard(contentText, errorText)
        }

        loadDashboard(contentText, errorText)
    }

    override fun onDestroy() {
        super.onDestroy()
        apiClient.close()
    }

    private fun loadDashboard(contentText: TextView, errorText: TextView) {
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
                val dashboard = runBlocking {
                    apiClient.fetchClientDashboard(accessToken = accessToken)
                }
                runOnUiThread {
                    contentText.text = buildString {
                        appendLine("Клиент: ${dashboard.clientId}")
                        appendLine("Велосипед: ${dashboard.bikeModel}")
                        appendLine("Аренда с: ${dashboard.rentalStart}")
                        appendLine("Оплачено до: ${dashboard.paidUntil}")
                        appendLine("Долг: ${dashboard.debtRub} ₽")
                        appendLine("Остаток: ${dashboard.balanceRub} ₽")
                        appendLine("Корректировка: ${dashboard.totalAdjustmentRub} ₽")
                        appendLine()
                        appendLine("Оплата:")
                        appendLine("1 день: ${dashboard.presets.dayRub} ₽")
                        appendLine("1 неделя: ${dashboard.presets.weekRub} ₽")
                        appendLine("2 недели: ${dashboard.presets.twoWeeksRub} ₽")
                        appendLine("1 месяц: ${dashboard.presets.monthRub} ₽")
                        appendLine("Ровно долг: ${dashboard.presets.debtExactRub} ₽")
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
