package com.atomgo.android

object BackendConfig {
    private const val LOCAL_BASE_URL = "http://10.0.2.2:8080/api/v1"

    private val backendEnv = BuildConfig.ATOMGO_ENV.trim().lowercase()
    private val backendOverrideUrl = BuildConfig.ATOMGO_BACKEND_URL.trim().trimEnd('/')
    private val prodBaseUrl = BuildConfig.ATOMGO_BASE_URL_PROD.trim().trimEnd('/')

    val BASE_URL: String = when {
        backendOverrideUrl.isNotEmpty() -> backendOverrideUrl
        backendEnv == "prod" -> prodBaseUrl
        else -> LOCAL_BASE_URL
    }

    const val DEFAULT_CLIENT_LOGIN = "client1"
    const val DEFAULT_CLIENT_PASSWORD = "client123"
}
