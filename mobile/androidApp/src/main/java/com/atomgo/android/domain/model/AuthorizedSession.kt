package com.atomgo.android.domain.model

import com.atomgo.shared.api.UserRole

data class AuthorizedSession(
    val accessToken: String,
    val role: UserRole
)
