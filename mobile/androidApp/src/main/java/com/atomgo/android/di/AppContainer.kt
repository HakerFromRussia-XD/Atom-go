package com.atomgo.android.di

import android.app.Application
import com.atomgo.android.data.repository.DefaultAdminRepository
import com.atomgo.android.data.repository.DefaultAuthRepository
import com.atomgo.android.data.repository.DefaultClientRepository
import com.atomgo.android.domain.repository.AdminRepository
import com.atomgo.android.domain.repository.AuthRepository
import com.atomgo.android.domain.repository.ClientRepository
import com.atomgo.android.domain.usecase.AdminUseCases
import com.atomgo.android.domain.usecase.AuthUseCases
import com.atomgo.android.domain.usecase.ClientUseCases

interface AppContainer {
    val authUseCases: AuthUseCases
    val clientUseCases: ClientUseCases
    val adminUseCases: AdminUseCases
}

class DefaultAppContainer(
    application: Application
) : AppContainer {
    private val authRepository: AuthRepository by lazy { DefaultAuthRepository(application) }
    private val clientRepository: ClientRepository by lazy { DefaultClientRepository() }
    private val adminRepository: AdminRepository by lazy { DefaultAdminRepository() }

    override val authUseCases: AuthUseCases by lazy { AuthUseCases(authRepository = authRepository) }
    override val clientUseCases: ClientUseCases by lazy { ClientUseCases(clientRepository = clientRepository) }
    override val adminUseCases: AdminUseCases by lazy { AdminUseCases(adminRepository = adminRepository) }
}
