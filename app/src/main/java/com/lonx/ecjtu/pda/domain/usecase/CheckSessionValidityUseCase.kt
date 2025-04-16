package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.AuthRepository

class CheckSessionValidityUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Boolean {
        return authRepository.checkSessionValidity()
    }
}