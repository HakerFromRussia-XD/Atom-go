package com.atomgo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtomGoApp(
                appViewModel = appViewModel,
                loginViewModel = loginViewModel
            )
        }
    }

    companion object {
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}
