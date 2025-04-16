package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.AuthRepository

class LogoutUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(clearStoredCredentials: Boolean = true) {
        return authRepository.logout(clearStoredCredentials = clearStoredCredentials)
    }
}