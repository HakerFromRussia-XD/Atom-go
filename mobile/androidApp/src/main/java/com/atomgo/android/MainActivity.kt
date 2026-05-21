package com.atomgo.android

import com.atomgo.android.presentation.ui.AtomGoApp
import com.atomgo.android.presentation.viewmodel.AdminHomeViewModel
import com.atomgo.android.presentation.viewmodel.AdminHomeViewModelFactory
import com.atomgo.android.presentation.viewmodel.AppViewModel
import com.atomgo.android.presentation.viewmodel.ClientHomeViewModel
import com.atomgo.android.presentation.viewmodel.ClientHomeViewModelFactory
import com.atomgo.android.presentation.viewmodel.LoginViewModel
import com.atomgo.android.presentation.viewmodel.LoginViewModelFactory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val appContainer
        get() = (application as AtomGoApplication).appContainer

    private val appViewModel: AppViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(authUseCases = appContainer.authUseCases)
    }
    private val clientHomeViewModel: ClientHomeViewModel by viewModels {
        ClientHomeViewModelFactory(clientUseCases = appContainer.clientUseCases)
    }
    private val adminHomeViewModel: AdminHomeViewModel by viewModels {
        AdminHomeViewModelFactory(adminUseCases = appContainer.adminUseCases)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtomGoApp(
                appViewModel = appViewModel,
                loginViewModel = loginViewModel,
                clientHomeViewModel = clientHomeViewModel,
                adminHomeViewModel = adminHomeViewModel
            )
        }
    }

    companion object {
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}
