package com.atomgo.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.atomgo.android.domain.usecase.AdminUseCases
import com.atomgo.android.domain.usecase.AuthUseCases
import com.atomgo.android.domain.usecase.ClientUseCases

class LoginViewModelFactory(
    private val authUseCases: AuthUseCases
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(authUseCases = authUseCases) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class ClientHomeViewModelFactory(
    private val clientUseCases: ClientUseCases
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ClientHomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ClientHomeViewModel(clientUseCases = clientUseCases) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class AdminHomeViewModelFactory(
    private val adminUseCases: AdminUseCases
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminHomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AdminHomeViewModel(adminUseCases = adminUseCases) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
